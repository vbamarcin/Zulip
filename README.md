<div align="center">

# Toya Zulip

### Unofficial Android client for Zulip — fast, lightweight, open source

[![Release](https://img.shields.io/github/v/release/vbamarcin/Zulip?color=blue&label=latest)](https://github.com/vbamarcin/Zulip/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%209%2B-green)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0-purple)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue)](https://developer.android.com/jetpack/compose)

</div>

---

## Overview

**Toya Zulip** is an unofficial, open-source Android client for [Zulip](https://zulip.com) — a powerful open-source team chat platform. The app aims to deliver a clean, responsive experience with full support for channels, direct messages, real-time events, and push notifications.

> Zulip is a registered trademark of Kandra Labs, Inc. This project is not affiliated with or endorsed by Kandra Labs or the Zulip project.

---

## Screenshots

| Login | DM List | Channel Chat |
|-------|---------|--------------|
| _(screenshot)_ | _(screenshot)_ | _(screenshot)_ |

---

## Features

- **Direct Messages (DM)** — One-on-one and group private conversations
- **Channels & Topics** — Browse subscribed streams and threaded topics
- **All Messages** — Unified inbox with read/unread tracking
- **Real-time events** — Long-poll event queue via foreground service
- **Push notifications** — Deep-link navigation from notification to conversation
- **Full-text search** — Search messages across all channels
- **Emoji reactions** — React to messages with emoji
- **File & image uploads** — Attach files and photos to messages
- **Secure session storage** — EncryptedSharedPreferences (AES-256)
- **API key or password login** — Supports both authentication modes

---

## Installation

### Download APK

The latest release APK is available on the [Releases page](https://github.com/vbamarcin/Zulip/releases).

```
Latest: v1.7.0
```

> **Note:** You may need to enable *Install from unknown sources* in your Android settings.

### Build from Source

**Requirements:**
- Android Studio Hedgehog or newer
- JDK 17
- Android SDK (API 35)

```bash
git clone https://github.com/vbamarcin/Zulip.git
cd Zulip
./gradlew assembleRelease
```

The output APK will be at `app/build/outputs/apk/release/`.

---

## Requirements

| Component | Minimum |
|-----------|---------|
| Android   | 9.0 (API 28) |
| Target SDK | 35 |
| RAM       | 2 GB recommended |

---

## Architecture

The app follows **MVVM + Clean Architecture** with a strict layered structure:

```
Presentation  →  Domain  →  Data  →  Core
```

| Layer | Contents |
|-------|----------|
| **Presentation** | Jetpack Compose screens, ViewModels |
| **Domain** | Use cases, repository interfaces |
| **Data** | Retrofit (remote), Room DB (local), DTOs |
| **Core** | Hilt DI, real-time event service, notifications, security |

### Tech Stack

| Technology | Usage |
|------------|-------|
| Kotlin 2.0 | Primary language |
| Jetpack Compose | UI framework |
| Hilt | Dependency injection |
| Retrofit + Moshi | REST API client |
| Room | Local database (SQLite) |
| OkHttp | HTTP client with logging |
| Kotlin Coroutines + Flow | Async / reactive data |
| EncryptedSharedPreferences | Secure session storage |
| ZulipEventService | Foreground service for real-time events |

---

## Project Structure

```
app/src/main/java/com/mkras/zulip/
├── core/
│   ├── di/                    # Hilt modules (Network, Repository, Storage)
│   ├── network/               # ZulipApiFactory (Basic Auth Retrofit)
│   ├── realtime/              # Long-poll event service & notifications
│   └── security/              # SecureSessionStorage
├── data/
│   ├── local/                 # Room DB, DAOs, entities
│   ├── remote/                # Retrofit API service, DTOs
│   └── repository/            # Repository implementations
├── domain/
│   ├── model/                 # Domain models
│   ├── repository/            # Repository interfaces
│   └── usecase/               # Business logic use cases
└── presentation/
    ├── login/                 # Login screen + ViewModel
    ├── home/                  # Main navigation
    ├── chat/                  # Channel & DM chat screen
    ├── channels/              # Channels & topics list
    ├── dm/                    # Direct messages list
    ├── search/                # Search screen
    └── settings/              # App settings
```

---

## Configuration

The app connects to **any self-hosted or cloud Zulip server**. On the login screen, enter:

| Field | Example |
|-------|---------|
| Server URL | `https://your-org.zulip.com` |
| Email | `user@example.com` |
| Password or API key | your credentials |

---

## Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m 'Add some feature'`
4. Push to the branch: `git push origin feature/your-feature`
5. Open a Pull Request

### Reporting Issues

Use [GitHub Issues](https://github.com/vbamarcin/Zulip/issues) to report bugs or request features.

---

## Documentation

- [User Documentation (PL)](docs/DOKUMENTACJA_UZYTKOWNIKA.md)
- [Technical Documentation (PL)](docs/DOKUMENTACJA_TECHNICZNA.md)
- [Release History](releases/RELEASES.md)

---

## License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

```
MIT License — Copyright (c) 2024 vbamarcin
```

---

## Disclaimer

This is an **unofficial** third-party client. It is not affiliated with, endorsed by, or in any way officially connected to Zulip or Kandra Labs, Inc.

