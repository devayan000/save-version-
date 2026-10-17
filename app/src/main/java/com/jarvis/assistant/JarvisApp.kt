package com.jarvis.assistant

import android.app.Application
import com.jarvis.assistant.security.SecurityManager
import com.jarvis.assistant.backend.LicenseManager
import com.jarvis.assistant.audio.VoskModelManager
import com.jarvis.assistant.automation.command.CommandExecutor
import com.jarvis.assistant.automation.contacts.ContactResolver
import com.jarvis.assistant.automation.phone.PhoneControlManager
import com.jarvis.assistant.automation.whatsapp.WhatsAppAssistant
import com.jarvis.assistant.automation.youtube.YouTubeAutomationManager
import com.jarvis.assistant.data.memory.MemoryDatabase
import com.jarvis.assistant.data.memory.MemoryManager
import com.jarvis.assistant.data.memory.MemoryRepository
import com.jarvis.assistant.data.preferences.AppPreferences
import com.jarvis.assistant.data.preferences.GithubPreferences
import com.jarvis.assistant.data.preferences.ImgbbPreferences
import com.jarvis.assistant.data.preferences.YouTubePreferences
import com.jarvis.assistant.data.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JarvisApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var guardReportTts: android.speech.tts.TextToSpeech? = null

    lateinit var preferences: AppPreferences
        private set

    lateinit var chatRepository: ChatRepository
        private set

    lateinit var memoryDatabase: MemoryDatabase
        private set

    lateinit var memoryRepository: MemoryRepository
        private set

    lateinit var memoryManager: MemoryManager
        private set

    lateinit var youTubePreferences: YouTubePreferences
        private set

    lateinit var imgbbPreferences: ImgbbPreferences
        private set

    lateinit var githubPreferences: GithubPreferences
        private set

    lateinit var youTubeAutomationManager: YouTubeAutomationManager
        private set

    lateinit var contactResolver: ContactResolver
        private set

    lateinit var phoneControlManager: PhoneControlManager
        private set

    lateinit var whatsAppAssistant: WhatsAppAssistant
        private set

    lateinit var commandExecutor: CommandExecutor
        private set

    override fun onCreate() {
        super.onCreate()
        SecurityManager.verify(this)
        instance = this
        preferences = AppPreferences(this)
        chatRepository = ChatRepository(preferences)

        // Memory System
        memoryDatabase = MemoryDatabase.getInstance(this)
        memoryRepository = MemoryRepository(memoryDatabase.memoryDao())
        memoryManager = MemoryManager(memoryRepository, preferences, chatRepository, appScope)

        // Automation Systems
        youTubePreferences = YouTubePreferences(this)
        imgbbPreferences = ImgbbPreferences(this)
        githubPreferences = GithubPreferences(this)
        youTubeAutomationManager = YouTubeAutomationManager(this, youTubePreferences)
        contactResolver = ContactResolver(this)
        phoneControlManager = PhoneControlManager(this, contactResolver)
        whatsAppAssistant = WhatsAppAssistant(this, contactResolver)

        // Command Engine
        commandExecutor = CommandExecutor(
            phoneManager = phoneControlManager,
            youtubeManager = youTubeAutomationManager,
            whatsappAssistant = whatsAppAssistant,
            memoryManager = memoryManager
        )

        VoskModelManager.init(this, CoroutineScope(Dispatchers.Default))

        // DEV STYLE: On-Guard mode resumes automatically when enabled
        if (preferences.isThiefGuardEnabled) {
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(
                    this@JarvisApp,
                    android.content.Intent(this@JarvisApp, com.jarvis.assistant.security.ThiefGuardService::class.java)
                )
            }
        }

        // DEV STYLE: On-Guard report - tell the owner about captures taken while away
        val unseen = preferences.thiefGuardUnseenCaptures
        if (unseen > 0) {
            preferences.thiefGuardUnseenCaptures = 0
            runCatching {
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                guardReportTts = android.speech.tts.TextToSpeech(this@JarvisApp) { status ->
                    if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                        handler.postDelayed({
                            val msg = if (unseen == 1)
                                "On-Guard alert. While you were away, one intruder photo was captured. Saved in the JarvisGuard folder."
                            else
                                "On-Guard alert. While you were away, " + unseen + " intruder photos were captured. Saved in the JarvisGuard folder."
                            guardReportTts?.speak(msg, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "guard_report")
                            android.widget.Toast.makeText(
                                this@JarvisApp,
                                "On-Guard: " + unseen + " intruder photo(s) saved while you were away",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }, 1500)
                    }
                }
            }
        }

        // DEV STYLE Backend — device registration + licence heartbeat
        appScope.launch {
            val wasRegistered = runCatching {
                com.jarvis.assistant.security.InstallationManager.isRegistered(this@JarvisApp)
            }.getOrDefault(false)
            val createdNow = runCatching {
                LicenseManager.registerOnLaunch(this@JarvisApp, preferences.userName.ifBlank { "DEV STYLE" })
            }.getOrDefault(false)
            // DEV STYLE: a freshly created backend user gets the one-time admin contact popup
            if (createdNow || !wasRegistered) preferences.pendingAdminContactPopup = true
            LicenseManager.runHeartbeatLoop(this@JarvisApp)
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivities = 0
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {
                com.jarvis.assistant.util.ThemeManager.applyTheme(activity)
            }
            override fun onActivityStarted(activity: android.app.Activity) {
                startedActivities++
                if (startedActivities == 1) _isAppForeground.value = true
            }
            override fun onActivityResumed(activity: android.app.Activity) {
                currentResumedActivity = activity
                com.jarvis.assistant.util.ThemeManager.applyTheme(activity)
            }
            override fun onActivityPaused(activity: android.app.Activity) {
                if (currentResumedActivity === activity) {
                    currentResumedActivity = null
                }
            }
            override fun onActivityStopped(activity: android.app.Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) _isAppForeground.value = false
            }
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {
                if (currentResumedActivity === activity) {
                    currentResumedActivity = null
                }
            }
        })
    }

    var currentResumedActivity: android.app.Activity? = null
        private set

    fun moveAppToBack(): Boolean {
        val act = currentResumedActivity ?: return false
        return try {
            act.moveTaskToBack(true)
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private val _isAppForeground = MutableStateFlow(false)
        val isAppForeground: StateFlow<Boolean> = _isAppForeground.asStateFlow()

        lateinit var instance: JarvisApp
            private set
    }
}

