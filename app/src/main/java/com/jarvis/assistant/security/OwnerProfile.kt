package com.jarvis.assistant.security

/**
 * Single source of truth for the creator's public identity and official links.
 * Managed by DEV STYLE. Change values here only — every screen reads from this file.
 */
object OwnerProfile {
    const val BRAND = "DEV STYLE"
    const val CREATOR_NAME = "Ayan Ahamed"
    const val CREATOR_ALIAS = "16-year-old developer"

    // Official links (same as used across the app's settings screens)
    const val PORTFOLIO_URL = "https://arian00001.site"
    const val TELEGRAM_URL = "https://t.me/DevStyleHQ"
    const val INSTAGRAM_URL = "https://www.instagram.com/dev_ayan00001"

    // Official YouTube channel — DEV STYLE / JARVIS AI
    const val YOUTUBE_URL = "https://www.youtube.com/@JARVISAI-f3r"

    // Official WhatsApp — international format for wa.me deep links
    val WHATSAPP_NUMBER: String? = "+8801612509753"

    const val IDENTITY_ANSWER_EN =
        "My app creator and owner is Ayan Ahamed, a 16-year-old developer. His portfolio is arian00001.site. The official brand is DEV STYLE."
    const val IDENTITY_ANSWER_BN =
        "আমার অ্যাপের নির্মাতা ও মালিক Ayan Ahamed, একজন ১৬ বছর বয়সী ডেভেলপার। তাঁর পোর্টফোলিও arian00001.site। আমার অফিসিয়াল ব্র্যান্ড DEV STYLE।"
}
