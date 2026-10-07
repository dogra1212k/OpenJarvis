# Hindi JARVIS Android Client

यह OpenJarvis server के लिए native Android client है।

## Features

- Hindi / Hinglish / English chat
- Mic से Hindi speech-to-text
- JARVIS reply का Android Text-to-Speech
- OpenJarvis server URL setting
- Bearer API key support
- Model name setting
- Session chat history
- GitHub Actions से automatic debug APK build

## OpenJarvis server तैयार करें

Host machine पर:

```bash
jarvis init --preset hindi-jarvis --force
jarvis auth generate-key
```

Generated key को environment variable में रखें:

```bash
export OPENJARVIS_API_KEY="YOUR_KEY"
```

Android phone से LAN पर connect करने के लिए OpenJarvis config में server host को `0.0.0.0` करें। फिर:

```bash
jarvis serve
```

Server को बिना API key के public internet पर expose मत करें।

## Android app में क्या भरना है

- Server URL: `http://YOUR_COMPUTER_LAN_IP:8000`
- API key: `jarvis auth generate-key` से मिली key
- Model: server पर available model, जैसे `qwen3.5:4b`

`10.0.2.2` केवल Android emulator के लिए उपयोगी है जब server उसी development computer पर चल रहा हो।

## APK build

Repo में workflow है:

`.github/workflows/android-apk.yml`

यह `android-client` बदलने पर debug APK build करता है और artifact का नाम होगा:

`hindi-jarvis-debug-apk`

Manual build भी किया जा सकता है:

```bash
cd android-client
gradle :app:assembleDebug
```

Output:

`app/build/outputs/apk/debug/app-debug.apk`

## API

Client OpenJarvis के OpenAI-compatible endpoint को call करता है:

```
POST /v1/chat/completions
Authorization: Bearer <key>
Content-Type: application/json
```

## Security

- API key GitHub पर commit न करें।
- Trusted LAN या VPN prefer करें।
- Local development के लिए HTTP cleartext enabled है।
- Internet exposure के लिए HTTPS reverse proxy use करें।
