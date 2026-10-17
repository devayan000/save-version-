package com.jarvis.assistant.notifications

import org.junit.Assert.*
import org.junit.Test

class NotificationBriefingBuilderTest {
    @Test fun emptyBriefingShowsClearMessage() {
        NotificationBriefingBuilder.clear()
        val briefing = NotificationBriefingBuilder.buildBriefing()
        assertEquals(0, briefing.totalCount)
        val formatted = NotificationBriefingBuilder.formatBriefing(briefing)
        assertTrue(formatted.contains("No active notifications"))
    }
    @Test fun duplicateNotificationsAreGrouped() {
        NotificationBriefingBuilder.clear()
        NotificationBriefingBuilder.addNotification("com.test.app", "Title", "Body", "msg", 1, 100)
        NotificationBriefingBuilder.addNotification("com.test.app", "Title", "Body", "msg", 1, 200)
        val briefing = NotificationBriefingBuilder.buildBriefing()
        assertEquals(1, briefing.totalCount)
        assertEquals(2, briefing.groups[NotificationBriefingBuilder.Category.MESSAGES]?.first()?.occurrences)
        NotificationBriefingBuilder.clear()
    }
    @Test fun importantItemsAreFlagged() {
        NotificationBriefingBuilder.clear()
        NotificationBriefingBuilder.addNotification("com.test.app", "URGENT", "Action required now", "msg", 1, 100)
        val briefing = NotificationBriefingBuilder.buildBriefing()
        assertTrue(briefing.importantItems.isNotEmpty())
        NotificationBriefingBuilder.clear()
    }
}
