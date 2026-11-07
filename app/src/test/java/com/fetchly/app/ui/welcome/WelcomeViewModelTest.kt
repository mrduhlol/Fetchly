package com.fetchly.app.ui.welcome

import com.fetchly.app.MainDispatcherRule
import com.fetchly.app.data.prefs.WelcomePreferences
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class Preferences(initial: Boolean? = false) : WelcomePreferences {
        val values = MutableSharedFlow<Boolean>(replay = 1).apply {
            initial?.let { tryEmit(it) }
        }
        override val welcomeCompleted: Flow<Boolean> = values
        var writes = 0
        var gate: CompletableDeferred<Unit>? = null
        var fail = false

        override suspend fun completeWelcome() {
            writes++
            gate?.await()
            if (fail) throw IOException("disk unavailable")
            values.emit(true)
        }
    }

    @Test fun `unknown first-launch state never flashes Home or accepts continue`() = runTest(main.dispatcher) {
        val preferences = Preferences(initial = null)
        val vm = WelcomeViewModel(preferences)
        runCurrent()
        assertNull(vm.state.value.completed)
        vm.getStarted()
        runCurrent()
        assertEquals(0, preferences.writes)
        preferences.values.emit(false)
        runCurrent()
        assertEquals(false, vm.state.value.completed)
    }

    @Test fun `returning users skip the welcome screen`() = runTest(main.dispatcher) {
        val preferences = Preferences(initial = true)
        val vm = WelcomeViewModel(preferences)
        runCurrent()
        assertEquals(true, vm.state.value.completed)
        vm.getStarted()
        runCurrent()
        assertEquals(0, preferences.writes)
    }

    @Test fun `continue waits for durable save and rapid taps cannot write twice`() = runTest(main.dispatcher) {
        val preferences = Preferences().apply { gate = CompletableDeferred() }
        val vm = WelcomeViewModel(preferences)
        runCurrent()
        vm.getStarted()
        vm.getStarted()
        runCurrent()
        assertTrue(vm.state.value.saving)
        assertEquals(false, vm.state.value.completed)
        assertEquals(1, preferences.writes)

        preferences.gate!!.complete(Unit)
        runCurrent()
        assertEquals(true, vm.state.value.completed)
        assertFalse(vm.state.value.saving)

        val reopened = WelcomeViewModel(preferences)
        runCurrent()
        assertEquals(true, reopened.state.value.completed)
        assertEquals(1, preferences.writes)
    }

    @Test fun `a failed save keeps welcome visible and can be retried`() = runTest(main.dispatcher) {
        val preferences = Preferences().apply { fail = true }
        val vm = WelcomeViewModel(preferences)
        runCurrent()
        vm.getStarted()
        runCurrent()
        assertEquals(false, vm.state.value.completed)
        assertFalse(vm.state.value.saving)
        assertNotNull(vm.state.value.error)

        preferences.fail = false
        vm.getStarted()
        runCurrent()
        assertEquals(true, vm.state.value.completed)
        assertNull(vm.state.value.error)
        assertEquals(2, preferences.writes)
    }
}
