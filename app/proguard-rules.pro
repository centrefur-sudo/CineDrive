# ── LibVLC ────────────────────────────────────────────────────────────────
# LibVLC JNI resolves Java classes/methods by name (MediaPlayer, Media,
# IVLCVout, event listeners). Keep the whole package.
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.medialibrary.** { *; }
-dontwarn org.videolan.**

# ── kotlinx.coroutines ────────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**

# Room and Hilt ship their own consumer R8 rules; nothing needed here.
