# PersonaAI 🧠✨

> **Autonomous Digital Twin & Contextual Life Synthesis Engine for Android**

PersonaAI is an on-device personal intelligence platform designed to passively understand your digital life, synthesize life patterns, and construct an evolving digital persona. Operating across **Day Mode** (passive background ingestion) and **Night Mode** (autonomous cognitive synthesis and dreaming), PersonaAI bridges local on-device small language models (SLMs) and frontier cloud models (Gemini) while maintaining strict local privacy controls.

---

## 🌟 Key Features

### 1. 🌓 Dual-Phase Cognitive Lifecycle
* **Day Mode (Multimodal Context Ingestion):**
  * **Typing & Accessibility Monitor:** Captures keystroke interactions and active application contexts via an Android Accessibility Service.
  * **Notification Listener:** Monitors communications across SMS, messaging apps, and email with sender-attribution tracking.
  * **System Telemetry:** Tracks screen time, app usage metrics (`UsageStatsManager`), and call activity (`CallLog` & Contacts).
  * **Multimodal OCR Analyzer:** Periodically inspects newly captured photos and screenshots using Google ML Kit on-device Text Recognition.
* **Night Mode ("Dream Journal" & Synthesis):**
  * Runs automated background processing (via Android WorkManager) while the device charges overnight.
  * Analyzes chronological raw events, extracts emotional states, refines interpersonal relationships, and generates narrative reflections ("Dreams").
  * Dynamically updates distinct persona models for the user and recurring contacts.

### 2. ⚡ Hybrid Intelligence (Cloud + On-Device)
* **Local SLM Engine:** Powered by **Google MediaPipe Tasks GenAI** with OpenCL GPU acceleration (Adreno & Mali GPUs) for private, zero-latency on-device processing.
* **Frontier Cloud Reasoning:** Integrated with **Google Gemini SDK** for deep semantic synthesis, long-context life summaries, and conversational dialogue.
* **Queue & Fallback System:** Offline-first architecture with manual review queue and graceful failover between cloud and local inference.

### 3. 🧠 Memory Weighting & Predictive Autonomy
* **Emotional Salience Weighting:** Algorithms score daily interactions based on emotional resonance and recurrence.
* **Forgetting & Retrieval Mechanics:** Low-impact memories decay gracefully into structured archive headers, enabling efficient local SLM retrieval without context bloat.
* **Predictive Interaction:** Surfaces proactive suggestions and dialogue prompts tailored to the user's current habits and emotional trajectory.

### 4. 🔒 Privacy, Transparency & Control
* **On-Device SQLite / Room Architecture:** Raw events are indexed locally in encrypted application storage.
* **Transparency Ledger:** Dedicated UI screen displaying raw logs, telemetry payloads, prompt inputs, and model outputs.
* **Zero Hidden Telemetry:** Full user control over service switches, battery optimization whitelist, and local ZIP export of raw & synthesized data.

---

## 📱 User Interface (Jetpack Compose)

* **Dashboard:** Real-time stream of today's telemetry, event counters, and quick manual note entry.
* **Persona Hub:** Dedicated inspector for viewing and editing evolving character and user persona profiles.
* **Memory Stream:** Chronological feed of captured events, OCR extracts, and conversation logs.
* **Dream Journal:** Night Mode insights, emotional graphs, and autonomous reflections.
* **Voice Dialogue:** Interactive conversational interface driven by synthesized persona context.
* **Transparency Screen:** Live terminal-style inspectability for all system events and LLM interactions.
* **System Settings:** Granular permission switches, API key configuration, and SLM/LLM mode selector.

---

## 🛠️ Tech Stack & Architecture

| Layer | Technologies |
|---|---|
| **Language & Platform** | Kotlin 2.0.21, Android SDK 34 (Min SDK 26) |
| **UI Framework** | Jetpack Compose + Material 3, Jetpack Navigation |
| **Database & Persistence** | Room Database (SQLite), AndroidX DataStore / SharedPreferences, KSP |
| **Background Orchestration** | AndroidX WorkManager, Foreground Services, AccessibilityService, NotificationListenerService |
| **AI & ML (On-Device)** | Google MediaPipe GenAI (`tasks-genai:0.10.20`), Google ML Kit Text Recognition |
| **AI (Cloud)** | Google Gemini Generative AI SDK (`com.google.ai.client.generativeai`) |
| **Native Acceleration** | OpenCL (`libOpenCL.so`, `libGLES_mali.so`, `libvndksupport.so`) |

---

## 🚀 Getting Started

### Prerequisites
* **Android Studio** Hedgehog | 2023.1.1 or newer (Ladybug / Koala recommended)
* **JDK 17** or newer
* **Physical Android Device** running Android 8.0 (API level 26) or higher *(Recommended: Android 12+ with GPU acceleration for local SLM)*

### Setup Instructions
1. **Clone the repository:**
   ```bash
   git clone https://github.com/AryanJaiswal27/PersonaAI.git
   cd PersonaAI
   ```
2. **Open in Android Studio:**
   * Select **File > Open...** and navigate to the cloned project directory.
   * Allow Gradle to sync dependencies.
3. **Configure API Keys:**
   * Open the app on your device.
   * Navigate to **Settings** and supply your **Google Gemini API Key** (optional if using pure local SLM).
4. **Grant Required Permissions:**
   * Follow the built-in **Genesis Setup Guide** to enable Accessibility Service, Notification Listener, and Storage permissions.

---

## 📂 Project Structure

```
PersonaApp/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/personaapp/
│   │   │   ├── ai/              # GeminiClient, LocalSLMEngine, MemoryWeighting, NightMode
│   │   │   ├── data/            # Room Database, EventDao, AppPreferences, ContactHelper
│   │   │   ├── services/        # AccessibilityService, NotificationListener, DataPoller, MediaAnalyzer
│   │   │   ├── storage/         # LocalStorageManager (ZIP export, persona files)
│   │   │   ├── ui/              # Compose screens (Dashboard, Dreams, Settings, Transparency)
│   │   │   └── MainActivity.kt
│   │   ├── res/                 # Resources, accessibility XML config, file paths
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 📜 License & Disclaimer

PersonaAI is intended for personal research, lifelogging, and experimental AI agent workflows. All captured data remains strictly stored on the user's physical device unless explicitly sent to configured cloud LLM APIs.
