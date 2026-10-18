package com.jarvis.assistant.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * DEV STYLE: App Updates was moved from the home dock into Settings - this
 * small activity hosts the same updates list.
 */
class UpdatesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "App Updates"
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, UpdatesFragment())
            .commit()
    }
}
