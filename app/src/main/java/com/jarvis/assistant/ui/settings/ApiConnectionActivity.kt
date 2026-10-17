package com.jarvis.assistant.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.network.EndpointConfig
import com.jarvis.assistant.network.HttpChatClient
import com.jarvis.assistant.data.model.GeminiConstants
import com.jarvis.assistant.databinding.ActivityApiConnectionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ApiConnectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityApiConnectionBinding
    private val preferences by lazy { JarvisApp.instance.preferences }
    private val imgbbPreferences by lazy { JarvisApp.instance.imgbbPreferences }
    private val testHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityApiConnectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.root)

        setupUI()
        loadCurrentPreferences()
        setupListeners()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadCurrentPreferences() {
        binding.etApiKey.setText(preferences.apiKey)
        binding.etCustomEndpoint.setText(preferences.customEndpoint)
        binding.etCustomChatModel.setText(preferences.customChatModel)
        updateImgbbStatus()

        if (!preferences.canStoreApiKeySecurely) {
            binding.tvLiveStatus.text = "SECURE STORAGE UNAVAILABLE • API KEY DISABLED"
            binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
        }

        when (preferences.aiModel) {
            GeminiConstants.MODEL_3_8_LIVE -> binding.rbModelFlash20.isChecked = true
            GeminiConstants.MODEL_3_1_FLASH_AGENT -> binding.rbModelFlash15.isChecked = true
            else -> binding.rbModelFlashThinking.isChecked = true
        }

        if (!preferences.canStoreApiKeySecurely) {
            // Keep the secure-storage warning set above.
        } else if (preferences.apiKey.isNotBlank()) {
            binding.tvLiveStatus.text = "KEY CONFIGURED • READY"
            binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#22C55E"))
        } else {
            binding.tvLiveStatus.text = "NO API KEY • ENTER KEY BELOW"
            binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EAB308"))
        }

        if (preferences.customEndpoint.isNotBlank()) {
            binding.tvEndpointSummary.text = "Active Endpoint: ${preferences.customEndpoint}"
        }
        if (EndpointConfig.httpEndpoint(preferences.apiKey, preferences.customEndpoint) != null) {
            binding.tvLiveStatus.text = "PERSONAL HTTPS ENDPOINT CONFIGURED"
            binding.tvEndpointSummary.text = "Personal endpoint: " + preferences.customEndpoint
        } else if (preferences.apiKey.isBlank()) {
            binding.tvEndpointSummary.text = "No default API. Add your own endpoint or Gemini key."
        }
    }

    private fun setupListeners() {
        binding.btnPasteKey.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val item = clipboard.primaryClip?.getItemAt(0)
            val pasted = item?.text?.toString()?.trim() ?: ""
            if (pasted.isNotBlank()) {
                binding.etApiKey.setText(pasted)
                Toast.makeText(this, "API Key pasted from clipboard", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnClearKey.setOnClickListener {
            binding.etApiKey.setText("")
        }

        binding.btnTestConnection.setOnClickListener {
            val enteredKey = binding.etApiKey.text?.toString()?.trim() ?: ""
            val configured = binding.etCustomEndpoint.text?.toString()?.trim().orEmpty()
            val error = EndpointConfig.validationError(configured)
            if (error != null) { Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show(); return@setOnClickListener }
            val endpoint = EndpointConfig.httpEndpoint(enteredKey, configured)
            if (enteredKey.isBlank() && endpoint == null) {
                Snackbar.make(binding.root, "Add your own endpoint or Gemini key first.", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            testConnection(enteredKey, endpoint)
        }

        binding.btnSaveConnection.setOnClickListener {
            saveAndApply()
        }

        binding.btnImgbbKey.setOnClickListener {
            showImgbbKeyDialog()
        }
 

        // GitHub Research launcher (added in code: the XML layout is near the entity size limit)
        val researchBtn = com.google.android.material.button.MaterialButton(this)
        researchBtn.text = "GitHub Research (Developer Agent)"
        researchBtn.isAllCaps = false
        researchBtn.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            (40 * resources.displayMetrics.density).toInt()
        ).apply { topMargin = (12 * resources.displayMetrics.density).toInt() }
        researchBtn.setOnClickListener {
            startActivity(android.content.Intent(this, com.jarvis.assistant.developer.GitHubResearchActivity::class.java))
        }
        (binding.btnImgbbKey.parent as? android.view.ViewGroup)?.addView(researchBtn)
    }

    private fun updateImgbbStatus() {
        val hasKey = runCatching { imgbbPreferences.imgbbApiKey.isNotBlank() }.getOrDefault(false)
        binding.tvImgbbStatus.text = if (hasKey) {
            "ImgBB key configured - saved securely"
        } else {
            "No ImgBB key configured"
        }
    }

    private fun showImgbbKeyDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "ImgBB API key"
            setText(runCatching { imgbbPreferences.imgbbApiKey }.getOrDefault(""))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(48, 32, 48, 8)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("ImgBB API Key")
            .setMessage("Uploads go through api.imgbb.com. Leave blank to remove the saved key.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                try {
                    imgbbPreferences.imgbbApiKey = input.text?.toString()?.trim().orEmpty()
                    Toast.makeText(this, "ImgBB key saved securely", Toast.LENGTH_SHORT).show()
                } catch (e: IllegalStateException) {
                    Snackbar.make(binding.root, e.message ?: "Secure storage is unavailable.", Snackbar.LENGTH_LONG).show()
                }
                updateImgbbStatus()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun testConnection(apiKey: String, endpoint: String?) {
        val customModel = binding.etCustomChatModel.text?.toString()?.trim().orEmpty()
        binding.btnTestConnection.isEnabled = false
        binding.tvPingResult.text = "Testing ping..."
        binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3B82F6"))

        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                try {
                    if (endpoint != null) {
                        HttpChatClient().ask(endpoint, "Reply only with OK.", apiKey, customModel)
                        Pair(true, System.currentTimeMillis() - startTime)
                    } else {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
                    val request = Request.Builder().url(url).build()
                    testHttpClient.newCall(request).execute().use { response ->
                        val duration = System.currentTimeMillis() - startTime
                        if (response.isSuccessful) {
                            Pair(true, duration)
                        } else {
                            Pair(false, response.code)
                        }
                    }
                    }
                } catch (e: Exception) {
                    Pair(false, -1)
                }
            }

            binding.btnTestConnection.isEnabled = true
            if (result.first) {
                val ping = result.second
                binding.tvPingResult.text = "Ping: ${ping}ms • Valid"
                binding.tvLiveStatus.text = "CONNECTED • HANDSHAKE OK"
                binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#22C55E"))
                Snackbar.make(binding.root, "Connection verified! Ping: ${ping}ms", Snackbar.LENGTH_LONG).show()
            } else {
                val code = result.second
                binding.tvPingResult.text = if (code == -1) "Network Timeout" else "HTTP $code Error"
                binding.tvLiveStatus.text = "FAILED • INVALID KEY OR NETWORK"
                binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
                Snackbar.make(binding.root, "Connection failed. Check the endpoint, request format, model and personal key.", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun saveAndApply() {
        val key = binding.etApiKey.text?.toString()?.trim() ?: ""
        val customEp = binding.etCustomEndpoint.text?.toString()?.trim() ?: ""
        val error = EndpointConfig.validationError(customEp)
        if (error != null) { Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show(); return }

        val selectedModel = when (binding.rgModelGroup.checkedRadioButtonId) {
            binding.rbModelFlash20.id -> GeminiConstants.MODEL_3_8_LIVE
            binding.rbModelFlash15.id -> GeminiConstants.MODEL_3_1_FLASH_AGENT
            else -> GeminiConstants.MODEL_3_1_FLASH
        }

        try {
            if (key.isNotBlank() || preferences.canStoreApiKeySecurely) preferences.apiKey = key
        } catch (e: IllegalStateException) {
            Snackbar.make(binding.root, e.message ?: "Secure key storage is unavailable.", Snackbar.LENGTH_LONG).show()
            return
        }
        preferences.aiModel = selectedModel
        preferences.customEndpoint = customEp
        preferences.customChatModel = binding.etCustomChatModel.text?.toString()?.trim().orEmpty()
        com.jarvis.assistant.background.JarvisConversationService.clearLiveError()
        com.jarvis.assistant.background.JarvisConversationService.restartSession()

        Toast.makeText(this, "API & Connection settings saved!", Toast.LENGTH_SHORT).show()
        finish()
    }
}
