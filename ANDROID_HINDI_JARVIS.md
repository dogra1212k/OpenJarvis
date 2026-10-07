# Hindi JARVIS on Android

This fork includes a `hindi-jarvis` preset and Hindi/Hinglish persona.

## What this gives you

- Hindi/Hinglish-first assistant behavior
- Lightweight local model preset using Ollama + Qwen
- Browser/API access through OpenJarvis
- A safe base for adding voice input, TTS, and phone automation later

## Important Android note

OpenJarvis is a Python-first desktop/server project. It is not currently a native Android APK project.

The most reliable Android setup is:

1. Run OpenJarvis on a Linux, Windows/WSL2, or macOS machine.
2. Start the server:
   ```bash
   jarvis init --preset hindi-jarvis --force
   jarvis serve
   ```
3. Open the OpenJarvis browser interface from your Android phone.

For LAN access, follow OpenJarvis security requirements: bind to `0.0.0.0` only after setting an API key. Do not expose an unauthenticated server to the internet.

## Local setup

Install OpenJarvis using the official installer for your host system, then:

```bash
jarvis init --preset hindi-jarvis --force
jarvis chat
```

To use the GUI:

```bash
jarvis gui
```

## Android-native roadmap

A proper Android version should be a separate mobile client that connects to the OpenJarvis API. Recommended features:

- microphone button using Android SpeechRecognizer
- Hindi speech recognition
- Hindi Text-to-Speech
- chat screen
- configurable OpenJarvis server URL and API key
- safe app-opening intents for allowed apps
- explicit confirmation for sensitive actions

Do not place API keys directly in a public GitHub repository.
