# 🔑 Підписання APK в GitHub Actions

Без підпису APK встановлюється лише в debug-режимі.  
Для release APK (signed) потрібно налаштувати keystore один раз.

---

## Крок 1 — Згенеруй keystore (один раз)

```bash
keytool -genkey -v \
  -keystore ai-chat-parser.keystore \
  -alias ai_chat_parser \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Запам'ятай або збережи:
- `keystore password` → буде `KEYSTORE_PASSWORD`
- `key alias` → `ai_chat_parser` → буде `KEY_ALIAS`
- `key password` → буде `KEY_PASSWORD`

---

## Крок 2 — Конвертуй keystore у Base64

**Linux / Mac:**
```bash
base64 -i ai-chat-parser.keystore | pbcopy
# або
base64 ai-chat-parser.keystore > keystore_b64.txt
```

**Windows (PowerShell):**
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("ai-chat-parser.keystore")) | clip
```

---

## Крок 3 — Додай Secrets у GitHub

1. Відкрий репозиторій → **Settings → Secrets and variables → Actions**
2. Натисни **New repository secret** і додай 4 секрети:

| Secret name | Значення |
|-------------|----------|
| `KEYSTORE_BASE64` | Base64 з кроку 2 |
| `KEY_ALIAS` | `ai_chat_parser` |
| `KEYSTORE_PASSWORD` | пароль keystore |
| `KEY_PASSWORD` | пароль ключа |

---

## Результат

| Подія | Debug APK | Release APK |
|-------|-----------|-------------|
| Push до `main` | ✅ завжди | ✅ якщо secrets є |
| Pull Request | ✅ завжди | ❌ (безпека) |
| Tag `v1.0.0` | ✅ | ✅ + GitHub Release |
| Ручний запуск | ✅ | ✅ якщо secrets є |

---

## Де знайти APK після білду

1. GitHub → репозиторій → вкладка **Actions**
2. Вибери потрібний run
3. Внизу сторінки → **Artifacts**
4. Скачай ZIP → розпакуй → встанови APK на телефон

При тегу `v*` APK також з'являється у **Releases**.

---

## ⚠️ Важливо

- **Ніколи не комміть** файл `.keystore` або `keystore.properties` у репозиторій
- Він вже у `.gitignore`
- Втрата keystore = неможливість оновлювати APK (треба буде перевстановлювати)
- Зберігай keystore у надійному місці (Google Drive, 1Password, тощо)
