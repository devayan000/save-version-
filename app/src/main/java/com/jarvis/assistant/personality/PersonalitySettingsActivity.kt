package com.jarvis.assistant.personality

import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.util.ThemeManager

/** Dedicated settings screen for personality, memory, proactive help, and quiet hours. */
class PersonalitySettingsActivity : AppCompatActivity() {

    private lateinit var store: ConversationMemoryStore

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        store = ConversationMemoryStore(this)
        buildUi()
    }

    private fun buildUi() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        TextView(this).apply { text = "Personality & Memory Settings"; textSize = 20f; setPadding(0, 0, 0, 16); container.addView(this) }

        // Long-Term Memory toggle
        container.addView(Button(this).apply {
            text = "Long-Term Memory: " + if (store.isMemoryEnabled()) "ON" else "OFF"
            setOnClickListener {
                store.setMemoryEnabled(!store.isMemoryEnabled())
                text = "Long-Term Memory: " + if (store.isMemoryEnabled()) "ON" else "OFF"
            }
        })

        // Learning from Corrections toggle
        container.addView(Button(this).apply {
            text = "Learning from Corrections: " + if (store.isLearningEnabled()) "ON" else "OFF"
            setOnClickListener {
                store.setLearningEnabled(!store.isLearningEnabled())
                text = "Learning from Corrections: " + if (store.isLearningEnabled()) "ON" else "OFF"
            }
        })

        // Proactive Help level
        container.addView(Button(this).apply {
            text = "Proactive Help: " + store.getProactiveLevel()
            setOnClickListener {
                val levels = arrayOf("off", "balanced", "active")
                MaterialAlertDialogBuilder(this@PersonalitySettingsActivity).setTitle("Proactive Help Level")
                    .setItems(levels) { _, which -> store.setProactiveLevel(levels[which]); text = "Proactive Help: " + store.getProactiveLevel() }.show()
            }
        })

        // Return Greeting toggle
        container.addView(Button(this).apply {
            text = "Return Greeting: " + if (store.isReturnGreetingEnabled()) "ON" else "OFF"
            setOnClickListener {
                store.setReturnGreetingEnabled(!store.isReturnGreetingEnabled())
                text = "Return Greeting: " + if (store.isReturnGreetingEnabled()) "ON" else "OFF"
            }
        })

        // Personality Style
        container.addView(Button(this).apply {
            text = "Personality Style: " + store.getPersonalityStyle()
            setOnClickListener {
                val styles = ConversationMemoryStore.PersonalityStyle.values().map { it.name }.toTypedArray()
                MaterialAlertDialogBuilder(this@PersonalitySettingsActivity).setTitle("Personality Style")
                    .setItems(styles) { _, which -> store.setPersonalityStyle(styles[which]); text = "Personality Style: " + store.getPersonalityStyle() }.show()
            }
        })

        // Humor Intensity slider
        val humorLabel = TextView(this).apply { text = "Humor Intensity: " + store.getHumorIntensity() + "%"; setPadding(0, 16, 0, 0) }
        container.addView(humorLabel)
        val seekBar = SeekBar(this).apply {
            max = 100; progress = store.getHumorIntensity()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { humorLabel.text = "Humor Intensity: $p%" }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { store.setHumorIntensity(sb?.progress ?: 50) }
            })
        }
        container.addView(seekBar)

        // Quiet Hours
        container.addView(Button(this).apply {
            text = "Quiet Hours: " + (if (store.getQuietHoursStart() >= 0) "" + store.getQuietHoursStart() + ":00 - " + store.getQuietHoursEnd() + ":00" else "Not set")
            setOnClickListener { showQuietHoursDialog() }
        })

        // Memory Management
        container.addView(Button(this).apply {
            text = "Memory Management (View/Edit/Delete)"
            setOnClickListener { startActivity(android.content.Intent(this@PersonalitySettingsActivity, MemoryManagementActivity::class.java)) }
        })

        // Clear Summaries
        container.addView(Button(this).apply {
            text = "Clear Conversation Summaries"
            setOnClickListener { store.clearSummaries(); Toast.makeText(this@PersonalitySettingsActivity, "Summaries cleared.", Toast.LENGTH_SHORT).show() }
        })

        // Reset Emotional State
        container.addView(Button(this).apply {
            text = "Reset Simulated Emotional State"
            setOnClickListener { PersonalityEngine.resetState(); Toast.makeText(this@PersonalitySettingsActivity, "Emotional state reset.", Toast.LENGTH_SHORT).show() }
        })

        val scroll = ScrollView(this).apply { addView(container); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(scroll); addView(Button(this@PersonalitySettingsActivity).apply { text = "Close"; setOnClickListener { finish() } }) }
        setContentView(root)
    }

    private fun showQuietHoursDialog() {
        val ctx = this
        val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 16) }
        val start = EditText(ctx).apply { hint = "Start hour (0-23)"; inputType = InputType.TYPE_CLASS_NUMBER; container.addView(this) }
        val end = EditText(ctx).apply { hint = "End hour (0-23)"; inputType = InputType.TYPE_CLASS_NUMBER; container.addView(this) }
        MaterialAlertDialogBuilder(ctx).setTitle("Quiet Hours").setView(container)
            .setPositiveButton("Save") { _, _ ->
                val s = start.text.toString().toIntOrNull() ?: -1
                val e = end.text.toString().toIntOrNull() ?: -1
                if (s in 0..23 && e in 0..23) { store.setQuietHours(s, e); recreate() }
                else Toast.makeText(ctx, "Enter valid hours 0-23.", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }
}
