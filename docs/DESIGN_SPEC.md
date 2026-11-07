# Google File+ — Product & UI Specification

## Product identity
- App name: **Google File+**
- Package namespace: `com.amrbendari.googlefileplus`
- Platform: Android; tablet-first, responsive on phones.
- Visual language: Google-inspired Material 3, without presenting the app as an official Google product.
- App icon: white base with a simple folder/file symbol using a restrained Google-inspired four-color palette.

## Appearance
- Follow the system light/dark setting by default.
- Use Android dynamic color when available; provide a Material 3 fallback palette on older supported versions.
- Floating bottom navigation, inset from screen edges with rounded pill geometry and subtle elevation.
- Navigation appearance setting:
  - **Material**: opaque or softly elevated Material 3 surface.
  - **Liquid Glass**: translucent surface, layered highlights, soft border, and background blur where platform performance permits.
- Liquid Glass is an optional navigation treatment, not a requirement to make the entire app translucent.
- Respect reduced-motion and system accessibility settings.

## Navigation destinations
- Home / Recent
- Browse
- Space
- Trash
- Settings

## File operations
- Browse internal/shared storage through Android's supported document and storage APIs.
- Support multi-selection, copy, move, rename, share, create folder, delete, sorting, searching, and file details.
- Hidden files are controlled by a clear setting.
- Provide a dual-pane layout on sufficiently wide screens. Each pane has an independent current path and selection.
- For protected system paths, offer root mode only when a working root manager grants `su`. Show clear warnings before destructive system changes.

## Archives
- Provide archive creation and extraction.
- Target formats: ZIP, RAR (including RAR5 where supported), 7Z, TAR, GZIP, BZIP2, and XZ.
- Detect unsupported encryption, split archives, corrupt files, and unsupported compression methods and explain the limitation instead of silently failing.

## Trash
- Deleting through the app moves supported files to an app-managed Trash entry with original location and deletion timestamp metadata.
- Offer restore and permanent deletion.
- Schedule cleanup after 90 days.
- Explain that Android/provider-managed locations and privileged root deletions may have different recovery guarantees.

## Private Space
- Space remains visible in the main navigation and is always shown as locked until authentication succeeds.
- Unlock methods: app password and Android BiometricPrompt (fingerprint or face, depending on secure hardware and system enrollment).
- Encrypt private file contents using vetted Android cryptographic APIs and securely manage keys with Android Keystore.
- Never rely on a hidden folder or filename obfuscation as the security boundary.
- Lock Space when the app goes to background or after a configurable inactivity period.
- Do not promise face authentication if the device only exposes a weak/non-qualifying face unlock method.

## Localization and accessibility
- Arabic and English first; support RTL mirroring.
- Use string resources, not hard-coded UI text.
- Support large font sizes, TalkBack labels, keyboard navigation, and adequate touch targets.

## Security and reliability
- Root is optional and never silently enabled.
- Validate paths and prevent path traversal in archive extraction.
- Avoid extracting archive entries outside the selected destination.
- Use background work for long operations with progress and cancellation.
- Do not claim full format compatibility until tested against real-world archives.

## Delivery milestones
1. Project foundation, theme system, navigation shell, and tablet-responsive layout.
2. Storage browsing and core file operations.
3. Dual-pane mode and archive operations.
4. Trash lifecycle and restore.
5. Encrypted Space and biometric/password authentication.
6. Optional root explorer and privileged file operations.
7. Localization, accessibility, performance, and device testing.