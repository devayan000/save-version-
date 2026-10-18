package com.jarvis.assistant.automation.command

import android.content.Intent
import android.os.BatteryManager
import android.provider.MediaStore
import android.provider.Settings
import com.jarvis.assistant.JarvisApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DEV STYLE: instant executor for the 10000+ multilingual command pack
 * (English, Bangla, Banglish, Hindi, Hinglish). Exact phrase lookup on a
 * compact asset table runs BEFORE the slow parser/AI path so common device
 * commands answer immediately.
 */
object FastCommandRouter {

    @Volatile
    private var phraseMap: HashMap<String, String>? = null

    private val punct = Regex("[\\p{Punct}\\u0964]")

    private fun normalize(text: String): String = java.text.Normalizer
        .normalize(text.trim().lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFKC)
        .replace(punct, " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun ensureLoaded(context: android.content.Context): HashMap<String, String> {
        phraseMap?.let { return it }
        synchronized(this) {
            phraseMap?.let { return it }
            val map = HashMap<String, String>(2048)
            runCatching {
                context.assets.open("commands_fast.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (line in lines) {
                        val idx = line.indexOf('\t')
                        if (idx > 0) map[line.substring(0, idx)] = line.substring(idx + 1)
                    }
                }
            }
            phraseMap = map
            return map
        }
    }

    fun lookup(context: android.content.Context, rawText: String): String? =
        ensureLoaded(context)[normalize(rawText)]

    /** Non-suspend entry for the conversation service: runs on main and calls back with the reply. */
    fun tryExecuteAsync(app: JarvisApp, rawText: String, onReply: (String) -> Unit) {
        val action = lookup(app, rawText) ?: return
        CoroutineScope(Dispatchers.Main).launch {
            onReply(executeAction(app, action))
        }
    }

    private fun launch(app: JarvisApp, intent: Intent): Boolean = runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
        true
    }.getOrDefault(false)

    suspend fun executeAction(app: JarvisApp, action: String): String = withContext(Dispatchers.Main) {
        val executor = app.commandExecutor
        when (action) {
            "FLASH_ON" -> executor.execute(JarvisCommand.Flash(true))
            "FLASH_OFF" -> executor.execute(JarvisCommand.Flash(false))
            "VOLUME_UP" -> executor.execute(JarvisCommand.Volume(0, isRelative = true, delta = 10))
            "VOLUME_DOWN" -> executor.execute(JarvisCommand.Volume(0, isRelative = true, delta = -10))
            "MUTE" -> executor.execute(JarvisCommand.Volume(0))
            "BRIGHTNESS_UP" -> executor.execute(JarvisCommand.Brightness(0, isRelative = true, delta = 15))
            "BRIGHTNESS_DOWN" -> executor.execute(JarvisCommand.Brightness(0, isRelative = true, delta = -15))
            "LOCK_SCREEN" -> executor.execute(JarvisCommand.LockPhone)
            "STOP_MUSIC" -> executor.execute(JarvisCommand.PauseMedia)
            "NEXT_TRACK" -> executor.execute(JarvisCommand.NextMedia)
            "PREVIOUS_TRACK" -> executor.execute(JarvisCommand.PreviousMedia)
            "OPEN_YOUTUBE" -> executor.execute(JarvisCommand.OpenApp("com.google.android.youtube"))
            "OPEN_WHATSAPP" -> executor.execute(JarvisCommand.OpenApp("com.whatsapp"))
            "OPEN_CALCULATOR" -> executor.execute(JarvisCommand.OpenApp("com.google.android.calculator"))
            "OPEN_MAPS" -> executor.execute(JarvisCommand.OpenApp("com.google.android.apps.maps"))
            "OPEN_CALENDAR" -> executor.execute(JarvisCommand.OpenApp("com.google.android.calendar"))
            "OPEN_CONTACTS" -> executor.execute(JarvisCommand.OpenApp("com.google.android.contacts"))
            "OPEN_MESSAGES" -> executor.execute(JarvisCommand.OpenApp("com.google.android.apps.messaging"))
            "OPEN_PHONE" -> executor.execute(JarvisCommand.OpenApp("com.google.android.dialer"))
            "OPEN_FILES" -> executor.execute(JarvisCommand.OpenApp("com.google.android.apps.nbu.files"))
            "OPEN_MUSIC" -> executor.execute(JarvisCommand.OpenApp("com.google.android.apps.youtube.music"))
            "SCREENSHOT" -> "Screenshot Android er security policy er karone direct API diye chalano jay na, Sir."
            "GO_HOME" -> if (launch(app, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))) "Home e niye gelam, Sir." else "Home e jete pari nai."
            "OPEN_CAMERA" -> if (launch(app, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) "Camera khule dilam, Sir." else "Camera khola gelo na."
            "OPEN_SETTINGS" -> if (launch(app, Intent(Settings.ACTION_SETTINGS))) "Settings khule dilam." else "Settings khola gelo na."
            "OPEN_WIFI_SETTINGS" -> if (launch(app, Intent(Settings.ACTION_WIFI_SETTINGS))) "WiFi settings khule dilam." else "WiFi settings khola gelo na."
            "OPEN_BLUETOOTH_SETTINGS" -> if (launch(app, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) "Bluetooth settings khule dilam." else "Bluetooth settings khola gelo na."
            "OPEN_BATTERY_SETTINGS" -> if (launch(app, Intent("android.settings.BATTERY_SAVER_SETTINGS"))) "Battery settings khule dilam." else "Battery settings khola gelo na."
            "OPEN_NOTIFICATIONS" -> "Android er security rule e notification shade direct khola jay na, Sir."
            "BACK" -> "Back action er jonno Android e Accessibility permission lagbe - Settings > Accessibility > Jarvis on koro."
            "RECENT_APPS" -> "Recent apps er jonno Accessibility permission lagbe - Settings > Accessibility > Jarvis on koro."
            "OPEN_GALLERY" -> if (launch(app, Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))) "Gallery khule dilam." else "Gallery khola gelo na."
            "OPEN_BROWSER" -> if (launch(app, Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com")))) "Browser khule dilam." else "Browser khola gelo na."
            "OPEN_CLOCK" -> if (launch(app, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))) "Clock khule dilam." else "Clock khola gelo na."
            "READ_TIME" -> {
                val now = java.util.Calendar.getInstance()
                "%02d:%02d baje, Sir.".format(java.util.Locale.US, now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE))
            }
            "READ_DATE" -> {
                val now = java.util.Calendar.getInstance()
                val days = arrayOf("Robibar", "Sombar", "Mongolbar", "Budhbar", "Brihospotibar", "Shukrobar", "Shonibar")
                "%d-%d-%d, %s.".format(
                    java.util.Locale.US,
                    now.get(java.util.Calendar.DAY_OF_MONTH), now.get(java.util.Calendar.MONTH) + 1,
                    now.get(java.util.Calendar.YEAR), days[now.get(java.util.Calendar.DAY_OF_WEEK) - 1]
                )
            }
            "BATTERY_STATUS" -> {
                val bm = app.getSystemService(android.content.Context.BATTERY_SERVICE) as BatteryManager
                val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val status = app.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val charging = status?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
                "Battery $pct percent ${if (charging) "ache ar charge hocche" else "ache"}, Sir."
            }
            "HELP" -> com.jarvis.assistant.security.FeatureKnowledge.capabilitiesText()
            else -> "Ei command ta ekhono map kora nei, Sir."
        }
    }
}