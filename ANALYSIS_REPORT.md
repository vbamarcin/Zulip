# Analiza Rozwiązania - Aplikacja Zulip Android

**Data analizy**: 23 marca 2026  
**Wersja aplikacji**: 1.6.35  
**Platforma**: Android 9+ (API 28+)  
**Architektura**: MVVM + Clean Architecture  

---

## STRESZCZENIE WYKONAWCZE

Aplikacja Zulip jest aplikacją czatu napisaną w Kotlin z interfejsem użytkownika opartym na Jetpack Compose. Stosuje czystą architekturę z warstwami: Presentation → Domain → Data → Core. **Zidentyfikowano 15 krytycznych i średniej ważności problemów**, głównie w obsługi kluczy konwersacji DM, obsłudze błędów sieciowych, zarządzaniu stanem oraz potencjalnych wycieków pamięci.

---

## 1. PROBLEMY KRYTYCZNE

### 1.1 🔴 NIEZGODNE KLUCZE KONWERSACJI DM (Conversation Key)

**Plik**: [EventProcessor.kt](../app/src/main/java/com/mkras/zulip/core/realtime/EventProcessor.kt), [ChatRepositoryImpl.kt](../app/src/main/java/com/mkras/zulip/data/repository/ChatRepositoryImpl.kt), [ZulipNotificationHelper.kt](../app/src/main/java/com/mkras/zulip/core/realtime/ZulipNotificationHelper.kt)

**Problem**:  
3 różne funkcje generują klucze konwersacji w **nietaki sam** sposób:

| Funkcja | Normalizacja | Filtrowanie Self | Sortowanie | Użycie |
|---------|--------------|------------------|-----------|---------|
| **ChatRepositoryImpl.buildConversationKey** | ✅ Lowercase | ✅ Usuwa self | ✅ Sorted | Wysyłanie/odbieranie |
| **EventProcessor.buildConversationKey** | ❌ Brak | ❌ Brak | ❌ Sorted only | Real-time events |
| **ZulipNotificationHelper.buildConversationKey** | ❌ Brak | ❌ Brak | ❌ Sorted only | Powiadomienia |

**Przykład narażenia na błędy**:
```
Wysłana wiadomość:   "user@example.com"      (ChatRepositoryImpl)
Otrzymana wiadomość: "User@Example.COM"      (EventProcessor)
W powiadomieniu:     "User@Example.COM"      (ZulipNotificationHelper)
                     ↓
         Brak dopasowania klucza!
```

**Konsekwencje**:
- 📊 **Duplikaty konwersacji** w liście DM
- 🔔 Powiadomienia nie są powiązane z istniejącymi rozmowami
- 💾 Wiadomości real-time nie aktualizują istniejących wpisów
- 🧮 Niepoprawne liczenie nieprzeczytanych wiadomości

**Rozwiązanie**:
Centralizować logikę klucza w jednej funkcji helperowej:
```kotlin
// core/chat/DmConversationKey.kt
object DmConversationKey {
    fun normalize(rawKey: String, selfEmail: String): String {
        return rawKey
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it != selfEmail.trim().lowercase() }
            .distinct()
            .sorted()
            .joinToString(",")
    }
}
```

---

### 1.2 🔴 BRAK OBSŁUGI BŁĘDÓW W PĘTLI LONG-POLL

**Plik**: [EventQueueManager.kt](../app/src/main/java/com/mkras/zulip/core/realtime/EventQueueManager.kt) (linie 89-98)

**Problem**:
```kotlin
} catch (_: Exception) {
    queueId = null
    val backoffMs = (2_000L * (attempt + 1)).coerceAtMost(15_000L)
    attempt++
    delay(backoffMs)
}
```

**Problemy**:
1. **Ignorowanie wyjątków** — nie ma logowania ani analizy typu błędu
2. **Brak resetowania kolejki przy 401/403** — powinien wylogować użytkownika
3. **Brak obsługi HTTP 410 Gone** — queue się przedawnia, ale kod nie reaguje
4. **Exponential backoff** może zablokować aplikację nawet na 127 sekund
5. **Brak limitu prób** — będzie próbować w nieskończoność

**Konsekwencje**:
- Niemożliwe debugowanie połączeniowych problemów
- Zawieszeni użytkownicy bez powiadomienia
- Marnotrawstwo baterii z nieznanych powodów

