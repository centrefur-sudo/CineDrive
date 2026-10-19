package com.cinedrive.tv.data.thumbs

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Auto-thumbnails: pulls a frame out of a video file for titles that have no
 * sidecar poster (poster.jpg / <name>.jpg) and no .nfo <thumb>.
 *
 *  • Framework decoder ([MediaMetadataRetriever]) — H.264/HEVC/MPEG-4 keyframes
 *    decode on every Android 10+ TV SoC via MediaCodec; exotic containers that
 *    the framework cannot demux simply produce no thumbnail and the card keeps
 *    its icon fallback. LibVLC stays reserved for actual playback.
 *  • Cache lives in the app-private filesDir, keyed by md5(path)-sizeBytes, so
 *    a re-encoded file gets a fresh frame while re-plugging the same drive
 *    never re-decodes.
 *  • Frames are taken ~5 s in (past studio fade-ins; OPTION_CLOSEST_SYNC
 *    guarantees a cheap keyframe seek) and scaled to at most [MAX_WIDTH] px —
 *    card-sized art, not a 1080p memory hog.
 */
@Singleton
class VideoThumbnailer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dir: File
        get() = File(context.filesDir, "thumbs").apply { mkdirs() }

    /**
     * JPEG path for [videoPath]'s frame, extracted on first call and cached
     * afterwards. Returns null when the file is unreadable or the framework
     * decoder cannot produce a frame.
     */
    suspend fun thumbnailFor(videoPath: String, sizeBytes: Long): String? =
        withContext(Dispatchers.IO) {
            val key = md5(videoPath)
            val target = File(dir, "$key-$sizeBytes.jpg")
            if (target.isFile && target.length() > 0) {
                return@withContext target.absolutePath
            }

            // Same video at a different size (re-encoded): sweep stale frames.
            dir.listFiles()?.forEach { stale ->
                if (stale.name.startsWith("$key-") && stale.name != target.name) {
                    stale.delete()
                }
            }

            val frame = extractFrame(videoPath) ?: return@withContext null
            try {
                FileOutputStream(target).use { out ->
                    frame.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                target.absolutePath
            } catch (error: Throwable) {
                target.delete()
                null
            } finally {
                frame.recycle()
            }
        }

    private fun extractFrame(videoPath: String): Bitmap? {
        val file = File(videoPath)
        val pfd = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (error: Throwable) {
            return null // drive yanked / file gone mid-pass
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(pfd.fileDescriptor)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            // ~5 s in for features; quarter-point for clips shorter than 15 s.
            val atUs = if (durationMs > 15_000) {
                5_000_000L
            } else {
                (durationMs / 4).coerceAtLeast(1L) * 1000L
            }
            retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.let { scaledDown(it) }
        } catch (error: Throwable) {
            null // unsupported container/codec — icon fallback remains
        } finally {
            try {
                retriever.release()
            } catch (ignored: Throwable) {
            }
            try {
                pfd.close()
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun scaledDown(frame: Bitmap): Bitmap {
        if (frame.width <= MAX_WIDTH) return frame
        val height = (frame.height.toLong() * MAX_WIDTH / frame.width).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(frame, MAX_WIDTH, height, true)
        if (scaled != frame) frame.recycle()
        return scaled
    }

    private fun md5(text: String): String =
        MessageDigest.getInstance("MD5")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_WIDTH = 512
        const val JPEG_QUALITY = 82
    }
}
