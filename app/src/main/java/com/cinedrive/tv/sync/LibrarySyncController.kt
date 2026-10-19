package com.cinedrive.tv.sync

import android.content.Intent
import com.cinedrive.tv.data.storage.StoragePermissionManager
import com.cinedrive.tv.domain.model.StorageAccessLevel
import com.cinedrive.tv.domain.model.UsbVolume
import com.cinedrive.tv.domain.repository.LibraryRepository
import com.cinedrive.tv.domain.repository.StorageRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lifecycle of a single volume's library index.
 */
sealed interface VolumeIndexState {

    /** Waiting to be scanned (behind another volume's scan). */
    data object Queued : VolumeIndexState

    /** Recursive scan in progress; [indexedFiles] is throttled progress. */
    data class Indexing(val indexedFiles: Int) : VolumeIndexState

    /** Scan finished this session; counts from [LibraryIndexResult]. */
    data class Done(
        val files: Int,
        val movies: Int,
        val shows: Int,
        val episodes: Int,
    ) : VolumeIndexState

    /** Scan failed (drive yanked mid-scan, IO error). Retry on re-plug/rescan. */
    data class Failed(val message: String) : VolumeIndexState

    /** Drive is present in the volume list but not mounted. */
    data object Unmounted : VolumeIndexState
}

/** One volume plus where its index currently stands. */
data class VolumeSyncStatus(
    val volume: UsbVolume,
    val state: VolumeIndexState,
)

/**
 * Top-level sync state consumed by the browse UI: the permission gate plus a
 * live status line per attached drive.
 */
data class LibrarySyncState(
    val access: StorageAccessLevel = StorageAccessLevel.NEEDS_ALL_FILES_ACCESS,
    val volumes: List<VolumeSyncStatus> = emptyList(),
) {
    val anyIndexing: Boolean
        get() = volumes.any { it.state is VolumeIndexState.Indexing || it.state is VolumeIndexState.Queued }

    val mountedCount: Int
        get() = volumes.count { it.volume.isMounted }

    val mountedUuids: Set<String>
        get() = volumes.filter { it.volume.isMounted }.map { it.volume.uuid }.toSet()
}

/**
 * App-scoped orchestrator of the media library (Step 4). Replaces the Step 3
 * smoke-screen ViewModel and owns everything that must survive navigation:
 *
 *  • watches the volume list (hot-plug events from [StorageRepository])
 *  • auto-indexes every newly mounted drive exactly once per mount, with
 *    throttled progress updates for the status line
 *  • exposes the storage permission gate; the UI only launches the Intents
 *  • supports a manual full rescan
 *
 * Idempotence: [LibraryRepository.indexVolume] refreshes metadata, preserves
 * playback progress and prunes vanished files, so "index once per mount" is
 * always safe.
 */
