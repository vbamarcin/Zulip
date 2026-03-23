# Git Setup — Instrukcje do wrzucenia na GitHub

Projekt Zulip jest gotowy do GitHub. Wykonaj poniższe kroki:

---

## 1️⃣ Zainstaluj Git

### Windows
Pobierz z: https://git-scm.com/download/win

Zainstaluj domyślnymi ustawieniami (use Git from PowerShell rekomendowane).

Po instalacji **otwórz nowy terminal PowerShell** (zamknij i otwórz ponownie).

Sprawdź:
```powershell
git --version
```

Powinna ukazać się wersja (np. `git version 2.43.0.windows.1`).

---

## 2️⃣ Skonfiguruj Git

Pierwszy raz używasz Git? Ustaw swoje dane:

```powershell
git config --global user.name "Twoja Nazwa"
git config --global user.email "twoj.email@example.com"
```

Sprawdź:
```powershell
git config --global --list
```

---

## 3️⃣ Utwórz repozytorium GitHub

1. Przejdź na https://github.com/new
2. **Repository name:** `zulip` (lub dowolna nazwa)
3. **Description:** `Nowoczesny klient Zulip na Android — Kotlin + Jetpack Compose`
4. **Visibility:** `Public` (jeśli chcesz, żeby wszyscy widzieli) lub `Private`
5. **Initialize this repository with:** 
   - ☐ Nie zaznaczaj nic (dodamy własne pliki)
6. Kliknij **Create repository**

GitHub pokaże Ci URL repozytorium, np.:
```
https://github.com/TWOJA_NAZWA/zulip.git
```

Schowaj ten URL — będzie potrzebny za chwilę.

---

## 4️⃣ Zainicjuj Git lokalnie i wrzuć kod

W terminalu PowerShell **w folderze projektu** (`C:\Users\mkras\Desktop\zulip`):

```powershell
cd C:\Users\mkras\Desktop\zulip

# Zainicjuj repozytorium
git init

# Ustaw branch na 'main'
git branch -m main

# Dodaj wszystkie pliki
git add .

# Sprawdź co będzie przesłane
git status
```

Powinna ukazać się lista plików przygotowanych do commit. Folderów takich jak `.gradle/`, `build/`, `.idea/` **nie powinno być** (chroniony przez `.gitignore`).

Jeśli są niepotrzebne pliki — sprawdź `.gitignore`.

---

## 5️⃣ Wykonaj pierwszy commit

```powershell
git commit -m "Initial commit: Zulip Android app v1.5.0"
```

---

## 6️⃣ Podłącz repozytorium GitHub

Zastąp `TWOJA_NAZWA/zulip` swoimi danymi:

```powershell
git remote add origin https://github.com/TWOJA_NAZWA/zulip.git
```

Sprawdź:
```powershell
git remote -v
```

Powinna ukazać się:
```
origin  https://github.com/TWOJA_NAZWA/zulip.git (fetch)
origin  https://github.com/TWOJA_NAZWA/zulip.git (push)
```

---

## 7️⃣ Wrzuć kod na GitHub (HTTPS)

```powershell
git push -u origin main
```

GitHub poprosi Cię o logowanie się (uwierzytelnienie):

### Opcja A: GitHub CLI (rekomendowana)
Jeśli zainstalowałeś GitHub CLI (`gh`), automatycznie się zaloguje:
```powershell
gh auth login
```

### Opcja B: Personal Access Token (Token)
1. GitHub → Settings → Developer settings → Personal access tokens (classic)
2. Kliknij **Generate new token**
3. Nazwa: `git-push-token`
4. Zaznacz: `repo` (pełny dostęp do repozytorium)
5. Wygeneruj token
6. **Skopiuj token** — pokaże się tylko raz!

W terminalu, gdy GitHub poprosi o hasło:
```
Username: TWOJA_NAZWA
Password: <wklej_token_tutaj>
```

### Opcja C: SSH (zaawansowane)
Jeśli znasz SSH:
1. Wygeneruj klucz:
   ```powershell
   ssh-keygen -t ed25519 -C "twoj.email@example.com"
   ```
2. Dodaj do GitHub: Settings → SSH and GPG keys → New SSH key
3. W `.git/config` zmień URL na `git@github.com:TWOJA_NAZWA/zulip.git`

---

## 8️⃣ Potwierdź wysłanie

Po `git push` możesz sprawdzić na GitHub:
- https://github.com/TWOJA_NAZWA/zulip
- Powinna ukazać się zawartość repozytorium
- README.md będzie wyświetlony na stronie głównej

---

## 📋 Szybki checklist

- [ ] Git zainstalowany (`git --version` działa)
- [ ] Git skonfigurowany (`user.name` i `user.email`)
- [ ] Repozytorium GitHub utworzone
- [ ] `.gitignore` w projekcie ✓ (już tam)
- [ ] `README.md` w projekcie ✓ (już tam)
- [ ] `DOKUMENTACJA_UZYTKOWNIKA.md` w `docs/` ✓
- [ ] `DOKUMENTACJA_TECHNICZNA.md` w `docs/` ✓
- [ ] `git init` + `git add .` + `git commit` 
- [ ] `git remote add origin https://...`
- [ ] `git push -u origin main`

---

## 🔄 Kolejne zmiany (po setapie)

Gdy będziesz edytował kod i chcesz wrzucić kolejne zmiany:

```powershell
git add .
git commit -m "Zmieniam funkcję X"
git push
```

---

## ❓ Troubleshooting

### "git: command not found"
→ Git nie zainstalowany. Pobierz z https://git-scm.com/download/win i zainstaluj.

### "fatal: bad config file for C:/Users/.gitconfig"
→ Skonfiguruj ponownie:
```powershell
git config --global user.name "Twoja Nazwa"
git config --global user.email "email@example.com"
```

### "Permission denied (publickey)" (SSH)
→ Klucz SSH nie jest zsynchronizowany z GitHub. Użyj HTTPS zamiast SSH lub dodaj klucz.

### "fatal: not a Git repository"
→ Jesteś w złym folderze. Upewnij się, że jesteś w `C:\Users\mkras\Desktop\zulip`, a `.git` folder został utworzony.

---

## 📞 Potrzebujesz pomocy?

Zachowaj ten dokument. Możesz wrócić do tego kroku-po-kroku.

**Powodzenia! 🚀**

