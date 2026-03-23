# Zulip — Dokumentacja techniczna
**Wersja aplikacji: 1.5.0**
**Platforma: Android 9+ (API 28+)**
**Język: Kotlin, UI: Jetpack Compose**

---

## Spis treści
1. [Architektura ogólna](#1-architektura-ogólna)
2. [Struktura modułów i pakietów](#2-struktura-modułów-i-pakietów)
3. [Warstwa danych — Room Database](#3-warstwa-danych--room-database)
4. [Warstwa sieci — Retrofit + OkHttp](#4-warstwa-sieci--retrofit--okhttp)
5. [Uwierzytelnianie i bezpieczne przechowywanie sesji](#5-uwierzytelnianie-i-bezpieczne-przechowywanie-sesji)
6. [System zdarzeń czasu rzeczywistego (Long-Poll)](#6-system-zdarzeń-czasu-rzeczywistego-long-poll)
7. [System powiadomień](#7-system-powiadomień)
8. [Przepływy danych — diagramy](#8-przepływy-danych--diagramy)
9. [Dependency Injection — Hilt](#9-dependency-injection--hilt)
10. [Warstwowe mapowanie danych DTO → Entity → UI](#10-warstwowe-mapowanie-danych-dto--entity--ui)
11. [Obsługa kanałów i tematów](#11-obsługa-kanałów-i-tematów)
12. [Obsługa wiadomości — flagi, oznaczenia](#12-obsługa-wiadomości--flagi-oznaczenia)
13. [Konfiguracja budowania i wersjonowanie](#13-konfiguracja-budowania-i-wersjonowanie)
14. [Bezpieczeństwo](#14-bezpieczeństwo)
15. [Znane ograniczenia i uwagi wdrożeniowe](#15-znane-ograniczenia-i-uwagi-wdrożeniowe)

---

## 1. Architektura Ogólna

Aplikacja stosuje architekturę **MVVM + Clean Architecture** z wyraźnym podziałem na warstwy:

```
┌──────────────────────────────────────────────────────────────┐
│  Presentation Layer (Compose UI + ViewModel)                 │
│  LoginScreen, ZulipHomeScreen, ChannelsScreen,               │
│  ChatScreen, SearchScreen, AllMessagesScreen                 │
├──────────────────────────────────────────────────────────────┤
│  Domain Layer (Use Cases + Repository Interfaces)            │
│  LoginUseCase, GetStoredAuthUseCase, LogoutUseCase           │
│  AuthRepository, ChatRepository, ChannelsRepository          │
├──────────────────────────────────────────────────────────────┤
│  Data Layer                                                  │
│  ├── Remote: ZulipApiService (Retrofit), DTOs                │
│  └── Local: Room Database (5 entities), SecureSessionStorage │
├──────────────────────────────────────────────────────────────┤
│  Core Layer                                                  │
│  ├── DI: Hilt modules (Network, Repository, Storage)         │
│  ├── Realtime: ZulipEventService, EventQueueManager,         │
│  │             EventProcessor, ZulipNotificationHelper        │
│  └── Security: SecureSessionStorage (EncryptedSharedPrefs)  │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. Struktura Modułów i Pakietów

```
com.mkras.zulip/
├── core/
│   ├── di/
│   │   ├── NetworkModule.kt          # Retrofit, Moshi, ZulipApiFactory
│   │   ├── RepositoryModule.kt       # Binds interfejsy → implementacje
│   │   └── StorageModule.kt          # Room DB, DAO, SecureSessionStorage
│   ├── network/
│   │   └── ZulipApiFactory.kt        # Tworzy Retrofit z Basic Auth
│   ├── realtime/
│   │   ├── ZulipEventService.kt      # Foreground Service (Android Service)
│   │   ├── EventQueueManager.kt      # Pętla long-poll
│   │   ├── EventProcessor.kt         # Przetwarza 7 typów zdarzeń
│   │   ├── ZulipNotificationHelper.kt# Tworzenie i zarządzanie notyfikacjami
│   │   └── EventServiceController.kt # Start/stop serwisu
│   └── security/
│       └── SecureSessionStorage.kt   # EncryptedSharedPreferences wrapper
│
├── data/
│   ├── local/
│   │   ├── db/
│   │   │   ├── ZulipDatabase.kt      # Room DB version 6
│   │   │   ├── MessageDao.kt
│   │   │   ├── StreamDao.kt
│   │   │   ├── TopicDao.kt
│   │   │   └── DirectMessageCandidateDao.kt
│   │   └── entity/
│   │       ├── MessageEntity.kt
│   │       ├── StreamEntity.kt
│   │       ├── TopicEntity.kt
│   │       ├── DirectMessageCandidateEntity.kt
│   │       └── AppBootstrapEntity.kt
│   ├── remote/
│   │   ├── api/
│   │   │   └── ZulipApiService.kt    # Retrofit interface (16 endpointów)
│   │   └── dto/
│   │       ├── AuthDtos.kt
│   │       ├── MessagesDtos.kt
│   │       ├── ChannelsDtos.kt
│   │       ├── UsersDtos.kt
│   │       ├── MessageActionDtos.kt
│   │       └── MyProfileDto.kt
│   └── repository/
│       ├── AuthRepositoryImpl.kt
│       ├── ChatRepositoryImpl.kt
│       └── ChannelsRepositoryImpl.kt
│
├── domain/
│   ├── repository/
│   │   ├── AuthRepository.kt
│   │   ├── ChatRepository.kt
│   │   └── ChannelsRepository.kt
│   └── usecase/auth/
│       ├── LoginUseCase.kt
│       ├── GetStoredAuthUseCase.kt
│       └── LogoutUseCase.kt
│
└── presentation/
    ├── auth/
    │   ├── AuthViewModel.kt
    │   └── LoginScreen.kt
    ├── channels/
    │   ├── ChannelsViewModel.kt
    │   └── ChannelsScreen.kt
    ├── chat/
    │   ├── ChatViewModel.kt
    │   ├── ChatScreen.kt
    │   ├── ComposeUI.kt
    │   ├── EmojiHelper.kt
    │   └── AttachmentPickerUtils.kt
    ├── home/
    │   ├── ZulipHomeScreen.kt
    │   └── AllMessagesScreen.kt
    ├── navigation/
    │   └── ZulipRoot.kt
    └── search/
        └── SearchScreen.kt
```

---

## 3. Warstwa Danych — Room Database

### Konfiguracja bazy
```
Nazwa pliku:    zulip.db
Wersja schema:  6
Migracja:       fallbackToDestructiveMigration()
                (zmiana schematu powoduje wyczyszczenie cache przy aktualizacji)
```

### Encje i pola

#### `MessageEntity` (tabela: `messages`)
| Pole | Typ | Opis |
|------|-----|------|
| `id` | Long (PK) | ID wiadomości z serwera |
| `senderFullName` | String | Imię i nazwisko nadawcy |
| `senderEmail` | String | Email nadawcy |
| `content` | String | Treść HTML wiadomości |
| `topic` | String | Temat (puste dla DM) |
| `streamName` | String? | Nazwa kanału (null dla DM) |
| `timestampSeconds` | Long | Unix timestamp |
| `isRead` | Boolean | Czy przeczytana |
| `isStarred` | Boolean | Czy oznaczona gwiazdką |
| `isMentioned` | Boolean | Czy bezpośrednia wzmianka `@user` |
| `isWildcardMentioned` | Boolean | Czy `@all`/`@channel`/`@everyone` |
| `reactionSummary` | String? | Skrócony zapis reakcji `op:emoji_name` |
| `avatarUrl` | String | URL awatara nadawcy |
| `messageType` | String | `"stream"` lub `"private"` |
| `conversationKey` | String | Klucz rozmowy DM (email(e) rozmówców) |
| `dmDisplayName` | String | Wyświetlana nazwa rozmowy DM |

#### `StreamEntity` (tabela: `streams`)
| Pole | Typ | Opis |
|------|-----|------|
| `id` | Int (PK) | ID strumienia |
| `name` | String | Nazwa kanału |
| `description` | String | Opis kanału |
| `subscribed` | Boolean | Czy użytkownik jest subskrybowany |

#### `TopicEntity` (tabela: `topics`)
| Pole | Typ | Opis |
|------|-----|------|
| `key` | String (PK) | `"{streamId}:{topicName}"` |
| `streamId` | Int | ID strumienia |
| `name` | String | Nazwa tematu |
| `maxMessageId` | Long | ID ostatniej wiadomości w temacie |

#### `DirectMessageCandidateEntity` (tabela: `dm_candidates`)
| Pole | Typ | Opis |
|------|-----|------|
| `email` | String (PK) | Email użytkownika |
| `userId` | Int | ID użytkownika na serwerze |
| `fullName` | String | Pełna nazwa |
| `avatarUrl` | String | URL awatara |
| `sortKey` | String | Klucz sortowania dla wyświetlania |

### Operacje DAO — kluczowe zapytania

**MessageDao:**
```kotlin
// Obserwuj wiadomości w kanale/temacie (Flow)
@Query("SELECT * FROM messages WHERE streamName = :stream AND topic = :topic ORDER BY timestampSeconds ASC")
fun observeMessages(stream: String, topic: String): Flow<List<MessageEntity>>

// Zaktualizuj flagę przeczytania
@Query("UPDATE messages SET isRead = :isRead WHERE id IN (:ids)")
suspend fun updateReadFlags(ids: List<Long>, isRead: Boolean)

// Zaktualizuj flagę gwiazdki
@Query("UPDATE messages SET isStarred = :isStarred WHERE id IN (:ids)")
suspend fun updateStarredFlags(ids: List<Long>, isStarred: Boolean)

// Zaktualizuj wzmiankę bezpośrednią
@Query("UPDATE messages SET isMentioned = :isMentioned WHERE id IN (:ids)")
suspend fun updateMentionedFlags(ids: List<Long>, isMentioned: Boolean)

// Zaktualizuj wzmiankę globalną
@Query("UPDATE messages SET isWildcardMentioned = :val WHERE id IN (:ids)")
suspend fun updateWildcardMentionedFlags(ids: List<Long>, isWildcardMentioned: Boolean)
```

---

## 4. Warstwa Sieci — Retrofit + OkHttp

### Konfiguracja HTTP klienta
```
connectTimeout:   30 sekund
readTimeout:      90 sekund  (długi timeout dla long-poll /api/v1/events)
Interceptory:
  1. HttpLoggingInterceptor — BASIC w DEBUG, NONE w RELEASE
  2. AuthHeaderProvider — dodaje "Authorization: Basic <base64(email:apikey)>"
Serializacja:     Moshi + KotlinJsonAdapterFactory
```

### Normalizacja adresu serwera (`ZulipApiFactory`)
- Brak schematu → dodaj `https://`
- `http://` → zamień na `https://`
- Brak trailing-slash → dodaj `/`
- Przykład: `zulip.firma.pl` → `https://zulip.firma.pl/`

### Wszystkie endpoint API

| Metoda | Endpoint | Cel |
|--------|----------|-----|
| POST | `/api/v1/fetch_api_key` | Pobranie klucza API (logowanie hasłem) |
| POST | `/api/v1/register` | Rejestracja kolejki zdarzeń |
| GET | `/api/v1/events?queue_id=X&last_event_id=Y&dont_block=false` | Long-poll — pobierz zdarzenia |
| GET | `/api/v1/messages?narrow=[...]&anchor=X&num_before=N&num_after=0&apply_markdown=false` | Pobierz wiadomości |
| POST | `/api/v1/messages/flags` | Aktualizacja flag (read, starred, itd.) |
| GET | `/api/v1/streams` | Lista kanałów |
| GET | `/api/v1/users/me/{stream_id}/topics` | Tematy kanału |
| GET | `/api/v1/users/me` | Profil bieżącego użytkownika (rola/uprawnienia) |
| GET | `/api/v1/users` | Lista użytkowników (do autouzupełniania DM) |
| POST | `/api/v1/messages` | Wysyłanie wiadomości (stream lub DM) |
| POST | `/api/v1/user_uploads` | Upload pliku (multipart/form-data) |
| POST | `/api/v1/messages/{id}/reactions` | Dodanie reakcji emoji |
| DELETE | `/api/v1/messages/{id}/reactions` | Usunięcie reakcji emoji |
| PATCH | `/api/v1/messages/{id}` | Edycja wiadomości (treść / temat) |
| DELETE | `/api/v1/messages/{id}` | Usunięcie wiadomości |

### Formaty narrow (JSON)

```json
// Wiadomości kanału + tematu
[
  {"operator": "stream", "operand": "NazwaKanalu"},
  {"operator": "topic", "operand": "NazwaTematu"}
]

// Wyszukiwanie globalne
[{"operator": "search", "operand": "szukana fraza"}]
```

---

## 5. Uwierzytelnianie i Bezpieczne Przechowywanie Sesji

### `SecureSessionStorage` — klucze EncryptedSharedPreferences

| Klucz | Typ | Opis |
|-------|-----|------|
| `server_url` | String | Adres serwera |
| `email` | String | Email użytkownika |
| `api_key` | String | Klucz API |
| `auth_type` | String | `"PASSWORD"` lub `"API_KEY"` |
| `compact_mode` | Boolean | Tryb kompaktowy |
| `font_scale` | Float | Skala czcionki |
| `markdown_enabled` | Boolean | Markdown rendering |
| `notifications_enabled` | Boolean | Globalne powiadomienia |
| `dm_notifications_enabled` | Boolean | Powiadomienia DM |
| `channel_notifications_enabled` | Boolean | Powiadomienia kanałów |
| `muted_channels` | Set\<String\> | Wyciszone kanały (lowercase) |
| `disabled_channels` | Set\<String\> | Ukryte kanały (lowercase) |
| `muted_direct_messages` | Set\<String\> | Wyciszone rozmowy DM |
| `dm_candidate_cache_time` | Long | Timestamp ostatniego pobrania listy użytkowników |

### Schemat szyfrowania
```
MasterKey:
  - Scheme: AES256_GCM

SharedPreferences:
  - Key encryption:   AES256_SIV
  - Value encryption: AES256_GCM
```

### Przepływ logowania

```
[LoginScreen]
  ↓ Tryb HASŁO:
    POST /api/v1/fetch_api_key (Basic Auth: email:password)
    ← FetchApiKeyResponseDto { api_key }
  ↓ Tryb API_KEY:
    Pomiń fetch_api_key
  ↓
  POST /api/v1/register (Basic Auth: email:apikey)
  ← RegisterResponseDto { queue_id, ... }
  ↓ Jeśli NOWE konto (zmiana email lub serwera):
    MessageDao.clear()
    StreamDao.clear()
    TopicDao.clear()
    DmCandidateDao.clear()
  ↓
  SecureSessionStorage.saveAuth(serverUrl, email, apiKey, authType)
  ↓
[ZulipHomeScreen]
```

### Wylogowanie
```
SecureSessionStorage.clearAuth()
EventServiceController.stop()
Room DB cache pozostaje (następne logowanie wyczyści przy zmianie konta)
→ NavigateTo(LoginScreen)
```

---

## 6. System Zdarzeń Czasu Rzeczywistego (Long-Poll)

### Komponenty

```
ZulipEventService (Android ForegroundService)
  └── EventQueueManager (coroutine loop)
        ├── Rejestruje kolejkę: POST /api/v1/register
        │   event_types: ["message","update_message","delete_message",
        │                 "update_message_flags","reaction","typing","presence"]
        ├── Pętla: GET /api/v1/events?queue_id=X&last_event_id=Y&dont_block=false
        ├── Exponential backoff przy błędzie: 2s → 4s → 8s → max 15s
        └── Deleguje do EventProcessor.process(EventDto)
```

### Obsługiwane typy zdarzeń

| Typ zdarzenia | Obsługa |
|---------------|---------|
| `message` | `MessageDao.upsert()` + `ZulipNotificationHelper.showMessageNotification()` |
| `update_message` | `MessageDao.updateContent()` + aktualizacja flagi `isRead` |
| `delete_message` | `MessageDao.delete()` + `ZulipNotificationHelper.onMessagesDeleted()` |
| `update_message_flags` | Aktualizacja `isRead`/`isStarred`/`isMentioned`/`isWildcardMentioned` w DAO; przy `flag=read` → `onMessagesRead()` |
| `reaction` | `MessageDao.updateReactionSummary()` |
| `typing` | Emituje wskaźnik pisania do UI (nie persystowany) |
| `presence` | Zbierane, ale nie wyświetlane w UI |

### Obsługa zmiany konta w czasie działania
Gdy `AuthViewModel.login()` wywoła nowy cykl auth, `EventQueueManager` wykrywa zmianę poświadczeń i re-rejestruje nową kolejkę zdarzeń.

### Obsługa błędów
- `HTTP 401 / 403` → Wylogowanie, navigacja do LoginScreen
- `HTTP 400 (BAD_EVENT_QUEUE_ID)` → Re-rejestracja kolejki
- Timeout/brak sieci → Exponential backoff, ponowna próba

---

## 7. System Powiadomień

### Kanały Android (NotificationChannel)

| ID kanału | Nazwa | Importance | Cel |
|-----------|-------|-----------|-----|
| `zulip_sync_service` | Zulip Synchronization | MIN (2) | Trwały silent notification serwisu |
| `zulip_dm_messages` | Zulip DM | HIGH (4) | Heads-up powiadomienia DM |
| `zulip_stream_messages` | Zulip Kanały | HIGH (4) | Heads-up powiadomienia kanałów |

> **Migracja kanału stream**: Przy pierwszym uruchomieniu po aktualizacji, jeśli istniejący kanał `zulip_stream_messages` ma importance < HIGH (stara instalacja z DEFAULT), jest automatycznie usuwany i tworzony ponownie z HIGH.

### Logika wyświetlania powiadomienia

```
Nowa wiadomość z EventProcessor
  ↓
[Wstępny filtr]
  • własna wiadomość? → pomiń
  • brak uprawnień POST_NOTIFICATIONS? → pomiń
  ↓
[DM]
  • dmNotificationsEnabled == false? → pomiń
  • conversationKey w muted_direct_messages? → pomiń
  • → Pokaż powiadomienie w kanale zulip_dm_messages
  ↓
[Stream]
  • channelNotificationsEnabled == false? → pomiń
  • streamName (lowercase) w muted_channels? → pomiń
  • → Pokaż powiadomienie w kanale zulip_stream_messages
```

### ID powiadomień
- Każda wiadomość → `notificationId = message.id.toInt()`
- Grupy: `GROUP_DM = "zulip_group_dm"`, `GROUP_STREAM = "zulip_group_stream"`
- Stała ID dla podsumowań: np. `DM_SUMMARY_ID = -1`, `STREAM_SUMMARY_ID = -2`

### Usuwanie / czyszczenie powiadomień

**`onMessagesRead(ids: List<Long>)`:**
```kotlin
notificationManager.activeNotifications
    .filter { it.id.toLong() in ids }
    .forEach { notificationManager.cancel(it.id) }
reconcileGroupSummaries()
```

**`reconcileGroupSummaries()`:**
- Liczy aktywne powiadomienia DM i Stream
- Jeśli 0 → anuluje podsumowanie
- Jeśli >0 → aktualizuje podsumowanie z nową liczbą

### Intent nawigacyjny
Powiadomienie zawiera `PendingIntent` z `NotificationNavigationTarget`:
```kotlin
data class NotificationNavigationTarget(
    val messageId: Long,
    val streamName: String?,
    val topic: String?,
    val conversationKey: String?
)
```
`MainActivity` parsuje intent i nawiguje do właściwej rozmowy.

---

## 8. Przepływy Danych — Diagramy

### Wysyłanie wiadomości w kanale

```
ChannelsScreen.onSendMessage(streamName, topic, content)
  ↓
ChannelsViewModel.sendMessage()
  ↓
ChatRepository.sendMessage(type="stream", to=streamId, content, topic)
  ↓
ZulipApiService.sendMessage() → POST /api/v1/messages
  ← SendMessageResponseDto { id }
  ↓
MessageDao.upsert(MessageEntity(...))
  ↓
Flow<List<MessageEntity>> emits → ChannelsViewModel.messages StateFlow
  ↓
UI recompose → nowa wiadomość widoczna
```

### Odbieranie wiadomości (realtime)

```
ZulipEventService (foreground)
  ↓
EventQueueManager.longPollLoop()
  ↓ GET /api/v1/events
  ← EventsResponseDto { events: [EventDto] }
  ↓
EventProcessor.process(event)
  type == "message":
    ↓
    MessageDao.upsert(MessageEntity)
    ↓ if eligible notification:
    ZulipNotificationHelper.showMessageNotification()
    ↓
    NotificationManager.notify(messageId, notification)
  ↓
Flow emits → ViewModel → UI recompose
```

### Oznaczanie wiadomości jako przeczytanych

```
ChannelsScreen / ChatScreen — wiadomości przewinięte na ekran
  ↓
ViewModel.onMessagesRendered(ids: List<Long>)
  ↓
ChatRepository.markMessagesAsRead(ids)
  ↓
  MessageDao.updateReadFlags(ids, isRead=true)     [lokalnie, natychmiast]
  POST /api/v1/messages/flags (op=add, flag=read)  [do serwera, async]
  ↓
EventProcessor (zdarzenie update_message_flags z serwera)
  flag=="read", op=="add":
    MessageDao.updateReadFlags(ids, true)          [redundant, idempotent]
    ZulipNotificationHelper.onMessagesRead(ids)    [czyści powiadomienia]
```

---

## 9. Dependency Injection — Hilt

### NetworkModule
```kotlin
@Provides @Singleton
fun provideZulipApiFactory(storage: SecureSessionStorage): ZulipApiFactory

@Provides @Singleton
fun provideZulipApiService(factory: ZulipApiFactory, storage: SecureSessionStorage): ZulipApiService
```

### StorageModule
```kotlin
@Provides @Singleton
fun provideSecureSessionStorage(@ApplicationContext ctx: Context): SecureSessionStorage

@Provides @Singleton
fun provideDatabase(@ApplicationContext ctx: Context): ZulipDatabase

@Provides fun provideMessageDao(db: ZulipDatabase): MessageDao
@Provides fun provideStreamDao(db: ZulipDatabase): StreamDao
@Provides fun provideTopicDao(db: ZulipDatabase): TopicDao
@Provides fun provideDirectMessageCandidateDao(db: ZulipDatabase): DirectMessageCandidateDao
```

### RepositoryModule
```kotlin
@Binds @Singleton AuthRepository ← AuthRepositoryImpl
@Binds @Singleton ChatRepository ← ChatRepositoryImpl
@Binds @Singleton ChannelsRepository ← ChannelsRepositoryImpl
```

---

## 10. Warstwowe Mapowanie Danych DTO → Entity → UI

### Przykład: Wiadomość ze Strumienia

```
Sieć (ZulipApiService):
  MessageDto {
    id, sender_full_name, sender_email, content, subject (=topic),
    stream_id, display_recipient, timestamp, flags[], reactions[], avatar_url, type
  }
  ↓
ChatRepositoryImpl.toMessageEntity():
  MessageEntity {
    id         = dto.id,
    topic      = dto.subject ?: "",
    streamName = dto.displayRecipient as? String,
    isRead     = dto.flags?.contains("read") == true,
    isStarred  = dto.flags?.contains("starred") == true,
    isMentioned           = dto.flags?.contains("mentioned") == true,
    isWildcardMentioned   = dto.flags?.contains("wildcard_mentioned") == true,
    reactionSummary       = dto.reactions?.joinToString("|") { "${it.op}:${it.emojiName}" },
    messageType           = dto.type ?: "",
    conversationKey       = buildConversationKey(dto),
    dmDisplayName         = buildDmDisplayName(dto)
  }
  ↓
UI (ChannelsScreen/ChatScreen/AllMessagesScreen):
  Compose renders MessageEntity fields directly
  Content HTML → rendered via Markwon
  avatarUrl → loaded via Coil (with auth header)
```

---

## 11. Obsługa Kanałów i Tematów

### Odświeżanie strumieni
```kotlin
ChannelsRepositoryImpl.refreshStreams():
  GET /api/v1/streams
  ← StreamsResponseDto { streams: [StreamDto] }
  ↓
  StreamDao.replaceAll(streams.map { StreamEntity(...) })
  // atomowe: DELETE + INSERT w transakcji
```

### Pobieranie tematów z fallback logiką
```kotlin
ChannelsRepositoryImpl.refreshTopics(streamId):
  GET /api/v1/users/me/{streamId}/topics
  ← TopicsResponseDto { topics: [TopicDto] }
  
  if (topics.isEmpty()):
    // Fallback: pobierz ostatnie 1000 wiadomości kanału
    // Wywnioskuj tematy na podstawie pola subject w wiadomościach
  else:
    TopicDao.replaceForStream(streamId, topics.map { TopicEntity(...) })
```

### Paginacja wiadomości w temacie
- Pierwsze załadowanie: `anchor=newest`, `numBefore=100`
- Załaduj starsze: `anchor=<najstarszyMessageId>`, `numBefore=50`
- Polling co 2.5s w `ChannelsViewModel` gdy temat jest otwarty

---

## 12. Obsługa Wiadomości — Flagi, Oznaczenia

### Cykl życia flagi `isRead`

1. Wiadomości pobrane z API → `isRead = flags.contains("read")`
2. Wiadomości z EventProcessor (zdarzenie `message`) → `isRead = false` (nowe)
3. Renderowane w viewporcie → `ViewModel.onMessagesRendered(ids)` → lokalne + API update
4. Zdarzenie `update_message_flags` (flag=read) → aktualizacja lokalna

### Obsługa flag w EventProcessor

```kotlin
"update_message_flags" event:
  flag == "read":
    MessageDao.updateReadFlags(ids, isRead = op == "add")
    if (op == "add") notificationHelper.onMessagesRead(ids)
  
  flag == "starred":
    MessageDao.updateStarredFlags(ids, isStarred = op == "add")
  
  flag == "mentioned":
    MessageDao.updateMentionedFlags(ids, isMentioned = op == "add")
  
  flag == "wildcard_mentioned":
    MessageDao.updateWildcardMentionedFlags(ids, isWildcardMentioned = op == "add")
```

### Kolory i ikony oznaczeń w UI

| Warunek | Kolor tła | Chip |
|---------|-----------|------|
| `isStarred == true` | `0xFF53401A` (złoty) | `★ gwiazdka` |
| `isWildcardMentioned == true` | `0xFF5A3E1A` (pomarańcz.) | `@all` |
| `isMentioned == true` | `0xFF1E4B35` (zielony) | `@ty` |
| Własna wiadomość | `0xFF23415F` (niebieski) | — |
| Domyślny | `PanelCard` | — |

---

## 13. Konfiguracja Budowania i Wersjonowanie

### `app/build.gradle.kts`

```kotlin
android {
    namespace = "com.mkras.zulip"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.mkras.zulip"
        minSdk = 28      // Android 9.0
        targetSdk = 35
        versionCode = 1
        versionName = "1.5.0"
    }
    buildFeatures {
        compose = true
        buildConfig = true   // ← wymagane dla BuildConfig.VERSION_NAME/CODE
    }
    signingConfigs {
        release {
            storeFile = file("${home}/.android/debug.keystore")
            storePassword / keyAlias / keyPassword = debug defaults
        }
    }
}
```

### Wersjonowanie w aplikacji
Wersja jest wyświetlana dynamicznie w ekranie Ustawień przez:
```kotlin
import com.mkras.zulip.BuildConfig
// ...
Text("Zulip v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
```

Aby zaktualizować wersję — zmień tylko `versionCode` i `versionName` w `build.gradle.kts`.

### Ładowanie obrazów (Coil)
`ZulipApp` tworzy customowy `ImageLoader` z headerm Basic Auth:
```kotlin
ImageLoader.Builder(context)
    .okHttpClient {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Basic $credentials")
                        .build()
                )
            }.build()
    }.build()
```

---

## 14. Bezpieczeństwo

### Szyfrowanie poświadczeń
- `EncryptedSharedPreferences` (Jetpack Security, AES256-GCM / AES256-SIV)
- Klucz masterowy chroniony przez Android Keystore
- Żadne poświadczenia nie są przechowywane w plaintext

### Transport
- Wymuszony HTTPS (`android:usesCleartextTraffic="false"`)
- Klucz API nigdy nie jest logowany w RELEASE (logging interceptor = NONE)

### Uprawnienia aplikacji
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />  <!-- Android 13+ -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

### Kontrola uprawnień moderatora
Przed wyświetleniem opcji edycji/usunięcia wiadomości innego użytkownika, `ChatRepositoryImpl` sprawdza profil:
```kotlin
GET /api/v1/users/me
← { is_admin, is_owner, is_moderator, role }
→ canModerate = isAdmin || isOwner || isModerator
```

---

## 15. Znane Ograniczenia i Uwagi Wdrożeniowe

### Room DB — destrukcyjna migracja
`fallbackToDestructiveMigration()` oznacza, że **każda zmiana schematu DB czyści cache** przy aktualizacji. Dane są pobierane ponownie z serwera. Jest to celowe zachowanie — app jest klientem Zulip, "źródłem prawdy" zawsze jest serwer.

### Notification Channel Importance (Android)
Android nie pozwala zmienić `importance` istniejącego kanału powiadomień z poziomu aplikacji. Aplikacja zawiera kod migracyjny, który **usuwa i tworzy ponownie** kanał `zulip_stream_messages` jeśli importance < HIGH. Jednak na urządzeniu, gdzie użytkownik ręcznie zmienił importance poprzez ustawienia systemowe, zmiana ta nie jest możliwa programatycznie.

### Polling kanałów (2.5s)
`ChannelsViewModel` używa aktywnego pollingu co 2.5 sekundy dla otwartego tematu (jako uzupełnienie long-poll serwisu). Może powodować dodatkowe zużycie baterii, gdy ekran kanałów jest aktywny przez długi czas.

### Cache kandydatów DM
Lista użytkowników (do autouzupełniania @wzmianek) jest cachowana z TTL **6 godzin** (`dm_candidate_cache_time` w SecureSessionStorage). Na dużych serwerach Zulip z wieloma użytkownikami pierwsze załadowanie może chwilę potrwać.

### Konfiguracja release signing
Aktualny `signingConfig` dla buildu `release` używa **debug.keystore** (`~/.android/debug.keystore`). Do produkcyjnego wdrożenia należy zastąpić własnym keystore i odpowiednimi hasłami.

### Obsługa reakcji
`reactionSummary` w `MessageEntity` przechowuje reakcje jako string `"op:emoji_name"`. Bieżąca implementacja nie deduplicuje ani nie agreguje reakcji po emoji — każda zmiana z serwera nadpisuje całe pole. Przy wdrożeniu na serwerach z intensywnym użyciem reakcji może to powodować niespójności w podsumowaniu.
