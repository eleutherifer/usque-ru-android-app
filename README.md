<h1 align="center">Usque Android App RU</h1>

<p align="center">
  <strong>Cloudflare WARP / MASQUE VPN Client for Android</strong>
</p>

<p align="center">
  <a href="https://github.com/eleutherifer/usque-android-app-ru/releases">
    <img src="https://img.shields.io/github/v/release/eleutherifer/usque-android-app-ru?style=flat-square" alt="Release">
  </a>
  <a href="https://github.com/eleutherifer/usque-android-app-ru">
    <img src="https://img.shields.io/badge/platform-android-blue?style=flat-square" alt="Platform">
  </a>
  <a href="https://github.com/eleutherifer/usque-android-app-ru/actions/workflows/android.yml">
    <img src="https://img.shields.io/github/actions/workflow/status/eleutherifer/usque-android-app-ru/android.yml?branch=main&style=flat-square" alt="Android APK">
  </a>
</p>


---

## ✨ Features

- 🚀 **Cloudflare WARP** - MASQUE-based WARP tunnel using the upstream Go core
- 🔒 **Global VPN** - Route all app traffic through the VPN
- 📱 **Per-App VPN** - Route only selected apps through the VPN
- ⚙️ **Profiles** - Save and apply multiple endpoint / SNI profiles
- 📊 **Speed Display** - Runtime traffic speed using Android `TrafficStats`
- 🛡️ **Foreground Service** - Better long-running VPN stability on Android
- 🔁 **Auto Restart** - Automatically restarts the native tunnel after unexpected disconnects
- ⚡ **Native Core** - Go + gomobile AAR integration for native performance

## 📲 Download

Download the latest APK from the [Releases](https://github.com/eleutherifer/usque-android-app-ru/releases) page.

Direct download:

```text
https://github.com/eleutherifer/usque-android-app-ru/releases/download/v1.0.4.1/usque-android-app-ru-release-v1.0.4.1.apk
```

> The GitHub Actions workflow reads `versionName` from `app/build.gradle` and publishes the APK to the matching versioned Release, for example `1.0.4.1` → `v1.0.4.1`.

## 🛠️ Build from Source

### Prerequisites

- JDK 17
- Android SDK, API 34
- Gradle 8.5 or compatible Gradle setup
- Prebuilt upstream Android artifacts already included in this repository:
  - `app/libs/usque.aar`
  - `app/libs/usque-classes.jar`

### Build Steps

```bash
# Debug APK
gradle clean assembleDebug

# Release APK
gradle clean assembleRelease
```

If no release signing config is provided, the release APK may be unsigned depending on the environment.

### GitHub Actions Recommended

Push to `main` or manually run the Android APK workflow:

```bash
gh workflow run android.yml
```

The workflow will:

- Build with JDK 17 and Gradle 8.5
- Run `gradle clean` to avoid stale dex / manifest output
- Build a signed release APK when signing secrets are configured
- Upload the APK and `SHA256SUMS` to the matching GitHub Release

Required signing secrets:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

See also:

```text
docs/github-actions-signing.md
```

## 📖 Usage

1. **Install APK** - Download and install the latest APK on an Android device.
2. **First Run** - The app registers a Cloudflare WARP account automatically if no valid config exists.
3. **Configure** - Set SNI, endpoint, port, and optional profiles.
4. **Choose Mode** - Use Global mode or Per-App mode.
5. **Connect** - Tap the connect button to start the VPN.

### Proxy Modes

| Mode | Description |
|------|-------------|
| **Global** | All app traffic goes through the VPN. The app itself is excluded to avoid routing the control connection back into its own tunnel. |
| **Per-App** | Only selected apps use the VPN. Other apps connect directly. The app itself is skipped for the same control-connection reason. |

### Settings

| Parameter | Example / Default | Description |
|-----------|-------------------|-------------|
| SNI | `my.mail.ru` | TLS SNI value used by the tunnel |
| Endpoint | `162.159.198.2:433` | WARP / MASQUE endpoint |
| Mode | Global / Per-App | Traffic routing mode |

## 🏗️ Architecture

```text
usque-android-app-ru/
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/warp/usque/      # Kotlin UI and VPN service
│   │   ├── res/                        # Android resources
│   │   └── AndroidManifest.xml
│   ├── libs/                           # Prebuilt upstream AAR / Java classes
│   │   ├── usque.aar
│   │   └── usque-classes.jar
│   └── build.gradle
├── docs/
│   └── github-actions-signing.md
└── .github/workflows/
    └── android.yml                     # CI APK build and Release upload
```

### Tech Stack

- **UI**: Kotlin + Android Views
- **VPN Service**: Android `VpnService` + real TUN fd
- **VPN Core**: Go + gomobile → AAR
- **Protocol**: Cloudflare MASQUE / WARP
- **Build**: Gradle + GitHub Actions

## 🎨 Design

- **Style**: Material-style Android UI
- **Theme**: Soft warm orange / light card layout
- **Modes**: Overview, Config, Apps
- **UX**: Mobile-first controls and searchable app list

## 📝 Notes

- The app uses `VpnService.Builder.establish()` to obtain a real Android TUN fd.
- The detached fd is passed to the upstream native layer through `startTunnelWithFd`.
- Endpoint and SNI are configured through the `usqueandroid` API before the tunnel starts.
- The VPN service runs as a foreground service for better long-running stability.
- Unexpected native tunnel disconnects trigger automatic restart unless the user manually stopped the VPN.
- GitHub Actions uses `gradle clean` before building to prevent stale dex / manifest mismatches after package-name changes.

## 📝 License

This project is based on [`usque`](https://github.com/Diniboy1123/usque) by Diniboy1123. Check the upstream project license and comply with its terms when redistributing the combined work.

## 🙏 Acknowledgements

- [`usque`](https://github.com/Diniboy1123/usque) - Original WARP / MASQUE implementation
- [Cloudflare WARP](https://developers.cloudflare.com/warp-client/) - WARP platform
- [MASQUE](https://datatracker.ietf.org/doc/rfc9484/) - RFC 9484
