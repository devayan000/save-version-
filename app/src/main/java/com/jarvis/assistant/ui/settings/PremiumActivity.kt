package com.jarvis.assistant.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.R
import com.jarvis.assistant.databinding.ActivityPremiumBinding
import com.jarvis.assistant.backend.LicenseManager
import com.jarvis.assistant.security.InstallationManager
import com.jarvis.assistant.util.ThemeManager
import kotlin.concurrent.thread

class PremiumActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPremiumBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityPremiumBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.applyDynamicBackground(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        InstallationManager.licenseKey(this)?.let { binding.etKey.setText(it) }
        refreshStatus()

        binding.btnCheck.setOnClickListener {
            val key = binding.etKey.text?.toString()?.trim().orEmpty()
            if (key.isEmpty()) {
                Toast.makeText(this, "Enter a license key first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            setBusy(true)
            thread {
                val result = runCatching { LicenseManager.checkKey(key) }
                runOnUiThread {
                    setBusy(false)
                    result.fold(
                        onSuccess = { binding.tvStatus.text = it.message + " • Seats left: " + it.seatsLeft },
                        onFailure = { binding.tvStatus.text = "Backend unreachable — check internet" }
                    )
                }
            }
        }

        binding.btnActivate.setOnClickListener {
            val key = binding.etKey.text?.toString()?.trim().orEmpty()
            if (key.isEmpty()) {
                Toast.makeText(this, "Enter a license key first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            setBusy(true)
            thread {
                val outcome = LicenseManager.activate(this, key)
                runOnUiThread {
                    setBusy(false)
                    binding.tvStatus.text = outcome.message +
                        (if (outcome.success) "\nDevice: " + InstallationManager.installationId(this) else "")
                    if (outcome.success) {
                        Toast.makeText(this, "Premium activated", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun refreshStatus() {
        val inst = InstallationManager.installationId(this)
        binding.tvStatus.text =
            if (InstallationManager.isPremium(this)) "PREMIUM ACTIVE\nDevice: " + inst
            else "Not activated\nDevice: " + inst
    }

    private fun setBusy(busy: Boolean) {
        binding.btnCheck.isEnabled = !busy
        binding.btnActivate.isEnabled = !busy
    }
}
