# TX Music Player

An original Android offline music player starter project built with Kotlin, Jetpack Compose, Android MediaStore and AndroidX Media3.

## Included
- TX-branded Compose interface with code-drawn TX logo mark
- Dark/light theme toggle
- Local audio scanning via MediaStore
- Song search and list
- Favorites for the current app session
- Playback queue, next/previous, shuffle and repeat
- MediaSessionService for background playback and system media controls
- Notification and lock-screen controls provided by Media3

## Build
1. Open this folder in a recent Android Studio.
2. Allow Gradle to sync.
3. Connect an Android device or start an emulator.
4. Run the `app` configuration.
5. To make an APK, choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

## Notes
- Audio permission is requested at runtime.
- This starter is functional but not yet a fully feature-complete commercial player: album/artist/folder drill-down, persistent Room playlists, recently/most played tracking, sleep timer, artwork extraction, and Bengali localization need further implementation.
- The generated vector logo is a simple original TX monogram, not a trademark claim.
