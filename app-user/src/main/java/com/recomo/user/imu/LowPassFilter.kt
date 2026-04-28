package com.recomo.user.imu

/**
 * Single-pole IIR low-pass filter. Ported as-is from `:app`
 * `com.recomo.remotecontrol.imu.LowPassFilter` — no behavioural changes.
 *
 * @param cutoffHz desired cutoff frequency (e.g. 2.0–3.0 Hz)
 * @param sampleHz expected sensor sample rate (e.g. 100 Hz)
 */
class LowPassFilter(cutoffHz: Float, sampleHz: Float) {

    private val alpha: Float = 1.0f - Math.exp(
        -2.0 * Math.PI * cutoffHz / sampleHz
    ).toFloat()

    private var value = 0f
    private var initialized = false

    fun filter(input: Float): Float {
        if (!initialized) {
            value = input
            initialized = true
            return input
        }
        value += alpha * (input - value)
        return value
    }

    fun reset() {
        initialized = false
    }

    fun current(): Float = value
}
