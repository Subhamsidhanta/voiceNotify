package com.example

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Smart NotificationListenerService that listens to incoming notifications across the device
 * and uses TextToSpeech (TTS) to announce ONLY the App Name and the Sender's Name
 * (e.g., "WhatsApp notification from Rahul").
 *
 * - Strips all emojis from Sender Name, Conversation Title, and App Name before speaking so TTS
 *   never reads verbose emoji descriptions aloud and instead speaks the remaining text cleanly.
 * - Records every spoken announcement to the persistent Voice Log in PrefManager.
 * - Strictly enforces privacy by NEVER reading or extracting the actual message body/content.
 */
class NotificationReaderService : NotificationListenerService(), TextToSpeech.OnInitListener {

    private lateinit var prefManager: PrefManager
    private lateinit var audioManager: AudioManager
    private var tts: TextToSpeech? = null
    private var isTtsReady: Boolean = false

    // Anti-spam cooldown tracker: maps "packageName|senderName" -> last spoken timestamp (ms)
    private val senderCooldownMap = ConcurrentHashMap<String, Long>()

    // Modern AudioFocusRequest for Android 8.0+ (API 26+)
    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                tts?.stop()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefManager = PrefManager(applicationContext)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        try {
            tts = TextToSpeech(applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return
            val langResult = engine.setLanguage(Locale.getDefault())
            if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                langResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                engine.setLanguage(Locale.US)
            }

            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            engine.setAudioAttributes(audioAttrs)

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    // Audio focus is requested right before speak()
                }

