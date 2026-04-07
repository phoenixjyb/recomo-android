package com.recomo.remotecontrol.imu

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LowPassFilterTest {

    @Test
    fun `initial output equals first input`() {
        val filter = LowPassFilter(cutoffHz = 3.0f, sampleHz = 100f)
        val result = filter.filter(5.0f)
        assertEquals(5.0f, result, 1e-6f)
    }

    @Test
    fun `step response converges to constant input`() {
        val filter = LowPassFilter(cutoffHz = 3.0f, sampleHz = 100f)
        // Feed constant value 10.0 for many samples
        var output = 0f
        for (i in 0 until 500) {
            output = filter.filter(10.0f)
        }
        // After 500 samples at 100Hz (5 seconds), should be very close to 10.0
        assertEquals(10.0f, output, 0.01f)
    }

    @Test
    fun `step response is monotonically increasing for positive step`() {
        val filter = LowPassFilter(cutoffHz = 2.5f, sampleHz = 100f)
        filter.filter(0.0f) // initialize at 0
        var prev = 0.0f
        for (i in 0 until 100) {
            val out = filter.filter(1.0f)
            assertTrue(out >= prev, "Output should be monotonically increasing: prev=$prev, out=$out at step $i")
            prev = out
        }
    }

    @Test
    fun `high frequency input is attenuated`() {
        val filter = LowPassFilter(cutoffHz = 2.0f, sampleHz = 100f)
        // Alternate between +1 and -1 at 50Hz (well above 2Hz cutoff)
        var maxAbsOutput = 0f
        for (i in 0 until 200) {
            val input = if (i % 2 == 0) 1.0f else -1.0f
            val output = filter.filter(input)
            if (i > 20) { // skip initial transient
                maxAbsOutput = maxOf(maxAbsOutput, kotlin.math.abs(output))
            }
        }
        // At 50Hz with a 2Hz cutoff, output amplitude should be heavily attenuated
        assertTrue(maxAbsOutput < 0.3f, "50Hz signal should be attenuated below 0.3, was $maxAbsOutput")
    }

    @Test
    fun `reset causes filter to reinitialize from next input`() {
        val filter = LowPassFilter(cutoffHz = 3.0f, sampleHz = 100f)
        // Drive to 10
        for (i in 0 until 500) filter.filter(10.0f)
        assertEquals(10.0f, filter.current(), 0.01f)

        // Reset and feed 20 — first output should be exactly 20 (reinitialized)
        filter.reset()
        val afterReset = filter.filter(20.0f)
        assertEquals(20.0f, afterReset, 1e-6f)
    }

    @Test
    fun `current returns last filtered value without advancing`() {
        val filter = LowPassFilter(cutoffHz = 3.0f, sampleHz = 100f)
        filter.filter(5.0f)
        filter.filter(5.0f)
        val c1 = filter.current()
        val c2 = filter.current()
        assertEquals(c1, c2, 1e-6f, "current() should not change the filter state")
    }

    @Test
    fun `alpha coefficient is in valid range`() {
        // alpha = 1 - exp(-2*pi*cutoff/sample)
        // For cutoff=3, sample=100: alpha = 1 - exp(-0.1885) ~ 0.172
        // It should always be in (0, 1) for reasonable inputs
        val cutoffs = floatArrayOf(0.5f, 1.0f, 2.0f, 5.0f, 10.0f, 20.0f)
        val sampleRate = 100f
        for (cutoff in cutoffs) {
            val filter = LowPassFilter(cutoff, sampleRate)
            // Feed a step: first output = input, second output = input * alpha + (1-alpha) * input = input
            // Actually test by feeding 0 then 1:
            filter.filter(0.0f)
            val second = filter.filter(1.0f)
            // second = 0 + alpha * (1 - 0) = alpha
            assertTrue(second > 0f, "Alpha should be > 0 for cutoff=$cutoff")
            assertTrue(second < 1f, "Alpha should be < 1 for cutoff=$cutoff")
        }
    }

    @Test
    fun `higher cutoff means faster convergence`() {
        val filterSlow = LowPassFilter(cutoffHz = 1.0f, sampleHz = 100f)
        val filterFast = LowPassFilter(cutoffHz = 10.0f, sampleHz = 100f)

        filterSlow.filter(0.0f)
        filterFast.filter(0.0f)

        // After 10 samples of step input
        var slowOut = 0f
        var fastOut = 0f
        for (i in 0 until 10) {
            slowOut = filterSlow.filter(1.0f)
            fastOut = filterFast.filter(1.0f)
        }
        assertTrue(fastOut > slowOut, "Higher cutoff should converge faster: fast=$fastOut, slow=$slowOut")
    }
}
