package com.jarvis.assistant.terminal

import android.os.Bundle
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.util.ThemeManager

/** Termux-style terminal screen. Commands run in the background via TerminalManager. */
class TerminalActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)

        val out = TextView(this).apply {
            textSize = 12f
            textIsSelectable = true
            setPadding(24, 24, 24, 24)
            text = TerminalManager.historyList().joinToString("\n") { "$ " + it.command + "\n" + it.output + "\n" }
                .ifBlank { "Jarvis Terminal\nType a shell command and press Run.\n" }
        }
        val scroll = ScrollView(this).apply {
            addView(out, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val input = EditText(this).apply {
            hint = "sh command"
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_GO
        }
        val run = Button(this).apply { text = "Run" }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 8, 16, 8)
            addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(run, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val clear = Button(this).apply { text = "Clear" }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            addView(TextView(this@TerminalActivity).apply {
                text = "Jarvis Terminal (" + TerminalManager.currentDir() + ")"
                textSize = 14f
                setPadding(8, 8, 8, 8)
            })
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bottom)
            addView(clear, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(root)

        val execute = {
            val cmd = input.text?.toString()?.trim().orEmpty()
            if (cmd.isNotBlank()) {
                input.setText("")
                out.append("\n$ " + cmd)
                TerminalManager.run(cmd) { r ->
                    runOnUiThread {
                        out.append("\n" + r.output + "\n")
                        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
            }
        }
        run.setOnClickListener { execute() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) { execute(); true } else false
        }
        clear.setOnClickListener { out.text = "Jarvis Terminal\n" }
    }
}
