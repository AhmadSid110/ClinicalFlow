# ClinicalFlow

AI-augmented audio notebook for medical students and residents.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    ANDROID APP                          │
├─────────────────────────────────────────────────────────┤
│  UI Layer (Jetpack Compose)                            │
│  ├── HomeScreen (notes list)                           │
│  ├── RecordingScreen (mic + live transcript)           │
│  ├── NoteDetailScreen (view/regenerate)                │
│  └── SettingsScreen (API keys)                         │
├─────────────────────────────────────────────────────────┤
│  Audio Layer                                           │
│  ├── AudioRecordService (foreground, PCM 16kHz)        │
│  └── DeepgramClient (WebSocket streaming STT)          │
├─────────────────────────────────────────────────────────┤
│  Processing Layer                                      │
│  ├── GeminiClient (SOAP/Study Notes/Summary)           │
│  └── PiiScrubber (regex-based de-identification)       │
├─────────────────────────────────────────────────────────┤
│  Data Layer                                            │
│  ├── Room Database (3-layer: transcript/notes/output)  │
│  └── SecureStorage (EncryptedSharedPreferences)       │
└─────────────────────────────────────────────────────────┘
```

## Stack

| Component | Technology |
|-----------|------------|
| UI | Jetpack Compose + Material 3 |
| Audio | AudioRecord (PCM 16kHz mono) |
| STT | Deepgram WebSocket (nova-2-medical) |
| LLM | Gemini 1.5 Flash API |
| Storage | Room + EncryptedSharedPreferences |
| Networking | OkHttp |

## Setup

1. Get API keys:
   - Deepgram: https://console.deepgram.com
   - Gemini: https://aistudio.google.com

2. Open in Android Studio

3. Build & run

4. Enter API keys in Settings

## Note Types

| Type | Gemini Output |
|------|---------------|
| Patient Encounter | SOAP Note |
| Lecture | Study Notes + Practice Questions |
| Study Session | Summary |

## PII Scrubbing

Automatically scrubs before Gemini:
- Names with titles (Mr, Mrs, Dr, etc.)
- Ages with context
- Phone numbers
- MRNs
- DOBs
- SSNs
- Emails
- Addresses

## Build

```bash
./gradlew assembleDebug
```