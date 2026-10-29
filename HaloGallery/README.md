# Halo Gallery + Halo Video — independent Android apps (v2.0.0)

Two independent apps built from one offline Android source tree, with no ads, tracking, internet access, or delete permission.

- **Halo Gallery** (`com.halogallery.gallery`): Pictures, Albums, Favorites, photo viewing/zoom and search.
- **Halo Video** (`com.halogallery.video`): Videos, Folders, Favorites, playback/seek/skip/speed and sharing.

Original Samsung-inspired designs, **not Samsung apps**. Proprietary Samsung AI, cloud sync, editor, codecs and feature parity are not included.

Build in Android Studio or run `gradle -p . assembleGalleryDebug assembleVideoDebug`.
Generated APK files: `app/build/outputs/apk/gallery/debug/app-gallery-debug.apk`, `app/build/outputs/apk/video/debug/app-video-debug.apk`.
Apps use distinct IDs and may be installed together. Since these are new IDs, favorites from older Halo Gallery do not transfer automatically.