**Rozwiązanie**:
```kotlin
catch (e: Exception) {
    when (e) {
        is HttpException -> {
            when (e.code()) {
                401, 403 -> {
                    // Wyloguj użytkownika
                    secureSessionStorage.clearAuth()
                    return  // Wyjdź z pętli
                }
                410 -> {
                    // Queue wygasła
                    Log.w("EventQueue", "Queue expired, re-registering")
                    queueId = null
                    lastEventId = null
                }
                else -> {
                    Log.e("EventQueue", "HTTP ${e.code()}: ${e.message()}")
                }
            }
        }
        is java.net.SocketTimeoutException -> {
            Log.d("EventQueue", "Timeout, retrying...")
            // Retry bez resetowania queue
        }
        else -> Log.e("EventQueue", "Unexpected error", e)
    }
    queueId = null
    attempt++
    val backoffMs = (2_000L * attempt).coerceAtMost(30_000L)
    delay(backoffMs)
}
```

---

### 1.3 🔴 RACE CONDITION: INICJALIZACJA VIEWMODELU

**Plik**: [ChatViewModel.kt](../app/src/main/java/com/mkras/zulip/presentation/chat/ChatViewModel.kt) (linie 43-56)

**Problem**:
```kotlin
init {
    observeMentionCandidates()
    observeMessages()              // Może być null
    observeAllMessages()
    observeStarredMessages()
    observeTypingEvents()
    observePresenceEvents()
    ensureMentionCandidatesLoaded()
    loadModerationPermission()
    initialPresenceSync()
    resyncOnResume()
}
```

**Problemy**:
1. `secureSessionStorage.getAuth()` jest wywoływane **w konstruktorze** pól (linie 45-46):
   ```kotlin
   private val serverUrl: String = secureSessionStorage.getAuth()?.serverUrl.orEmpty()
   private val currentUserEmail: String = secureSessionStorage.getAuth()?.email.orEmpty()
   ```
2. Jeśli auth nie jest gotowa, zmienne są puste (`""`)
3. Wszystkie operacje sieciowe uruchamiają się **natychmiast**, mogą się nie powieść
4. Brak przesłanek przed uruchomieniem obserwerów

