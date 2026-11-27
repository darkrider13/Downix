# Downly

Android download manager (Kotlin + Jetpack Compose). Paste a link, download inside the app.

## Release flow (automatic)
Push to `main` -> GitHub Actions (`.github/workflows/release.yml`) builds a **signed** APK and publishes it as a
GitHub Release (`v1.0.<run number>`). Installed Downly apps find it through **Settings > Check now**
(and automatically: on app start and twice a day in the background) and install it **over the old version**,
keeping downloads and settings.

- Version name = `downly.versionBase` (gradle.properties) + `.` + GitHub run number, e.g. `1.0.12`
- Version code = run number (always grows, required by Android for updates)
- The repository must be **public** (the app reads the Releases API without a login)

## Signing key (one time setup)
All builds must be signed with the SAME key, otherwise Android refuses to update over the installed app.
The key is in `signing-private/` (NOT in git). Put it into GitHub once:

GitHub repo > Settings > Secrets and variables > Actions > New repository secret:

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | content of `signing-private/keystore.base64.txt` |
| `KEYSTORE_PASSWORD` | `storePassword` in `signing-private/keystore.properties` |
| `KEY_ALIAS` | `downly` |
| `KEY_PASSWORD` | `keyPassword` in `signing-private/keystore.properties` |

Back up `signing-private/` somewhere safe. Lost key = users must uninstall once to install a build signed with a new key.

## Local release build
Keep `signing-private/` next to `app/`, then: `gradle :app:assembleRelease -PbuildNumber=5 -PupdateRepo=OWNER/REPO`
(no key = the build stops with a clear message).

## Settings backup
Settings > Backup > Export / Import. The file is JSON with only settings (never the download list); every entry has
the same name the app shows (for example "Wi-Fi only"), so it is readable.

## UI design
Day and AMOLED Dark themes follow the Downly_Design_AI pack. All colours, glass surfaces, buttons, toggles, sheets, toast and icons
live in `DownlyTheme.kt` (read `Downly.colors` inside any composable). Settings > Theme: Auto / Day / Dark.
Button rules: white = normal actions, green = Download only, red = delete / cancel.
