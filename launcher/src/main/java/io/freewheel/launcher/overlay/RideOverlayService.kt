package io.freewheel.launcher.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import io.freewheel.launcher.VeloLauncherApp
import io.freewheel.launcher.bridge.BridgeConnectionManager
import io.freewheel.launcher.data.RideSummary
import io.freewheel.launcher.data.Workout
import io.freewheel.launcher.data.WorkoutSegment
import io.freewheel.ucb.RidePhysics
import io.freewheel.fit.PowerTarget
import io.freewheel.fit.RideStats
import io.freewheel.fit.VeloFitnessClient
import kotlinx.coroutines.*

class RideOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private lateinit var bridge: BridgeConnectionManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var fitnessClient: VeloFitnessClient

    // Ride state
    private var rideStartTime = 0L
    private var lastSampleTime = 0L
    private var power = 0
    private var rpm = 0
    private var resistance = 0
    private var heartRate = 0
    private var calories = 0
    private var elapsedSeconds = 0
    private var ridePowerSum = 0L
    private var rideRpmSum = 0L
    private var rideResistanceSum = 0L
    private var rideSampleCount = 0
    private var rideMaxPower = 0
    private var distanceMiles = 0f
    private var speedMph = 0f
    private var connected = false
    private var powerHistory = mutableListOf<Int>()

    // Workout info
    private var workoutName = "Free Ride"
    private var workoutId: String? = null
    private var segments: List<WorkoutSegment> = emptyList()
    private var difficultyMultiplier = 1.0f

    // Views
    private var powerText: TextView? = null
    private var cadenceText: TextView? = null
    private var resistanceText: TextView? = null
    private var heartText: TextView? = null
    private var caloriesText: TextView? = null
    private var workoutLabel: TextView? = null
    private var segmentLabel: TextView? = null
    private var elapsedLabel: TextView? = null
    private var connectionDot: View? = null
    private var hillChartView: HillChartView? = null
    private var effortBarView: EffortBarView? = null
    private var effortBarOverlay: View? = null
    private var difficultyLabel: TextView? = null
    private var overlayExpanded = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        bridge = VeloLauncherApp.get(this).bridgeConnectionManager
        fitnessClient = VeloFitnessClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Hide status bar AND navigation bar so user can't accidentally leave
                try {
                    android.provider.Settings.Global.putString(
                        contentResolver, "policy_control", "immersive.full=*"
                    )
                } catch (_: Exception) {}
                val workoutJson = intent.getStringExtra("workout_json")
                val workout = workoutJson?.let {
                    try { Workout.fromJson(it) } catch (_: Exception) { null }
                }
                if (workout != null) {
                    workoutName = workout.name
                    workoutId = workout.id
                    segments = workout.segments
                }

                startForeground(NOTIFICATION_ID, createNotification())
                createOverlay()
                startRide()
            }
            ACTION_STOP -> {
                stopRide()
                stopSelf()
            }
            ACTION_TOGGLE -> {
                toggleOverlay()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "velolauncher_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Ride Overlay", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, RideOverlayService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return Notification.Builder(this, channelId)
            .setContentTitle("Ride Active")
            .setContentText("Workout overlay running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(Notification.Action.Builder(null, "End Ride", stopPending).build())
            .setOngoing(true)
            .build()
    }


    @Suppress("DEPRECATION")
    private fun createOverlay() {
        overlayView = buildOverlayView()

        val dp = resources.displayMetrics.density
        val sideMargin = (48 * dp).toInt()  // leave room for Netflix back arrow etc.
        val screenWidth = resources.displayMetrics.widthPixels
        val params = WindowManager.LayoutParams(
            screenWidth - sideMargin * 2,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

        // Drag support
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        overlayView?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try { windowManager?.updateViewLayout(v, params) } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            android.util.Log.e("RideOverlay", "Failed to add overlay — permission denied?", e)
            stopSelf()
            return
        }

        // Right-side effort bar (only for workouts with segments)
        if (segments.isNotEmpty()) {
            createEffortBar()
        }
    }

    private fun createEffortBar() {
        val dp = resources.displayMetrics.density
        val bar = EffortBarView(this).apply {
            setBackgroundColor(Color.parseColor("#DD0A0B14"))
        }
        effortBarView = bar
        effortBarOverlay = bar

        val barHeight = (500 * dp).toInt()
        val params = WindowManager.LayoutParams(
            (48 * dp).toInt(),
            barHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.END or Gravity.CENTER_VERTICAL

        windowManager?.addView(bar, params)
    }

    private fun buildOverlayView(): View {
        val ctx = this
        val dp = resources.displayMetrics.density

        // Single-row compact overlay with rounded corners
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#DD0A0B14"))
                cornerRadius = 16 * dp
            }
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            gravity = Gravity.CENTER_VERTICAL
        }

        // Connection dot
        connectionDot = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((8 * dp).toInt(), (8 * dp).toInt()).apply {
                marginEnd = (8 * dp).toInt()
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#EF4444"))
            }
        }
        root.addView(connectionDot)

        // Workout name + elapsed
        val infoCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = (12 * dp).toInt() }
        }

        segmentLabel = TextView(ctx).apply {
            text = workoutName
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
        }
        infoCol.addView(segmentLabel)

        elapsedLabel = TextView(ctx).apply {
            text = "00:00"
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 10f
        }
        infoCol.addView(elapsedLabel)

        root.addView(infoCol)

        // Compact inline stats: PWR | RPM | RES
        powerText = createInlineStat(ctx, dp, "0", "W")
        root.addView(powerText?.parent as View)
        cadenceText = createInlineStat(ctx, dp, "0", "RPM")
        root.addView(cadenceText?.parent as View)
        resistanceText = createInlineStat(ctx, dp, "0", "RES")
        root.addView(resistanceText?.parent as View)
        heartText = createInlineStat(ctx, dp, "--", "\u2764")
        root.addView(heartText?.parent as View)
        caloriesText = createInlineStat(ctx, dp, "0", "CAL")
        root.addView(caloriesText?.parent as View)

        // Hill chart (compact)
        if (segments.isNotEmpty()) {
            hillChartView = HillChartView(ctx, segments, fitnessClient.fitnessConfig.ftp).apply {
                layoutParams = LinearLayout.LayoutParams(0, (64 * dp).toInt(), 1f).apply {
                    marginStart = (8 * dp).toInt()
                    marginEnd = (8 * dp).toInt()
                }
            }
            root.addView(hillChartView)
        } else {
            // Spacer to push buttons right
            root.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            })
        }

        // Difficulty buttons (only for structured workouts)
        if (segments.isNotEmpty()) {
            val diffDown = TextView(ctx).apply {
                text = "\u2212"  // minus sign
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt()).apply {
                    marginEnd = (2 * dp).toInt()
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#1F2937"))
                    cornerRadius = 16 * dp
                }
                setOnClickListener { adjustDifficulty(-0.1f) }
            }
            root.addView(diffDown)

            difficultyLabel = TextView(ctx).apply {
                text = "1.0x"
                setTextColor(Color.parseColor("#22D3EE"))
                textSize = 10f
                gravity = Gravity.CENTER
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams((36 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = (2 * dp).toInt()
                }
            }
            root.addView(difficultyLabel)

            val diffUp = TextView(ctx).apply {
                text = "+"
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt()).apply {
                    marginEnd = (6 * dp).toInt()
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#1F2937"))
                    cornerRadius = 16 * dp
                }
                setOnClickListener { adjustDifficulty(0.1f) }
            }
            root.addView(diffUp)
        }

        // Hide button (small)
        val hideBtn = TextView(ctx).apply {
            text = "\u25B2"
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt()).apply {
                marginEnd = (6 * dp).toInt()
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#1F2937"))
                cornerRadius = 16 * dp
            }
            setOnClickListener { toggleOverlay() }
        }
        root.addView(hideBtn)

        // End Ride button (compact) — requires double-tap confirmation
        var endConfirmPending = false
        val endBtn = TextView(ctx).apply {
            text = "END"
            setTextColor(Color.WHITE)
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#F43F5E"))
                cornerRadius = 16 * dp
            }
            setPadding((14 * dp).toInt(), (6 * dp).toInt(), (14 * dp).toInt(), (6 * dp).toInt())
            setOnClickListener { btn ->
                val tv = btn as TextView
                if (endConfirmPending) {
                    stopRide()
                    stopSelf()
                } else {
                    endConfirmPending = true
                    tv.text = "SURE?"
                    (tv.background as? android.graphics.drawable.GradientDrawable)
                        ?.setColor(Color.parseColor("#DC2626"))
                    tv.postDelayed({
                        endConfirmPending = false
                        tv.text = "END"
                        (tv.background as? android.graphics.drawable.GradientDrawable)
                            ?.setColor(Color.parseColor("#F43F5E"))
                    }, 3000)
                }
            }
        }
        root.addView(endBtn)

        // Hidden workoutLabel (still used by updateUI)
        workoutLabel = TextView(ctx).apply { visibility = View.GONE }

        return root
    }

    private fun createInlineStat(ctx: Context, dp: Float, initialValue: String, label: String): TextView {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = (4 * dp).toInt() }
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#1A1C2E"))
                cornerRadius = 8 * dp
            }
        }

        val valueView = TextView(ctx).apply {
            text = initialValue
            setTextColor(Color.parseColor("#22D3EE"))
            textSize = 15f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        }
        box.addView(valueView)

        val labelView = TextView(ctx).apply {
            text = " $label"
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 9f
        }
        box.addView(labelView)

        return valueView
    }

    private var rideActive = false
    private var ridePaused = false
    private var pauseStartTime = 0L
    private var totalPausedMs = 0L
    private var pauseOverlayView: View? = null
    private val RPM_PAUSE_THRESHOLD = 5  // below this RPM = not pedaling
    private val PAUSE_DELAY_MS = 3000L   // wait 3s of no pedaling before pausing
    private var lastPedalingTime = 0L

    private fun startRide() {
        rideActive = true
        ridePaused = false
        totalPausedMs = 0L
        rideStartTime = System.currentTimeMillis()
        lastSampleTime = rideStartTime
        lastPedalingTime = rideStartTime
        powerHistory.clear()

        // Start workout on the bridge
        bridge.startWorkout()

        // Timer (only counts non-paused time)
        scope.launch {
            while (isActive) {
                delay(1000)
                if (!ridePaused) {
                    elapsedSeconds = ((System.currentTimeMillis() - rideStartTime - totalPausedMs) / 1000).toInt()
                    powerHistory.add(power)
                }
                updateUI()
            }
        }

        // Collect sensor data from bridge
        scope.launch {
            bridge.sensorData.collect { data ->
                power = data.power.toInt()
                rpm = data.rpm
                resistance = data.resistanceLevel

                // Auto-pause logic: pause when not pedaling, resume when pedaling
                val now = System.currentTimeMillis()
                if (rpm >= RPM_PAUSE_THRESHOLD) {
                    lastPedalingTime = now
                    if (ridePaused) resumeRide()
                } else if (!ridePaused && rideActive && (now - lastPedalingTime > PAUSE_DELAY_MS)) {
                    pauseRide()
                }

                if (!ridePaused) {
                    speedMph = RidePhysics.speedMph(power)

                    val deltaHours = (now - lastSampleTime) / 3_600_000.0
                    distanceMiles += (speedMph * deltaHours).toFloat()
                    lastSampleTime = now

                    ridePowerSum += power
                    rideRpmSum += rpm
                    rideResistanceSum += resistance
                    rideSampleCount++
                    if (power > rideMaxPower) rideMaxPower = power

                    val elapsedHours = (now - rideStartTime - totalPausedMs) / 3_600_000.0
                    val avgPower = if (rideSampleCount > 0) ridePowerSum.toDouble() / rideSampleCount else 0.0
                    calories = RidePhysics.calories(avgPower, elapsedHours)
                } else {
                    lastSampleTime = now  // don't accumulate distance while paused
                }

                updateUI()
            }
        }

        // Collect heart rate from bridge
        scope.launch {
            bridge.heartRate.collect { hr ->
                if (hr > 0) {
                    heartRate = hr
                }
            }
        }

        // Collect connection state from bridge
        scope.launch {
            bridge.connected.collect { isConnected ->
                connected = isConnected
                updateConnectionUI()
            }
        }

        // Watch for external workout stop (e.g. watchdog, another app ending the ride)
        scope.launch {
            bridge.workoutActive.collect { active ->
                if (!active && rideActive) {
                    stopRide()
                    stopSelf()
                }
            }
        }
    }

    private fun pauseRide() {
        if (ridePaused) return
        ridePaused = true
        pauseStartTime = System.currentTimeMillis()

        // Pause media playback
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE))
            am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE))
        } catch (_: Exception) {}

        // Show pause overlay
        showPauseOverlay()
    }

    private fun resumeRide() {
        if (!ridePaused) return
        totalPausedMs += System.currentTimeMillis() - pauseStartTime
        ridePaused = false

        // Resume media playback
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
            am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
        } catch (_: Exception) {}

        // Remove pause overlay
        removePauseOverlay()
    }

    private fun showPauseOverlay() {
        if (pauseOverlayView != null) return
        val dp = resources.displayMetrics.density
        val pauseView = TextView(this).apply {
            text = "PAUSED\n\nStart pedaling to resume"
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding((40 * dp).toInt(), (40 * dp).toInt(), (40 * dp).toInt(), (40 * dp).toInt())
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        )
        try {
            windowManager?.addView(pauseView, params)
            pauseOverlayView = pauseView
        } catch (_: Exception) {}
    }

    private fun removePauseOverlay() {
        pauseOverlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        pauseOverlayView = null
    }

    private fun updateUI() {
        scope.launch(Dispatchers.Main) {
            powerText?.text = "$power"
            cadenceText?.text = "$rpm"
            resistanceText?.text = "$resistance"
            heartText?.text = if (heartRate > 0) "$heartRate" else "--"
            caloriesText?.text = "$calories"

            val currentIdx = currentSegmentIndex()
            val h = elapsedSeconds / 3600
            val m = (elapsedSeconds % 3600) / 60
            val s = elapsedSeconds % 60
            val timeStr = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)

            if (segments.isNotEmpty()) {
                val seg = segments.getOrNull(currentIdx)
                segmentLabel?.text = seg?.label ?: workoutName
                elapsedLabel?.text = "$timeStr elapsed \u00B7 Segment ${currentIdx + 1}/${segments.size}"
            } else {
                elapsedLabel?.text = "$timeStr elapsed"
            }

            hillChartView?.currentIndex = currentIdx
            hillChartView?.elapsedSeconds = elapsedSeconds
            hillChartView?.powerHistory = powerHistory
            hillChartView?.invalidate()

            // Update effort bar via fitness API
            effortBarView?.let { bar ->
                val seg = segments.getOrNull(currentIdx)
                if (seg != null) {
                    val scaledRes = (seg.resistance * difficultyMultiplier).toInt().coerceIn(1, 25)
                    bar.powerTarget = fitnessClient.getTargetPower(scaledRes)
                    bar.actualPower = power
                    bar.actualResistance = resistance
                }
                bar.invalidate()
            }
        }
    }

    private fun updateConnectionUI() {
        scope.launch(Dispatchers.Main) {
            val bg = connectionDot?.background as? android.graphics.drawable.GradientDrawable
            bg?.setColor(if (connected) Color.parseColor("#22C55E") else Color.parseColor("#EF4444"))
        }
    }

    private fun currentSegmentIndex(): Int {
        var acc = 0
        for ((i, seg) in segments.withIndex()) {
            acc += seg.durationSeconds
            if (elapsedSeconds < acc) return i
        }
        return segments.lastIndex.coerceAtLeast(0)
    }

    private fun toggleOverlay() {
        overlayExpanded = !overlayExpanded
        overlayView?.visibility = if (overlayExpanded) View.VISIBLE else View.GONE
        effortBarOverlay?.visibility = if (overlayExpanded) View.VISIBLE else View.GONE

        if (!overlayExpanded) {
            showMiniFab()
        } else {
            removeMiniFab()
        }
    }

    private var miniFab: View? = null

    private fun showMiniFab() {
        if (miniFab != null) return
        val dp = resources.displayMetrics.density
        val btn = Button(this).apply {
            text = "LIVE"
            setTextColor(Color.WHITE)
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#22D3EE"))
                cornerRadius = 24 * dp
            }
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
            setOnClickListener { toggleOverlay() }
        }
        miniFab = btn

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.END
        params.x = (16 * dp).toInt()
        params.y = (16 * dp).toInt()

        windowManager?.addView(btn, params)
    }

    private fun removeMiniFab() {
        miniFab?.let { windowManager?.removeView(it) }
        miniFab = null
    }

    private fun adjustDifficulty(delta: Float) {
        difficultyMultiplier = (difficultyMultiplier + delta).coerceIn(0.5f, 2.0f)
        difficultyLabel?.text = "%.1fx".format(difficultyMultiplier)
        hillChartView?.difficultyMultiplier = difficultyMultiplier
        hillChartView?.invalidate()
        updateUI()
    }

    private fun stopRide() {
        if (!rideActive) return
        rideActive = false

        val elapsed = ((System.currentTimeMillis() - rideStartTime) / 1000).toInt()

        val avgPower = if (rideSampleCount > 0) (ridePowerSum / rideSampleCount).toInt() else 0
        val avgRpm = if (rideSampleCount > 0) (rideRpmSum / rideSampleCount).toInt() else 0
        val avgRes = if (rideSampleCount > 0) (rideResistanceSum / rideSampleCount).toInt() else 0
        val avgSpeed = if (elapsed > 0) distanceMiles / (elapsed / 3600f) else 0f
        val avgHr = if (rideSampleCount > 0 && heartRate > 0) heartRate else 0

        // Only log rides >= 1 minute
        if (elapsed >= 60) {
            val stats = RideStats.Builder()
                .startTime(rideStartTime)
                .durationSeconds(elapsed)
                .calories(calories)
                .avgPower(avgPower)
                .avgRpm(avgRpm)
                .avgResistance(avgRes)
                .maxPower(rideMaxPower)
                .distanceMiles(distanceMiles)
                .avgSpeedMph(avgSpeed)
                .avgHeartRate(avgHr)
                .source(packageName, "VeloLauncher")
                .apply {
                    if (workoutId != null) workout(workoutId!!, workoutName)
                }
                .build()
            fitnessClient.logRide(stats)
        }

        // Stop media playback
        stopMediaPlayback()

        // Stop workout on bridge
        bridge.stopWorkout()

        // Store summary for launcher to pick up on resume
        if (elapsed >= 60) {
            val summary = RideSummary(
                durationSeconds = elapsed,
                calories = calories,
                avgPower = avgPower,
                maxPower = rideMaxPower,
                avgRpm = avgRpm,
                avgResistance = avgRes,
                avgHeartRate = avgHr,
                avgSpeedMph = avgSpeed,
                distanceMiles = distanceMiles,
                workoutName = if (segments.isNotEmpty()) workoutName else null,
            )
            // Store in shared prefs so the launcher can show summary when it resumes
            try {
                val prefs = getSharedPreferences("ride_overlay", Context.MODE_PRIVATE)
                prefs.edit()
                    .putInt("summary_duration", summary.durationSeconds)
                    .putInt("summary_calories", summary.calories)
                    .putInt("summary_avg_power", summary.avgPower)
                    .putInt("summary_max_power", summary.maxPower)
                    .putInt("summary_avg_rpm", summary.avgRpm)
                    .putInt("summary_avg_resistance", summary.avgResistance)
                    .putInt("summary_avg_hr", summary.avgHeartRate)
                    .putFloat("summary_avg_speed", summary.avgSpeedMph)
                    .putFloat("summary_distance", summary.distanceMiles)
                    .putString("summary_workout_name", summary.workoutName)
                    .putLong("summary_timestamp", System.currentTimeMillis())
                    .apply()
            } catch (_: Exception) {}
        }

        // Cleanup overlay
        scope.cancel()
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        effortBarOverlay?.let { windowManager?.removeView(it) }
        effortBarOverlay = null
        effortBarView = null
        removeMiniFab()
        removePauseOverlay()
        restoreStatusBar()
    }

    private fun stopMediaPlayback() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE))
            am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE))
        } catch (_: Exception) {}
    }

    private fun restoreStatusBar() {
        try {
            android.provider.Settings.Global.putString(
                contentResolver, "policy_control", ""
            )
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager?.removeView(it) }
        effortBarOverlay?.let { windowManager?.removeView(it) }
        removeMiniFab()
        scope.cancel()
        restoreStatusBar()
    }

    companion object {
        const val ACTION_START = "io.freewheel.launcher.OVERLAY_START"
        const val ACTION_STOP = "io.freewheel.launcher.OVERLAY_STOP"
        const val ACTION_TOGGLE = "io.freewheel.launcher.OVERLAY_TOGGLE"
        const val NOTIFICATION_ID = 1001
    }
}

