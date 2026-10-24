# VoiceNotify — Privacy-First TTS Notification Announcer

VoiceNotify is a privacy-focused Android utility that listens to incoming notifications across the device and uses Text-to-Speech (TTS) to announce **only** the originating App Name and Sender Name (e.g., *"WhatsApp notification from Rahul"*), never reading sensitive message bodies aloud.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following product and design choices have been confirmed from your preferences:

- **Confirmed App List Ordering**: Installed apps are sorted with **Selected (Enabled) apps pinned to the top first**, followed by alphabetical order (A–Z) for quick management.
- **Confirmed Voice & Diagnostic Controls**: Includes an interactive **Test Voice Announcement** button alongside **Speech Rate and Pitch sliders** for personalizing TTS playback.
- **Confirmed Visual Aesthetic**: **Modern Material 3 dynamic colors with high-contrast status cards**, making it effortless to see at a glance whether announcements, permissions, and headphone gates are active.

---

## 1. Overview & Core Concept

- **What It Does**: Intercepts device notifications in real time, filters out noise (ongoing media/system notifications, duplicate group summaries, rapid consecutive messages), respects phone calls and ringer states, and speaks a concise, privacy-safe announcement of the app and sender.
- **Target Audience / Persona**: Users who want hands-free awareness of who is contacting them (while driving, wearing headphones, cooking, or working) without risking private message text being read aloud in public or shared spaces.
- **Key Value**: Instant eyes-free situational awareness paired with strict privacy guarantees, anti-spam cooldowns, and one-tap control from both the app and the Android Quick Settings shade.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Onboarding & System Readiness**: On launch, a high-contrast Permission & System Status Banner checks whether Notification Listener access is granted and whether Battery Optimization is ignored. Tapping either action launches the exact system settings screen (`ACTION_NOTIFICATION_LISTENER_SETTINGS` or `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and automatically refreshes status when returning to the app.
  2. **Master & Context Controls**: A prominent hero card houses the **Master On/Off Switch** (synced in real time with the Android Quick Settings Tile) and the **Headphone-Only Mode** toggle (which restricts speech to active wired or Bluetooth audio routes).
  3. **Voice Tuning & Instant Test**: An expandable/integrated Voice Settings card lets the user adjust **Speech Rate** (`0.5x` – `2.0x`) and **Voice Pitch** (`0.5x` – `2.0x`) with sliders and trigger a live **"Test Voice Announcement"** sample immediately.
  4. **App Selection & Filtering**: Users browse all launchable installed apps with their native icons, display names, and package names. A real-time search bar filters by app or package name, **Select All / Deselect All** action chips allow bulk configuration, and enabled apps automatically float to the top of the list.
  5. **Complete Code Reference Viewer**: In addition to running natively on the device, an optional in-app code reference sheet/dialog allows inspecting or copying the complete source code of the 6 core architecture files if desired.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Modern Material 3 utility with bold, high-contrast status cards and clear semantic hierarchy.
  - *Color Palette & Mood*:
    - Active Master State: High-contrast `primaryContainer` / `onPrimaryContainer` hero surface with an energetic accent indicator.
    - Paused / Disabled State: Muted `surfaceVariant` card posture so the user immediately recognizes when voice output is silenced.
    - Warning / Permission Missing State: Distinct `errorContainer` / `tertiaryContainer` callout banners that disappear or turn into a compact verified badge once granted.
  - *Typography & Hierarchy*: Bold title typography for status headers, clean medium-weight labels for app names, and monospace/compact secondary labels for package identifiers.
  - *Component Styling & Layout*: Edge-to-edge Material 3 `Scaffold` with a pinned/scrollable control header and a high-performance `LazyColumn` using stable package keys and asynchronous native icon rendering.

- **Interactive Feedback & Motion**:
  - Smooth color transitions (`animateColorAsState`) on the Master Switch card when toggling between Active and Muted states.
  - Instant visual feedback and toast/snackbar confirmation when testing TTS speech or bulk-selecting applications.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Strict Privacy-By-Design Extraction**
  - *Chosen Approach*: Extract only the human-readable application label via `PackageManager` and `Notification.EXTRA_TITLE` / `Notification.EXTRA_CONVERSATION_TITLE` from the notification extras, completely ignoring `EXTRA_TEXT` and `EXTRA_BIG_TEXT`.
  - *Why*: Ensures private message contents, OTP codes, and confidential details are never spoken aloud under any circumstances.
- **Decision 2: Smart Deduplication & 10-Second Per-Sender Cooldown**
  - *Chosen Approach*: Filter out `sbn.isOngoing` and `Notification.FLAG_GROUP_SUMMARY` notifications, and maintain an in-memory timestamp map keyed by `packageName + senderName` enforcing a 10-second cooldown window.
  - *Why*: Messaging apps like WhatsApp and Telegram emit both a group summary notification and individual child notifications simultaneously, and chatty senders often send 4–5 short messages in a row. Filtering summaries plus a 10s cooldown prevents annoying back-to-back repetition.
- **Decision 3: Audio Focus, Call & Ringer Respect**
  - *Chosen Approach*: Check `AudioManager.mode` to abort announcements during active cellular or VoIP calls (`MODE_IN_CALL`, `MODE_IN_COMMUNICATION`). Check `ringerMode` to stay silent in Vibrate/Silent modes unless Headphone-Only mode is active with headphones connected. Request transient ducking audio focus (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) during TTS playback and abandon focus in `UtteranceProgressListener.onDone`/`onError`.
  - *Why*: Prevents speaking over phone calls or blasting announcements in silent environments while cleanly ducking background music when speaking through headphones.
- **Decision 4: Reactive SharedPreferences Sync (`PrefManager`)**
  - *Chosen Approach*: Centralize all persistent settings (Master Switch, Headphone-Only Mode, Speech Rate, Speech Pitch, and Selected App Package Set) in `PrefManager` backed by `SharedPreferences` and `OnSharedPreferenceChangeListener`.
  - *Why*: Guarantees instantaneous synchronization when the user toggles the Master Switch from the Android Quick Settings Tile while the main activity or listener service is running.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Android System Services                          │
│  ┌──────────────────────────┐         ┌──────────────────────────────┐  │
│  │ NotificationManager / OS │         │ Quick Settings Shade (OS UI) │  │
│  └────────────┬─────────────┘         └──────────────┬───────────────┘  │
└───────────────┼──────────────────────────────────────┼──────────────────┘
                │ onNotificationPosted(sbn)            │ onClick() / Tile sync
                ▼                                      ▼
┌───────────────────────────────────┐   ┌─────────────────────────────────┐
│     NotificationReaderService     │   │     QuickToggleTileService      │
│  (NotificationListenerService)    │   │          (TileService)          │
│  • Ongoing & Group Summary Filter │   │  • Reads & toggles Master state │
│  • Call & Ringer Mode Gate        │   │  • Updates Tile ACTIVE/INACTIVE │
│  • Wired/BT Headphone Detection   │   └──────────────┬──────────────────┘
│  • 10s Sender Cooldown Map        │                  │
│  • AudioFocus + TextToSpeech      │                  │
└───────────────┬───────────────────┘                  │
                │                                      │
                ▼                                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     PrefManager (SharedPreferences)                     │
│  • KEY_MASTER_ENABLED (Boolean)     • KEY_SPEECH_RATE (Float)           │
│  • KEY_HEADPHONE_ONLY (Boolean)     • KEY_SPEECH_PITCH (Float)          │
│  • KEY_SELECTED_PACKAGES (Set<String>)                                  │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    ▲
                                    │ Reads / Persists / Observes
┌───────────────────────────────────┴─────────────────────────────────────┐
│                   MainActivity (Jetpack Compose UI)                     │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │ Permission & Battery Optimization Status Banner                   │  │
│  ├───────────────────────────────────────────────────────────────────┤  │
│  │ High-Contrast Master Switch Card & Headphone-Only Toggle          │  │
│  ├───────────────────────────────────────────────────────────────────┤  │
│  │ Voice Controls Card (Rate/Pitch Sliders + Test Voice Button)      │  │
│  ├───────────────────────────────────────────────────────────────────┤  │
│  │ App Filter Bar (Search Input + Select All / Deselect All)         │  │
│  ├───────────────────────────────────────────────────────────────────┤  │
│  │ Sorted LazyColumn (Selected Apps First -> Alphabetical A-Z)       │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State

- **`AppInfo` UI Model**: Holds `appName: String`, `packageName: String`, `icon: Drawable?`, and selection state derived from the persisted `Set<String>` of enabled package names.
- **`PrefManager` State Store**:
  - `isMasterEnabled`: Controls global announcement state across UI, Service, and Quick Settings Tile.
  - `isHeadphoneOnly`: Restricts announcements to connected `TYPE_WIRED_HEADSET`, `TYPE_WIRED_HEADPHONES`, `TYPE_BLUETOOTH_A2DP`, `TYPE_BLUETOOTH_SCO`, `TYPE_USB_HEADSET`, or `TYPE_BLE_HEADSET` audio devices.
  - `speechRate` & `speechPitch`: Float parameters (`0.5f` to `2.0f`, default `1.0f`) applied to `TextToSpeech`.
  - `selectedPackages`: Immutable defensive copies of `Set<String>` stored in `SharedPreferences` to avoid Android's `getStringSet` mutation pitfall.

### Interactive Component & State Mapping

- **Lifecycle Permission Sync**: `DisposableEffect(LocalLifecycleOwner)` observes `Lifecycle.Event.ON_RESUME` to re-verify `NotificationManagerCompat.getEnabledListenerPackages(context)` and `PowerManager.isIgnoringBatteryOptimizations(packageName)` immediately when returning from system settings.
- **Quick Settings Tile Sync**: `PrefManager` registers a preference change listener so toggling the Quick Settings Tile while the app is open immediately updates the Compose UI state, and toggling in Compose requests a tile state refresh via `TileService.requestListeningState`.
- **Dynamic App List Sorting & Filtering**: Installed launchable packages are loaded asynchronously on `Dispatchers.IO`, cached in memory, filtered in real time by the search query, and sorted dynamically (`selectedPackages.contains(pkg)` descending, then `appName.lowercase()` ascending).
