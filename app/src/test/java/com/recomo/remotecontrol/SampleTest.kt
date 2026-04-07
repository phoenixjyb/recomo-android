package com.recomo.remotecontrol

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Sample test file verifying the JUnit 5 + MockK + Turbine infrastructure.
 *
 * These tests exercise no production code — they validate that all three test
 * libraries resolve and execute correctly under the current build configuration.
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.SampleTest"
 */
class SampleTest {

    // ── JUnit 5 ──────────────────────────────────────────────────────────────

    @Test
    fun `basic assertion passes`() {
        val result = 2 + 2
        assertEquals(4, result)
    }

    @Test
    fun `string operations work`() {
        val label = "RUN"
        assertTrue(label.isNotBlank())
        assertEquals(3, label.length)
    }

    /** Local data class — mirrors the shape of SafetyStatus without importing production code. */
    private data class FakeSafetyStatus(
        val estop: Boolean,
        val freezeAll: Boolean,
        val estopCooldownMs: Long = 0L,
        val deadmanOk: Boolean,
        val commOk: Boolean
    )

    @Test
    fun `data class equality and copy`() {
        val a = FakeSafetyStatus(estop = false, freezeAll = false, deadmanOk = true, commOk = true)
        val b = FakeSafetyStatus(estop = false, freezeAll = false, deadmanOk = true, commOk = true)
        assertEquals(a, b)

        val withEstop = a.copy(estop = true)
        assertFalse(withEstop == a)
    }

    // ── MockK ────────────────────────────────────────────────────────────────

    /**
     * Simple interface used only inside this test — no production dependency.
     */
    interface CommandSender {
        fun sendStop(): Boolean
        fun sendGo(speedMs: Float): Boolean
    }

    @Test
    fun `mockk stubs return configured values`() {
        val sender = mockk<CommandSender>()
        every { sender.sendStop() } returns true
        every { sender.sendGo(any()) } returns false

        assertTrue(sender.sendStop())
        assertFalse(sender.sendGo(0.5f))
    }

    @Test
    fun `mockk verify call count`() {
        val sender = mockk<CommandSender>()
        every { sender.sendStop() } returns true

        sender.sendStop()
        sender.sendStop()

        verify(exactly = 2) { sender.sendStop() }
    }

    // ── Turbine (Kotlin Flow testing) ─────────────────────────────────────────

    @Test
    fun `StateFlow emits initial value`() = runTest {
        val flow = MutableStateFlow("IDLE")

        flow.test {
            assertEquals("IDLE", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `StateFlow emits updated values in order`() = runTest {
        val flow = MutableStateFlow("IDLE")

        flow.test {
            assertEquals("IDLE", awaitItem())

            flow.value = "RUNNING"
            assertEquals("RUNNING", awaitItem())

            flow.value = "STOPPED"
            assertEquals("STOPPED", awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `StateFlow deduplications — same value emitted twice yields one item`() = runTest {
        val flow = MutableStateFlow("IDLE")

        flow.test {
            assertEquals("IDLE", awaitItem())

            // Assigning the same value to a StateFlow must not emit a new item
            flow.value = "IDLE"

            flow.value = "DONE"
            assertEquals("DONE", awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
