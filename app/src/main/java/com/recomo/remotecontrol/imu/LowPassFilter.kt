package com.recomo.remotecontrol.imu

/**
 * Single-pole IIR low-pass filter.
 *
 * @param cutoffHz  desired cutoff frequency (e.g. 2.0–3.0 Hz)
 * @param sampleHz  expected sensor sample rate (e.g. 100 Hz)
 */
class LowPassFilter(cutoffHz: Float, sampleHz: Float) {

    /** Smoothing coefficient — higher = more responsive, lower = smoother. */
    private val alpha: Float = 1.0f - Math.exp(
        -2.0 * Math.PI * cutoffHz / sampleHz
    ).toFloat()

    private var value = 0f
    private var initialized = false

    fun filter(input: Float): Float {
        if (!initialized) { value = input; initialized = true; return input }
        value += alpha * (input - value)
        return value
    }

    fun reset() { initialized = false }

    /** Current filtered value without advancing the filter. */
    fun current(): Float = value
}
