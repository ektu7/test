# Halo Gallery + Halo Video — version 3.0.0 (Android)

Two standalone, **original Samsung-inspired** Android apps: Gallery package `com.halogallery.gallery30`, Video package `com.halogallery.video31`. Both can be installed alongside v2 versions (package IDs intentionally different to avoid debug signing conflicts).

## Gallery
- Timeline of photos **and videos**, albums, video tab, favorites, search, share, zoom.
- Tap a video for preview, then **Open in Video Player**; 3-dot menu has the same action.
- Gallery menu -> Settings -> toggle **Open videos directly in Halo Video**.
- If Halo Video isn't installed, dialog offers the Android app chooser.
- Media stays on device. Read-only media permissions.

## Video Player
- Accepts `ACTION_VIEW` of `content://` local video URIs from Gallery or other compatible apps.
- Previous/next video buttons; +/-10 seconds; drag the native seek bar to any timestamp.
- Swipe horizontally across video to preview and seek a timestamp (width of screen = 90 sec); double-tap left/right = +/-10 sec.
- Vertical swipe left: screen brightness; vertical swipe right: music volume.
- Playback speed, repeat, background-audio service/notification, picture in picture, rotation, sharing and favorites.
- Supports local file formats decoded by the device's Android MediaPlayer.

## Build

Using JDK 17, Android SDK 35, Gradle 8.11.1:

```sh
gradle -p HaloGallery :app:assembleGalleryDebug :app:assembleVideoDebug
```

APKs are generated under `HaloGallery/app/build/outputs/apk/gallery/debug/` and `.../video/debug/`.

Neither app is a Samsung proprietary binary; features requiring Samsung libraries, AI, Cloud or their editor are not included.
