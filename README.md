# ALPS Minimal

ALPS is a landscape-first Android cockpit launcher with a clean, CarPlay-inspired interface for vehicle head units.

## Included
- Two-page landscape cockpit: searchable app grid and a combined Neshan map / live music widget page, with horizontal swipe navigation.
- Liquid Glass side rail, clock/date header, and passive Zlink connection-status bridge.
- Live GPS position marker in the embedded map panel; no coordinate text overlay.
- Android media-session fallback for track metadata, album artwork, progress and transport controls. Direct iAP2 metadata/artwork ingestion is an integration task, not yet active.
- Five-column app drawer with live search and touch feedback.
- iOS 26 Liquid Glass-inspired app icons for supported app categories, with original app icons retained as fallback.
- Dark navy abstract wallpaper and custom ALPS launcher mark.

## Requirements
- Android 6.0 (API 23) or newer.
- Internet access for Neshan map tiles and map services.
- Location permission for live positioning.
- Notification access for reading active media sessions and controlling compatible media players.

## Build
The GitHub Actions workflow builds a debug APK on pushes that change `overrides/**`, the source ZIP, or the workflow itself. It can also be started manually with **workflow_dispatch**.

The build requires the repository Actions secret `NESHAN_API_KEY`. Never commit the key into source files.

The workflow downloads the selected iOS 26 Liquid Glass PNGs from a pinned iOSIconPack revision and the ALPS wallpaper from Unsplash, then packages the third-party notices into the app assets. See [THIRD_PARTY_NOTICES.md](overrides/THIRD_PARTY_NOTICES.md).

## Testing note
A successful GitHub Actions build confirms compilation, APK packaging, signature verification, and inclusion of the required visual assets. GPS behavior, media-session compatibility, map rendering, touch layout, and performance still need a final on-device test on the target head unit.

## Project layout
- `overrides/MainActivity.kt` — dashboard, Neshan map, GPS, and media controls.
- `overrides/AppAdapter.kt` — app drawer and Liquid Glass icon mapping.
- `overrides/ZlinkBridge.kt` — passive Zlink connection-state observer.
- `overrides/CarPlayNowPlaying.kt` — canonical partial-update media model and verified iAP2 message identifiers.
- `overrides/CarPlayUiComponents.kt` — horizontal two-page cockpit pager.
- `overrides/res/` — layouts, launcher mark, and glass UI resources.
- `.github/workflows/build.yml` — reproducible Android build and asset packaging.
