package com.example.irmusicsync

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

class AudioReactiveAnalyzer(
    private val sampleRate: Int
) {
    companion object {
        private const val MAX_WINDOW_SIZE = 1024
        private const val MIN_WINDOW_SIZE = 256
        private const val HISTORY_LIMIT = 18
        private const val BPM_HISTORY_LIMIT = 8
        private const val MIN_BEAT_INTERVAL = 180L
        private const val MAX_BEAT_INTERVAL = 900L
        private const val EPSILON = 1e-6
    }

    private val loudnessHistory = mutableListOf<Double>()
    private val bassHistory = mutableListOf<Double>()
    private val fluxHistory = mutableListOf<Double>()
    private val beatHistory = mutableListOf<Long>()

    private var previousSpectrum = DoubleArray(MAX_WINDOW_SIZE / 2)
    private var smoothedLoudness = 0.0
    private var smoothedBass = 0.0
    private var smoothedMid = 0.0
    private var smoothedHigh = 0.0
    private var currentBpm = 120.0
    private var lastBeatTime = 0L

    fun reset() {
        loudnessHistory.clear()
        bassHistory.clear()
        fluxHistory.clear()
        beatHistory.clear()
        previousSpectrum = DoubleArray(MAX_WINDOW_SIZE / 2)
        smoothedLoudness = 0.0
        smoothedBass = 0.0
        smoothedMid = 0.0
        smoothedHigh = 0.0
        currentBpm = 120.0
        lastBeatTime = 0L
    }

    fun analyze(audioBuffer: ShortArray, length: Int, sensitivity: Int, currentTime: Long): AnalysisFrame? {
        val windowSize = selectWindowSize(length)
        if (windowSize < MIN_WINDOW_SIZE) {
            return null
        }

        val real = DoubleArray(windowSize)
        val imaginary = DoubleArray(windowSize)
        val startIndex = length - windowSize

        var rmsSum = 0.0
        for (index in 0 until windowSize) {
            val sample = audioBuffer[startIndex + index].toDouble() / Short.MAX_VALUE
            rmsSum += sample * sample

            val window = 0.5 - 0.5 * cos((2.0 * PI * index) / (windowSize - 1))
            real[index] = sample * window
        }

        fft(real, imaginary)

        val spectrum = buildMagnitudeSpectrum(real, imaginary)
        val loudness = sqrt(rmsSum / windowSize)
        val bassEnergy = averageBandEnergy(spectrum, windowSize, 45.0, 180.0)
        val midEnergy = averageBandEnergy(spectrum, windowSize, 180.0, 1600.0)
        val highEnergy = averageBandEnergy(spectrum, windowSize, 1600.0, 6000.0)
        val dominantFrequency = findDominantFrequency(spectrum, windowSize)
        val spectralFlux = calculateSpectralFlux(spectrum)

        smoothedLoudness = smooth(smoothedLoudness, loudness, 0.30)
        smoothedBass = smooth(smoothedBass, bassEnergy, 0.36)
        smoothedMid = smooth(smoothedMid, midEnergy, 0.28)
        smoothedHigh = smooth(smoothedHigh, highEnergy, 0.28)

        pushHistory(loudnessHistory, smoothedLoudness, HISTORY_LIMIT)
        pushHistory(bassHistory, smoothedBass, HISTORY_LIMIT)
        pushHistory(fluxHistory, spectralFlux, HISTORY_LIMIT)

        val averageLoudness = loudnessHistory.averageOrDefault()
        val averageBass = bassHistory.averageOrDefault()
        val averageFlux = fluxHistory.averageOrDefault()

        val sensitivityFactor = sensitivity / 100.0
        val loudnessRatio = smoothedLoudness / max(averageLoudness, EPSILON)
        val bassRatio = smoothedBass / max(averageBass, EPSILON)
        val fluxRatio = spectralFlux / max(averageFlux, EPSILON)

        val minGap = (320 - (110 * sensitivityFactor)).toLong().coerceIn(MIN_BEAT_INTERVAL, 320L)
        val beatDetected = loudnessHistory.size >= 6 &&
            averageLoudness > EPSILON &&
            averageBass > EPSILON &&
            averageFlux > EPSILON &&
            bassRatio > (1.28 - (0.18 * sensitivityFactor)) &&
            (loudnessRatio > (1.12 - (0.10 * sensitivityFactor)) ||
                fluxRatio > (1.14 - (0.10 * sensitivityFactor))) &&
            currentTime - lastBeatTime > minGap

        val beatStrength = (((bassRatio - 1.0) * 0.55) + ((fluxRatio - 1.0) * 0.30) + ((loudnessRatio - 1.0) * 0.15))
            .coerceIn(0.0, 1.0)

        if (beatDetected) {
            beatHistory.add(currentTime)
            trimToSize(beatHistory, BPM_HISTORY_LIMIT)
            averageBeatInterval()?.let { currentBpm = 60_000.0 / it }
            lastBeatTime = currentTime
        }

        val totalBandEnergy = smoothedBass + smoothedMid + smoothedHigh + EPSILON
        val lowShare = smoothedBass / totalBandEnergy
        val midShare = smoothedMid / totalBandEnergy
        val highShare = smoothedHigh / totalBandEnergy

        val averageInterval = averageBeatInterval() ?: (60_000.0 / currentBpm)
        val beatPhase = if (lastBeatTime == 0L) {
            0.0
        } else {
            ((currentTime - lastBeatTime) / averageInterval).coerceIn(0.0, 1.0)
        }

        val intensity = ((loudnessRatio - 0.92) / 0.7).coerceIn(0.0, 1.0)

        return AnalysisFrame(
            loudness = smoothedLoudness,
            dominantFrequency = dominantFrequency,
            bassEnergy = smoothedBass,
            midEnergy = smoothedMid,
            highEnergy = smoothedHigh,
            lowShare = lowShare,
            midShare = midShare,
            highShare = highShare,
            spectralFlux = spectralFlux,
            intensity = intensity,
            bpm = currentBpm.toInt(),
            beatStrength = beatStrength,
            beatPhase = beatPhase,
            isOnBeat = beatDetected
        )
    }

    private fun selectWindowSize(length: Int): Int {
        var windowSize = 1
        while (windowSize * 2 <= length && windowSize * 2 <= MAX_WINDOW_SIZE) {
            windowSize *= 2
        }
        return windowSize
    }

    private fun buildMagnitudeSpectrum(real: DoubleArray, imaginary: DoubleArray): DoubleArray {
        val halfSize = real.size / 2
        return DoubleArray(halfSize) { index ->
            hypot(real[index], imaginary[index])
        }
    }

    private fun averageBandEnergy(
        spectrum: DoubleArray,
        windowSize: Int,
        startFrequency: Double,
        endFrequency: Double
    ): Double {
        val startBin = frequencyToBin(startFrequency, windowSize).coerceIn(1, spectrum.lastIndex)
        val endBin = frequencyToBin(endFrequency, windowSize).coerceIn(startBin, spectrum.lastIndex)
        var total = 0.0

        for (bin in startBin..endBin) {
            total += spectrum[bin]
        }

        return total / (endBin - startBin + 1)
    }

    private fun frequencyToBin(frequency: Double, windowSize: Int): Int {
        return ((frequency * windowSize) / sampleRate).toInt()
    }

    private fun findDominantFrequency(spectrum: DoubleArray, windowSize: Int): Double {
        val startBin = frequencyToBin(55.0, windowSize).coerceAtLeast(1)
        val endBin = frequencyToBin(4_000.0, windowSize).coerceAtMost(spectrum.lastIndex)

        var peakBin = startBin
        var peakMagnitude = 0.0

        for (bin in startBin..endBin) {
            if (spectrum[bin] > peakMagnitude) {
                peakMagnitude = spectrum[bin]
                peakBin = bin
            }
        }

        return peakBin * sampleRate.toDouble() / windowSize
    }

    private fun calculateSpectralFlux(spectrum: DoubleArray): Double {
        var flux = 0.0

        for (index in spectrum.indices) {
            val positiveDifference = spectrum[index] - previousSpectrum[index]
            if (positiveDifference > 0.0) {
                flux += positiveDifference
            }
            previousSpectrum[index] = spectrum[index]
        }

        return flux / spectrum.size
    }

    private fun averageBeatInterval(): Double? {
        if (beatHistory.size < 2) {
            return null
        }

        val intervals = mutableListOf<Double>()
        for (index in 1 until beatHistory.size) {
            val interval = beatHistory[index] - beatHistory[index - 1]
            if (interval in MIN_BEAT_INTERVAL..MAX_BEAT_INTERVAL) {
                intervals.add(interval.toDouble())
            }
        }

        return intervals.takeIf { it.isNotEmpty() }?.average()
    }

    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        val size = real.size
        var bitReversedIndex = 0

        for (index in 1 until size) {
            var bit = size shr 1
            while (bitReversedIndex and bit != 0) {
                bitReversedIndex = bitReversedIndex xor bit
                bit = bit shr 1
            }
            bitReversedIndex = bitReversedIndex xor bit

            if (index < bitReversedIndex) {
                val realValue = real[index]
                real[index] = real[bitReversedIndex]
                real[bitReversedIndex] = realValue

                val imaginaryValue = imaginary[index]
                imaginary[index] = imaginary[bitReversedIndex]
                imaginary[bitReversedIndex] = imaginaryValue
            }
        }

        var blockSize = 2
        while (blockSize <= size) {
            val angle = -2.0 * PI / blockSize
            val phaseStepReal = cos(angle)
            val phaseStepImaginary = kotlin.math.sin(angle)

            for (offset in 0 until size step blockSize) {
                var phaseReal = 1.0
                var phaseImaginary = 0.0

                for (index in 0 until blockSize / 2) {
                    val evenIndex = offset + index
                    val oddIndex = evenIndex + blockSize / 2

                    val oddReal = (phaseReal * real[oddIndex]) - (phaseImaginary * imaginary[oddIndex])
                    val oddImaginary = (phaseReal * imaginary[oddIndex]) + (phaseImaginary * real[oddIndex])

                    real[oddIndex] = real[evenIndex] - oddReal
                    imaginary[oddIndex] = imaginary[evenIndex] - oddImaginary
                    real[evenIndex] += oddReal
                    imaginary[evenIndex] += oddImaginary

                    val nextPhaseReal = (phaseReal * phaseStepReal) - (phaseImaginary * phaseStepImaginary)
                    phaseImaginary = (phaseReal * phaseStepImaginary) + (phaseImaginary * phaseStepReal)
                    phaseReal = nextPhaseReal
                }
            }

            blockSize = blockSize shl 1
        }
    }

    private fun smooth(previous: Double, current: Double, factor: Double): Double {
        return previous + ((current - previous) * factor)
    }

    private fun pushHistory(history: MutableList<Double>, value: Double, limit: Int) {
        history.add(value)
        trimToSize(history, limit)
    }

    private fun <T> trimToSize(history: MutableList<T>, limit: Int) {
        while (history.size > limit) {
            history.removeAt(0)
        }
    }

    private fun List<Double>.averageOrDefault(): Double {
        return if (isEmpty()) 0.0 else average()
    }

    data class AnalysisFrame(
        val loudness: Double,
        val dominantFrequency: Double,
        val bassEnergy: Double,
        val midEnergy: Double,
        val highEnergy: Double,
        val lowShare: Double,
        val midShare: Double,
        val highShare: Double,
        val spectralFlux: Double,
        val intensity: Double,
        val bpm: Int,
        val beatStrength: Double,
        val beatPhase: Double,
        val isOnBeat: Boolean
    )
}
