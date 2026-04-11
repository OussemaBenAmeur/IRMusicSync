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
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private const val SUPPORTED_CARRIER_FREQUENCY = 38_000
        private const val SAMPLE_RATE = 44_100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

        // Electronic music optimized parameters
        private const val COLOR_CHANGE_COOLDOWN_MS = 180L
        private const val FLOW_UPDATE_INTERVAL_MS = 320L
        private const val ERROR_RETRY_DELAY_MS = 20L
    }

    private lateinit var irManager: ConsumerIrManager
    private lateinit var irController: IRController
    private val analyzer = AudioReactiveAnalyzer(SAMPLE_RATE)

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private lateinit var mainHandler: Handler

    // UI Components
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusText: TextView
    private lateinit var analysisText: TextView
    private lateinit var modeHintText: TextView
    private lateinit var sensitivitySeekBar: SeekBar
    private lateinit var modeSpinner: Spinner
    private lateinit var paletteSpinner: Spinner

    // Color cycling and party effects
    private var paletteIndex = 0
    private var lastColorChangeTime = 0L
    private var currentColor = IRCommand.Color.RED
    private var lastSentColor: IRCommand.Color? = null

    // Settings
    private var sensitivity = 62
    private var syncMode = SyncMode.BEAT_PULSE
    private var palette = ColorPalette.NEON
    private var isIRReady = false

    enum class SyncMode(val label: String, val recommendation: String) {
        BEAT_PULSE("Beat Pulse", "house, disco edits, fast pop"),
        BASS_DRIVE("Bass Drive", "trap, hip-hop, dubstep"),
        COLOR_FLOW("Color Flow", "synthwave, melodic techno, ambient")
    }

    enum class ColorPalette(val label: String, val colors: List<IRCommand.Color>) {
        NEON("Neon", IRCommand.NEON_SEQUENCE),
        SUNSET("Sunset", IRCommand.SUNSET_SEQUENCE),
        ICE("Ice", IRCommand.ICE_SEQUENCE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mainHandler = Handler(Looper.getMainLooper())

        initializeViews()
        initializeIR()
        setupListeners()
        refreshPalette()
        updateModeHint()
        requestAudioPermissionIfNeeded()
    }

    private fun initializeViews() {
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        statusText = findViewById(R.id.statusText)
        analysisText = findViewById(R.id.analysisText)
        modeHintText = findViewById(R.id.modeHintText)
        sensitivitySeekBar = findViewById(R.id.sensitivitySeekBar)
        modeSpinner = findViewById(R.id.modeSpinner)
        paletteSpinner = findViewById(R.id.paletteSpinner)

        stopButton.isEnabled = false
        sensitivitySeekBar.progress = sensitivity

        setupSpinners()
    }

    private fun setupSpinners() {
        modeSpinner.adapter = createSpinnerAdapter(SyncMode.values().map { it.label })
        paletteSpinner.adapter = createSpinnerAdapter(ColorPalette.values().map { it.label })
    }

    private fun createSpinnerAdapter(items: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun initializeIR() {
        irManager = getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager
        irController = IRController(irManager)

        if (!irManager.hasIrEmitter()) {
            statusText.text = getString(R.string.status_no_ir)
            startButton.isEnabled = false
            return
        }

        if (supportsCarrierFrequency()) {
            statusText.text = getString(R.string.status_ready)
            isIRReady = true
        } else {
            statusText.text = getString(R.string.status_unsupported_frequency)
            startButton.isEnabled = false
        }
    }

    private fun supportsCarrierFrequency(): Boolean {
        val carrierFrequencies = irManager.carrierFrequencies ?: return false
        return carrierFrequencies.any { range ->
            SUPPORTED_CARRIER_FREQUENCY in range.minFrequency..range.maxFrequency
        }
    }

    private fun refreshPalette() {
        if (currentColor !in palette.colors) {
            currentColor = palette.colors.first()
            paletteIndex = 0
            return
        }

        paletteIndex = palette.colors.indexOf(currentColor).coerceAtLeast(0)
    }

    private fun requestAudioPermissionIfNeeded() {
        if (hasAudioPermission()) {
            return
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupListeners() {
        startButton.setOnClickListener { startSync() }
        stopButton.setOnClickListener { stopSync() }

        sensitivitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sensitivity = progress.coerceIn(10, 100)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                syncMode = SyncMode.values()[position]
                updateModeHint()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        paletteSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                palette = ColorPalette.values()[position]
                refreshPalette()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun updateModeHint() {
        modeHintText.text = getString(R.string.mode_recommendation, syncMode.recommendation)
    }

    private fun startSync() {
        if (!isIRReady) {
            showShortToast(R.string.toast_ir_not_ready)
            return
        }

        if (!hasAudioPermission()) {
            showShortToast(R.string.toast_microphone_permission_required)
            return
        }

        val recorder = createAudioRecord()
        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
            showLongToast(R.string.toast_audio_init_failed)
            recorder?.release()
            return
        }

        try {
            audioRecord = recorder
            recorder.startRecording()
            isRecording = true
            analyzer.reset()
            lastSentColor = null

            startButton.isEnabled = false
            stopButton.isEnabled = true
            statusText.text = getString(R.string.status_active)
            analysisText.text = getString(R.string.analysis_waiting)

            recordingThread = Thread(::processAudioLoop, "irmusicsync-audio")
            recordingThread?.start()
        } catch (exception: Exception) {
            audioRecord?.release()
            audioRecord = null
            showLongToast(getString(R.string.toast_start_failed, exception.message ?: "unknown error"))
            resetButtons()
        }
    }

    private fun createAudioRecord(): AudioRecord? {
        return try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                BUFFER_SIZE * 2
            )
        } catch (exception: IllegalArgumentException) {
            null
        }
    }

    private fun processAudioLoop() {
        val audioBuffer = ShortArray(BUFFER_SIZE)

        while (isRecording) {
            val recorder = audioRecord ?: break

            try {
                val bytesRead = recorder.read(audioBuffer, 0, audioBuffer.size)
                if (bytesRead <= 0) {
                    continue
                }

                val currentTime = System.currentTimeMillis()
                val analysis = analyzer.analyze(audioBuffer, bytesRead, sensitivity, currentTime) ?: continue

                handleLightResponse(analysis, currentTime)

                mainHandler.post {
                    updateUi(analysis)
                }
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (exception: Exception) {
                try {
                    Thread.sleep(ERROR_RETRY_DELAY_MS)
                } catch (interruptedException: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
    }

    private fun handleLightResponse(analysis: AudioReactiveAnalyzer.AnalysisFrame, currentTime: Long) {
        when (syncMode) {
            SyncMode.BEAT_PULSE -> handleBeatPulse(analysis, currentTime)
            SyncMode.BASS_DRIVE -> handleBassDrive(analysis, currentTime)
            SyncMode.COLOR_FLOW -> handleColorFlow(analysis, currentTime)
        }
    }

    private fun handleBeatPulse(analysis: AudioReactiveAnalyzer.AnalysisFrame, currentTime: Long) {
        if (!analysis.isOnBeat || currentTime - lastColorChangeTime < COLOR_CHANGE_COOLDOWN_MS) {
            return
        }

        val nextColor = if (analysis.beatStrength > 0.78) {
            IRCommand.Color.WHITE
        } else {
            advancePaletteColor()
        }

        sendColorIfChanged(nextColor)
        lastColorChangeTime = currentTime
    }

    private fun handleBassDrive(analysis: AudioReactiveAnalyzer.AnalysisFrame, currentTime: Long) {
        if (currentTime - lastColorChangeTime < COLOR_CHANGE_COOLDOWN_MS) {
            return
        }

        val heat = ((analysis.lowShare * 0.7) + (analysis.intensity * 0.3)).coerceIn(0.0, 1.0)
        val colorIndex = (heat * (palette.colors.lastIndex)).roundToInt().coerceIn(0, palette.colors.lastIndex)
        val candidate = if (analysis.isOnBeat && analysis.beatStrength > 0.86) {
            IRCommand.Color.WHITE
        } else {
            palette.colors[colorIndex]
        }

        sendColorIfChanged(candidate)
        lastColorChangeTime = currentTime
    }

    private fun handleColorFlow(analysis: AudioReactiveAnalyzer.AnalysisFrame, currentTime: Long) {
        if (currentTime - lastColorChangeTime < FLOW_UPDATE_INTERVAL_MS) {
            return
        }

        val bandAnchor = when {
            analysis.lowShare >= analysis.midShare && analysis.lowShare >= analysis.highShare -> 0.18
            analysis.midShare >= analysis.highShare -> 0.52
            else -> 0.84
        }
        val movementOffset = (analysis.intensity - 0.5) * 0.22
        val palettePosition = (bandAnchor + movementOffset).coerceIn(0.0, 1.0)
        val colorIndex = (palettePosition * palette.colors.lastIndex).roundToInt().coerceIn(0, palette.colors.lastIndex)
        val candidate = palette.colors[colorIndex]

        sendColorIfChanged(candidate)
        lastColorChangeTime = currentTime
    }

    private fun advancePaletteColor(): IRCommand.Color {
        val colors = palette.colors
        paletteIndex = (paletteIndex + 1) % colors.size
        return colors[paletteIndex]
    }

    private fun sendColorIfChanged(color: IRCommand.Color) {
        if (color == lastSentColor) {
            return
        }

        currentColor = color
        lastSentColor = color
        irController.sendCommand(IRCommand(color))
    }

    private fun updateUi(analysis: AudioReactiveAnalyzer.AnalysisFrame) {
        val beatLabel = if (analysis.isOnBeat) {
            getString(R.string.beat_state_hit)
        } else {
            getString(R.string.beat_state_listening)
        }

        val bpm = analysis.bpm.coerceAtLeast(0)
        statusText.text = getString(
            R.string.status_running,
            beatLabel,
            syncMode.label,
            bpm
        )

        val lowPercent = (analysis.lowShare * 100).roundToInt()
        val midPercent = (analysis.midShare * 100).roundToInt()
        val highPercent = (analysis.highShare * 100).roundToInt()
        val dominantFrequency = analysis.dominantFrequency.roundToInt().absoluteValue

        analysisText.text = getString(
            R.string.analysis_metrics,
            lowPercent,
            midPercent,
            highPercent,
            dominantFrequency
        )
    }

    private fun stopSync() {
        isRecording = false
        releaseAudioRecord()
        joinRecordingThread()

        if (!::startButton.isInitialized) {
            return
        }

        resetButtons()
        statusText.text = getString(R.string.status_stopped)
        analysisText.text = getString(R.string.analysis_idle)
    }

    private fun releaseAudioRecord() {
        audioRecord?.apply {
            if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                stop()
            }
            release()
        }
        audioRecord = null
    }

    private fun joinRecordingThread() {
        recordingThread?.let { thread ->
            try {
                thread.join(1_000)
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        recordingThread = null
    }

    private fun resetButtons() {
        startButton.isEnabled = isIRReady
        stopButton.isEnabled = false
    }

    private fun showShortToast(messageResId: Int) {
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show()
    }

    private fun showLongToast(messageResId: Int) {
        Toast.makeText(this, messageResId, Toast.LENGTH_LONG).show()
    }

    private fun showLongToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != PERMISSION_REQUEST_CODE) {
            return
        }

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showShortToast(R.string.toast_permission_granted)
            return
        }

        showLongToast(R.string.toast_permission_denied)
        finish()
    }

    override fun onDestroy() {
        stopSync()
        super.onDestroy()
    }
}
