package com.jarvis.assistant.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Typeface
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.R
import com.jarvis.assistant.bugs.BugReportRepository
import com.jarvis.assistant.security.CreatorAuthenticationService
import com.jarvis.assistant.databinding.ActivityCreatorProfileBinding
import com.jarvis.assistant.security.OwnerProfile
import com.jarvis.assistant.util.ThemeManager

class CreatorProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatorProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityCreatorProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.applyDynamicBackground(binding.root)

        // DEV STYLE: creator-only repository analyzer (visible only with the creator key)
        if (CreatorAuthenticationService.isCreator(this)) {
            val scroll = (0 until binding.root.childCount)
                .map { binding.root.getChildAt(it) }
                .filterIsInstance<android.widget.ScrollView>()
                .firstOrNull()
            val column = scroll?.getChildAt(0) as? android.widget.LinearLayout
            if (column != null) {
                val analyzerButton = MaterialButton(this)
                analyzerButton.text = "REPOSITORY ANALYZER - paste a GitHub link, I analyze the full code"
                com.jarvis.assistant.util.PremiumUi.premiumButton(analyzerButton)
                analyzerButton.isAllCaps = false
                analyzerButton.setOnClickListener {
                    startActivity(Intent(this, com.jarvis.assistant.developer.CreatorRepoActivity::class.java))
                }
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = (20 * resources.displayMetrics.density).toInt()
                column.addView(analyzerButton, 0, lp)
            }
        }

        binding.tvCreatorName.text = OwnerProfile.CREATOR_NAME
        binding.tvCreatorAlias.text = "${OwnerProfile.CREATOR_ALIAS} • ${OwnerProfile.BRAND}"
        binding.tvCreatorTagline.text =
            "Created & developed by ${OwnerProfile.CREATOR_NAME} — the mind behind ${OwnerProfile.BRAND}."

        binding.btnBack.setOnClickListener { finish() }

        setupCreatorConsole()

        binding.rowPortfolio.setOnClickListener { openUrl(OwnerProfile.PORTFOLIO_URL) }
        binding.rowTelegram.setOnClickListener { openUrl(OwnerProfile.TELEGRAM_URL) }
        binding.rowInstagram.setOnClickListener { openUrl(OwnerProfile.INSTAGRAM_URL) }

        // YouTube: channel not configured yet — text-only state, never an invented link.
        if (OwnerProfile.YOUTUBE_URL == null) {
            binding.rowYoutube.isEnabled = false
            binding.rowYoutube.alpha = 0.55f
            binding.tvYoutubeValue.text = "Coming soon"
        } else {
            OwnerProfile.YOUTUBE_URL?.let { url -> binding.rowYoutube.setOnClickListener { openUrl(url) } }
        }

        // WhatsApp: number not confirmed — text-only, no deep link.
        if (OwnerProfile.WHATSAPP_NUMBER == null) {
            binding.rowWhatsapp.isEnabled = false
            binding.rowWhatsapp.alpha = 0.55f
            binding.tvWhatsappValue.text = "Contact via official channels"
        }

        binding.tvFooterCredit.text = "${OwnerProfile.BRAND} • ${OwnerProfile.CREATOR_ALIAS}"
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show()
        }
    }

    /** Creator Access card: verify the creator key here, then browse saved bugs and improvements. */
    private fun setupCreatorConsole() {
        val root = binding.root as? android.view.ViewGroup ?: return
        var column: android.view.ViewGroup? = null
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is android.widget.ScrollView && child.childCount > 0) {
                column = child.getChildAt(0) as? android.view.ViewGroup
                break
            }
        }
        val target = column ?: return

        val card = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_jarvis_action_card)
            setPadding(48, 40, 48, 40)
        }
        buildConsoleCard(card)
        target.addView(card, 0)
    }

    private fun buildConsoleCard(card: android.widget.LinearLayout) {
        card.removeAllViews()
        card.addView(android.widget.TextView(this).apply {
            text = "CREATOR ACCESS"
            textSize = 16f
            setTypeface(Typeface.DEFAULT_BOLD)
        })
        if (!CreatorAuthenticationService.isCreator(this)) {
            card.addView(android.widget.TextView(this).apply {
                text = "Enter the creator key to unlock the bug console."
                textSize = 13f
                setPadding(0, 8, 0, 12)
            })
            val keyInput = android.widget.EditText(this).apply {
                hint = "Creator key"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            card.addView(keyInput)
            card.addView(MaterialButton(this).apply {
                text = "Verify creator key"
                com.jarvis.assistant.util.PremiumUi.premiumButton(this)
                setOnClickListener {
                    if (CreatorAuthenticationService.verify(this@CreatorProfileActivity, keyInput.text.toString().trim())) {
                        Toast.makeText(this@CreatorProfileActivity, "Creator verified - bug console unlocked.", Toast.LENGTH_LONG).show()
                        buildConsoleCard(card)
                    } else {
                        Toast.makeText(this@CreatorProfileActivity, "Invalid creator key.", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        } else {
            val repo = BugReportRepository(this)
            val bugs = repo.loadAll()
            card.addView(android.widget.TextView(this).apply {
                text = "Creator verified. Bugs saved: " + bugs.size
                textSize = 14f
                setPadding(0, 10, 0, 12)
            })
            card.addView(MaterialButton(this).apply {
                text = "View bugs, errors & improvements"
                com.jarvis.assistant.util.PremiumUi.premiumButton(this)
                setOnClickListener {
                    if (bugs.isEmpty()) {
                        Toast.makeText(this@CreatorProfileActivity, "No bugs detected yet.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val items = bugs.take(50).map { b ->
                        b.feature + " | " + b.errorType + " | " + b.severity + " | " + b.status +
                            (b.suggestedFix?.let { "\n   Fix: " + it.take(120) } ?: "")
                    }.toTypedArray()
                    MaterialAlertDialogBuilder(this@CreatorProfileActivity)
                        .setTitle("BUGS, ERRORS & IMPROVEMENTS (" + bugs.size + ")")
                        .setItems(items, null)
                        .setPositiveButton("Close", null)
                        .show()
                }
            })
        }
    }
}
