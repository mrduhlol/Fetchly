package com.fetchly.app.data.download

import org.junit.Assert.assertNotEquals
import org.junit.Test

class DownloadNotificationsTest {

    /**
     * WorkManager cancels the foreground notification id itself when the work
     * ends; a terminal notification that reused it would be wiped instantly.
     */
    @Test
    fun `terminal notifications use an id WorkManager does not cancel`() {
        assertNotEquals(
            DownloadNotifications.idFor("a-download-id"),
            DownloadNotifications.terminalIdFor("a-download-id"),
        )
    }
}
