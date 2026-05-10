# Usque Android App / Usque 安卓应用

[English](#english) | [中文](#中文)

---

## English

Usque Android App is a native Android VPN app for Cloudflare WARP / Zero Trust. It wraps the MASQUE-based Go client from the upstream `usque` project and provides an Android UI, `VpnService` integration, and APK build automation.

- Upstream project: <https://github.com/Diniboy1123/usque>
- Android package name: `com.warp.usque`
- Latest release: <https://github.com/garthnet/usque-android-app/releases/tag/v1.0.2>
- Latest APK: <https://github.com/garthnet/usque-android-app/releases/download/v1.0.2/usque-android-app-release-v1.0.2.apk>

### Features

- Native Android VPN implementation based on `VpnService`
- Cloudflare WARP / MASQUE tunnel through the upstream `usque` core
- Automatic first-run WARP registration
- Global VPN mode and selected-app VPN mode
- App selection list with search, select-all, and clear-selection controls
- Multiple connection profiles
- Chinese / English language toggle
- Runtime speed display using Android `TrafficStats`
- Foreground VPN service for better long-running stability
- Automatic tunnel restart when the native tunnel disconnects unexpectedly

### Repository contents

Important tracked files:

```text
app/src/main/kotlin/com/warp/usque/MainActivity.kt
app/src/main/kotlin/com/warp/usque/UsqueVpnService.kt
app/libs/usque.aar
app/libs/usque-classes.jar
.github/workflows/android.yml
docs/github-actions-signing.md
```

The repository intentionally does **not** include local build outputs, local Gradle caches, `local.properties`, APK artifacts, or signing keystores.

### Build locally

Use JDK 17 and a compatible Android SDK / Gradle environment.

Debug build:

```bash
gradle clean assembleDebug
```

Release build:

```bash
gradle clean assembleRelease
```

If no release signing config is provided, the local release APK may be unsigned depending on the environment.

### GitHub Actions APK build

The workflow is located at:

```text
.github/workflows/android.yml
```

Behavior:

- If signing secrets are not configured, the workflow builds a debug APK.
- If signing secrets are configured, the workflow builds a signed release APK.
- The workflow reads `versionName` from `app/build.gradle` and uploads the APK to the matching versioned GitHub Release, for example `versionName '1.0.2'` → `v1.0.2`.
- The workflow runs `gradle clean` before building to avoid stale package / dex output after package-name changes.

Signing setup:

```text
docs/github-actions-signing.md
```

Required repository secrets for signed release builds:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

Do **not** commit keystore files or secrets to this repository.

### Architecture

```text
Android App / Kotlin UI
        │
        ▼
Android VpnService + real TUN fd
        │
        ▼
usqueandroid package from usque.aar / usque-classes.jar
        │
        ▼
Upstream Go usque core
        │
        ▼
Cloudflare WARP / MASQUE
```

### Notes

- The app uses `VpnService.Builder.establish()` to obtain a real Android TUN fd.
- The detached fd is passed to the upstream native layer through `startTunnelWithFd`.
- Endpoint and SNI are configured through the `usqueandroid` API before starting the tunnel.
- Global mode excludes the app itself from the VPN route to avoid routing the control connection back into its own VPN tunnel.
- Selected-app mode skips the app's own package for the same reason.
- The VPN service runs as a foreground service and restarts the tunnel automatically after unexpected native disconnects.

### License and attribution

This repository is an Android app wrapper around the upstream `usque` project. Check the upstream project license and comply with its terms when redistributing the combined work:

<https://github.com/Diniboy1123/usque>

---

## 中文

Usque 安卓应用是一个面向 Cloudflare WARP / Zero Trust 的原生 Android VPN 应用。它基于上游 `usque` 项目的 MASQUE Go 客户端，提供 Android 图形界面、`VpnService` 集成和 APK 自动构建流程。

- 上游项目：<https://github.com/Diniboy1123/usque>
- Android 包名：`com.warp.usque`
- 最新版本：<https://github.com/garthnet/usque-android-app/releases/tag/v1.0.2>
- 最新 APK：<https://github.com/garthnet/usque-android-app/releases/download/v1.0.2/usque-android-app-release-v1.0.2.apk>

### 功能特性

- 基于 Android `VpnService` 的原生 VPN 实现
- 通过上游 `usque` 核心连接 Cloudflare WARP / MASQUE 隧道
- 首次运行自动注册 WARP
- 支持全局 VPN 模式和分应用 VPN 模式
- 应用列表支持搜索、全选和清空选择
- 支持多个连接配置档
- 支持中文 / 英文切换
- 基于 Android `TrafficStats` 显示实时速度
- 使用前台 VPN 服务，提升长时间后台运行稳定性
- native 隧道异常断开后自动重启连接

### 仓库内容

主要文件：

```text
app/src/main/kotlin/com/warp/usque/MainActivity.kt
app/src/main/kotlin/com/warp/usque/UsqueVpnService.kt
app/libs/usque.aar
app/libs/usque-classes.jar
.github/workflows/android.yml
docs/github-actions-signing.md
```

仓库刻意不包含本地构建产物、本地 Gradle 缓存、`local.properties`、APK 文件或签名 keystore。

### 本地构建

需要 JDK 17，以及与本项目兼容的 Android SDK / Gradle 环境。

Debug 构建：

```bash
gradle clean assembleDebug
```

Release 构建：

```bash
gradle clean assembleRelease
```

如果没有配置 release 签名，本地 release APK 可能会是未签名产物，具体取决于构建环境。

### GitHub Actions APK 构建

工作流文件位于：

```text
.github/workflows/android.yml
```

行为说明：

- 未配置签名 secrets 时，构建 debug APK。
- 已配置签名 secrets 时，构建签名 release APK。
- 工作流会从 `app/build.gradle` 读取 `versionName`，并上传 APK 到对应的版本 Release。例如 `versionName '1.0.2'` 会上传到 `v1.0.2`。
- 构建前会执行 `gradle clean`，避免修改包名后出现旧 dex / 新 Manifest 混用的问题。

签名配置说明：

```text
docs/github-actions-signing.md
```

签名 release 构建需要配置以下 GitHub Repository Secrets：

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

不要把 keystore 文件或任何密钥提交到仓库。

### 架构

```text
Android App / Kotlin UI
        │
        ▼
Android VpnService + 真实 TUN fd
        │
        ▼
来自 usque.aar / usque-classes.jar 的 usqueandroid 包
        │
        ▼
上游 Go usque 核心
        │
        ▼
Cloudflare WARP / MASQUE
```

### 说明

- 应用通过 `VpnService.Builder.establish()` 获取真实 Android TUN fd。
- detached fd 会通过 `startTunnelWithFd` 传给上游 native 层。
- 启动隧道前，会通过 `usqueandroid` API 设置 endpoint 和 SNI。
- 全局模式会把应用自身排除出 VPN 路由，避免控制连接被套回自己的 VPN 隧道。
- 分应用模式也会跳过应用自身包名，原因相同。
- VPN 服务以前台服务运行，并在 native 隧道异常断开后自动重连。

### 许可证与致谢

本仓库是围绕上游 `usque` 项目封装的 Android 应用。重新分发组合产物时，请检查并遵守上游项目许可证：

<https://github.com/Diniboy1123/usque>
