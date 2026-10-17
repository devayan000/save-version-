package com.jarvis.assistant.google

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.jarvis.assistant.R
import com.jarvis.assistant.util.ThemeManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Connect / test / disconnect Google Workspace (Gmail, Calendar, Drive). */
class GoogleWorkspaceActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var accountText: TextView
    private lateinit var connectButton: MaterialButton
    private lateinit var disconnectButton: MaterialButton
    private lateinit var testResult: TextView
    private val testButtons = ArrayList<MaterialButton>()

    private val consentLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        GoogleWorkspaceManager.handleConsentResult(this, result.data, { token ->
            onTokenAcquired(token)
        }, { error ->
            showError(error)
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        root = buildUi()
        setContentView(root)
        ThemeManager.applyDynamicBackground(root)
        refreshUi()
    }

    private fun attrColor(attr: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    private fun px(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val textPrimary = attrColor(R.attr.themeTextPrimary)
        val textMuted = attrColor(R.attr.themeTextMuted)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(22), px(20), px(22), px(32))
        }

        // Hero header: gradient "G" tile + title stack
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val iconTile = TextView(this).apply {
            text = "G"
            textSize = 26f
            setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = ContextCompat.getDrawable(context, R.drawable.bg_premium_button_gradient)
        }
        iconTile.layoutParams = LinearLayout.LayoutParams(px(52), px(52)).apply { marginEnd = px(14) }
        headerRow.addView(iconTile)

        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(TextView(this).apply {
            text = "GOOGLE WORKSPACE"
            textSize = 20f
            setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
            setTextColor(textPrimary)
            letterSpacing = 0.04f
        })
        titleCol.addView(TextView(this).apply {
            text = "Gmail  ·  Calendar  ·  Drive"
            textSize = 13f
            setTextColor(textMuted)
            setPadding(0, px(3), 0, 0)
        })
        headerRow.addView(titleCol)
        column.addView(headerRow)

        column.addView(TextView(this).apply {
            text = "Official Google authorization API. Read-only access — the token is never saved, only the encrypted connection status."
            textSize = 13f
            setTextColor(textMuted)
            setPadding(0, px(12), 0, px(18))
        })

        // Status card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_jarvis_action_card)
            setPadding(px(20), px(16), px(20), px(20))
        }
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(px(10), px(10)).apply { marginEnd = px(9) }
            background = ContextCompat.getDrawable(context, R.drawable.bg_status_dot)
        }
        statusRow.addView(statusDot)
        statusText = TextView(this).apply {
            textSize = 15f
            setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
            setTextColor(textPrimary)
            letterSpacing = 0.06f
        }
        statusRow.addView(statusText)
        card.addView(statusRow)
        accountText = TextView(this).apply {
            textSize = 13f
            setTextColor(textMuted)
            setPadding(0, px(6), 0, 0)
        }
        card.addView(accountText)
        column.addView(card)

        // Connect — premium gradient pill
        connectButton = MaterialButton(this).apply {
            text = "Connect Google Workspace"
            isAllCaps = false
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.WHITE)
            backgroundTintList = null
            background = ContextCompat.getDrawable(context, R.drawable.bg_premium_button_gradient)
            minHeight = px(54)
            isSoundEffectsEnabled = false
        }
        column.addView(connectButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, px(18), 0, 0)
        })

        // Section label
        column.addView(TextView(this).apply {
            text = "TEST EACH API"
            textSize = 12f
            setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
            setTextColor(textMuted)
            letterSpacing = 0.12f
            setPadding(0, px(24), 0, px(10))
        })

        listOf(
            "Test Gmail" to { cb: (String) -> Unit -> GoogleWorkspaceManager.testGmail(this, cb) },
            "Test Calendar" to { cb: (String) -> Unit -> GoogleWorkspaceManager.testCalendar(this, cb) },
            "Test Drive" to { cb: (String) -> Unit -> GoogleWorkspaceManager.testDrive(this, cb) }
        ).forEach { pair ->
            val label = pair.first
            val run = pair.second
            val button = MaterialButton(this).apply {
                text = label
                isAllCaps = false
                textSize = 14f
                setTextColor(textPrimary)
                backgroundTintList = null
                background = ContextCompat.getDrawable(context, R.drawable.bg_gw_test_button)
                minHeight = px(50)
            }
            button.setOnClickListener {
                testResult.text = label.replace("Test ", "") + ": testing..."
                run { message -> testResult.text = message }
            }
            testButtons.add(button)
            column.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, px(6), 0, 0)
            })
        }

        testResult = TextView(this).apply {
            textSize = 13f
            setTextColor(textMuted)
            setPadding(0, px(12), 0, 0)
        }
        column.addView(testResult)

        // Disconnect — ghost pill
        disconnectButton = MaterialButton(this).apply {
            text = "Disconnect"
            isAllCaps = false
            textSize = 14f
            setTextColor(textMuted)
            backgroundTintList = null
            background = ContextCompat.getDrawable(context, R.drawable.bg_gw_ghost_button)
            minHeight = px(48)
        }
        column.addView(disconnectButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, px(22), 0, 0)
        })

        return ScrollView(this).apply { addView(column) }
    }

    private fun refreshUi() {
        val prefs = GoogleWorkspaceManager.preferences(this)
        if (prefs.connected) {
            statusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
            statusText.text = "CONNECTED"
            statusText.setTextColor(Color.parseColor("#10B981"))
            val time = if (prefs.connectedAtMs > 0) SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(prefs.connectedAtMs)) else "unknown"
            accountText.text = prefs.accountEmail + "\nConnected " + time + " - tokens are requested fresh whenever needed"
            connectButton.text = "Re-authorize"
            disconnectButton.isEnabled = true
        } else {
            statusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
            statusText.text = "NOT CONNECTED"
            statusText.setTextColor(Color.parseColor("#EF4444"))
            accountText.text = "Connect to let Jarvis read your Gmail, Calendar and Drive."
            connectButton.text = "Connect Google Workspace"
            disconnectButton.isEnabled = false
        }
    }

    private fun connect() {
        connectButton.isEnabled = false
        testResult.text = "Connecting to Google..."
        GoogleWorkspaceManager.requestAuthorization(this, { token ->
            onTokenAcquired(token)
        }, { sender ->
            connectButton.isEnabled = true
            consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }, { error ->
            showError(error)
        })
    }

    private fun onTokenAcquired(token: String) {
        testResult.text = "Verifying the Google account..."
        GoogleWorkspaceManager.fetchAccountEmail(token, { email ->
            GoogleWorkspaceManager.setConnected(this, email)
            refreshUi()
            testResult.text = "Connected to Google Workspace."
            Toast.makeText(this, "Google Workspace connected", Toast.LENGTH_SHORT).show()
        }, { error ->
            showError(error)
        })
    }

    private fun disconnect() {
        disconnectButton.isEnabled = false
        GoogleWorkspaceManager.disconnect(this) { message ->
            refreshUi()
            testResult.text = message
            Toast.makeText(this, "Google Workspace disconnected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showError(message: String) {
        connectButton.isEnabled = true
        testResult.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
