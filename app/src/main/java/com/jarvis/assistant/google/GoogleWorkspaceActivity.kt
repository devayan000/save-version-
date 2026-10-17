package com.jarvis.assistant.google

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.jarvis.assistant.R
import com.jarvis.assistant.util.ThemeManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Connect / test / disconnect Google Workspace (Gmail, Calendar, Drive). */
class GoogleWorkspaceActivity : AppCompatActivity() {

    private lateinit var root: View
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
            setPadding(px(24), px(20), px(24), px(32))
        }

        column.addView(TextView(this).apply {
            text = "GOOGLE WORKSPACE"
            textSize = 20f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(textPrimary)
        })
        column.addView(TextView(this).apply {
            text = "Gmail, Calendar and Drive through the official Google authorization API. Read-only access. The access token is never saved - only the connection status is, encrypted."
            textSize = 13f
            setTextColor(textMuted)
            setPadding(0, px(6), 0, px(16))
        })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_jarvis_action_card)
            setPadding(px(20), px(16), px(20), px(20))
        }
        statusText = TextView(this).apply {
            textSize = 15f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(textPrimary)
        }
        accountText = TextView(this).apply {
            textSize = 13f
            setTextColor(textMuted)
            setPadding(0, px(4), 0, 0)
        }
        card.addView(statusText)
        card.addView(accountText)
        column.addView(card)

        connectButton = MaterialButton(this).apply {
            text = "Connect Google Workspace"
            setOnClickListener { connect() }
        }
        column.addView(connectButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, px(16), 0, 0) })

        column.addView(TextView(this).apply {
            text = "TEST EACH API"
            textSize = 13f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(textMuted)
            setPadding(0, px(20), 0, px(8))
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
                setOnClickListener {
                    testResult.text = label.replace("Test ", "") + ": testing..."
                    run { message ->
                        testResult.text = message
                    }
                }
            }
            testButtons.add(button)
            column.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, px(4), 0, 0) })
        }

        testResult = TextView(this).apply {
            textSize = 13f
            setTextColor(textMuted)
            setPadding(0, px(10), 0, 0)
        }
        column.addView(testResult)

        disconnectButton = MaterialButton(this).apply {
            text = "Disconnect"
            setOnClickListener { disconnect() }
        }
        column.addView(disconnectButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, px(20), 0, 0) })

        return ScrollView(this).apply { addView(column) }
    }

    private fun refreshUi() {
        val prefs = GoogleWorkspaceManager.preferences(this)
        if (prefs.connected) {
            statusText.text = "CONNECTED"
            statusText.setTextColor(Color.parseColor("#10B981"))
            val time = if (prefs.connectedAtMs > 0) SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(prefs.connectedAtMs)) else "unknown"
            accountText.text = prefs.accountEmail + "\nConnected " + time + " - tokens are requested fresh whenever needed"
            connectButton.text = "Re-authorize"
            disconnectButton.isEnabled = true
        } else {
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
