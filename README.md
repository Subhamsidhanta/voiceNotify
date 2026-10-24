# VoiceNotify — Privacy-First TTS Notification Announcer

**VoiceNotify** is a modern, privacy-first Android application built with **Kotlin** and **Jetpack Compose (Material 3)**. It listens to incoming notifications across the device and uses Android's native **Text-to-Speech (TTS)** engine to announce **only** the originating **App Name** and **Sender Name** (e.g., *"WhatsApp notification from Rahul"*).

For strict privacy protection, VoiceNotify **never** reads, extracts, or logs the actual notification message body.

---

## Key Features

- **Strict Privacy Shield**: Extracts only the human-readable application label via `PackageManager` and the notification title (`EXTRA_TITLE` / `EXTRA_CONVERSATION_TITLE`). Message contents (`EXTRA_TEXT`, `EXTRA_BIG_TEXT`) are never accessed.
- **Emoji Skipping & Clean Speech**: Automatically strips all Unicode emojis, pictographs, variation selectors, zero-width joiners, skin-tone modifiers, and flags before speaking so TTS reads the remaining text cleanly without pronouncing verbose emoji descriptions.
- **Master On/Off Switch**: Prominent high-contrast status card on the main screen to immediately mute or enable all voice announcements.
- **Quick Settings Tile (`TileService`)**: Toggle VoiceNotify ON or OFF directly from the Android notification shade without opening the app, synced in real time with `SharedPreferences`.
- **Headphone-Only Mode**: Restricts voice announcements to play only when wired headphones, a USB headset, or Bluetooth audio devices (`A2DP`, `SCO`, `BLE`) are actively connected.
- **Smart Notification Filtering (`NotificationListenerService`)**:
  - Ignores ongoing notifications (`sbn.isOngoing`) such as music players, timers, and active downloads.
  - Ignores group summary notifications (`Notification.FLAG_GROUP_SUMMARY`) to prevent duplicate announcements in messaging apps like WhatsApp and Telegram.
  - **10-Second Anti-Spam Cooldown**: Per-sender cooldown map prevents rapid consecutive messages from the same contact from triggering repetitive announcements.
  - **Call & Ringer Respect**: Stays silent during active cellular or VoIP calls (`MODE_IN_CALL`, `MODE_IN_COMMUNICATION`) and respects Silent/Vibrate ringer modes (unless Headphone-Only mode is active with headphones connected).
  - **Transient Audio Ducking**: Requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` before speaking and abandons focus cleanly when speech finishes.
- **Voice Tuning & Live Test**: Adjust **Speech Rate** (`0.5x` – `2.0x`) and **Voice Pitch** (`0.5x` – `2.0x`) with a one-tap **Test Voice** button.
- **Universal App Selection List**:
  - Queries all launchable user-installed applications asynchronously on `Dispatchers.IO`.
  - Automatically pins **Selected (Enabled) apps to the top**, followed by alphabetical order (`A–Z`).
  - Includes real-time search by app or package name plus **Select All** and **Deselect All** controls.
- **Dedicated Date-Wise Voice Announcement Logs Screen**:
  - Accessible via a single prominent button on the Home screen with a live log counter badge.
  - Organizes announcements **date-wise** (*Today*, *Yesterday*, or calendar date headers).
  - Filter logs for a **particular app** using one-tap App Filter Chips or the real-time search bar.
  - Replay any logged announcement, delete individual log items, delete all logs for a specific app, or clear all history.
- **Permission & Battery Readiness Banners**: Automatically checks Notification Listener access and Battery Optimization exemption on resume, providing one-tap shortcuts to system settings.

---

## Architecture & Project Structure

```text
app/src/main/
├── AndroidManifest.xml                  # Permissions, <queries> for TTS/Launcher, Services & Activity
├── java/com/example/
│   ├── MainActivity.kt                  # Jetpack Compose UI (Home Screen + Date-Wise Voice Logs Screen)
│   ├── NotificationReaderService.kt     # NotificationListenerService + TTS + Emoji Stripper + Cooldown
│   ├── PrefManager.kt                   # SharedPreferences helper for settings, selected apps & logs
│   ├── QuickToggleTileService.kt        # Android Quick Settings TileService synced with PrefManager
│   └── ui/theme/                        # Material 3 Dynamic Color, Typography & Theme definitions
└── res/
    ├── drawable/                        # Custom adaptive icon vectors & Quick Settings tile icon
    └── values/strings.xml               # Centralized user-facing string resources
```

---

## Tech Stack & Requirements

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Minimum SDK**: API 24 (Android 7.0)
- **Target / Compile SDK**: API 36
- **Core Android APIs**:
  - `NotificationListenerService`
  - `TextToSpeech` & `AudioManager` (`AudioFocusRequest`, `AudioDeviceInfo`)
  - `TileService` (Quick Settings Tile)
  - `PackageManager` & `SharedPreferences`

---

## Getting Started

### 1. Build & Run
1. Clone this repository and open it in **Android Studio** (or build directly inside Google AI Studio).
2. Sync Gradle and run the `app` configuration on a physical Android device or emulator:
   ```bash
   ./gradlew assembleDebug
   ```

### 2. Required Runtime Setup on Device
1. **Grant Notification Access**: Tap **Grant Access** on the top banner in the app (opens `ACTION_NOTIFICATION_LISTENER_SETTINGS`) and enable **VoiceNotify**.
2. **Unrestricted Battery Usage**: Tap **Optimize** on the battery banner (opens `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) so the background listener service is not killed by system battery savers.
3. **Select Apps**: Check the apps you want VoiceNotify to announce (selected apps automatically stay pinned to the top of the list).
4. **Quick Settings Tile (Optional)**: Pull down your Android notification shade, tap **Edit** on Quick Settings, and drag the **VoiceNotify** tile into your active tiles for instant toggling.

---

## Privacy Guarantee

VoiceNotify operates entirely offline on your device:
- It does **not** request `INTERNET` permission for notification reading.
- It **never** reads or stores notification body text (`EXTRA_TEXT`, `EXTRA_BIG_TEXT`, or messaging style message bodies).
- Voice logs are stored locally in `SharedPreferences` and can be deleted individually, per app, or in full at any time.