/** Simple custom View that draws the workout hill chart.
 *  Both target bars and actual power line are plotted in watts for correct alignment. */
class HillChartView(
    context: Context,
    private val segments: List<WorkoutSegment>,
    private val ftp: Int,
) : View(context) {

    var currentIndex = 0
    var elapsedSeconds = 0
    var powerHistory: List<Int> = emptyList()
    var difficultyMultiplier = 1.0f

    private val totalSeconds = segments.sumOf { it.durationSeconds }.coerceAtLeast(1)
    private val dp = resources.displayMetrics.density

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#39FF6E")
        strokeWidth = 2f * dp
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        strokeWidth = 1.5f * dp
    }

    /** Convert resistance level (1-25) to center power in watts. Same formula as VeloFit. */
    private fun resToWatts(resistance: Int): Float {
        val res = resistance.coerceIn(1, 25)
        val effortFraction = 0.10f + (res - 1) * (0.90f / 24f)
        return ftp * effortFraction
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (segments.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 2f

        // Unified watts scale for both target and actual
        val segmentWatts = segments.map { resToWatts(it.resistance) * difficultyMultiplier }
        val maxTarget = segmentWatts.max()
        val maxActual = if (powerHistory.isNotEmpty()) powerHistory.max().toFloat() else 0f
        val maxY = (maxOf(maxTarget, maxActual) * 1.2f).coerceAtLeast(1f)

        fun valToY(v: Float): Float = h - pad - ((v / maxY) * (h - pad * 2))

        // Draw target profile bars (filled, stepped) — in watts
        var xOff = 0f
        for ((i, seg) in segments.withIndex()) {
            val segW = (seg.durationSeconds.toFloat() / totalSeconds) * w
            val top = valToY(segmentWatts[i])

            barPaint.color = when {
                i == currentIndex -> Color.parseColor("#4422D3EE")  // current: bright cyan fill
                i < currentIndex -> Color.parseColor("#2222D3EE")   // past: dim cyan
                else -> Color.parseColor("#3322D3EE")               // future: visible cyan tint
            }
            canvas.drawRect(xOff, top, xOff + segW, h, barPaint)

            // Outline
            barPaint.color = when {
                i == currentIndex -> Color.parseColor("#AA22D3EE")  // current: strong outline
                i < currentIndex -> Color.parseColor("#5522D3EE")   // past: dim outline
                else -> Color.parseColor("#6622D3EE")               // future: visible outline
            }
            barPaint.style = Paint.Style.STROKE
            barPaint.strokeWidth = 1f * dp
            canvas.drawLine(xOff, top, xOff + segW, top, barPaint)
            if (i > 0) {
                canvas.drawLine(xOff, valToY(segmentWatts[i - 1]), xOff, top, barPaint)
            }
            barPaint.style = Paint.Style.FILL

            xOff += segW
        }

        // Draw actual power line (same watts scale)
        if (powerHistory.size >= 2) {
            val step = w / totalSeconds
            val path = android.graphics.Path()
            for ((i, power) in powerHistory.withIndex()) {
                val x = i * step
                val y = valToY(power.toFloat())
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, linePaint)
        }

        // Draw progress line
        if (elapsedSeconds > 0) {
            val px = (elapsedSeconds.toFloat() / totalSeconds) * w
            canvas.drawLine(px, 0f, px, h, progressPaint)
        }
    }
}

