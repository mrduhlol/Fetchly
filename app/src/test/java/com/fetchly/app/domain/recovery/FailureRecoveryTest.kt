package com.fetchly.app.domain.recovery

import com.fetchly.app.data.download.DownloadErrors
import org.junit.Assert.*
import org.junit.Test

class FailureRecoveryTest {
    @Test fun botChallengeIsNotTreatedAsExpiredUrl() {
        val raw = "HTTP 403: Sign in to confirm you're not a bot"
        assertEquals(FailureKind.BOT_CHECK, FailureRecovery.kind(raw))
        assertFalse(DownloadErrors.isExpiredLink(raw))
        assertTrue(DownloadErrors.textFor(raw).contains("bot check"))
    }
    @Test fun rateLimitSuggestsWaitingRatherThanRepeatedAttempts() {
        assertEquals(FailureKind.RATE_LIMIT, FailureRecovery.kind("HTTP 429 too many requests"))
        assertTrue(FailureRecovery.nextStep("HTTP 429").contains("Wait"))
    }
    @Test fun signInAndDrmCannotBeFixedByAutomaticRetry() {
        assertEquals(FailureKind.SIGN_IN, FailureRecovery.kind("Private video. Login required."))
        assertEquals(FailureKind.DRM, FailureRecovery.kind("DRM protected. Sign in required."))
        assertFalse(DownloadErrors.isExpiredLink("HTTP 403 DRM protected"))
    }
    @Test fun missingRuntimeHasAnEngineRecoveryPath() {
        assertEquals(FailureKind.ENGINE, FailureRecovery.kind("No supported JavaScript runtime"))
        assertTrue(FailureRecovery.nextStep("Unable to extract player response").contains("Settings"))
    }
    @Test fun storageAndMergeHaveDifferentNextSteps() {
        assertEquals(FailureKind.STORAGE, FailureRecovery.kind("ffmpeg: No space left on device"))
        assertEquals(FailureKind.MERGE, FailureRecovery.kind("ffmpeg merge failed"))
        assertTrue(FailureRecovery.nextStep("ffmpeg merge failed").contains("another available format"))
    }
}
