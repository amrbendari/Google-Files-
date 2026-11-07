# Google File+

**A modern Android file manager with powerful tools, root access, dual-pane browsing, and secure private storage.**

Google File+ is planned as a tablet-friendly Android file manager with a Google-inspired Material UI, adaptive light/dark themes, multilingual support, and an optional Liquid Glass navigation style.

## Planned features

- Material 3 interface with system theme support and dynamic colors where supported.
- Arabic and English localization, including right-to-left layouts.
- File operations: browse, search, sort, copy, move, rename, share, and delete.
- Optional root explorer using explicit user-granted `su` access.
- Dual-pane browsing for managing two locations side by side.
- Archive creation and extraction for ZIP, RAR, 7Z, TAR, GZ, BZ2, and XZ, subject to library and format support.
- Recycle Bin with restore and permanent-delete actions; automatic cleanup after 90 days.
- Hidden-file visibility controls.
- A visible **Space** entry protected by a password and supported device biometrics, with encrypted private storage.
- Built-in previews where practical and external-app handling for other file types.
- Floating bottom navigation with Material and optional Liquid Glass appearances.

## Design principles

1. Security before convenience: private files are encrypted, not merely hidden.
2. No silent root actions: root access is requested explicitly and privileged operations are clearly identified.
3. Respect Android permissions and scoped-storage rules.
4. Keep the interface responsive on tablets in portrait and landscape.
5. Do not claim a feature is complete until it is implemented and tested.

## Project status

**Early development — features are being implemented in stages.** The feature list above describes the project goals, not a claim that every feature is already available.

## Build

The Android project uses Kotlin and Jetpack Compose. Open the project in a compatible Android Studio version and allow Gradle sync before building.

## Disclaimer

Google File+ is an independent project and is not affiliated with or endorsed by Google.