/**
 * Power-based effort compliance bar. Uses PowerTarget from VeloFit API
 * for all zone/ratio computation — no hardcoded formulas.
 */
class EffortBarView(context: Context) : View(context) {

    var powerTarget: PowerTarget? = null
    var actualPower = 0
    var actualResistance = 0

    private val dp = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 10f * dp
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B7280")
        textSize = 7f * dp
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.15f
    }

    private val trackColor = Color.parseColor("#1A1A2E")
    private val targetZoneColor = Color.parseColor("#3322C55E")
    private val targetBorderColor = Color.parseColor("#9922C55E")
    private val greenColor = Color.parseColor("#22C55E")
    private val orangeColor = Color.parseColor("#FF9F2E")
    private val blueColor = Color.parseColor("#3B82F6")
    private val idleColor = Color.parseColor("#6B7280")

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 8 * dp
        val barLeft = pad
        val barRight = w - pad
        val cornerR = 4 * dp

        val target = powerTarget ?: return

        val zone = target.zone(actualPower)
        val ratio = target.complianceRatio(actualPower)
        val zoneLow = target.zoneLowFraction()
        val zoneHigh = target.zoneHighFraction()

        val zoneColor = when (zone) {
            PowerTarget.EffortZone.UNDER -> blueColor
            PowerTarget.EffortZone.OVER -> orangeColor
            PowerTarget.EffortZone.ON_TARGET -> greenColor
            else -> idleColor
        }

        // Labels at top
        labelPaint.color = idleColor
        canvas.drawText("TARGET", w / 2, pad + 10 * dp, labelPaint)

        textPaint.color = greenColor
        textPaint.textSize = 9 * dp
        canvas.drawText("${target.targetLow}-${target.targetHigh}W", w / 2, pad + 22 * dp, textPaint)

        // Bar area
        val barTop = pad + 30 * dp
        val barBottom = h - pad - 35 * dp
        val barHeight = barBottom - barTop

        // Track background
        paint.color = trackColor
        canvas.drawRoundRect(barLeft, barTop, barRight, barBottom, cornerR, cornerR, paint)

        // Target zone band
        val zoneTopY = barBottom - barHeight * zoneHigh
        val zoneBottomY = barBottom - barHeight * zoneLow
        paint.color = targetZoneColor
        canvas.drawRect(barLeft, zoneTopY, barRight, zoneBottomY, paint)

        // Zone borders
        paint.color = targetBorderColor
        paint.strokeWidth = 1.5f * dp
        paint.style = Paint.Style.STROKE
        canvas.drawLine(barLeft, zoneTopY, barRight, zoneTopY, paint)
        canvas.drawLine(barLeft, zoneBottomY, barRight, zoneBottomY, paint)
        paint.style = Paint.Style.FILL

        // Gradient fill from bottom to marker
        if (ratio > 0f) {
            val fillTop = barBottom - barHeight * ratio
            paint.color = Color.argb(60, Color.red(zoneColor), Color.green(zoneColor), Color.blue(zoneColor))
            canvas.drawRect(barLeft, fillTop, barRight, barBottom, paint)
        }

        // Marker line
        if (actualPower > 0) {
            val markerY = barBottom - barHeight * ratio
            paint.color = zoneColor
            paint.strokeWidth = 3 * dp
            paint.style = Paint.Style.STROKE
            canvas.drawLine(barLeft, markerY, barRight, markerY, paint)
            paint.style = Paint.Style.FILL

            paint.color = Color.WHITE
            paint.strokeWidth = 1.5f * dp
            paint.style = Paint.Style.STROKE
            canvas.drawLine(barLeft + 2 * dp, markerY, barRight - 2 * dp, markerY, paint)
            paint.style = Paint.Style.FILL
        }

        // Outer border
        paint.color = Color.parseColor("#2A2D40")
        paint.strokeWidth = 1 * dp
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(barLeft, barTop, barRight, barBottom, cornerR, cornerR, paint)
        paint.style = Paint.Style.FILL

        // Power value
        textPaint.color = zoneColor
        textPaint.textSize = 14 * dp
        canvas.drawText("${actualPower}W", w / 2, h - pad - 12 * dp, textPaint)

        // Zone label
        val zoneLabel = when (zone) {
            PowerTarget.EffortZone.UNDER -> "UNDER"
            PowerTarget.EffortZone.OVER -> "OVER"
            PowerTarget.EffortZone.ON_TARGET -> "ON TARGET"
            else -> "IDLE"
        }
        labelPaint.color = zoneColor
        canvas.drawText(zoneLabel, w / 2, h - pad, labelPaint)
    }
}