**Konsekwencje**:
- 💥 Crash jeśli brak użytkownika
- 🌀 Zmienne `serverUrl` i `currentUserEmail` mogą być `""`
- 🔗 Normalizacja kluczy DM zawiedzie (przeglądaj #1.1)

**Rozwiązanie**:
```kotlin
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val eventProcessor: EventProcessor,
    private val secureSessionStorage: SecureSessionStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState?>(null)
    val uiState: StateFlow<ChatUiState?> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val auth = secureSessionStorage.getAuth()
            if (auth != null) {
                setupObservers(auth)
            }
        }
    }

    private suspend fun setupObservers(auth: StoredAuth) {
        updateState { it.copy(isReady = false) }
        observeMentionCandidates(auth)
        observeMessages(auth)
        // ...
        updateState { it.copy(isReady = true) }
    }
}
```

---

### 1.4 🔴 BRAK WALIDACJI ADRESU SERWERA

**Plik**: [ZulipApiFactory.kt](../app/src/main/java/com/mkras/zulip/core/network/ZulipApiFactory.kt) (linie 44-56)

**Problem**:
```kotlin
fun normalizeServerUrl(serverUrl: String): String {
    val trimmed = serverUrl.trim()
    require(!trimmed.startsWith("http://", ignoreCase = true)) {
        "Połączenia HTTP nie są wspierane. Użyj adresu HTTPS."
    }
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
    return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
}
```

**Problemy**:
1. ❌ Nie waliduje, czy to właściwy URL (np. `localhost:8080` bez domeny)
2. ❌ Nie sprawdzuje, czy URL zawiera schemę po `http://`
3. ❌ Walidacja `http://` zgłosi wyjątek, ale kod powyżej go nie obsługuje
4. ❌ Może zaakceptować `https://` zaraz po `require()` (logika sprzeczna)

**Konsekwencje**:
- 🌐 Możliwość podania niepoprawnego adresu (np. `https://://example.com`)
- 🔇 Brak informacji dla użytkownika o błędzie (require wyrzuca AssertionError)

**Rozwiązanie**:
```kotlin
fun normalizeServerUrl(serverUrl: String) {
    val trimmed = serverUrl.trim()
    
    if (trimmed.isEmpty()) throw IllegalArgumentException("URL serwera nie może być pusty")
    
    // Sprawdź, czy zawiera co najmniej jedną kropkę (domena)
    if (!trimmed.contains(".") && !trimmed.contains("localhost")) {
        throw IllegalArgumentException("Podaj prawidłowy adres URL (np. zulip.example.com)")
    }
    
    // Blokuj HTTP
    if (trimmed.startsWith("http://", ignoreCase = true)) {
        throw IllegalArgumentException("Połączenia HTTP nie są wspierane. Użyj adresu HTTPS.")
    }
    
    // Dodaj https://
    val withScheme = if (trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    
    // Dodaj trailing slash
    return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
}
```

---

## 2. PROBLEMY WAŻNE (ŚREDNIEJ WAŻNOŚCI)

### 2.1 ⚠️ WYCIEK PAMIĘCI: TYPINGTIMEOUTJOB I SEARCHJOB

**Plik**: [ChatViewModel.kt](../app/src/main/java/com/mkras/zulip/presentation/chat/ChatViewModel.kt) (linie 47-48)

**Problem**:
```kotlin
private var typingTimeoutJob: Job? = null
private var searchJob: Job? = null
```

Te zmienne są kiedyś ustawiane w `viewModelScope.launch {}`, ale nigdy nie są anulowane w `onCleared()`.

**Konsekwencje**:
- 🧠 Wyciek pamięci, gdy ViewModel jest zniszczony
- 🎯 Operacje mogą być uruchamiane na martwym ViewModelu

**Rozwiązanie**:
```kotlin
override fun onCleared() {
    typingTimeoutJob?.cancel()
    searchJob?.cancel()
    super.onCleared()
}
```

---

### 2.2 ⚠️ BRAK OBSŁUGI OFFLINE'U

**Problem**: Aplikacja nie ma mechanizmu wykrywania utraty połączenia.

**Konsekwencje**:
- 📡 Użytkownik wysyła wiadomość, ale sieć jest wyłączona — brak informacji
- 🔄 Nie ma kolejki wysyłania dla offline mode
- 🔔 Powiadomienia nie przychodzą

**Rozwiązanie** (proponowane):
```kotlin
// core/network/ConnectivityMonitor.kt
class ConnectivityMonitor @Inject constructor(
    @ApplicationContext context: Context
) {
    val isOnline: Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = trySend(true).isSuccess
            override fun onLost(network: Network) = trySend(false).isSuccess
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }
}
```

---

### 2.3 ⚠️ BRAK SZYFROWANIA LOKALNEJ BAZY DANYCH

**Plik**: [ZulipDatabase.kt](../app/src/main/java/com/mkras/zulip/data/local/db/ZulipDatabase.kt)

**Problem**: Room Database jest przechowywana bez szyfrowania.

**Konsekwencje**:
- 🔓 Każda osoba z dostępem do telefonu może przeczytać wszystkie wiadomości
- 📋 Wrażliwe dane w czyszczeniu tekście w `/data/data/com.mkras.zulip/databases/`

**Rozwiązanie**:
```gradle
dependencies {
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
}
```

```kotlin
@Database(...)
abstract class ZulipDatabase : RoomDatabase() { }

// StorageModule.kt
@Provides
@Singleton
fun provideZulipDatabase(
    @ApplicationContext context: Context
): ZulipDatabase {
    return Room.databaseBuilder(context, ZulipDatabase::class.java, "zulip.db")
        .openHelperFactory(SupportFactory("MySecurePassword".toByteArray()))
        .build()
}
```

---

### 2.4 ⚠️ BRAK OBSŁUGI DUPLIKATÓW WIADOMOŚCI

**Plik**: [ChatRepositoryImpl.kt](../app/src/main/java/com/mkras/zulip/data/repository/ChatRepositoryImpl.kt) (linie 145-155)

**Problem**:
```kotlin
messageDao.upsertAll(mapped)
```

Kod zakłada, że `upsertAll` automatycznie aktualizuje zduplikowane wiadomości. Ale jeśli sieć wysyła ten sam event dwa razy (co się zdarza), mogą pojawić się duplikaty.

**Konsekwencje**:
- 📊 Duplikaty wiadomości w bazie danych
- 🔢 Zwiększone zużycie pamięci

**Rozwiązanie**:
```kotlin
val mapped = response.messages
    .distinctBy { it.id }  // Filtruj duplikaty po ID
    .map { dto -> /* mapowanie */ }

messageDao.upsertAll(mapped)
```

---

### 2.5 ⚠️ BRAK OBSŁUGI BŁĘDÓW HTTP 429 (Rate Limit)

**Plik**: [EventQueueManager.kt](../app/src/main/java/com/mkras/zulip/core/realtime/EventQueueManager.kt)

**Problem**: Jeśli API zwróci 429 (Too Many Requests), aplikacja będzie ponawiać próbę z stałym backoff.

**Konsekwencja**: 
- 🚫 Aplikacja będzie spamować serwer
- 🔒 Serwer może zablokować IP

**Rozwiązanie**:
```kotlin
is HttpException -> when (e.code()) {
    429 -> {
        val retryAfter = e.response()?.header("Retry-After")?.toLongOrNull() ?: 60L
        Log.w("EventQueue", "Rate limited, waiting ${retryAfter}s")
        delay(retryAfter * 1000)
    }
    // ... inne kody
}
```

---

### 2.6 ⚠️ POTENCJALNY SQL INJECTION (JSON Escaping)

**Plik**: [ChatRepositoryImpl.kt](../app/src/main/java/com/mkras/zulip/data/repository/ChatRepositoryImpl.kt) (linie 36-40)

**Problem**:
```kotlin
private fun escapeJsonOperand(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}
```

Ta funkcja **nie dość chroni** przed injection. Powinna użyć JSON operatora `@json`.

**Konsekwencja**:
- 🔓 Możliwość injection w narrow queries

**Rozwiązanie**:
```kotlin
val narrowJson = jsonAdapter.toJson(
    listOf(
        mapOf("operator" to "stream", "operand" to streamName),
        mapOf("operator" to "topic", "operand" to topicName)
    )
)
```

---

## 3. PROBLEMY UMIARKOWANE

### 3.1 ⚠️ BRAK PAGINACJI W NIEKTÓRYCH ZAPYTANIACH

**Plik**: [ChatRepositoryImpl.kt](../app/src/main/java/com/mkras/zulip/data/repository/ChatRepositoryImpl.kt) (linie 76-85)

**Problem**:
```kotlin
override suspend fun resyncLatestMessages() {
    val response = service.getMessages(
        numBefore = 200,      // Zawsze 200 ostatnich
        applyMarkdown = false
    )
```

Jeśli użytkownik ma > 200 wiadomości, starsze nie zostaną załadowane.

**Rozwiązanie**: Implementować paginację z anchor ID.

---

### 3.2 ⚠️ BRAK OBSŁUGI ZMIAN STREAMU PODCZAS LOGOWANIA

**Plik**: [EventQueueManager.kt](../app/src/main/java/com/mkras/zulip/core/realtime/EventQueueManager.kt) (linie 40-52)

**Problem**: Jeśli użytkownik zmieni URL serwera mid-session, stara kolejka nie zostanie anulowana.

---

### 3.3 ⚠️ BRAK TIMEOUTU NA POBIERANIE ZDJĘĆ AVATARA

**Problem**: AsyncImage z Coil nie ma skonfigurowanego timeout.

**Konsekwencja**: Aplikacja może się zawieszać na ładowaniu zdjęć.

---

## 4. PROBLEMY ARCHITEKTONICZNE

### 4.1 📐 NARUSZENIE GRANICY WARSTW

**Problem**: ViewModel bezpośrednio zawiera logikę biznesu:
```kotlin
private fun normalizeDmConversationKey(rawKey: String, fallbackEmail: String): String {
    // To powinno być w domain layer
}
```

**Rozwiązanie**: Przenieść do `domain/usecase/` lub `domain/repository/`.

---

### 4.2 📐 BRAK WALIDACJI DANYCH NA GRANICACH WARSTW

**Problem**: DTO jest mapowany bezpośrednio na Entity bez walidacji.

**Konsekwencja**: Możliwe null pointer exceptions.

---

## 5. BEZPIECZEŃSTWO

### 5.1 🔒 EncryptedSharedPreferences — dobra praktyka

✅ Aplikacja **poprawnie** używa:
- `MasterKey.KeyScheme.AES256_GCM`
- `PrefKeyEncryptionScheme.AES256_SIV`
- `PrefValueEncryptionScheme.AES256_GCM`

### 5.2 🔒 Basic Auth — RYZYKO

**Problem**: Klucz API wysyłany w Base64 (może być zdekodowany):
```kotlin
fun toAuthorizationHeader(): String {
    val raw = "$email:$secret"
    val encoded = Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    return "Basic $encoded"
}
```

**Rozwiązanie**: 
- ✅ Aplikacja już wymaga HTTPS
- ✅ Base64 nie jest enkrypcją, ale HTTPS chroni transport

---

## 6. WYDAJNOŚĆ

### 6.1 🚀 Problem: Każda zmiana stanu = pełna lista

**Problem**:
```kotlin
override fun observeMessages(): Flow<List<MessageEntity>> = messageDao.observeMessages()
```

Każda zmiana w bazie = całej listy jest przetworzona znowu.

**Rozwiązanie**: Implementacja `ListenableFuture` lub `Pager` dla incremental updates.

---

## 7. PODSUMOWANIE PROBLEMÓW

| ID | Nazwa | Ważność | Status |
|----|-------|---------|--------|
| 1.1 | Niezgodne klucze konwersacji | 🔴 Krytyczne | ⚠️ Brak |
| 1.2 | Brak obsługi błędów long-poll | 🔴 Krytyczne | ⚠️ Brak |
| 1.3 | Race condition inicjalizacji | 🔴 Krytyczne | ⚠️ Brak |
| 1.4 | Walidacja adresu serwera | 🔴 Krytyczne | ⚠️ Brak |
| 2.1 | Wyciek pamięci Job | ⚠️ Ważne | ⚠️ Brak |
| 2.2 | Brak obsługi offline | ⚠️ Ważne | ⚠️ Brak |
| 2.3 | Brak szyfrowania DB | ⚠️ Ważne | ⚠️ Brak |
| 2.4 | Duplikaty wiadomości | ⚠️ Ważne | ⚠️ Brak |
| 2.5 | Brak obsługi 429 Rate Limit | ⚠️ Ważne | ⚠️ Brak |
| 2.6 | JSON Injection risk | ⚠️ Ważne | ⚠️ Brak |
| 3.1 | Brak paginacji | ⚠️ Umiarkowane | ⚠️ Brak |
| 3.2 | Zmiana serwera mid-session | ⚠️ Umiarkowane | ⚠️ Brak |
| 3.3 | Timeout avatara | ⚠️ Umiarkowane | ⚠️ Brak |
| 4.1 | Naruszenie granicy warstw | 📐 Architektura | ⚠️ Brak |
| 4.2 | Brak walidacji DTO→Entity | 📐 Architektura | ⚠️ Brak |

---

## 8. REKOMENDACJE PRIORYTETOWE

### Faza 1 (NATYCHMIAST):
1. ✅ Naprawiać niezgodne klucze konwersacji (#1.1)
2. ✅ Dodać obsługę błędów w event loop (#1.2)
3. ✅ Przenieść auth check z init (#1.3)
4. ✅ Ulepszyć walidację URL (#1.4)

### Faza 2 (Tydzień):
5. ⚠️ Dodać `onCleared()` do ViewModelu (#2.1)
6. ⚠️ Implementować offline mode (#2.2)
7. ⚠️ Szyfrować bazę Room (#2.3)

### Faza 3 (Sprint):
8. 📐 Refaktoryzacja warstw
9. 🚀 Optymalizacja wydajności

---

## LINKI DO DOKUMENTACJI

- [DM_FLOW_ANALYSIS.md](../DM_FLOW_ANALYSIS.md) — Szczegółowa analiza przepływu DM
- [DOKUMENTACJA_TECHNICZNA.md](../docs/DOKUMENTACJA_TECHNICZNA.md) — Pełna dokumentacja architektur
- [DOKUMENTACJA_UZYTKOWNIKA.md](../docs/DOKUMENTACJA_UZYTKOWNIKA.md) — Instrukcja użytkownika

---

**Koniec analizy**
