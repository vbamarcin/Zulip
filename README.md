# Toya Zulip — Android Zulip Client

Nowoczesna aplikacja klienckna do Zulip na Android. Pisana w **Kotlin** z **Jetpack Compose**.

**Wersja:** 1.5.0  
**Target:** Android 9+ (API 28+)  

---

## 📱 Funkcje

- ✅ **Logowanie** — hasło lub klucz API
- ✅ **Wiadomości prywatne (DM)** — rozmowy jeden-na-jeden
- ✅ **Kanały i tematy** — hierarciczna struktura strumieni
- ✅ **Wszystkie wiadomości** — feed wszystkich konwersacji
- ✅ **Wyszukiwanie** — pełnotekstowe wyszukiwanie na serwerze
- ✅ **Powiadomienia** — heads-up dla DM i kanałów
- ✅ **Oznaczenia wiadomości** — gwiazdki, @mentions, @all/@channel
- ✅ **Reakcje emoji** — dodaj/usuń emotikonki
- ✅ **Edycja/usuwanie** — zarządzaj własnymi wiadomościami
- ✅ **Załączniki** — upload plików/zdjęć
- ✅ **Markdown** — renderowanie formatowanej treści
- ✅ **Real-time sync** — long-poll dla živych udpates

---

## 🏗️ Architektura

```
Presentation (Compose)
    ↓
Domain (Use Cases) 
    ↓
Data (ViewModel, Repository)
    ↓
    ├── Remote (Retrofit API)
    └── Local (Room Database)
```

Detailsów architekury w [DOKUMENTACJA_TECHNICZNA.md](docs/DOKUMENTACJA_TECHNICZNA.md).

---

## 📚 Dokumentacja

- **[DOKUMENTACJA_UZYTKOWNIKA.md](docs/DOKUMENTACJA_UZYTKOWNIKA.md)** — Dla użytkownika końcowego. Opis każdej funkcji i elementu interfejsu.
- **[DOKUMENTACJA_TECHNICZNA.md](docs/DOKUMENTACJA_TECHNICZNA.md)** — Dla administratora/developera. Architektura, przepływy, API, baza danych.

---

## 🛠️ Stack Technologiczny

### Android & Kotlin
- **Minimalny API:** 28 (Android 9.0)
- **Target API:** 35
- **Kotlin:** Latest stable
- **Gradle:** AGP 8.5.2

### UI
- **Jetpack Compose** — declarative UI
- **Material 3** — design system
- **Markwon** — markdown rendering
- **Coil** — image loading

### Data & Networking
- **Room** — local database (SQLite)
- **Retrofit** — REST client
- **Moshi** — JSON serialization
- **OkHttp** — HTTP client

### Architecture & DI
- **MVVM** — UI state management
- **Hilt** — dependency injection
- **Coroutines & Flow** — async programming

### Security
- **EncryptedSharedPreferences** — secure credential storage
- **Android Keystore** — encryption keys

---

## 🔧 Build & Run

### Wymagania
- Android Studio (Flamingo+)
- JDK 17+
- Gradle 8.x

### Build
```bash
./gradlew assembleDebug      # Debug APK
./gradlew assembleRelease    # Release APK (unsigned)
```

### Logowanie
1. Uruchom aplikację
2. Wpisz adres serwera Zulip (np. `zulip.firma.pl`)
3. Email i hasło / klucz API
4. Zatwierdzić

---

## 🔐 Bezpieczeństwo

- ✅ **HTTPS enforced** — brak cleartext traffic
- ✅ **Encrypted storage** — poświadczenia szyfrowane AES256-GCM
- ✅ **Basic Auth** — każde żądanie podpisane email:apikey
- ✅ **No logs in Release** — HTTP logging wyłączony w produkcji
- ✅ **Permission-based** — respektuje Android permission system

---

## 📝 API Endpoints

Aplikacja komunikuje się z Zulip API v1:
- `POST /api/v1/fetch_api_key` — logowanie hasłem
- `POST /api/v1/register` — rejestracja event queue
- `GET /api/v1/events` — long-poll (real-time updates)
- `GET /api/v1/messages` — pobieranie wiadomości
- `POST /api/v1/messages` — wysyłanie wiadomości
- `PATCH /api/v1/messages/{id}` — edycja
- `DELETE /api/v1/messages/{id}` — usuwanie
- `POST /api/v1/messages/{id}/reactions` — dodanie emoji
- `DELETE /api/v1/messages/{id}/reactions` — usunięcie emoji
- `POST /api/v1/messages/flags` — aktualizacja flag (read, starred, itd.)
- `GET /api/v1/streams` — lista kanałów
- `GET /api/v1/users/me/topics` — tematy kanału
- `POST /api/v1/user_uploads` — upload pliku

Pełna lista w [DOKUMENTACJA_TECHNICZNA.md](docs/DOKUMENTACJA_TECHNICZNA.md#4-warstwa-sieci--retrofit--okhttp).

---

## 📊 Database

Room Database v6 z 5 encjami:
- **MessageEntity** — wiadomości (16 pól)
- **StreamEntity** — kanały
- **TopicEntity** — tematy  
- **DirectMessageCandidateEntity** — cache użytkowników dla @wzmianek
- **AppBootstrapEntity** — metadata

Schema: [data/local/entity/](app/src/main/java/com/mkras/zulip/data/local/entity/)

---

## 🚀 Rozwój

### Struktura kodu
```
src/main/java/com/mkras/zulip/
├── core/           — DI, network, realtime, security
├── data/           — local DB, remote API, repository
├── domain/         — interfaces, use cases
└── presentation/   — UI screens, ViewModels
```

### Contributing
PRs welcoming. Upewnij się że:
- [ ] Koduje się odbija commit message
- [ ] Testuj na Android 9+ (minimalnie API 28)
- [ ] Nie dodawaj hardcoded credentials
- [ ] Sprawdź `.gitignore` (nie wrzucaj Build artefaktów)

---

## 📄 Licencja

MIT License — patrz [LICENSE](LICENSE) (jeśli istnieje)

---

## 👤 Autor

**SysADM Toya**  
[Zulip Community](https://zulip.com/)

---

## 🙋 Support

Problemy? Pytania?  
Otwórz [GitHub Issue](../../issues)

---

## 📦 Releases

Najnowsza: **v1.5.0**

APK dostępne w [Releases](../../releases)

