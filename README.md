# CineDrive

High-performance **Android TV** video player for local USB drives — Kodi-style library,
LibVLC-powered playback, zero cloud.

**Package:** `com.cinedrive.tv` · **minSdk 29** (Android 10) · **targetSdk 34** · **JDK 17**

## Why

TV boxes are the last honest way to watch a local library, but Kodi is heavy and TV
vendor players choke on DTS/PGS. CineDrive is a focused player that mounts your USB
drive, indexes it Kodi-style (`.nfo`, posters, sidecar subtitles), and plays everything
via LibVLC's bundled codecs — no server, no transcoding, no accounts.

## Feature roadmap (5-step build)

| Step | Status | Scope |
|------|--------|-------|
| 1 | ✅ | Gradle + toolchain: AGP 8.10.1 / Gradle 8.11.1 / Kotlin 2.0.21, Compose BOM 2024.12.01, tv-material 1.0.1, LibVLC 3.7.7, Room 2.6.1 (KSP), Hilt 2.52, TV manifest + USB permissions |
| 2 | ✅ | USB storage engine: `StorageVolumeCallback` (API 30+) / `ACTION_MEDIA_MOUNTED` receiver (API 29), volume-UUID identity, all-files-access gate, recursive scanner with sidecar binding (.srt/.vtt/poster/fanart/nfo) |
| 3 | ✅ | Room library cache: volumes / media_items / shows, `NfoParser` (XmlPullParser: movie / episodedetails / tvshow, modern + legacy ratings), `MediaClassifier` (S01E02 & 1x02 heuristics, Season-folder promotion, tvshow.nfo inheritance), progress-preserving re-scan, schema export |
| 4 | ✅ | TV browse UI: permission gate, home rows (continue-watching / movies / TV), movie & TV grids, show→season→episode drill-down, NFO detail pages, D-pad focus cards, cross-volume queries, LibrarySyncController |
| 5 | ✅ | Player: LibVLC 3.7.7 behind a `PlayerEngine` interface, D-pad transport (±10 s / ±1 min, long-press OK = settings), quick settings (audio/subtitle tracks, ±100 ms offsets), 90 % watched rule, auto-next-episode, resume from Room, FLAG_KEEP_SCREEN_ON |

**v1.1.0 additions:** auto-thumbnails — a frame is pulled from every video
that has no sidecar poster / .nfo thumb, cached app-privately, and used on
all cards; English-default audio — multi-audio files start on the labelled
English track unless you pick another one in quick settings. CI builds the
**armeabi-v7a** APK only (the target box); other ABIs: `./gradlew
assembleRelease -Pcinedrive.abiList=arm64-v8a,x86_64` locally.

## Install on a TV box

1. Open the latest **android-ci** run on GitHub → **Artifacts** → download `cinedrive-release-apks`.
2. CI builds **`app-armeabi-v7a-release.apk`** only (the armv7a target box).
   For other devices build locally with `-Pcinedrive.abiList=arm64-v8a` etc.
3. Copy it to a USB stick, plug it into the box.
4. On the box allow sideloading — **no root required** on stock Android TV:
   Settings → Device Preferences → Security & Restrictions → **Unknown sources** →
   enable it for the file manager you use to open the APK.
5. Open the APK from the file manager and install.

**Signing.** Release APKs are signed with `keystore/cinedrive-release.keystore`
(passwords in `keystore.properties`). Both are committed because this repo is
private and CI must produce installable, upgrade-safe builds with zero manual
steps. Consequences: every new build installs **over** the previous one without
losing the library index or watch progress. If the repo ever goes public, move
the keystore to encrypted GitHub secrets and strip it from git history.
Debug builds (`com.cinedrive.tv.debug`) are separate apps with a per-run
ephemeral key — for daily use install the **release** APK.

## Architecture

```
ui (Compose TV)  →  domain (models + repository interfaces)
     │                 ├─ player/    PlaybackMath, EpisodeNavigator (pure rules)
     │                 ↓
     ├─ playback/      PlayerEngine interface + LibVlcPlayerEngine
     ↓
data ── storage/   UsbVolumeWatcher, StoragePermissionManager
     ├─ scanner/   VideoFileScanner, MediaClassifier
     ├─ metadata/  NfoParser (XmlPullParser)
     ├─ indexer/   MediaIndexer (scan → parse → classify → Room tx)
     ├─ db/        Room: volumes, media_items, shows
     └─ repository/ StorageRepositoryImpl, LibraryRepositoryImpl
```

MVVM + Clean Architecture, Coroutines/Flow end-to-end, Hilt DI, single Activity.
The player UI only ever sees `PlayerEngine` — the LibVLC backend is swappable
(a Media3 implementation would not touch ui/ or domain/).

## Key design decisions

- **LibVLC 3.7.7** (`org.videolan.android:libvlc-all`) — one AAR covers MKV/AVI/WebM/MP4,
  AC3/E-AC3/DTS/TrueHD audio, PGS/VobSub/SSA subtitles, no NDK build. Swappable via
  `PlayerEngine` interface (Step 5) if Media3 is ever wanted.
- **Volume UUID identity** — playback progress is keyed to the physical drive: re-plug
  the same stick into any TV port and your resume points follow it; a reformatted
  drive (new UUID) is a fresh library.
- **Storage access** — sideload model à la VLC/Kodi: `MANAGE_EXTERNAL_STORAGE` gate
  (D-pad-friendly) instead of the phone-oriented SAF directory picker.
- **Progress-safe re-index** — every scan is one Room transaction; metadata refreshes,
  resume/watched state is re-attached from existing rows, vanished files are pruned.

## Building

Open in **Android Studio** (Ladybug+) with JDK 17, sync, and run on a TV device or
emulator. The Gradle wrapper jar is not committed — the first sync (or CI) generates
it (`gradle wrapper --gradle-version 8.11.1`). First sync downloads ~100 MB of LibVLC
AAR across 4 ABIs.

CI (`.github/workflows/android.yml`) runs JVM unit tests + `assembleDebug` on every
push to `main` and uploads per-ABI + universal APKs as artifacts.

## Tests

JVM unit tests cover the scanner (sidecar binding, junk skipping, depth cap,
tvshow.nfo inheritance), the NFO parser (all three flavours, lenient/BOM/trailing
junk), the classifier (filename patterns, NFO precedence, Season-folder promotion),
and the Room type converters.