@Singleton
class LibrarySyncController @Inject constructor(
    private val storage: StorageRepository,
    private val library: LibraryRepository,
    private val permissions: StoragePermissionManager,
) {

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("library-sync"))

    private val _state = MutableStateFlow(
        LibrarySyncState(access = permissions.currentAccessLevel())
    )
    val state: StateFlow<LibrarySyncState> = _state.asStateFlow()

    /** Volumes indexed during this app session (guarded by [gate]). */
    private val indexed = mutableSetOf<String>()

    /** Serialises reconcile passes; one scan at a time, volumes queue behind. */
    private val gate = Mutex()

    init {
        storage.startWatching()
        scope.launch {
            storage.volumes.collect { reconcile() }
        }
    }

    /**
     * Re-check permissions and pick up newly attached drives. Called on every
     * Activity ON_RESUME — returning from the all-files-access settings page
     * lands here and kicks off the first index.
     */
    fun refreshAccess() {
        _state.update { it.copy(access = permissions.currentAccessLevel()) }
        scope.launch { reconcile() }
    }

    /** Force re-index of every mounted volume (keeps playback progress). */
    fun rescanAll() {
        if (_state.value.access != StorageAccessLevel.GRANTED) return
        scope.launch {
            gate.withLock {
                indexed.clear()
                _state.update { st ->
                    st.copy(
                        volumes = st.volumes.map { vs ->
                            if (vs.volume.isMounted) vs.copy(state = VolumeIndexState.Queued) else vs
                        }
                    )
                }
                indexPendingVolumes()
            }
        }
    }

    // ── Permission intents (UI launches them; controller owns the state) ──

    fun allFilesAccessIntent(): Intent = permissions.allFilesAccessIntent()

    fun legacyPermissions(): Array<String> = permissions.legacyPermissions()

    // ── Internals ──────────────────────────────────────────────────────────

    private suspend fun reconcile() = gate.withLock {
        _state.update { it.copy(access = permissions.currentAccessLevel()) }
        if (_state.value.access != StorageAccessLevel.GRANTED) return@withLock
        syncVolumeList()
        indexPendingVolumes()
    }

    /**
     * Mirror the current volume list into the UI state, preserving statuses
     * we already have and defaulting new entries appropriately.
     */
    private fun syncVolumeList() {
        val volumes = storage.volumes.value
        _state.update { st ->
            val existing = st.volumes.associateBy { it.volume.uuid }
            st.copy(
                volumes = volumes.map { volume ->
                    val previous = existing[volume.uuid]
                    val nextState = when {
                        !volume.isMounted ->
                            if (previous?.state is VolumeIndexState.Indexing) previous.state
                            else VolumeIndexState.Unmounted

                        previous != null && previous.state != VolumeIndexState.Unmounted -> previous.state

                        volume.uuid in indexed ->
                            VolumeIndexState.Done(files = 0, movies = 0, shows = 0, episodes = 0)

                        else -> VolumeIndexState.Queued
                    }
                    VolumeSyncStatus(volume, nextState)
                }
            )
        }
    }

    private suspend fun indexPendingVolumes() {
        val pending = _state.value.volumes
            .filter { it.volume.isMounted && it.volume.uuid !in indexed }
            .map { it.volume }
        for (volume in pending) indexVolume(volume)
    }

    private suspend fun indexVolume(volume: UsbVolume) {
        indexed += volume.uuid
        setStatus(volume.uuid, VolumeIndexState.Queued)
        try {
            var lastReported = 0
            val result = library.indexVolume(volume.uuid) { indexedFiles ->
                // Throttle: one recomposition per ~20 files, not per file.
                if (indexedFiles - lastReported >= 20) {
                    lastReported = indexedFiles
                    setStatus(volume.uuid, VolumeIndexState.Indexing(indexedFiles))
                }
            }
            setStatus(
                volume.uuid,
                VolumeIndexState.Done(
                    files = result.filesScanned,
                    movies = result.movies,
                    shows = result.shows,
                    episodes = result.episodes,
                )
            )
            // Auto-frames for titles without sidecar/NFO art. Runs on the
            // controller scope OUTSIDE the sync gate, so the next volume can
            // index immediately while frames trickle in (one DB write per
            // frame → the browse Flows repopulate cards live).
            scope.launch {
                runCatching { library.generateMissingThumbnails(volume.uuid) }
                    .onFailure { error ->
                        android.util.Log.w("LibrarySync", "thumbnail pass failed", error)
                    }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            // Drive yanked mid-scan or transient IO error: allow a retry on
            // the next trigger (re-plug, rescan, resume) instead of dying.
            indexed -= volume.uuid
            setStatus(
                volume.uuid,
                VolumeIndexState.Failed(error.message ?: error.javaClass.simpleName)
            )
        }
    }

    private fun setStatus(uuid: String, state: VolumeIndexState) {
        _state.update { st ->
            st.copy(
                volumes = st.volumes.map { vs ->
                    if (vs.volume.uuid == uuid) vs.copy(state = state) else vs
                }
            )
        }
    }
}
