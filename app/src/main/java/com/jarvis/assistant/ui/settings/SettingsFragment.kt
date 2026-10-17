package com.jarvis.assistant.ui.settings

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.data.model.GeminiConstants
import com.jarvis.assistant.databinding.FragmentSettingsBinding
import com.jarvis.assistant.ui.memory.MemoryActivity
import com.jarvis.assistant.ui.permission.PermissionActivity
import com.jarvis.assistant.ui.voice.VoiceEnrollmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.jarvis.assistant.premium.PremiumAccessService
import com.jarvis.assistant.security.CreatorAuthenticationService
import com.jarvis.assistant.bugs.BugReportRepository
import com.jarvis.assistant.performance.PerformanceMonitoringService
import com.jarvis.assistant.singing.SingingService
import com.jarvis.assistant.tools.GoogleSearchService
import com.jarvis.assistant.notifications.NotificationBriefingActivity
import com.jarvis.assistant.scanner.ScannerActivity
import com.jarvis.assistant.battery.BatteryActivity
import com.jarvis.assistant.media.MediaLibraryActivity
import com.jarvis.assistant.screencontext.ScreenContextActivity
import com.jarvis.assistant.share.ShareReceiverActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.content.ClipboardManager

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val preferences by lazy { JarvisApp.instance.preferences }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.root)

        loadUserData()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.root)
            loadUserData()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) {
            binding.root.visibility = View.VISIBLE
            com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.root)
            loadUserData()
        } else if (hidden && _binding != null) {
            binding.root.visibility = View.GONE
        }
    }

    private fun loadUserData() {
        val userName = preferences.userName.ifBlank { "Dev Ayan" }
        binding.tvProfileName.text = userName
        binding.tvProfileSubtitle.text = "JARVIS OPERATOR • PRIMARY USER"
        binding.ivPremiumBadge.visibility =
            if (com.jarvis.assistant.security.InstallationManager.isPremium(requireContext())) View.VISIBLE else View.GONE

        // Owner Voice Profile Status
        val hasVoiceProfile = preferences.hasOwnerVoiceProfile()
        if (hasVoiceProfile) {
            binding.tvOwnerVoiceBadge.text = "ACTIVE"
            binding.tvOwnerVoiceBadge.setTextColor(Color.parseColor("#10B981"))
            binding.tvOwnerVoiceBadge.setBackgroundResource(R.drawable.bg_jarvis_status_badge_green)
            binding.tvOwnerVoiceSubtitle.text = "Voice Profile Active • Only you can speak"

            binding.tvProfileVoiceStatus.text = "VOICE ENROLLED"
            binding.tvProfileVoiceStatus.setTextColor(Color.parseColor("#10B981"))
            binding.chipProfileVoiceStatus.setBackgroundResource(R.drawable.bg_jarvis_status_badge_green)
            binding.ivProfileVoiceBadgeIcon.setImageResource(R.drawable.ic_verified_badge)
            binding.ivProfileVoiceBadgeIcon.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#10B981"))
        } else {
            binding.tvOwnerVoiceBadge.text = "SETUP"
            binding.tvOwnerVoiceBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_sapphire))
            binding.tvOwnerVoiceBadge.setBackgroundResource(R.drawable.bg_jarvis_status_badge)
            binding.tvOwnerVoiceSubtitle.text = "Not enrolled • Tap to record voice profile"

            binding.tvProfileVoiceStatus.text = "ENROLL VOICE"
            binding.tvProfileVoiceStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_sapphire))
            binding.chipProfileVoiceStatus.setBackgroundResource(R.drawable.bg_jarvis_status_badge)
            binding.ivProfileVoiceBadgeIcon.setImageResource(R.drawable.ic_settings_owner_voice)
            binding.ivProfileVoiceBadgeIcon.imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.accent_sapphire)
            )
        }

        // Language
        binding.tvCurrentLanguage.text = preferences.language

        // Persona & Voice
        val currentPersona = preferences.personality
        val currentVoice = preferences.voice
        binding.tvCurrentPersona.text = "$currentPersona • Voice: $currentVoice"
        binding.tvProfilePersonaChip.text = currentPersona.uppercase()

        // Silent Execution Mode
        binding.switchSilentMode.isChecked = preferences.isSilentExecutionMode

        // Model
        val modelDisplay = preferences.aiModel.removePrefix("models/")
        binding.tvCurrentModel.text = "$modelDisplay • Live WebSocket"

        // Theme
        binding.tvCurrentTheme.text = when (preferences.appTheme) {
            "dark" -> "Dark Theme"
            "red" -> "Red Theme"
            "sunset" -> "Sunset Theme"
            "custom" -> "Custom (${preferences.customThemeColor})"
            else -> "Light Theme"
        }
    }

    private fun setupClickListeners() {
        // Profile Card -> Change Name
        binding.cardProfile.setOnClickListener {
            showEditNameDialog()
        }

        // Voice Status Chip on Profile Card -> Opens Voice Enrollment
        binding.chipProfileVoiceStatus.setOnClickListener {
            startActivity(Intent(requireContext(), VoiceEnrollmentActivity::class.java))
        }

        // Section 1: AI & Voice Intelligence
        binding.rowOwnerVoice.setOnClickListener {
            startActivity(Intent(requireContext(), VoiceEnrollmentActivity::class.java))
        }

        binding.rowLanguage.setOnClickListener {
            showLanguageDialog()
        }

        binding.rowPersonaVoice.setOnClickListener {
            startActivity(Intent(requireContext(), PersonaActivity::class.java))
        }

        binding.switchSilentMode.setOnCheckedChangeListener { _, isChecked ->
            preferences.isSilentExecutionMode = isChecked
            val msg = if (isChecked) "Silent Execution Mode Enabled" else "Silent Execution Mode Disabled"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // Section 2: System & Intelligence Data
        binding.rowApiConnection.setOnClickListener {
            startActivity(Intent(requireContext(), ApiConnectionActivity::class.java))
        }

        binding.rowMemory.setOnClickListener {
            startActivity(Intent(requireContext(), MemoryActivity::class.java))
        }

        binding.rowPermissions.setOnClickListener {
            startActivity(Intent(requireContext(), PermissionActivity::class.java))
        }

        binding.rowOverlayAvatars.setOnClickListener {
            startActivity(Intent(requireContext(), OverlayAvatarActivity::class.java))
        }

        binding.rowApplications.setOnClickListener {
            startActivity(Intent(requireContext(), HandsFreeActivity::class.java))
        }

        // Section 3: Appearance & Security
        binding.rowAppTheme.setOnClickListener {
            ThemeSelectionBottomSheet.newInstance().show(childFragmentManager, ThemeSelectionBottomSheet.TAG)
        }

        binding.rowPrivacyPolicy.setOnClickListener {
            startActivity(Intent(requireContext(), PrivacyShieldActivity::class.java))
        }

// Premium Activation — DEV STYLE licence
        binding.rowPremium.setOnClickListener {
            startActivity(Intent(requireContext(), PremiumActivity::class.java))
        }

// Creator Profile — About DEV STYLE
        binding.rowCreatorProfile.setOnClickListener {
            startActivity(Intent(requireContext(), CreatorProfileActivity::class.java))
        }

        binding.rowAppIdentity.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Jarvis Control Center")
                .setItems(arrayOf("App Identity", "Check for Updates", "Premium Access", "Creator Verification", "Bugs & Debug Center", "Performance Dashboard", "Singing Settings", "Search Settings", "Share to Jarvis", "Notification Briefing", "Camera Scanner", "Battery Intelligence", "Media Library", "Screen Context")) { _, which ->
                    when (which) {
                        0 -> MaterialAlertDialogBuilder(requireContext()).setTitle("JARVIS App Identity").setMessage(com.jarvis.assistant.security.AppIdentityVerifier.report(requireContext())).setPositiveButton("Close", null).show()
                        1 -> showUpdateDialog()
                        2 -> showPremiumAccessDialog()
                        3 -> showCreatorVerificationDialog()
                        4 -> showBugCenterDialog()
                        5 -> showPerformanceDashboardDialog()
                        6 -> showSingingSettingsDialog()
                        7 -> showSearchSettingsDialog()
                        8 -> startActivity(Intent(requireContext(), ShareReceiverActivity::class.java))
                        9 -> startActivity(Intent(requireContext(), NotificationBriefingActivity::class.java))
                        10 -> { if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestCameraLauncher.launch(android.Manifest.permission.CAMERA) else startActivity(Intent(requireContext(), ScannerActivity::class.java)) }
                        11 -> startActivity(Intent(requireContext(), BatteryActivity::class.java))
                        12 -> startActivity(Intent(requireContext(), MediaLibraryActivity::class.java))
                        13 -> startActivity(Intent(requireContext(), ScreenContextActivity::class.java))
                    }
                }
                .show()
        }

        // Social Media Community Buttons
        binding.btnSocialTelegram.setOnClickListener {
            openUrl("https://t.me/DevStyleHQ")
        }

        binding.btnSocialInstagram.setOnClickListener {
            openUrl("https://www.instagram.com/dev_ayan00001")
        }
        // YouTube creator link intentionally left blank.
        binding.btnSocialYoutube.visibility = View.GONE

        binding.tvFooterCredit.setOnClickListener {
            openUrl("https://t.me/DevStyleHQ")
        }
    }

    private fun showUpdateDialog() {
        val ctx = requireContext()
        val container = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(48, 32, 48, 16) }
        android.widget.TextView(ctx).apply {
            text = "Current: v" + com.jarvis.assistant.update.UpdateManager.currentVersionName() + " (build " + com.jarvis.assistant.update.UpdateManager.currentVersionCode() + ")\n\nSet your update URL. The APK must be signed with the same key as the installed app, or Android will ask you to uninstall first."
            setPadding(0, 0, 0, 24)
            container.addView(this)
        }
        val url = android.widget.EditText(ctx).apply { hint = "Update URL (HTTPS JSON: versionCode, versionName, downloadUrl)"; inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI; setText(preferences.updateCheckUrl) }
        container.addView(url)
        MaterialAlertDialogBuilder(ctx)
            .setTitle("App Updates")
            .setView(android.widget.ScrollView(ctx).apply { addView(container) })
            .setPositiveButton("Check Now") { _, _ -> preferences.updateCheckUrl = url.text.toString().trim(); checkForUpdates() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkForUpdates() {
        val url = preferences.updateCheckUrl
        if (url.isBlank()) { Toast.makeText(requireContext(), "Set an update URL first.", Toast.LENGTH_LONG).show(); return }
        Toast.makeText(requireContext(), "Checking for updates...", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) { com.jarvis.assistant.update.UpdateManager.checkForUpdate(url) }
            if (info == null) { Toast.makeText(requireContext(), "No update available or check failed.", Toast.LENGTH_LONG).show(); return@launch }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Update " + info.versionName + " available")
                .setMessage(info.changelog.ifBlank { "A newer version is available. Download and install?" })
                .setPositiveButton("Download & Install") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val file = withContext(Dispatchers.IO) { com.jarvis.assistant.update.UpdateManager.downloadApk(info.downloadUrl) { } }
                        if (file != null) com.jarvis.assistant.update.UpdateManager.installApk(requireActivity(), file)
                        else Toast.makeText(requireContext(), "Download failed.", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private val requestCameraLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startActivity(Intent(requireContext(), ScannerActivity::class.java))
        else Toast.makeText(requireContext(), "Camera permission denied. Grant it in Android Settings.", Toast.LENGTH_LONG).show()
    }

    private fun showPremiumAccessDialog() {
        val ctx = requireContext()
        val container = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(48, 32, 48, 16) }
        android.widget.TextView(ctx).apply { text = if (PremiumAccessService.isValidated(ctx)) "Premium: ACTIVE" else "Premium: Not activated"; setPadding(0, 0, 0, 16); container.addView(this) }
        val name = android.widget.EditText(ctx).apply { hint = "Your name"; inputType = android.text.InputType.TYPE_CLASS_TEXT; setText(PremiumAccessService.getUserName(ctx)) }
        container.addView(name)
        val device = android.widget.EditText(ctx).apply { hint = "Device model"; inputType = android.text.InputType.TYPE_CLASS_TEXT; setText(PremiumAccessService.getDeviceModel(ctx)) }
        container.addView(device)
        val key = android.widget.EditText(ctx).apply { hint = "Premium access key (AHONA-API-...)"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        container.addView(key)
        MaterialAlertDialogBuilder(ctx).setTitle("Premium Access").setView(android.widget.ScrollView(ctx).apply { addView(container) })
            .setPositiveButton("Activate") { _, _ ->
                val n = name.text.toString().trim()
                val d = device.text.toString().trim()
                val k = key.text.toString().trim()
                if (n.isBlank() || d.isBlank()) { Toast.makeText(ctx, "Enter name and device.", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                if (k.isBlank()) { Toast.makeText(ctx, "Enter a key.", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                PremiumAccessService.setUserName(ctx, n)
                PremiumAccessService.setDeviceModel(ctx, d)
                Toast.makeText(ctx, "Activating...", Toast.LENGTH_SHORT).show()
                viewLifecycleOwner.lifecycleScope.launch {
                    val check = withContext(Dispatchers.IO) { PremiumAccessService.checkKey(ctx, k) }
                    if (!check.valid) { Toast.makeText(ctx, check.message.ifBlank { "Invalid key" }, Toast.LENGTH_LONG).show(); return@launch }
                    val result = withContext(Dispatchers.IO) { PremiumAccessService.activatePremium(ctx, k) }
                    if (result.valid) Toast.makeText(ctx, "Premium activated! Features: " + result.features.joinToString(", "), Toast.LENGTH_LONG).show()
                    else { val msg = if (result.isNetworkError) "Network error: " + result.error else result.error; Toast.makeText(ctx, msg ?: "Activation failed", Toast.LENGTH_LONG).show() }
                }
            }
            .setNegativeButton("Get Key (WhatsApp)") { _, _ -> if (!PremiumAccessService.openWhatsAppForKey(ctx)) Toast.makeText(ctx, "WhatsApp not available.", Toast.LENGTH_SHORT).show() }
            .setNeutralButton("Revoke") { _, _ -> PremiumAccessService.revoke(ctx); Toast.makeText(ctx, "Premium revoked.", Toast.LENGTH_SHORT).show() }
            .show()
    }

    private fun showCreatorVerificationDialog() {
        val ctx = requireContext()
        val container = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(48, 32, 48, 16) }
        android.widget.TextView(ctx).apply { text = if (CreatorAuthenticationService.isCreator(ctx)) "Creator: VERIFIED" else "Creator: Not verified"; setPadding(0, 0, 0, 16); container.addView(this) }
        val key = android.widget.EditText(ctx).apply { hint = "Creator verification key"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        container.addView(key)
        MaterialAlertDialogBuilder(ctx).setTitle("Creator Verification").setView(android.widget.ScrollView(ctx).apply { addView(container) })
            .setPositiveButton("Verify") { _, _ ->
                if (CreatorAuthenticationService.verify(ctx, key.text.toString().trim())) Toast.makeText(ctx, "Creator verified! Debug Center unlocked.", Toast.LENGTH_LONG).show()
                else Toast.makeText(ctx, "Invalid creator key.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .setNeutralButton("Revoke") { _, _ -> CreatorAuthenticationService.revoke(ctx); Toast.makeText(ctx, "Creator access revoked.", Toast.LENGTH_SHORT).show() }
            .show()
    }

    private fun showBugCenterDialog() {
        val ctx = requireContext()
        if (!CreatorAuthenticationService.isCreator(ctx)) { Toast.makeText(ctx, "Creator verification required.", Toast.LENGTH_LONG).show(); return }
        val repo = BugReportRepository(ctx)
        val bugs = repo.loadAll()
        if (bugs.isEmpty()) { MaterialAlertDialogBuilder(ctx).setTitle("BUGS, ERRORS & IMPROVEMENTS").setMessage("No bugs detected yet.").setPositiveButton("Close", null).show(); return }
        val items = bugs.map { it.bugId + " | " + it.feature + " | " + it.severity + " | " + it.status }.toTypedArray()
        val selected = booleanArrayOf(*BooleanArray(bugs.size))
        MaterialAlertDialogBuilder(ctx).setTitle("BUGS, ERRORS & IMPROVEMENTS (" + bugs.size + ")").setMultiChoiceItems(items, selected) { _, _, _ -> }
            .setPositiveButton("Copy Selected") { _, _ ->
                val sel = bugs.filterIndexed { i, _ -> selected[i] }
                if (sel.isEmpty()) { Toast.makeText(ctx, "No bugs selected.", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Jarvis Bugs", repo.copyToClipboardText(sel)))
                Toast.makeText(ctx, "Copied " + sel.size + " bugs to clipboard.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Export JSON") { _, _ ->
                val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Jarvis Bugs JSON", repo.exportJson(bugs)))
                Toast.makeText(ctx, "JSON exported to clipboard.", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Clear Resolved") { _, _ -> repo.clearResolved(); Toast.makeText(ctx, "Resolved bugs cleared.", Toast.LENGTH_SHORT).show() }
            .show()
    }

    private fun showPerformanceDashboardDialog() {
        val ctx = requireContext()
        val d = PerformanceMonitoringService.getDashboard(ctx)
        val nl = 10.toChar()
        val report = "Total Commands: " + d.totalCommands + nl + "Successful: " + d.successfulCommands + nl + "Failed: " + d.failedCommands + nl + "Avg Latency: " + d.avgLatencyMs + " ms" + nl + "Recognition Failure: " + (d.recognitionFailureRate * 100).toInt() + "%" + nl + "Tool Failure: " + (d.toolFailureRate * 100).toInt() + "%" + nl + "Screenshot Failure: " + (d.screenshotFailureRate * 100).toInt() + "%" + nl + "Bugs Detected: " + d.recentUnresolvedBugs + (if (d.mostFailingFeatures.isNotEmpty()) nl + "Most Failing: " + d.mostFailingFeatures.joinToString(", ") { it.first + " (" + it.second + ")" } else "")
        MaterialAlertDialogBuilder(ctx).setTitle("Performance Dashboard").setMessage(report).setPositiveButton("Close", null).show()
    }

    private fun showSingingSettingsDialog() {
        val ctx = requireContext()
        val container = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(48, 32, 48, 16) }
        android.widget.TextView(ctx).apply { text = "Set a singing provider endpoint to generate original songs with actual sung audio. Normal TTS is not singing."; setPadding(0, 0, 0, 16); container.addView(this) }
        val endpoint = android.widget.EditText(ctx).apply { hint = "Singing endpoint URL (HTTPS)"; inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI; setText(SingingService.getEndpoint(ctx)) }
        container.addView(endpoint)
        val genre = android.widget.EditText(ctx).apply { hint = "Genre (pop, rock, classical...)"; setText("pop") }
        container.addView(genre)
        val mood = android.widget.EditText(ctx).apply { hint = "Mood (happy, sad, calm...)"; setText("happy") }
        container.addView(mood)
        MaterialAlertDialogBuilder(ctx).setTitle("Singing Settings").setView(android.widget.ScrollView(ctx).apply { addView(container) })
            .setPositiveButton("Save") { _, _ -> SingingService.setEndpoint(ctx, endpoint.text.toString().trim()); SingingService.setStyle(ctx, genre.text.toString().trim(), "medium", mood.text.toString().trim(), "warm"); Toast.makeText(ctx, "Singing settings saved.", Toast.LENGTH_SHORT).show() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showSearchSettingsDialog() {
        val ctx = requireContext()
        val container = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(48, 32, 48, 16) }
        android.widget.TextView(ctx).apply { text = "Set a search provider endpoint to enable Google Search. Without this, search requests create bug reports."; setPadding(0, 0, 0, 16); container.addView(this) }
        val endpoint = android.widget.EditText(ctx).apply { hint = "Search endpoint URL (use {query} placeholder)"; inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI; setText(GoogleSearchService.getEndpoint(ctx)) }
        container.addView(endpoint)
        val apiKey = android.widget.EditText(ctx).apply { hint = "API key (optional)"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        container.addView(apiKey)
        MaterialAlertDialogBuilder(ctx).setTitle("Google Search Settings").setView(android.widget.ScrollView(ctx).apply { addView(container) })
            .setPositiveButton("Save") { _, _ -> GoogleSearchService.setEndpoint(ctx, endpoint.text.toString().trim()); GoogleSearchService.setApiKey(ctx, apiKey.text.toString().trim()); Toast.makeText(ctx, "Search settings saved.", Toast.LENGTH_SHORT).show() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showLanguageDialog() {
        val languages = GeminiConstants.SUPPORTED_LANGUAGES.toTypedArray()
        val currentIndex = languages.indexOfFirst { it.equals(preferences.language, ignoreCase = true) }.let { if (it >= 0) it else 0 }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Conversation Language")
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                val chosen = languages[which]
                preferences.language = chosen
                binding.tvCurrentLanguage.text = chosen
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showWakeWordDialog() {
        val ctx = requireContext()
        preferences.clearLegacyUnlockCredential()
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        android.widget.TextView(ctx).apply {
            text = "• Wake Words: 'Jarvis', 'Hey Jarvis'\n• Acoustic Trigger: Double Clap detection\n• Standby Mode: Low-power local Vosk offline engine\n\nWhen standby listening is active, Jarvis awakens instantly upon hearing your voice.\n\nSave your creator greeting phrase below. This personalizes greetings only; it is not voice authentication and grants no extra permissions. Saying 'unlock phone' offers Android's unlock screen; PIN, password or biometrics are still required for a secure lock."
            setPadding(0, 0, 0, 24)
            container.addView(this)
        }
        val phrase = android.widget.EditText(ctx).apply { hint = "Creator greeting phrase (e.g. AHONA-I-LOVE-YOU)"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD; setText(preferences.creatorSecretPhrase) }
        val name = android.widget.EditText(ctx).apply { hint = "Creator name (e.g. Ayan Sir)"; inputType = android.text.InputType.TYPE_CLASS_TEXT; setText(preferences.creatorName) }
        val email = android.widget.EditText(ctx).apply { hint = "Creator email (optional)"; inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS; setText(preferences.creatorEmail) }
        for (et in listOf(phrase, name, email)) {
            (et.layoutParams as? android.widget.LinearLayout.LayoutParams)?.setMargins(0, 12, 0, 12)
            container.addView(et)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Wake Words & Creator Greeting")
            .setView(android.widget.ScrollView(ctx).apply { addView(container) })
            .setPositiveButton("Save") { _, _ ->
                preferences.creatorSecretPhrase = phrase.text.toString().trim()
                preferences.creatorName = name.text.toString().trim()
                preferences.creatorEmail = email.text.toString().trim()
                Toast.makeText(ctx, "Creator greeting saved. Android handles phone authentication; no phone PIN is stored.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAppAutomationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Autonomous App Automation")
            .setMessage("Jarvis is integrated with your device capabilities:\n\n• WhatsApp: Hands-free messaging & calls\n• YouTube: Search and playback automation\n• Phone Controls: Flashlight, Volume, Brightness, Bluetooth\n• Apps & Tools: Launch, search, and manage apps via voice")
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showEditNameDialog() {
        val input = EditText(requireContext()).apply {
            setText(preferences.userName.ifBlank { "Dev Ayan" })
            setSelection(text.length)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Operator Name")
            .setMessage("Enter the name Jarvis should use to address you:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank()) {
                    preferences.userName = newName
                    loadUserData()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "Unable to open link", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
