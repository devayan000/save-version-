package com.jarvis.assistant.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.R
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

        binding.tvCreatorName.text = OwnerProfile.CREATOR_NAME
        binding.tvCreatorAlias.text = "${OwnerProfile.CREATOR_ALIAS} • ${OwnerProfile.BRAND}"
        binding.tvCreatorTagline.text =
            "Created & developed by ${OwnerProfile.CREATOR_NAME} — the mind behind ${OwnerProfile.BRAND}."

        binding.btnBack.setOnClickListener { finish() }

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
}
