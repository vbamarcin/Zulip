# Zulip — Dokumentacja użytkownika
**Wersja aplikacji: 1.5.0**

---

## Spis treści
1. [Pierwsze uruchomienie i logowanie](#1-pierwsze-uruchomienie-i-logowanie)
2. [Nawigacja – główne zakładki](#2-nawigacja--główne-zakładki)
3. [Wiadomości prywatne (DM)](#3-wiadomości-prywatne-dm)
4. [Kanały i tematy](#4-kanały-i-tematy)
5. [Wszystkie wiadomości](#5-wszystkie-wiadomości)
6. [Wyszukiwanie](#6-wyszukiwanie)
7. [Pisanie i wysyłanie wiadomości](#7-pisanie-i-wysyłanie-wiadomości)
8. [Reakcje emoji](#8-reakcje-emoji)
9. [Dodawanie plików i zdjęć](#9-dodawanie-plików-i-zdjęć)
10. [Oznaczenia wiadomości](#10-oznaczenia-wiadomości)
11. [Ustawienia](#11-ustawienia)
12. [Powiadomienia](#12-powiadomienia)
13. [Wylogowanie](#13-wylogowanie)

---

## 1. Pierwsze uruchomienie i logowanie

Po uruchomieniu aplikacji pojawia się ekran logowania. Uzupełnij trzy pola:

| Pole | Co wpisać |
|------|-----------|
| **Adres serwera** | Adres Twojego serwera Zulip, np. `zulip.firma.pl` |
| **Email** | Adres e-mail przypisany do Twojego konta |
| **Hasło / Klucz API** | Wybierz typ: hasło lub klucz API |

### Tryby logowania
- **Hasło** – wpisujesz hasło do swojego konta. Aplikacja pobierze klucz API automatycznie.
- **Klucz API** – jeśli masz gotowy klucz API (możesz go uzyskać w ustawieniach profilu na stronie serwera Zulip), wklej go tutaj bezpośrednio.

> **Ważne:** Połączenie odbywa się wyłącznie przez bezpieczny protokół HTTPS. Adresy HTTP są blokowane ze względów bezpieczeństwa.

Po zalogowaniu aplikacja zapamiętuje sesję – nie trzeba logować się ponownie po każdym zamknięciu.

---

## 2. Nawigacja – główne zakładki

Na dole ekranu znajduje się pasek nawigacyjny z **5 zakładkami**:

| Ikona | Nazwa | Co robi |
|-------|-------|---------|
| 💬 | **DM** | Wiadomości prywatne – rozmowy jeden na jeden |
| 📡 | **Kanały** | Lista kanałów (strumieni) i tematów |
| 📥 | **Wszystkie** | Podgląd wszystkich wiadomości ze wszystkich miejsc |
| 🔍 | **Szukaj** | Wyszukiwanie wiadomości po treści |
| ⚙️ | **Ustawienia** | Preferencje aplikacji |

Cyfra na ikonce zakładki oznacza liczbę nieprzeczytanych wiadomości.

---

## 3. Wiadomości prywatne (DM)

Zakładka **DM** pokazuje listę wszystkich prowadzonych rozmów prywatnych.

### Lista rozmów
- Każda rozmowa pokazuje **imię i nazwisko** rozmówcy oraz **fragment ostatniej wiadomości**.
- Po prawej stronie widoczna jest **data/godzina** ostatniej wiadomości.
- Jeśli masz nieprzeczytane wiadomości, przy rozmowie pojawi się **kolorowa liczba**.
- Rozmowy są posortowane od najnowszej do najstarszej.

### Otwieranie rozmowy
Dotknij wybranej rozmowy – otwiera się pełny wątek z historią wiadomości.

### Nowa rozmowa DM
Naciśnij przycisk **„+"** (lub „Nowa wiadomość") w prawym dolnym rogu ekranu, a następnie wpisz email osoby, do której chcesz napisać.

### Powrót do listy rozmów
Użyj przycisku **wstecz** (systemowego lub strzałki w górę ekranu) lub ponownie dotknij zakładki **DM**.

---

## 4. Kanały i tematy

Zakładka **Kanały** to hierarchiczna struktura:
```
Kanał (np. „Dział IT")
  └── Temat (np. „Awarie")
        └── Wiadomości
```

### Przeglądanie kanałów
- Wyświetlana jest lista dostępnych kanałów z ich nazwami.
- Użyj pola **wyszukiwania** u góry, żeby szybko znaleźć kanał po nazwie.
- Dotknij kanału, by zobaczyć listę jego tematów.

### Przeglądanie tematów
Po wybraniu kanału pojawi się lista tematów posortowanych według aktywności (najnowsze na górze). Dotknij tematu, żeby otworzyć rozmowę.

### Opcje przy kanale (długie przytrzymanie lub menu ⋮)

| Opcja | Efekt |
|-------|-------|
| **Wycisz** | Powiadomienia z tego kanału nie będą się pojawiać. Kanał nadal jest widoczny na liście. |
| **Wyłącz** | Kanał znika z listy. Możesz go przywrócić w **Ustawieniach → Wyłączone kanały**. Pojawia się też przycisk **Cofnij** przez chwilę po wyłączeniu. |

> Aby **przywrócić wyciszony lub wyłączony kanał**, przejdź do zakładki **Ustawienia**, gdzie znajdziesz listy wyciszonych i wyłączonych kanałów z przyciskami „Przywróć" i „Włącz".

---

## 5. Wszystkie wiadomości

Zakładka **Wszystkie wiadomości** to podgląd wszystkich wiadomości — zarówno z kanałów, jak i wiadomości prywatnych — posortowanych chronologicznie.

### Karta wiadomości zawiera:
- **Imię i nazwisko** nadawcy
- **Nazwę kanału i tematu** (dla wiadomości w kanałach) lub **„Prywatna"** (dla DM)
- **Treść wiadomości** (skrócona w podglądzie)
- Miniaturkę pierwszego zdjęcia, jeśli wiadomość zawiera obraz
- Znaczniki: **★ gwiazdka**, **@all**, **@ty** (patrz [Oznaczenia wiadomości](#10-oznaczenia-wiadomości))

### Przejście do rozmowy
Dotknij dowolnej karty, aby przejść bezpośrednio do oryginalnej rozmowy (kanał+temat lub wątek DM).

---

## 6. Wyszukiwanie

Zakładka **Szukaj** pozwala przeszukiwać wiadomości na całym serwerze.

### Jak szukać
1. Wpisz szukany tekst w pole u góry ekranu.
2. Wyniki pojawiają się automatycznie.
3. Szukany fragment jest **podświetlony** w treści każdego wyniku.
4. Każdy wynik pokazuje nadawcę, kanał/temat i fragment wiadomości.
5. Dotknij wyniku, aby przejść do pełnej rozmowy.

---

## 7. Pisanie i wysyłanie wiadomości

### Pole tekstowe
Na dole każdej rozmowy (DM lub kanał/temat) znajduje się pole do pisania wiadomości.

### Autouzupełnianie
Podczas pisania możesz użyć:

| Znak | Efekt |
|------|-------|
| `@` | Pojawia się lista osób do oznaczenia. Wybierz osobę, a jej wzmianka `@Imię` zostanie wstawiona automatycznie. |
| `#` | Pojawia się lista tematów w bieżącym kanale. Wybierz temat, a jego odnośnik zostanie wstawiony. |

### Wysyłanie
Naciśnij przycisk **Wyślij** (ikona strzałki). Wiadomość pojawi się w rozmowie.

### Edycja wysłanej wiadomości
Długo przytrzymaj swoją wiadomość lub użyj menu przy wiadomości → wybierz **„Edytuj"**. Zmień treść i potwierdź.

> **Uwaga:** Możliwość edycji i usuwania wiadomości innych użytkowników zależy od Twoich uprawnień na serwerze (moderator / właściciel).

### Usuwanie wiadomości
Długo przytrzymaj wiadomość → wybierz **„Usuń"** → potwierdź.

---

## 8. Reakcje emoji

Możesz reagować na wiadomości za pomocą emoji.

### Jak dodać reakcję
1. Długo przytrzymaj wiadomość lub naciśnij ikonę **☺** przy wiadomości.
2. Wybierz emoji z listy.
3. Emoji pojawi się pod wiadomością z licznikiem.

### Usunięcie reakcji
Ponownie dotknij tego samego emoji pod wiadomością – zostanie usunięte.

---

## 9. Dodawanie plików i zdjęć

W polu do pisania wiadomości znajdziesz ikonę **📎** (spinacz) — naciśnij ją, aby wybrać plik ze swojego urządzenia.

- Plik zostanie wgrany na serwer.
- W treści wiadomości automatycznie pojawi się odnośnik do pliku: `[nazwa_pliku](link)`.
- Zdjęcia i obrazki są wyświetlane bezpośrednio w wiadomości jako miniaturki.

---

## 10. Oznaczenia wiadomości

Niektóre wiadomości mają kolorowe oznaczenia, które ułatwiają znalezienie ważnych treści:

| Oznaczenie | Kolor | Znaczenie |
|-----------|-------|-----------|
| **★ gwiazdka** | Złoty | Wiadomość została przez Ciebie oznaczona gwiazdką (ulubione/zapisane) |
| **@all** | Pomarańczowy | Ktoś użył wzmianki `@all`, `@everyone` lub `@channel` — dotyczy wszystkich |
| **@ty** | Zielony | Ktoś oznaczył Ciebie bezpośrednio wzmianką `@TwójEmail` lub `@TwojaNazwa` |

Kolorowe tło i ramka wiadomości dodatkowo pomagają wyróżnić ważne treści w długich listach.

---

## 11. Ustawienia

Zakładka **Ustawienia** pozwala dostosować wygląd i działanie aplikacji.

### Konto
Sekcja informacyjna pokazuje:
- Adres serwera
- Zalogowany email
- Tryb logowania (hasło / klucz API)

### Preferencje wyglądu

| Opcja | Opis |
|-------|------|
| **Tryb kompaktowy** | Włącz, jeśli chcesz zmniejszyć odstępy i wyświetlić więcej wiadomości na ekranie |
| **Rozmiar tekstu** | Przesuń suwak, żeby zwiększyć lub zmniejszyć czcionkę. Dostępne też szybkie przyciski: Mała / Normalna / Duża |
| **Markdown** | Włącz, aby wiadomości były renderowane z formatowaniem (pogrubienia, listy, tabele, zdjęcia). Wyłącz, jeśli wolisz czysty tekst |

### Powiadomienia

| Opcja | Opis |
|-------|------|
| **Powiadomienia (global)** | Główny przełącznik – wyłącz, aby całkowicie wyciszyć aplikację |
| **Powiadomienia DM** | Tylko dla wiadomości prywatnych |
| **Powiadomienia kanałów** | Tylko dla wiadomości w kanałach |
| **Reset notyfikacji** | Przywraca wszystkie ustawienia powiadomień do domyślnych |

### Wyciszone kanały
Lista kanałów, które zostały wyciszone (powiadomienia wyłączone, kanał nadal widoczny). Przy każdym kanale jest przycisk **„Przywróć"**, a na dole **„Przywróć wszystkie kanały"**.

### Wyłączone kanały
Lista kanałów ukrytych z widoku. Przy każdym jest przycisk **„Włącz"**, na dole **„Włącz wszystkie kanały"**.

### Wersja aplikacji
Na dole ekranu ustawień widoczna jest wersja aplikacji, np. `Zulip v1.5.0 (1)`.

---

## 12. Powiadomienia

Aplikacja wysyła powiadomienia push o nowych wiadomościach, gdy jesteś poza aplikacją.

### Rodzaje powiadomień
- **DM** – gdy ktoś wyśle Ci wiadomość prywatną
- **Kanały** – gdy pojawi się nowa wiadomość w obserwowanym kanale

### Wymagania
- Na Androidzie 13 i nowszym aplikacja poprosi o **zgodę na powiadomienia** przy pierwszym uruchomieniu. Bez tej zgody powiadomienia nie będą działać.

### Co widzisz w powiadomieniu
- Imię i nazwisko nadawcy
- Treść wiadomości
- Dotknięcie powiadomienia otwiera aplikację bezpośrednio przy odpowiedniej wiadomości

### Automatyczne czyszczenie
Gdy przeczytasz wiadomości w aplikacji, powiązane powiadomienia znikają automatycznie z paska systemowego.

---

## 13. Wylogowanie

W zakładce **Ustawienia** na dole strony znajdziesz przycisk **„Wyloguj"**.

Po naciśnięciu:
- Sesja zostaje zakończona
- Dane logowania zostają usunięte z urządzenia
- Aplikacja wraca do ekranu logowania

> Ponowne zalogowanie wymaga wpisania danych dostępowych.
