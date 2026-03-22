# Releases

Tutaj dostępne są gotowe do instalacji wersje aplikacji Toya Zulip.

---

## 📦 v1.6.4 (2026-03-22) — NAJNOWSZA

**Plik:** `Toya-Zulip-v1.6.4.apk`  
**Rozmiar:** 55.0 MB  

### Zmiany w v1.6.4
- ✅ Poprawione zliczanie badge na ikonie aplikacji (bez zawyżania 1+2+3...)
- ✅ Licznik na ikonie odpowiada rzeczywistej liczbie nieprzeczytanych powiadomień

---

## 📦 v1.6.3 (2026-03-22)

**Plik:** `Toya-Zulip-v1.6.3.apk`  
**Rozmiar:** 55.0 MB  

### Zmiany w v1.6.3
- ✅ System automatycznej aktualizacji z GitHub Releases
- ✅ Obsługa repozytorium prywatnego przez token GitHub (zapisywany szyfrowanym storage)
- ✅ Automatyczne sprawdzanie nowej wersji przy starcie (opcjonalne)
- ✅ Ręczne sprawdzenie i instalacja aktualizacji z Ustawień

---

## 📦 v1.6.2 (2026-03-22)

**Plik:** `Toya-Zulip-v1.6.2.apk`  
**Rozmiar:** 54.9 MB  

### Zmiany w v1.6.2
- ✅ Przywrócone oznaczenia statusów wiadomości w widoku „Wszystkie”: **★ gwiazdka**, **@all**, **@wzmianka**
- ✅ Naprawione mapowanie statusów obecności użytkowników (normalizacja email do lowercase)

---

## 📦 v1.6.1 (2026-03-22)

**Plik:** `Toya-Zulip-v1.6.1.apk`  
**Rozmiar:** 54.9 MB  

### Zmiany w v1.6.1
- ✅ Blokada biometryczna/PIN **włączona domyślnie** po instalacji
- ✅ Fallback na PIN systemowy gdy brak biometrii w urządzeniu
- ✅ Toggle w Ustawieniach zmieniony na "Blokada biometryczna / PIN"

---

## 📦 v1.6.0 (2026-03-22)

**Plik:** `Toya-Zulip-v1.6.0.apk`  
**Rozmiar:** 54.9 MB  

### Nowe funkcje w v1.6.0
- ✅ **Pulsująca obwódka wskaźnika pisania** — turkusowa animowana ramka wokół avatara gdy ktoś pisze (1000ms cykl, alpha 0.3→1.0)
- ✅ **Kropki obecności użytkowników** — zielony (aktywny), żółty (bezczynny), szary (offline) na avatarach DM
- ✅ **Wiadomości posortowane od najnowszych** w zakładce Wszystkie
- ✅ **Zakładka Gwiazdki (★)** — widok wiadomości oznaczonych gwiazdką
- ✅ **Przycisk "Przewiń do najnowszych" (FAB)** — pojawia się po przewinięciu w górę (Wszystkie + DM)
- ✅ **Blokada biometryczna** — weryfikacja odciskiem/twarzą przy starcie i powrocie z tła
- ✅ Powiadomia przy minimalzacji aplikacji (ON_RESUME lifecycle)

---

## 📦 v1.5.1 (2026-03-22)

**Plik:** `Toya-Zulip-v1.5.1.apk`  
**Rozmiar:** 54.9 MB  

### Zmiany w v1.5.1
- ✅ Powiadomienia zawierają nazwę kanału lub etykietę "Wiadomość bezpośrednia" (`setSubText`)
- ✅ Zastąpiono przycisk "Wyłącz" ikoną DoNotDisturb w ekranie kanałów

---

## 📦 v1.5.0 (2026-03-22)

**Plik:** `Toya-Zulip-v1.5.0.apk`  
**Rozmiar:** 54.7 MB  

### Nowe funkcje w v1.5.0
- ✅ Dynamiczny system wersjonowania (BuildConfig)
- ✅ Pełna dokumentacja dla użytkownika i administratora
- ✅ Stabilna obsługa powiadomień push
- ✅ Oznaczenia wiadomości (gwiazdka, @all, @mention)
- ✅ Real-time synchronizacja (long-poll)
- ✅ Bezpieczne przechowywanie poświadczeń (EncryptedSharedPreferences)
- ✅ Projekt opublikowany na GitHub

---

## Wymagania (wszystkie wersje)

- **Android:** 9.0+ (API 28+)
- **RAM:** minimum 256 MB (rekomendowany 512 MB+)
- **Serwer:** Zulip 2.1.0+

## Instalacja

1. Pobierz plik `.apk` z folderu `releases/`
2. Prześlij na urządzenie Android
3. Otwórz menedżerem plików i naciśnij "Zainstaluj"
4. Jeśli wymagane: **Ustawienia → Bezpieczeństwo → Instalacja z nieznanych źródeł**

Przez ADB:
```bash
adb install releases/Toya-Zulip-v1.6.4.apk
```

---

## ❓ Wsparcie

- Sprawdź [DOKUMENTACJA_UZYTKOWNIKA.md](../docs/DOKUMENTACJA_UZYTKOWNIKA.md)
- Otwórz [Issue](../../issues) na GitHub

---

## 🔐 Bezpieczeństwo

- ✅ HTTPS-only komunikacja
- ✅ Basic Auth (email:apikey)
- ✅ Encrypted local storage (AES256-GCM)
- ✅ Blokada biometryczna / PIN (domyślnie włączona od v1.6.1)
- ✅ Brak logowania poświadczeń w release builds

---

## 📊 Historia wersji

| Wersja | Data | APK | Opis |
|--------|------|-----|------|
| **1.6.4** | 2026-03-22 | ✅ | Poprawka licznika badge na ikonie aplikacji |
| **1.6.3** | 2026-03-22 | ✅ | Auto-update z prywatnego GitHub Releases (token) |
| **1.6.2** | 2026-03-22 | ✅ | Naprawa widoczności gwiazdek/@wzmianek i statusów obecności |
| **1.6.1** | 2026-03-22 | ✅ | Biometria/PIN domyślnie włączona |
| 1.6.0 | 2026-03-22 | ✅ | 6 nowych funkcji: obecność, animacja pisania, FAB, gwiazdki, biometria |
| 1.5.1 | 2026-03-22 | ✅ | Kontekst powiadomień (nazwa kanału / DM) |
| 1.5.0 | 2026-03-22 | ✅ | Wystabilizowana wersja z pełną dokumentacją |

---

**Ostatnia aktualizacja:** 2026-03-22
