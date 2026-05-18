package risk.tech.volumebuttons

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.preference.PreferenceManager

class VolumePowerAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var isWaitingForDoubleTapUp = false
    private var isLongPressUp = false
    private var isWaitingForDoubleTapDown = false
    private var isLongPressDown = false
    
    private var isFlashlightOn = false
    private var cameraManager: CameraManager? = null
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            isFlashlightOn = enabled
        }
    }
    private var mediaSession: MediaSession? = null
    
    private var isVolumePanelVisible = false
    private var isWaitingToClickClearAll = false
    private val resetVolumePanelRunnable = Runnable {
        isVolumePanelVisible = false
        // Panel closed — re-sync MediaSession to screen state so bypass
        // doesn't leave cast volume active after the panel dismisses.
        syncMediaSession()
    }

    // Runnables for Volume Up
    private val singlePressUpRunnable = Runnable {
        isWaitingForDoubleTapUp = false
        performAction(getPref("up_single", "normal"), KeyEvent.KEYCODE_VOLUME_UP)
    }
    private val longPressUpRunnable = Runnable {
        isLongPressUp = true
        isWaitingForDoubleTapUp = false
        handler.removeCallbacks(singlePressUpRunnable)
        performAction(getPref("up_long", "power"), KeyEvent.KEYCODE_VOLUME_UP)
    }
    
    // Runnables for Volume Down
    private val singlePressDownRunnable = Runnable {
        isWaitingForDoubleTapDown = false
        performAction(getPref("down_single", "normal"), KeyEvent.KEYCODE_VOLUME_DOWN)
    }
    private val longPressDownRunnable = Runnable {
        isLongPressDown = true
        isWaitingForDoubleTapDown = false
        handler.removeCallbacks(singlePressDownRunnable)
        performAction(getPref("down_long", "none"), KeyEvent.KEYCODE_VOLUME_DOWN)
    }

    private var screenStateReceiver: android.content.BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraManager?.registerTorchCallback(torchCallback, handler)
        } catch (e: Exception) {}
        
        mediaSession = MediaSession(this, "VolumePowerSession")
        mediaSession?.setPlaybackState(PlaybackState.Builder()
            .setState(PlaybackState.STATE_PLAYING, 0, 1.0f)
            .build())
        mediaSession?.setPlaybackToRemote(object : android.media.VolumeProvider(android.media.VolumeProvider.VOLUME_CONTROL_RELATIVE, 100, 50) {
            override fun onAdjustVolume(direction: Int) {}
        })
        
        syncMediaSession()

        screenStateReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                syncMediaSession()
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { cameraManager?.unregisterTorchCallback(torchCallback) } catch (e: Exception) {}
        screenStateReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) {}
        }
        mediaSession?.isActive = false
        mediaSession?.release()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        if (isWaitingToClickClearAll) {
            attemptClickClearAll()
        }
        
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: ""
            val cls = event.className?.toString() ?: ""
            
            if (pkg == "com.android.systemui" && (cls.contains("Volume", ignoreCase = true) || cls.contains("Slider", ignoreCase = true))) {
                isVolumePanelVisible = true
                handler.removeCallbacks(resetVolumePanelRunnable)
                handler.postDelayed(resetVolumePanelRunnable, 4000)
            }
        }
    }
    
    override fun onInterrupt() {}

    /**
     * MediaSession should only be active when the screen is OFF.
     * When active on screen-on it hijacks volume keys and shows Cast volume UI.
     */
    private fun syncMediaSession() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            mediaSession?.isActive = !pm.isInteractive
        } catch (e: Exception) {}
    }

    private fun getPref(key: String, default: String): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getString(key, default) ?: default
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            if (!prefs.getBoolean("vibrate_enable", false)) return
            
            val intensity = prefs.getInt("vibrate_intensity", 50)
            val amplitude = ((intensity * 255) / 100).coerceIn(1, 255)
            val duration = intensity.toLong()
            
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                vm.defaultVibrator
            } else {
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun performAction(action: String, keyCode: Int) {
        if (action != "none" && action != "normal") {
            vibrate()
        }
        
        if (action.startsWith("app:")) {
            val pkg = action.substring(4)
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                launchApp(intent)
            } else {
                handler.post { Toast.makeText(this, "App not installed", Toast.LENGTH_SHORT).show() }
            }
            return
        }
        
        when (action) {
            "none" -> return
            "lock" -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            "power" -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "screenshot" -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "voice_command" -> launchApp(Intent(Intent.ACTION_VOICE_COMMAND))
            "web_search" -> launchApp(Intent(Intent.ACTION_WEB_SEARCH))
            
            "wifi" -> {
                try {
                    val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                    @Suppress("DEPRECATION")
                    val wasEnabled = wifiManager.isWifiEnabled
                    @Suppress("DEPRECATION")
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                        wifiManager.isWifiEnabled = !wasEnabled
                    } else {
                        val intent = Intent(Settings.Panel.ACTION_WIFI).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val intent = Intent(Settings.Panel.ACTION_WIFI).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        try { startActivity(intent) } catch (ex: Exception) {}
                    }
                }
            }
            "bluetooth" -> {
                try {
                    val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
                    val bluetoothAdapter = bluetoothManager.adapter
                    if (bluetoothAdapter != null) {
                        val wasEnabled = bluetoothAdapter.isEnabled
                        @Suppress("DEPRECATION")
                        if (wasEnabled) {
                            bluetoothAdapter.disable()
                        } else {
                            bluetoothAdapter.enable()
                        }
                        handler.postDelayed({
                            try {
                                if (bluetoothAdapter.isEnabled == wasEnabled) {
                                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                    startActivity(intent)
                                    handler.post { Toast.makeText(this@VolumePowerAccessibilityService, "Direct Bluetooth toggle restricted by Android. Please toggle here.", Toast.LENGTH_LONG).show() }
                                }
                            } catch (ex: Exception) {}
                        }, 300)
                    }
                } catch (e: Exception) {
                    handler.post { Toast.makeText(this, "Please grant Bluetooth Connect permission in App Settings", Toast.LENGTH_LONG).show() }
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try { startActivity(intent) } catch (ex: Exception) {}
                }
            }
            "dnd" -> toggleDnd()
            "flashlight" -> toggleFlashlight()
            "auto_rotate" -> checkWriteSettingsAndRun {
                val current = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (current == 1) 0 else 1)
            }
            "auto_brightness" -> checkWriteSettingsAndRun {
                val current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                val nextMode = if (current == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL else Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, nextMode)
            }
            "brightness_plus" -> checkWriteSettingsAndRun {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                val current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                val next = (current + 25).coerceIn(1, 255)
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, next)
            }
            "brightness_minus" -> checkWriteSettingsAndRun {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                val current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                val next = (current - 25).coerceIn(1, 255)
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, next)
            }
            "brightness_0" -> checkWriteSettingsAndRun {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 1)
            }
            "brightness_50" -> checkWriteSettingsAndRun {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            }
            "brightness_100" -> checkWriteSettingsAndRun {
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 255)
            }

            "volume_control" -> {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            }
            "volume_up" -> {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            }
            "volume_down" -> {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            }
            "media_next" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            "media_previous" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "media_play_pause" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "doomscroll_up" -> performDoomscroll(isUp = true)
            "doomscroll_down" -> performDoomscroll(isUp = false)

            "launch_this_app" -> {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                launchApp(intent)
            }
            "pause_app_10s" -> {
                isTemporarilyPaused = true
                handler.post { Toast.makeText(this, "Volume Buttons Mapper paused for 10 seconds", Toast.LENGTH_SHORT).show() }
                handler.postDelayed({
                    isTemporarilyPaused = false
                    handler.post { Toast.makeText(this, "Volume Buttons Mapper resumed", Toast.LENGTH_SHORT).show() }
                }, 10000)
            }
            "close_all_apps" -> {
                isWaitingToClickClearAll = true
                handler.post { Toast.makeText(this, "Clearing recent apps...", Toast.LENGTH_SHORT).show() }
                performGlobalAction(GLOBAL_ACTION_RECENTS)
                
                // Fallback checks to ensure the button is clicked even if animations are slow
                handler.postDelayed({ attemptClickClearAll() }, 500)
                handler.postDelayed({
                    attemptClickClearAll()
                    isWaitingToClickClearAll = false
                }, 1000)
            }

            "camera" -> launchApp(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            "dialer" -> launchApp(Intent(Intent.ACTION_DIAL))
            "browser" -> launchApp(Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com")))
            "settings" -> launchApp(Intent(Settings.ACTION_SETTINGS))

            "normal" -> {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val direction = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            }
        }
    }

    private fun checkWriteSettingsAndRun(action: () -> Unit) {
        if (Settings.System.canWrite(this)) {
            try { action() } catch (e: Exception) {}
        } else {
            handler.post { Toast.makeText(this, "Please grant Modify System Settings permission first", Toast.LENGTH_LONG).show() }
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try { startActivity(intent) } catch (e: Exception) {}
        }
    }

    private fun sendMediaKey(keyCode: Int) {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (e: Exception) {}
    }
    
    private fun toggleFlashlight() {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, !isFlashlightOn)
        } catch (e: Exception) {}
    }
    
    private fun toggleDnd() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            } else {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        } else {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(intent)
                handler.post { Toast.makeText(this, "Please grant Do Not Disturb access", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                handler.post { Toast.makeText(this, "DND Settings not available on this device", Toast.LENGTH_LONG).show() }
            }
        }
    }
    
    private fun launchApp(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(intent) } catch (e: Exception) {}
    }

    private var hasPerformedDoubleTapUp = false
    private var hasPerformedDoubleTapDown = false
    private var isTemporarilyPaused = false

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        if (!prefs.getBoolean("master_toggle", false) || isTemporarilyPaused) {
            return super.onKeyEvent(event)
        }

        if (prefs.getBoolean("bypass_volume_dialog", false) && isVolumePanelVisible) {
            // Ensure MediaSession is inactive while panel is open so cast volume
            // doesn't appear — super.onKeyEvent will hit native media volume instead.
            mediaSession?.isActive = false
            handler.removeCallbacks(resetVolumePanelRunnable)
            handler.postDelayed(resetVolumePanelRunnable, 4000)
            return super.onKeyEvent(event)
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOn = pm.isInteractive

        val keyCode = event.keyCode
        val action = event.action

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (!isScreenOn) {
                if (action == KeyEvent.ACTION_UP) {
                    performAction(getPref("up_off", "none"), keyCode)
                }
                return true
            }
            
            when (action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        if (isWaitingForDoubleTapUp) {
                            handler.removeCallbacks(singlePressUpRunnable)
                            handler.removeCallbacks(longPressUpRunnable)
                            isWaitingForDoubleTapUp = false
                            isLongPressUp = false
                            hasPerformedDoubleTapUp = true
                            performAction(getPref("up_double", "none"), keyCode)
                        } else {
                            isLongPressUp = false
                            hasPerformedDoubleTapUp = false
                            handler.postDelayed(longPressUpRunnable, 500)
                        }
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressUpRunnable)
                    if (hasPerformedDoubleTapUp) {
                        hasPerformedDoubleTapUp = false
                        return true
                    }
                    if (!isLongPressUp) {
                        if (!prefs.getBoolean("up_double_enable", false)) {
                            performAction(getPref("up_single", "normal"), keyCode)
                        } else {
                            isWaitingForDoubleTapUp = true
                            handler.postDelayed(singlePressUpRunnable, 300)
                        }
                    }
                    isLongPressUp = false
                    return true
                }
            }
        }
        
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (!isScreenOn) {
                if (action == KeyEvent.ACTION_UP) {
                    performAction(getPref("down_off", "none"), keyCode)
                }
                return true
            }
            
            when (action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        if (isWaitingForDoubleTapDown) {
                            handler.removeCallbacks(singlePressDownRunnable)
                            handler.removeCallbacks(longPressDownRunnable)
                            isWaitingForDoubleTapDown = false
                            isLongPressDown = false
                            hasPerformedDoubleTapDown = true
                            performAction(getPref("down_double", "none"), keyCode)
                        } else {
                            isLongPressDown = false
                            hasPerformedDoubleTapDown = false
                            handler.postDelayed(longPressDownRunnable, 500)
                        }
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressDownRunnable)
                    if (hasPerformedDoubleTapDown) {
                        hasPerformedDoubleTapDown = false
                        return true
                    }
                    if (!isLongPressDown) {
                        if (!prefs.getBoolean("down_double_enable", false)) {
                            performAction(getPref("down_single", "normal"), keyCode)
                        } else {
                            isWaitingForDoubleTapDown = true
                            handler.postDelayed(singlePressDownRunnable, 300)
                        }
                    }
                    isLongPressDown = false
                    return true
                }
            }
        }
        
        return super.onKeyEvent(event)
    }

    private fun attemptClickClearAll(): Boolean {
        if (!isWaitingToClickClearAll) return false
        val root = rootInActiveWindow ?: return false
        val keywords = listOf("clear all", "close all", "clear", "close", "dismiss all")
        
        val queue = java.util.ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
        queue.add(root)
        while (!queue.isEmpty()) {
            val node = queue.poll() ?: continue
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            
            if (keywords.any { text.contains(it) || desc.contains(it) }) {
                if (node.isClickable) {
                    val success = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    if (success) {
                        isWaitingToClickClearAll = false
                        handler.post { Toast.makeText(this, "Recent apps cleared", Toast.LENGTH_SHORT).show() }
                        return true
                    }
                } else {
                    val parent = node.parent
                    if (parent != null && parent.isClickable) {
                        val success = parent.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        if (success) {
                            isWaitingToClickClearAll = false
                            handler.post { Toast.makeText(this, "Recent apps cleared", Toast.LENGTH_SHORT).show() }
                            return true
                        }
                    }
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun performDoomscroll(isUp: Boolean) {
        try {
            val displayMetrics = resources.displayMetrics
            val width = displayMetrics.widthPixels.toFloat()
            val height = displayMetrics.heightPixels.toFloat()
            
            val startX = width / 2f
            val endX = width / 2f
            
            val startY = if (isUp) height * 0.8f else height * 0.2f
            val endY = if (isUp) height * 0.2f else height * 0.8f
            
            val path = android.graphics.Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 250L)
            val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
            
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
