# ALPS integration checklist

Legend: **Implemented** = source added; **Hardware test required** = compilation or real-device behavior has not yet been confirmed; **Blocked** = requires head-unit/vendor support.

| Item | Status | Notes |
|---|---|---|
| Existing ALPS CarPlay-style dashboard and layout | Implemented | Existing visual structure retained. |
| MediaSession metadata and album art | Hardware test required | Reads media-session art/URIs and active MediaStyle notification artwork; Bluetooth/ZLINK must expose metadata or artwork to Android. |
| Music progress drag-to-seek | Hardware test required | Dashboard and full player now share a guarded seek command; the selected player must implement seek. |
| Full music player and transport controls | Implemented | Previous, play/pause, stop and next controls remain. |
| Volume booster | Hardware test required | LoudnessEnhancer availability and clean gain must be checked on the YT7260D. |
| Brightness control | Implemented | User must grant Modify system settings. |
| Wi-Fi, Bluetooth and hotspot settings | Implemented | Opens the corresponding Android settings panels; does not bypass Android restrictions. |
| App drawer and search | Implemented | Existing search retained. |
| App favorites | Implemented | Long-press an app; favorites persist with Preferences DataStore and sort to the top. |
| Neshan map and live location | Hardware test required | Existing MapLibre/Neshan map and location flow retained; location permission and API key required. |
| Phone, contacts and call history | Implemented | Uses public Android intents; availability depends on head-unit apps. |
| Settings panel | Implemented | Includes system settings, brightness, connectivity, weather, notifications and recent apps. |
| Weather | Hardware test required | Uses current device location and Open-Meteo; requires location and internet. |
| Notifications panel | Hardware test required | Lists active notifications exposed to the Notification Listener; requires Notification Access. |
| Recent apps | Implemented with limitation | Shows apps launched through ALPS; Android does not expose the system Recents task list to ordinary third-party apps. |
| Persian Vazirmatn font | Implemented | Bundled by the build workflow with its OFL license. |
| Persistent settings | Implemented | Favorites use DataStore; existing audio booster and recent-app history use SharedPreferences. |
| Steering-wheel keys, reverse camera and vehicle data | Blocked | Requires a documented head-unit API, broadcast or vendor permission. |

**Build/device status:** GitHub Actions run #168 completed successfully, including APK packaging, package-name verification and signature verification. The final YT7260D hardware test is still required before calling runtime behavior complete.
