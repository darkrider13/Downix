<div align="center">

<img src="branding/downix-icon-512.png" alt="Downix app icon" width="128" height="128">

# Downix

**A fast, clean download manager for Android.**
Paste a link, tap *Connect*, and let Downix handle the rest in the background.

[![Latest release](https://img.shields.io/github/v/release/darkrider13/Downix?style=for-the-badge&color=000000&labelColor=1c1c1e)](https://github.com/darkrider13/Downix/releases/latest)
[![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-000000?style=for-the-badge&logo=android&logoColor=white&labelColor=1c1c1e)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-000000?style=for-the-badge&logo=kotlin&logoColor=white&labelColor=1c1c1e)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-000000?style=for-the-badge&logo=jetpackcompose&logoColor=white&labelColor=1c1c1e)](https://developer.android.com/jetpack/compose)

[Features](#features) ·
[Install](#install) ·
[How it works](#how-it-works) ·
[Tech stack](#tech-stack) ·
[Build from source](#build-from-source) ·
[Releases and updates](#releases-and-updates) ·
[Developer](#developer)

</div>

---

## Features

### Downloading
- **Link check first.** Tap *Connect* to check a link before anything is saved, so broken or unsafe links are caught early.
- **Pause and resume** at any time. Downloads continue from where they stopped (HTTP `Range` requests).
- **Background downloads** through a foreground service with live notifications, even when the app is closed.
- **Smart engine.** Watches the network, waits when you are offline, resumes by itself and retries failed downloads.
- **Parallel downloads.** Choose how many files download at the same time (1 to 5).
- **Wi-Fi only mode** to never touch your mobile data.

### Organising
- **Filters and search.** *All*, *Active*, *Completed* and *Failed* tabs with live counts, plus search by file name or link.
- **Details popup.** File name, link, size, date added, status and save location, with one-tap **copy file name** and **copy link**.
- **File actions.** Open, share or delete the downloaded file straight from the app.
- **Duplicate detection.** Downix tells you when a link is already in your list.
- **Clipboard suggestion.** Copied a link? Downix picks it up from *From clipboard*.
- **Multi-select.** Select several downloads and remove them together.
- **Share and open-with support.** Send a link to Downix from any app, or open a download link with Downix.

### Design
- **AMOLED dark theme** with glass surfaces and a clean, minimal layout.
- **Instant background blur** behind every popup (Android 12 and above).
- Centred popups, large touch targets and consistent red, white and green button rules.

### Updates
- **In-app updates** from GitHub Releases. Downix checks on start and installs the new build over the old one, keeping your downloads and settings.

---

## Install

1. Open the [**latest release**](https://github.com/darkrider13/Downix/releases/latest).
2. Download the `Downix-<version>.apk` file.
3. Open it on your phone and allow *Install unknown apps* if Android asks.

After the first install, new versions arrive inside the app: **Settings → Updates → Check now**.

### Requirements

| | |
|---|---|
| Android | 7.0 (API 24) or newer |
| Blur effect | Android 12 (API 31) or newer |
| Permissions | Internet, notifications, foreground service |

---

## How it works

1. Tap **+** and choose **Add link** (or **From clipboard**).
2. Paste the link and tap **Connect**. Downix checks the file and shows its status.
3. Start the download. Progress, speed and size are shown on the card.
4. Pause, resume or remove it whenever you like. Finished files are saved to your download folder, which you can change in **Settings**.

---

## Settings

| Setting | What it does |
|---|---|
| Download folder | Where finished files are saved |
| Simultaneous downloads | How many files download at once (1 to 5) |
| Wi-Fi only | Never use mobile data |
| Notifications | Alert when a download finishes or fails |
| Check now | Look for a new version of Downix |

---

## Tech stack

| Area | Technology |
|---|---|
| Language | Kotlin 1.9 |
| UI | Jetpack Compose, Material 3 |
| Networking | OkHttp 4 |
| Database | Room 2.6 (KSP) |
| Background work | Foreground service, WorkManager, Kotlin Coroutines |
| Build | Gradle (Kotlin DSL), Android Gradle Plugin 8.5, JDK 17 |
| Min / target SDK | 24 / 34 |

### Project structure

```text
Downix/
├── app/src/main/java/com/downix/app/
│   ├── MainActivity.kt         Main screen, popups and Settings screen
│   ├── DownixTheme.kt          Colours, glass surfaces, buttons, icons, popup blur
│   ├── DownloadEngine.kt       Download logic: pause, resume, retry, network awareness
│   ├── DownloadService.kt      Foreground service and notifications
│   ├── DownloadDatabase.kt     Room database and repository
│   ├── DownloadTools.kt        Filters, search, details popup, copy and share actions
│   ├── LinkChecker.kt          Checks a link before it is added
│   ├── NetworkMonitor.kt       Connectivity and Wi-Fi state
│   ├── SettingsRepository.kt   Saved preferences
│   ├── SaveLocation.kt         Download folder handling
│   ├── UpdateManager.kt        In-app update checker and installer
│   ├── UpdateUi.kt             Update popup and Settings row
│   └── DeveloperCredit.kt      Developer credit shown in Settings
├── branding/                   App icon source (SVG) and 512 px PNG
└── .github/workflows/          Automatic build and release
```

---

## Build from source

Requirements: **Android Studio** (latest stable) and **JDK 17**.

```bash
git clone https://github.com/darkrider13/Downix.git
cd Downix
```

Open the folder in Android Studio and run the `app` configuration on a device or emulator.

A signed release build needs the signing key (see below):

```bash
gradle :app:assembleRelease -PbuildNumber=5 -PupdateRepo=darkrider13/Downix
```

---

## Releases and updates

Every push to `main` triggers **GitHub Actions** (`.github/workflows/release.yml`), which builds a **signed** APK and publishes it as a GitHub Release named `v1.0.<run number>`.

- **Version name** = `downix.versionBase` (in `gradle.properties`) + `.` + the run number, for example `1.0.12`.
- **Version code** = the run number, so it always grows and Android accepts it as an update.
- The repository must stay **public**, because the app reads the Releases API without a login.
- Changes to Markdown files and `branding/` do not trigger a build.

### Signing key (one-time setup)

Every build must be signed with the **same key**, otherwise Android refuses to update over the installed app. Keep the key in a local `signing-private/` folder (it is in `.gitignore` and must never be committed) and add it to GitHub once under
**Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | Base64 text of the keystore file |
| `KEYSTORE_PASSWORD` | Store password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

> Back up `signing-private/` somewhere safe. If the key is lost, users must uninstall Downix once to install a build signed with a new key.

---

## Developer

**MD. Insanul Islam Abir**

[![Instagram](https://img.shields.io/badge/Instagram-000000?style=for-the-badge&logo=instagram&logoColor=white)](https://www.instagram.com/md.insanulislam)
[![Telegram](https://img.shields.io/badge/Telegram-000000?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/insanul13)

---

<div align="center">

© MD. Insanul Islam Abir. All rights reserved.

</div>
