# 🤖 AI Chat Parser — Android 14

Parse, export and save conversations from any AI chat app.  
Supports **ChatGPT, Claude, Gemini, Copilot, Perplexity, Grok** and any unknown AI app.

---

## ✨ Features

| Feature | Details |
|---------|---------|
| **Floating button** | Draggable overlay button over any app |
| **Full parse** | Auto-scrolls to top → collects entire chat |
| **Quick parse** | Grabs only the current visible screen |
| **Smart role detection** | Identifies User vs Assistant messages |
| **Formatting detection** | Code blocks, headers, bullets, tables, links |
| **Attachment detection** | Images, files, audio, video, code files |
| **Timestamp extraction** | Original timestamps from the chat UI |
| **Export formats** | JSON (structured) + Markdown (readable) |
| **Filename** | `ai_chatgpt_My_Chat_Title_20240516_143022.json` |
| **History screen** | Browse, share, delete past exports |

---

## 📁 Project Structure

```
app/src/main/
├── java/com/parser/aichat/
│   ├── MainActivity.kt                    # Permissions + history UI
│   ├── model/
│   │   └── Models.kt                      # Data classes (ChatMessage, ChatSession…)
│   ├── parser/
│   │   ├── AppDetector.kt                 # Detects AI platform + chat title
│   │   └── MessageExtractor.kt            # Core extraction logic (3 strategies)
│   ├── service/
│   │   ├── ChatParserAccessibilityService.kt  # Reads UI, scrolls, collects
│   │   └── OverlayService.kt              # Floating button (foreground service)
│   └── storage/
│       ├── JsonExporter.kt                # Full JSON with metadata + formatting
│       ├── MarkdownExporter.kt            # Human-readable Markdown
│       └── FileManager.kt                # Saves to device storage
└── res/
    ├── layout/
    │   ├── activity_main.xml
    │   ├── overlay_button.xml
    │   └── item_export.xml
    └── xml/
        ├── accessibility_service_config.xml
        └── file_paths.xml
```

---

## 🚀 Build & Install

### Requirements
- Android Studio Hedgehog or newer
- Android SDK 34
- Device or emulator running Android 8.0+

### Steps

```bash
# Clone / open project in Android Studio
# Sync Gradle
# Build → Run on device
```

Or build APK:
```bash
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔐 Permissions Required

### 1. Overlay Permission (`SYSTEM_ALERT_WINDOW`)
- Needed for the floating button
- Go to: **Settings → Apps → AI Chat Parser → Display over other apps → Enable**
- Or tap "Enable" in the app → it opens the right screen

### 2. Accessibility Service
- Needed to read chat message content
- Go to: **Settings → Accessibility → Installed services → AI Chat Parser → Enable**
- The app will guide you there automatically

> ⚠️ **Privacy**: All data stays on your device. No internet permission. No data sent anywhere.

---

## 📖 How It Works

### Extraction Strategies (in priority order)

**Strategy 1 — View IDs** (most accurate)
Looks for known resource IDs from ChatGPT, Claude, Gemini.  
Works when the exact view ID is known.

**Strategy 2 — Heuristic traversal** (general)
Walks the accessibility tree, detects message containers by:
- Content description keywords (`user`, `assistant`, `ai`, etc.)
- View ID patterns
- Parent node role hints
- Screen position

**Strategy 3 — Fallback** (catch-all)
Extracts all text nodes if strategies 1 & 2 find nothing.

### Scroll & Parse Flow
```
User taps 🤖
     ↓
Scroll to top (ACTION_SCROLL_BACKWARD × 20)
     ↓
Wait 1.5s
     ↓
Loop:
  collectCurrentScreen() → deduplicate messages
  ACTION_SCROLL_FORWARD
  Wait 600ms
  (repeat until can't scroll)
     ↓
Build ChatSession
     ↓
Save JSON + Markdown
     ↓
Toast notification
```

---

## 📄 Output Format

### JSON example
```json
{
  "version": "1.0",
  "platform": "ChatGPT",
  "chat_title": "My awesome chat",
  "total_messages": 12,
  "stats": {
    "user_messages": 6,
    "assistant_messages": 6,
    "messages_with_code": 2
  },
  "messages": [
    {
      "index": 0,
      "role": "user",
      "content": "Explain quantum computing",
      "timestamp": "2024-05-16 14:30:22",
      "formatting": {
        "has_code_blocks": false,
        "has_bullet_points": false
      },
      "attachments": []
    },
    {
      "index": 1,
      "role": "assistant",
      "content": "Quantum computing uses qubits...",
      "formatting": {
        "has_code_blocks": true,
        "code_blocks": [
          { "language": "python", "code": "from qiskit import QuantumCircuit" }
        ]
      }
    }
  ]
}
```

### Markdown example
```markdown
# My awesome chat

| Field | Value |
|-------|-------|
| **Platform** | ChatGPT |
| **Total messages** | 12 |

## 👤 User — `14:30`
Explain quantum computing

---

## 🤖 Assistant — `14:30`
Quantum computing uses qubits...
```python
from qiskit import QuantumCircuit
```
*Format: `code`*
```

---

## 🛠 Adding New AI Apps

In `Models.kt`, add to the `AIPlatform` enum:

```kotlin
MY_AI("MyAI", listOf("com.myai.app")),
```

In `MessageExtractor.kt`, add View IDs to `MESSAGE_CONTAINER_IDS`:

```kotlin
"com.myai.app" to listOf(
    "com.myai.app:id/message_bubble",
    "com.myai.app:id/chat_content"
)
```

---

## 📱 File Locations

- **Android 10+**: `/Android/data/com.parser.aichat/files/AIChatParser/`
- **Android 9 and below**: `/Download/AIChatParser/`

Access via: **Files app → Internal storage → Android → data → com.parser.aichat → files → AIChatParser**

---

## ⚠️ Known Limitations

1. **Role detection** may be inaccurate for unknown apps (uses heuristics)
2. **Streaming responses** (typing animation) — parse after response is complete
3. **Encrypted/WebView chats** may not expose accessibility tree
4. **Voice messages** — detected as attachment but audio not extracted
5. **Images in chat** — detected and noted but image files not copied

---

## 📋 Roadmap

- [ ] Image attachment extraction (copy to export folder)
- [ ] Auto-parse on chat open
- [ ] Cloud backup (Google Drive optional)
- [ ] SQLite local database for search
- [ ] PDF export format
- [ ] Chat comparison / diff tool
