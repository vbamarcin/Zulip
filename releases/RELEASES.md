# Releases

latest_version: 1.7.5
latest_apk: Zulip-v1.7.5.apk
latest_url: https://github.com/vbamarcin/Zulip/raw/main/releases/Zulip-v1.7.5.apk

## v1.7.5
Zulip-v1.7.5.apk

- Naprawiono układ czatu przy otwarciu klawiatury: pole wpisywania i ostatnia wiadomość pozostają widoczne.
- Dodano niezawodne przewijanie do najnowszej wiadomości przy fokusie pola wpisywania.
- Usprawniono obsługę reakcji emoji w kanałach i DM (unicode/custom), w tym zgodność aliasów emoji_name.
- Dodano lepszą diagnostykę błędów API dla dodawania/usuwania reakcji oraz odświeżanie widoku po sukcesie.
- Poprawiono kompatybilność realtime event queue dla zdarzeń reaction na różnych wersjach serwera Zulip.

## v1.7.3
Zulip-v1.7.3.apk

- Powiadomienia kanałowe respektują ustawienia desktopowe serwera (mute i desktop_notifications).
- Zsynchronizowano subskrypcje z serwera (/users/me/subscriptions).
- Włączono globalny przełącznik enable_desktop_notifications z serwera.

## v1.7.2
Zulip-v1.7.2.apk

- Naprawiono regresję powiadomień kanałowych, gdy lokalny cache streamów jest pusty.
- Dodano fallback rejestracji kolejki zdarzeń dla kompatybilności z różnymi wersjami serwera Zulip.
- Dodano logowanie błędów pętli realtime i przetwarzania eventów dla łatwiejszej diagnostyki.
- Powiadomienia kanałowe respektują teraz ustawienia desktopowe serwera (mute i desktop_notifications).

## v1.7.1
Zulip-v1.7.1.apk
