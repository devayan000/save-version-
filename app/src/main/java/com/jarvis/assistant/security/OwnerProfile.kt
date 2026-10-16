package com.jarvis.assistant.security

/**
 * Single source of truth for the creator's public identity and official links.
 * Managed by DEV STYLE. Change values here only — every screen reads from this file.
 */
object OwnerProfile {
    const val BRAND = "DEV STYLE"
    const val CREATOR_NAME = "Ayan Sir"
    const val CREATOR_ALIAS = "Dev Ayan"

    // Official links (same as used across the app's settings screens)
    const val PORTFOLIO_URL = "https://github.com/arian00001"
    const val TELEGRAM_URL = "https://t.me/DevStyleHQ"
    const val INSTAGRAM_URL = "https://www.instagram.com/dev_ayan00001"

    // Official YouTube channel — DEV STYLE / JARVIS AI
    const val YOUTUBE_URL = "https://www.youtube.com/@JARVISAI-f3r"

    // Official WhatsApp — international format for wa.me deep links
    val WHATSAPP_NUMBER: String? = "+8801612509753"

    const val IDENTITY_ANSWER_EN =
        "I was created and developed by Ayan Sir — also known as Dev Ayan — under the DEV STYLE brand."
    const val IDENTITY_ANSWER_BN =
        "আমাকে তৈরি করেছেন Ayan Sir — যিনি Dev Ayan নামেও পরিচিত। আমার অফিসিয়াল ব্র্যান্ড DEV STYLE।"
}
