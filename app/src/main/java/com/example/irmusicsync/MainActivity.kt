package com.example.irmusicsync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

        // Electronic music optimized parameters
        private const val BEAT_DETECTION_THRESHOLD = 0.15
        private const val MIN_BEAT_INTERVAL = 120L // Minimum 120ms between beats (500 BPM max)
        private const val MAX_BEAT_INTERVAL = 800L // Maximum 800ms between beats (75 BPM min)
        private const val ENERGY_SMOOTHING_FACTOR = 0.3
        private const val FREQUENCY_BANDS = 8
    }

    private lateinit var irManager: ConsumerIrManager
    private lateinit var irController: IRController
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private lateinit var mainHandler: Handler

    // UI Components
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusText: TextView
    private lateinit var frequencyText: TextView
    private lateinit var sensitivitySeekBar: SeekBar
    private lateinit var animationSpinner: Spinner
    private lateinit var colorModeSpinner: Spinner

    // Enhanced beat detection variables
    private var energyHistory = mutableListOf<Double>()
    private var beatHistory = mutableListOf<Long>()
    private var lastBeatTime = 0L
    private var currentBPM = 128.0 // Default EDM BPM
    private var beatStrength = 0.0
    private var isOnBeat = false
    private var beatPhase = 0.0 // 0.0 to 1.0, position within current beat

    // Advanced audio analysis
    private var frequencyBands = DoubleArray(FREQUENCY_BANDS)
    private var lastFrequencyBands = DoubleArray(FREQUENCY_BANDS)
    private var spectralFlux = 0.0
    private var spectralCentroid = 0.0
    private var spectralRolloff = 0.0

    // Color cycling and party effects
    private var colorCycleIndex = 0
    private var lastColorChangeTime = 0L
    private var currentColor: IRCommand.Color = IRCommand.Color.RED
    private var nextColor: IRCommand.Color = IRCommand.Color.BLUE
    private var colorTransitionProgress = 0.0
    private var partyColorSequence = mutableListOf<IRCommand.Color>()

    // Settings
    private var sensitivity = 75 // Optimized for electronic music
    private var animationMode = AnimationMode.ELECTRONIC_PARTY
    private var colorMode = ColorMode.PARTY_MODE
    private var isIRReady = false

    enum class AnimationMode {
        ELECTRONIC_PARTY,    // Optimized for electronic music
        BEAT_SYNC_RAPID,     // Rapid color changes on every beat
        BASS_DROP_SPECIAL,   // Special effects for bass drops
        FREQUENCY_SPLIT,     // Different colors for different frequencies
        ENERGY_PULSE,        // Pulsing based on energy levels
        STROBE_PARTY        // Party strobe effects
    }

    enum class ColorMode {
        PARTY_MODE,          // High energy party colors
        NEON_ELECTRONIC,     // Neon colors perfect for electronic music
        BASS_COLORS,         // Colors that respond to bass
        RAVE_MODE,           // Classic rave colors
        FESTIVAL_VIBES,      // Festival-style color palette
        CLUB_ATMOSPHERE      // Club lighting colors
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        initializeIR()
        setupListeners()
        initializePartyColors()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE)
        }
    }

    private fun initializeViews() {
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        statusText = findViewById(R.id.statusText)
        frequencyText = findViewById(R.id.frequencyText)
        sensitivitySeekBar = findViewById(R.id.sensitivitySeekBar)
        animationSpinner = findViewById(R.id.animationSpinner)
        colorModeSpinner = findViewById(R.id.colorModeSpinner)

        stopButton.isEnabled = false
        sensitivitySeekBar.progress = sensitivity

        setupSpinners()
    }

    private fun setupSpinners() {
        val animationModes = arrayOf(
            "Electronic Party", "Beat Sync Rapid", "Bass Drop Special",
            "Frequency Split", "Energy Pulse", "Strobe Party"
        )
        val animationAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, animationModes)
        animationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        animationSpinner.adapter = animationAdapter

        val colorModes = arrayOf(
            "Party Mode", "Neon Electronic", "Bass Colors",
            "Rave Mode", "Festival Vibes", "Club Atmosphere"
        )
        val colorAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, colorModes)
        colorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        colorModeSpinner.adapter = colorAdapter
    }

    private fun initializeIR() {
        mainHandler = Handler(Looper.getMainLooper())
        irManager = getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager
        irController = IRController(irManager)

        if (!irManager.hasIrEmitter()) {
            statusText.text = "❌ No IR blaster found on this device"
            startButton.isEnabled = false
            return
        }

        val carrierFreqs = irManager.carrierFrequencies
        var supportsFrequency = false

        for (range in carrierFreqs) {
            if (38000 >= range.minFrequency && 38000 <= range.maxFrequency) {
                supportsFrequency = true
                break
            }
        }

        if (supportsFrequency) {
            statusText.text = "🎉 Ready for PARTY MODE! Electronic music optimized"
            isIRReady = true
        } else {
            statusText.text = "❌ Device doesn't support 38kHz frequency"
            startButton.isEnabled = false
        }
    }

    private fun initializePartyColors() {
        partyColorSequence = when (colorMode) {
            ColorMode.PARTY_MODE -> mutableListOf(
                IRCommand.Color.RED, IRCommand.Color.BLUE, IRCommand.Color.GREEN,
                IRCommand.Color.PURPLE, IRCommand.Color.PINK, IRCommand.Color.ORANGE,
                IRCommand.Color.YELLOW, IRCommand.Color.WHITE, IRCommand.Color.TURQUOISE
            )
            ColorMode.NEON_ELECTRONIC -> mutableListOf(
                IRCommand.Color.PURPLE, IRCommand.Color.PINK, IRCommand.Color.TURQUOISE,
                IRCommand.Color.LIGHT_GREEN, IRCommand.Color.BLUE, IRCommand.Color.LIGHT_PURPLE
            )
            ColorMode.BASS_COLORS -> mutableListOf(
                IRCommand.Color.RED, IRCommand.Color.PURPLE, IRCommand.Color.BLUE,
                IRCommand.Color.ORANGE, IRCommand.Color.PINK
            )
            ColorMode.RAVE_MODE -> mutableListOf(
                IRCommand.Color.GREEN, IRCommand.Color.PURPLE, IRCommand.Color.YELLOW,
                IRCommand.Color.PINK, IRCommand.Color.TURQUOISE, IRCommand.Color.WHITE
            )
            ColorMode.FESTIVAL_VIBES -> mutableListOf(
                IRCommand.Color.ORANGE, IRCommand.Color.YELLOW, IRCommand.Color.PINK,
                IRCommand.Color.TURQUOISE, IRCommand.Color.LIGHT_GREEN, IRCommand.Color.PURPLE
            )
            ColorMode.CLUB_ATMOSPHERE -> mutableListOf(
                IRCommand.Color.BLUE, IRCommand.Color.PURPLE, IRCommand.Color.RED,
                IRCommand.Color.WHITE, IRCommand.Color.PINK
            )
        }
    }

    private fun setupListeners() {
        startButton.setOnClickListener { startPartyMode() }
        stopButton.setOnClickListener { stopPartyMode() }

        sensitivitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sensitivity = progress
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        animationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                animationMode = AnimationMode.values()[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        colorModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                colorMode = ColorMode.values()[position]
                initializePartyColors()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun startPartyMode() {
        if (!isIRReady) {
            Toast.makeText(this, "IR Blaster not ready", Toast.LENGTH_SHORT).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE * 2)

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Toast.makeText(this, "Failed to initialize audio recording", Toast.LENGTH_LONG).show()
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            startButton.isEnabled = false
            stopButton.isEnabled = true

            statusText.text = "🎉 PARTY MODE ACTIVE! Drop the bass!"

            // Reset variables for new session
            energyHistory.clear()
            beatHistory.clear()
            lastBeatTime = System.currentTimeMillis()
            colorCycleIndex = 0

            recordingThread = Thread { processElectronicMusicAudio() }
            recordingThread?.start()

        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start party mode: ${e.message}",
                Toast.LENGTH_LONG).show()
            resetButtons()
        }
    }

    private fun processElectronicMusicAudio() {
        val audioBuffer = ShortArray(BUFFER_SIZE)
        var sampleCount = 0

        while (isRecording && audioRecord != null) {
            try {
                val bytesRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0

                if (bytesRead > 0) {
                    sampleCount++

                    // Process audio more frequently for better beat detection
                    if (sampleCount % 2 == 0) {
                        val audioAnalysis = analyzeElectronicMusic(audioBuffer, bytesRead)

                        // Advanced beat detection
                        val currentTime = System.currentTimeMillis()
                        detectElectronicBeats(audioAnalysis, currentTime)

                        // Update beat phase for smooth transitions
                        updateBeatPhase(currentTime)

                        // Handle color changes based on beats and music analysis
                        handlePartyColorLogic(audioAnalysis, currentTime)

                        // Update UI
                        mainHandler.post {
                            updatePartyUI(audioAnalysis)
                        }
                    }
                }

                Thread.sleep(2) // Higher frequency processing

            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                Thread.sleep(20)
            }
        }
    }

    private fun analyzeElectronicMusic(audioBuffer: ShortArray, length: Int): EnhancedAudioAnalysis {
        // Calculate RMS energy
        var sumSquares = 0.0
        for (i in 0 until length) {
            val sample = audioBuffer[i].toDouble()
            sumSquares += sample * sample
        }
        val energy = sqrt(sumSquares / length)

        // Calculate frequency bands for electronic music
        calculateFrequencyBands(audioBuffer, length)

        // Calculate spectral features
        spectralFlux = calculateSpectralFlux()
        spectralCentroid = calculateSpectralCentroid()
        spectralRolloff = calculateSpectralRolloff()

        // Detect dominant frequency for electronic music
        val dominantFreq = detectDominantFrequency(audioBuffer, length)

        // Calculate bass energy (important for electronic music)
        val bassEnergy = frequencyBands[0] + frequencyBands[1] // Low frequency bands

        // Calculate high frequency energy for synths and hi-hats
        val highEnergy = frequencyBands[6] + frequencyBands[7] // High frequency bands

        return EnhancedAudioAnalysis(
            energy, dominantFreq, spectralFlux, spectralCentroid,
            spectralRolloff, bassEnergy, highEnergy, frequencyBands.clone()
        )
    }

    private fun calculateFrequencyBands(audioBuffer: ShortArray, length: Int) {
        lastFrequencyBands = frequencyBands.clone()
        frequencyBands.fill(0.0)

        val bandSize = length / FREQUENCY_BANDS

        for (band in 0 until FREQUENCY_BANDS) {
            var bandEnergy = 0.0
            val startIdx = band * bandSize
            val endIdx = min((band + 1) * bandSize, length)

            for (i in startIdx until endIdx) {
                val sample = audioBuffer[i].toDouble()
                bandEnergy += sample * sample
            }

            frequencyBands[band] = sqrt(bandEnergy / (endIdx - startIdx))
        }
    }

    private fun calculateSpectralFlux(): Double {
        var flux = 0.0
        for (i in frequencyBands.indices) {
            val diff = frequencyBands[i] - lastFrequencyBands[i]
            if (diff > 0) flux += diff
        }
        return flux
    }

    private fun calculateSpectralCentroid(): Double {
        var weightedSum = 0.0
        var magnitudeSum = 0.0

        for (i in frequencyBands.indices) {
            val frequency = (i + 1) * (SAMPLE_RATE / (2 * FREQUENCY_BANDS))
            weightedSum += frequency * frequencyBands[i]
            magnitudeSum += frequencyBands[i]
        }

        return if (magnitudeSum > 0) weightedSum / magnitudeSum else 0.0
    }

    private fun calculateSpectralRolloff(): Double {
        val magnitudeSum = frequencyBands.sum()
        val threshold = magnitudeSum * 0.85

        var cumulativeSum = 0.0
        for (i in frequencyBands.indices) {
            cumulativeSum += frequencyBands[i]
            if (cumulativeSum >= threshold) {
                return (i + 1) * (SAMPLE_RATE / (2 * FREQUENCY_BANDS)).toDouble()
            }
        }
        return SAMPLE_RATE / 2.0
    }

    private fun detectElectronicBeats(analysis: EnhancedAudioAnalysis, currentTime: Long) {
        // Add current energy to history
        energyHistory.add(analysis.energy)
        if (energyHistory.size > 43) { // ~1 second at 44.1kHz/1024 samples
            energyHistory.removeAt(0)
        }

        if (energyHistory.size < 10) return

        // Calculate local energy average
        val localEnergyAvg = energyHistory.takeLast(10).average()
        val overallEnergyAvg = energyHistory.average()

        // Enhanced beat detection for electronic music
        val energyRatio = analysis.energy / (overallEnergyAvg + 1.0)
        val spectralFluxThreshold = spectralFlux > (sensitivity / 100.0) * 1000
        val bassKick = analysis.bassEnergy > localEnergyAvg * 1.5

        // Combine multiple detection methods
        val isBeat = (energyRatio > (1.0 + BEAT_DETECTION_THRESHOLD)) &&
                (spectralFluxThreshold || bassKick) &&
                (currentTime - lastBeatTime > MIN_BEAT_INTERVAL)

        if (isBeat) {
            // Update beat tracking
            beatHistory.add(currentTime)
            if (beatHistory.size > 16) beatHistory.removeAt(0) // Keep last 16 beats

            // Calculate BPM from recent beats
            if (beatHistory.size >= 4) {
                val intervals = mutableListOf<Long>()
                for (i in 1 until beatHistory.size) {
                    intervals.add(beatHistory[i] - beatHistory[i-1])
                }
                val avgInterval = intervals.filter { it in MIN_BEAT_INTERVAL..MAX_BEAT_INTERVAL }.average()
                currentBPM = 60000.0 / avgInterval
            }

            beatStrength = min(1.0, energyRatio - 1.0)
            lastBeatTime = currentTime
            isOnBeat = true
        } else {
            isOnBeat = false
        }
    }

    private fun updateBeatPhase(currentTime: Long) {
        if (beatHistory.size >= 2) {
            val avgBeatInterval = if (beatHistory.size >= 4) {
                val intervals = mutableListOf<Long>()
                for (i in 1 until beatHistory.size) {
                    intervals.add(beatHistory[i] - beatHistory[i-1])
                }
                intervals.filter { it in MIN_BEAT_INTERVAL..MAX_BEAT_INTERVAL }.average()
            } else {
                60000.0 / currentBPM
            }

            val timeSinceLastBeat = currentTime - lastBeatTime
            beatPhase = (timeSinceLastBeat / avgBeatInterval).coerceIn(0.0, 1.0)
        }
    }

    private fun handlePartyColorLogic(analysis: EnhancedAudioAnalysis, currentTime: Long) {
        when (animationMode) {
            AnimationMode.ELECTRONIC_PARTY -> {
                if (isOnBeat || (currentTime - lastColorChangeTime > 250 && analysis.energy > 5000)) {
                    changeToNextPartyColor()
                    lastColorChangeTime = currentTime
                }
            }

            AnimationMode.BEAT_SYNC_RAPID -> {
                if (isOnBeat) {
                    changeToNextPartyColor()
                    lastColorChangeTime = currentTime
                }
            }

            AnimationMode.BASS_DROP_SPECIAL -> {
                if (analysis.bassEnergy > analysis.energy * 0.6 && isOnBeat) {
                    // Special bass drop effect
                    currentColor = if (beatStrength > 0.7) {
                        IRCommand.Color.WHITE
                    } else {
                        partyColorSequence[colorCycleIndex % partyColorSequence.size]
                    }
                    sendColorCommand(currentColor)
                    changeToNextPartyColor()
                    lastColorChangeTime = currentTime
                }
            }

            AnimationMode.FREQUENCY_SPLIT -> {
                val color = when {
                    analysis.bassEnergy > analysis.energy * 0.4 -> IRCommand.Color.RED
                    analysis.frequencyBands[2] > analysis.frequencyBands[1] * 1.2 -> IRCommand.Color.BLUE
                    analysis.frequencyBands[4] > analysis.frequencyBands[3] * 1.2 -> IRCommand.Color.GREEN
                    analysis.highEnergy > analysis.energy * 0.3 -> IRCommand.Color.WHITE
                    else -> IRCommand.Color.PURPLE
                }
                if (color != currentColor && (isOnBeat || currentTime - lastColorChangeTime > 300)) {
                    currentColor = color
                    sendColorCommand(currentColor)
                    lastColorChangeTime = currentTime
                }
            }

            AnimationMode.ENERGY_PULSE -> {
                if (currentTime - lastColorChangeTime > 150) {
                    val energyLevel = (analysis.energy / 10000.0).coerceIn(0.0, 1.0)
                    val colorIndex = (energyLevel * partyColorSequence.size).toInt()
                        .coerceIn(0, partyColorSequence.size - 1)

                    val newColor = partyColorSequence[colorIndex]
                    if (newColor != currentColor) {
                        currentColor = newColor
                        sendColorCommand(currentColor)
                        lastColorChangeTime = currentTime
                    }
                }
            }

            AnimationMode.STROBE_PARTY -> {
                if (isOnBeat && beatStrength > 0.5) {
                    // Rapid strobe effect on strong beats
                    currentColor = if (colorCycleIndex % 2 == 0) {
                        IRCommand.Color.WHITE
                    } else {
                        partyColorSequence[colorCycleIndex / 2 % partyColorSequence.size]
                    }
                    sendColorCommand(currentColor)
                    colorCycleIndex++
                    lastColorChangeTime = currentTime
                }
            }
        }
    }

    private fun changeToNextPartyColor() {
        colorCycleIndex++
        currentColor = partyColorSequence[colorCycleIndex % partyColorSequence.size]
        sendColorCommand(currentColor)
    }

    private fun sendColorCommand(color: IRCommand.Color) {
        val command = IRCommand().apply { this.color = color }
        irController.sendCommand(command)
    }

    private fun detectDominantFrequency(audioBuffer: ShortArray, length: Int): Double {
        // Simple autocorrelation for dominant frequency
        val maxDelay = min(length / 4, SAMPLE_RATE / 50)
        var bestDelay = 0
        var maxCorrelation = 0.0

        for (delay in 20 until maxDelay) {
            var correlation = 0.0
            var count = 0

            for (i in delay until length) {
                correlation += audioBuffer[i].toDouble() * audioBuffer[i - delay].toDouble()
                count++
            }

            if (count > 0) {
                correlation /= count
                if (correlation > maxCorrelation) {
                    maxCorrelation = correlation
                    bestDelay = delay
                }
            }
        }

        return if (bestDelay > 0) {
            SAMPLE_RATE.toDouble() / bestDelay
        } else {
            0.0
        }
    }

    private fun updatePartyUI(analysis: EnhancedAudioAnalysis) {
        val energyPercent = ((analysis.energy / 15000.0) * 100).toInt().coerceIn(0, 100)
        val bassPercent = ((analysis.bassEnergy / 10000.0) * 100).toInt().coerceIn(0, 100)
        val beatIndicator = if (isOnBeat) "🔥" else "🎵"

        statusText.text = "$beatIndicator PARTY MODE - ${currentColor.name} | BPM: ${currentBPM.toInt()}"

        frequencyText.text = String.format(
            "Energy: %d%% | Bass: %d%% | Beat Strength: %.1f | Phase: %.2f",
            energyPercent,
            bassPercent,
            beatStrength,
            beatPhase
        )
    }

    private fun stopPartyMode() {
        isRecording = false

        audioRecord?.apply {
            if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                stop()
            }
            release()
        }
        audioRecord = null

        recordingThread?.let { thread ->
            try {
                thread.join(1000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        resetButtons()
        statusText.text = "🎉 Party mode stopped - Ready to drop the bass again!"
        frequencyText.text = "Ready for the next electronic adventure"
    }

    private fun resetButtons() {
        startButton.isEnabled = true
        stopButton.isEnabled = false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "🎉 Ready to party with microphone access!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "❌ Microphone permission required for party mode",
                    Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        stopPartyMode()
        super.onDestroy()
    }

    // Enhanced data class for electronic music analysis
    data class EnhancedAudioAnalysis(
        val energy: Double,
        val dominantFrequency: Double,
        val spectralFlux: Double,
        val spectralCentroid: Double,
        val spectralRolloff: Double,
        val bassEnergy: Double,
        val highEnergy: Double,
        val frequencyBands: DoubleArray
    )
}