                override fun onDone(utteranceId: String?) {
                    abandonAudioFocus()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    abandonAudioFocus()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    abandonAudioFocus()
                }
            })

            isTtsReady = true
        } else {
            isTtsReady = false
            Log.e(TAG, "TextToSpeech initialization failed with status: $status")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // 1. Check if Master Switch is ON
        if (!prefManager.isMasterEnabled) return

        // 2. Ignore ongoing notifications (music players, timers, active downloads, foreground services)
        if (sbn.isOngoing) return

        val notification = sbn.notification ?: return

        // 3. Ignore group summary notifications to prevent duplicate announcements on apps like WhatsApp
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val pkgName = sbn.packageName ?: return

        // Ignore our own package notifications if any
        if (pkgName == applicationContext.packageName) return

        // 4. Check if the incoming notification's packageName is in the user's selected app list
        if (!prefManager.isAppSelected(pkgName)) return

        // 5. Audio & Call Respect: Never speak during a cellular or VoIP call
        if (isDeviceInCall()) return

        // 6. Headphone-Only Mode & Ringer Mode Respect
        val headphonesConnected = isHeadphoneConnected()
        if (prefManager.isHeadphoneOnly && !headphonesConnected) {
            return
        }

        // Do not speak if ringer mode is Silent or Vibrate (unless Headphone-Only mode is active and headphones are connected)
        val ringerMode = audioManager.ringerMode
        val isSilentOrVibrate = ringerMode == AudioManager.RINGER_MODE_SILENT ||
            ringerMode == AudioManager.RINGER_MODE_VIBRATE
        if (isSilentOrVibrate && !(prefManager.isHeadphoneOnly && headphonesConnected)) {
            return
        }

        // 7. Extract human-readable App Name via PackageManager and Sender Name via Notification extras
        //    Strip emojis and read total remaining text cleanly.
        //    STRICT PRIVACY: Never read EXTRA_TEXT, EXTRA_BIG_TEXT, or message lines!
        val appName = stripEmojisAndNormalize(resolveAppName(pkgName)).ifEmpty { pkgName }
        val senderName = extractSenderName(notification.extras, appName)

        // 8. Anti-Spam Cooldown: 10-second cooldown per sender so rapid messages don't repeat
        val now = System.currentTimeMillis()
        val cooldownKey = "$pkgName|${senderName.lowercase(Locale.ROOT)}"
        val lastSpokenTime = senderCooldownMap[cooldownKey]
        if (lastSpokenTime != null && (now - lastSpokenTime) < COOLDOWN_MS) {
            return
        }
        senderCooldownMap[cooldownKey] = now
        pruneStaleCooldowns(now)

        // 9. Build privacy-safe announcement phrase (ONLY App Name and emoji-cleaned Sender Name)
        val announcement = buildAnnouncementPhrase(appName, senderName)

        // 10. Persist to Voice Log for full transparency and user review
        prefManager.appendVoiceLog(
            appName = appName,
            senderName = senderName.ifBlank { "General Alert" },
            spokenPhrase = announcement,
            timestamp = now
        )

        // 11. Speak via TextToSpeech with transient ducking audio focus
        speakAnnouncement(announcement)
    }

    /**
     * Resolves the human-readable application label from PackageManager.
     */
    private fun resolveAppName(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString().trim().ifEmpty { packageName }
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    /**
     * Extracts ONLY the sender/conversation title from notification extras, skips all emojis,
     * and reads the total remaining name/title cleanly.
     * Handles group conversations cleanly if EXTRA_CONVERSATION_TITLE is present.
     * NEVER accesses EXTRA_TEXT or message body fields.
     */
    private fun extractSenderName(extras: Bundle?, fallbackAppName: String): String {
        if (extras == null) return ""

        val rawTitle = stripEmojisAndNormalize(
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        )
        val conversationTitle = stripEmojisAndNormalize(
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString().orEmpty()
        )

        val candidate = when {
            // When both conversation title and sender title exist (e.g. Group Chat + Sender)
            conversationTitle.isNotEmpty() && rawTitle.isNotEmpty() -> {
                if (rawTitle.startsWith(conversationTitle, ignoreCase = true)) {
                    // Often formatted as "Group Name: Sender Name" or "Group Name (3 messages): Sender"
                    val afterColon = rawTitle.substringAfter(':', "").trim()
                    if (afterColon.isNotEmpty()) {
                        "$afterColon in $conversationTitle"
                    } else {
                        conversationTitle
                    }
                } else if (!rawTitle.equals(conversationTitle, ignoreCase = true)) {
                    "$rawTitle in $conversationTitle"
                } else {
                    rawTitle
                }
            }
            rawTitle.isNotEmpty() -> rawTitle
            conversationTitle.isNotEmpty() -> conversationTitle
            else -> ""
        }

        // Strip trailing message counts like "(4 messages)" for cleaner speech
        val cleaned = candidate
            .replace(Regex("\\(\\d+\\s+messages?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Avoid saying "WhatsApp notification from WhatsApp" if the title is just the app name
        return if (cleaned.equals(fallbackAppName, ignoreCase = true)) "" else cleaned
    }

    /**
     * Constructs the spoken announcement strictly using App Name and emoji-stripped Sender Name.
     */
    private fun buildAnnouncementPhrase(appName: String, senderName: String): String {
        val cleanPhrase = if (senderName.isNotBlank()) {
            "$appName notification from $senderName"
        } else {
            "New notification from $appName"
        }
        return stripEmojisAndNormalize(cleanPhrase)
    }

    /**
     * Checks if the phone is currently in a voice or VoIP call.
     */
    private fun isDeviceInCall(): Boolean {
        val mode = audioManager.mode
        return mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE
    }

    /**
     * Checks if wired headphones, USB headset, or a Bluetooth audio output device is actively connected.
     */
    private fun isHeadphoneConnected(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (device in devices) {
            when (device.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_USB_HEADSET -> return true
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        (device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
                    ) {
                        return true
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        device.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Requests transient ducking audio focus and speaks the announcement using user-configured rate & pitch.
     */
    private fun speakAnnouncement(text: String) {
        val cleanText = stripEmojisAndNormalize(text)
        if (cleanText.isBlank()) return

        val engine = tts
        if (!isTtsReady || engine == null) {
            initTextToSpeech()
            return
        }

        requestAudioFocusForSpeech()

        engine.setSpeechRate(prefManager.speechRate)
        engine.setPitch(prefManager.speechPitch)

        val utteranceId = "vn_${UUID.randomUUID()}"
        engine.speak(cleanText, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    private fun requestAudioFocusForSpeech() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                audioFocusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_NOTIFICATION,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not request audio focus", e)
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not abandon audio focus", e)
        }
    }

    private fun pruneStaleCooldowns(now: Long) {
        if (senderCooldownMap.size > 100) {
            val iterator = senderCooldownMap.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > COOLDOWN_MS * 6) {
                    iterator.remove()
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        abandonAudioFocus()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NotificationReaderSvc"
        private const val COOLDOWN_MS = 10_000L // 10-second anti-spam cooldown per sender

        /**
         * Strips all Unicode emojis, pictographs, variation selectors, zero-width joiners,
         * skin-tone modifiers, keycap enclosures, and flag regional indicators while preserving
         * all letters, numbers, and standard punctuation across any language so TTS reads the
         * total text smoothly without pronouncing emoji names.
         */
        fun stripEmojisAndNormalize(input: String): String {
            if (input.isEmpty()) return ""
            val sb = StringBuilder(input.length)
            var index = 0
            while (index < input.length) {
                val codePoint = input.codePointAt(index)
                val charCount = Character.charCount(codePoint)
                if (!isEmojiOrVariationCodePoint(codePoint)) {
                    sb.appendCodePoint(codePoint)
                } else {
                    sb.append(' ')
                }
                index += charCount
            }
            return sb.toString()
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        private fun isEmojiOrVariationCodePoint(cp: Int): Boolean {
            return when {
                // Emoticons, Miscellaneous Symbols & Pictographs, Transport & Map, Supplemental Symbols
                cp in 0x1F600..0x1F64F -> true
                cp in 0x1F300..0x1F5FF -> true
                cp in 0x1F680..0x1F6FF -> true
                cp in 0x1F900..0x1F9FF -> true
                cp in 0x1FA70..0x1FAFF -> true
                // Regional indicator symbols (flags) & enclosed alphanumeric supplement
                cp in 0x1F1E6..0x1F1FF -> true
                cp in 0x1F100..0x1F1E5 -> true
                cp in 0x1F200..0x1F2FF -> true
                // Miscellaneous symbols, Dingbats, Miscellaneous Technical
                cp in 0x2600..0x26FF -> true
                cp in 0x2700..0x27BF -> true
                cp in 0x2300..0x23FF -> true
                cp in 0x2B50..0x2B55 -> true
                // Variation selectors, Zero-Width Joiner, Combining Enclosing Keycap, Tags
                cp in 0xFE00..0xFE0F -> true
                cp == 0x200D -> true
                cp == 0x20E3 -> true
                cp in 0xE0020..0xE007F -> true
                // Other symbol character type fallback (e.g. copyright/trademark/math pictographs)
                Character.getType(cp) == Character.OTHER_SYMBOL.toInt() -> true
                else -> false
            }
        }
    }
}
