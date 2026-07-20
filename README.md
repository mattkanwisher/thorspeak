# ThorSpeak

Voiceover companion for playing Japanese Game Boy games on the **AYN Thor**
(dual-screen Android handheld). The app runs on the bottom screen, periodically
captures the top screen (where the emulator runs), OCRs the Japanese dialogue,
and speaks it aloud in **Japanese, English, or Thai**. Tap a word to look it up,
save it as a flashcard, and export the deck to Anki.

```
┌─ AYN Thor ──────────────┐          ┌─ Desktop (this repo /server) ─────────┐
│ top: emulator (primary) │          │ FastAPI on :8737                      │
│   ▲ MediaProjection     │  LAN     │  manga-ocr   → Japanese text          │
│ bottom: ThorSpeak app ──┼─────────▶│  Claude Haiku → translation / glosses │
│   change gate (ML Kit)  │◀─────────┤  edge-tts    → mp3 (cached forever)   │
│   LRU audio cache       │  audio   │  genanki     → .apkg export           │
└─────────────────────────┘          └───────────────────────────────────────┘
```

Everything is cached content-addressed (sha256 of normalized text + lang +
voice): each unique dialogue line is translated and synthesized **once, ever**.
The handheld keeps its own rotating LRU audio cache so repeated lines play
instantly with zero network.

## Server

Requires Python 3.11+ and [uv](https://docs.astral.sh/uv/). First run downloads
the manga-ocr model (~450 MB) from HuggingFace.

```sh
cd server
cp .env.example .env        # set ANTHROPIC_API_KEY or OPENROUTER_API_KEY
uv sync                     # CPU torch by default (safe on any box, no CUDA)
./run.sh                    # serves on 0.0.0.0:8737
```

GPU inference is opt-in. For AMD cards with a ROCm-supported architecture
(roughly gfx90a/gfx11xx+ discrete GPUs — not the tiny iGPU on desktop Ryzen):

```sh
uv sync --no-group cpu --group rocm   # ~6 GB download, ~12 GB installed
```

Don't run `uv sync --no-default-groups` — with neither torch group selected,
the resolver falls back to the CUDA build from PyPI.

Run as a service (auto-start on boot, auto-restart):

```sh
mkdir -p ~/.config/systemd/user
cp server/thorspeak-server.service ~/.config/systemd/user/
systemctl --user enable --now thorspeak-server
loginctl enable-linger            # start at boot without logging in
```

Reach it from anywhere on your tailnet (the app's server URL then works
away from home, with TLS):

```sh
tailscale serve --bg 8737         # -> https://<machine>.<tailnet>.ts.net/
```

Smoke test:

```sh
curl localhost:8737/health
curl -X POST localhost:8737/speak -H 'content-type: application/json' \
     -d '{"text":"はい、そうです","lang":"ja"}'
curl -F image=@tests/fixtures/sample_frame.png -F lang=en localhost:8737/process
```

### Endpoints

| Endpoint | Purpose |
|---|---|
| `POST /process` | Hot path: image → OCR → translate (if lang≠ja) → TTS → `{text, translation, audio_url, ...}` |
| `POST /speak` | Text → translate/TTS without re-OCR (replay in another language) |
| `POST /ocr` | OCR only (debug) |
| `GET /audio/{hash}.mp3` | Cached audio, immutable |
| `POST /lookup` | Word → reading/meaning/example via Claude |
| `POST/GET/DELETE /flashcards` | Flashcard CRUD (409 on duplicate word+reading) |
| `GET /anki/export.apkg` | Anki deck built from all flashcards (stable GUIDs — re-import updates, never duplicates) |
| `GET /cache/stats` | Cache and DB counters |

Without `ANTHROPIC_API_KEY`, Japanese voiceover works fully; translation and
word lookup return 503.

## Android app

Kotlin + Jetpack Compose, minSdk 33 (the Thor runs Android 13). Open `app/` in
Android Studio, or build from CLI:

```sh
cd app
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Usage on the Thor

1. Start the emulator on the **top** screen, launch ThorSpeak on the **bottom**
   screen via AYN's launcher.
2. Settings → set the server URL to your desktop's LAN address, tap **Test**.
3. Tap **Start session** and accept the screen-capture prompt (once per
   session).
4. Play. When a dialogue box finishes drawing, you'll hear the voiceover
   within a couple of seconds. Advancing text interrupts the current line.
5. Tap any word in the recognized text → gloss popup → **Add to flashcards**.
6. Flashcards → **Export to Anki** shares the `.apkg` (AnkiDroid opens it
   directly); or download `http://<server>:8737/anki/export.apkg` on your
   desktop.

### How frames are filtered

Three-stage gate, cheapest first, so the server only sees genuinely new text:

1. **8×8 average-hash** pixel diff (microseconds) — unchanged frames drop.
2. **Stability debounce** — a changed frame must match the *next* frame before
   processing, which waits out the Game Boy typewriter text effect.
3. **ML Kit Japanese OCR** on-device — if the (normalized) text equals the last
   line sent, drop. ML Kit is only a change detector; the server's manga-ocr is
   authoritative. If ML Kit struggles with your game's pixel font, enable
   **pixel-only gate** in Settings.

If the emulator letterboxes or shows a HUD, use **Settings → Select capture
region** during a session and drag over just the dialogue box.

## Repo layout

```
server/                  Python FastAPI server (uv project)
  thorspeak_server/      main, ocr, translate, lookup, tts, anki, db, normalize
  tests/                 pytest + OCR fixture image
app/                     Android app (Gradle, single module)
  app/src/main/java/nu/hyperworks/thorspeak/
    capture/             MediaProjection, frame gate, ML Kit
    net/                 Retrofit API + DTOs
    audio/               LRU cache + ExoPlayer
    data/                DataStore settings, session state, ViewModel
    ui/                  Compose screens
```
