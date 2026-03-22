# Releases

Tutaj dostępne są gotowe do instalacji wersje aplikacji Toya Zulip.

---

## 📦 v1.5.0 (2026-03-22)

**Plik:** `Toya-Zulip-v1.5.0.apk`  
**Rozmiar:** 54.7 MB  
**Architektura:** ARM64 (obsługiwana przez praktycznie wszystkie współczesne urządzenia Android)  

### Nowe funkcje w v1.5.0
- ✅ Dynamiczny system wersjonowania (BuildConfig)
- ✅ Pełna dokumentacja dla użytkownika i administratora
- ✅ Stabilna obsługa powiadomień push
- ✅ Oznaczenia wiadomości (gwiazdka, @all, @mention)
- ✅ Real-time synchronizacja (long-poll)
- ✅ Bezpieczne przechowywanie poświadczeń (EncryptedSharedPreferences)

### Wymagania
- **Android:** 9.0+ (API 28+)
- **RAM:** minimum 256 MB (rekomendowany 512 MB+)
- **Serwer:** Zulip 2.1.0+

### Instalacja

#### Opcja 1: Z pliku APK (dla zaawansowanych)
1. Pobierz `Toya-Zulip-v1.5.0.apk`
2. Prześlij plik na swoje urządzenie Android
3. Otwórz plik menedżerem plików
4. Naciśnij "Zainstaluj"
5. W ustawieniach może być wymagane: **Ustawienia → Bezpieczeństwo → Instalacja z nieznanych źródeł** (włącz dla tej aplikacji)

#### Opcja 2: Przez Android Studio (dla developerów)
```bash
adb install releases/Toya-Zulip-v1.5.0.apk
```

### Uwagi
- APK jest **unsigned** (zbudowany w trybie debug/release)
- Do produkcyjnego wdrażania w Play Store wymagany jest signing kluczem prywatnym
- Niskie zużycie baterii dzięki smart notyfikacjom i pollingowi
- Wszystkie poświadczenia przechowywane lokalnie (zero logowania na serwerze)

### Lista zmian
- Dodany dynamiczny system wersji z `BuildConfig.VERSION_NAME` i `BuildConfig.VERSION_CODE`
- Strona ustawień wyświetla teraz wersję aplikacji
- Nowe dokumenty: DOKUMENTACJA_UZYTKOWNIKA.md, DOKUMENTACJA_TECHNICZNA.md
- Projekt wrzucony na GitHub

---

## ❓ Wsparcie

Problemy z instalacją lub uruchomieniem?
- Sprawdź [DOKUMENTACJA_UZYTKOWNIKA.md](../docs/DOKUMENTACJA_UZYTKOWNIKA.md)
- Otwórz [Issue](../../issues) na GitHub
- Sprawdź wymagania powyżej

---

## 🔐 Bezpieczeństwo

- ✅ HTTPS-only komunikacja
- ✅ Basic Auth (email:apikey)
- ✅ Encrypted local storage (AES256-GCM)
- ✅ Brak logowania poświadczeń w release builds

---

## 📊 Historia wersji

| Wersja | Data | APK | Opis |
|--------|------|-----|------|
| 1.5.0 | 2026-03-22 | ✅ | Wystabilizowana wersja z pełną dokumentacją |
| 1.0.0 | 2026-01-01 | — | Początkowa wersja (research) |

---

**Ostatnia aktualizacja:** 2026-03-22
