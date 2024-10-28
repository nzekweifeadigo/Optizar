// MainActivity.kt
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    companion object {
        const val OVERLAY_PERMISSION_REQUEST_CODE = 1001
        const val NOTIFICATION_CHANNEL_ID = "focus_assistant_channel"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: FocusViewModel
    private var timerRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        viewModel = ViewModelProvider(this).get(FocusViewModel::class.java)
        
        setupPermissions()
        createNotificationChannel()
        initializeUI()
        observeViewModel()
    }

    private fun setupPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Focus Assistant",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Focus mode notifications"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun initializeUI() {
        binding.apply {
            startButton.setOnClickListener {
                toggleFocusMode()
            }

            settingsButton.setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }

            statsButton.setOnClickListener {
                startActivity(Intent(this@MainActivity, StatsActivity::class.java))
            }
        }
    }

    private fun observeViewModel() {
        viewModel.focusTimeRemaining.observe(this) { timeRemaining ->
            updateTimerDisplay(timeRemaining)
        }

        viewModel.isFocusModeActive.observe(this) { isActive ->
            updateFocusModeUI(isActive)
        }
    }
}

// FocusViewModel.kt
class FocusViewModel : ViewModel() {
    private val _focusTimeRemaining = MutableLiveData<Long>()
    val focusTimeRemaining: LiveData<Long> = _focusTimeRemaining

    private val _isFocusModeActive = MutableLiveData<Boolean>()
    val isFocusModeActive: LiveData<Boolean> = _isFocusModeActive

    fun startFocusMode() {
        _isFocusModeActive.value = true
        startTimer()
    }

    fun stopFocusMode() {
        _isFocusModeActive.value = false
        cancelTimer()
    }
}

// FocusService.kt
class FocusService : Service() {
    private val overlayManager = OverlayManager()
    private val appBlocker = AppBlocker()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOCUS -> startFocusMode()
            ACTION_STOP_FOCUS -> stopFocusMode()
        }
        return START_STICKY
    }

    private fun startFocusMode() {
        if (Settings.canDrawOverlays(this)) {
            overlayManager.showBlueLight(this)
            appBlocker.enableBlocking()
        }
    }
}

// OverlayManager.kt
class OverlayManager {
    private var overlayView: View? = null

    fun showBlueLight(context: Context) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        val view = View(context).apply {
            setBackgroundColor(Color.argb(50, 0, 0, 255))
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.addView(view, params)
        overlayView = view
    }
}

// AppBlocker.kt
class AppBlocker {
    private val blockedApps = setOf(
        "com.facebook.katana",
        "com.instagram.android",
        "com.twitter.android",
        "com.google.android.youtube"
    )

    fun isAppBlocked(packageName: String): Boolean {
        return blockedApps.contains(packageName)
    }
}

// StatsManager.kt
class StatsManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("focus_stats", Context.MODE_PRIVATE)

    fun recordFocusSession(durationMinutes: Int) {
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val sessions = prefs.getInt("sessions_$today", 0)
        val totalMinutes = prefs.getInt("minutes_$today", 0)

        prefs.edit().apply {
            putInt("sessions_$today", sessions + 1)
            putInt("minutes_$today", totalMinutes + durationMinutes)
            apply()
        }
    }
}

// layout/activity_main.xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="16dp">

    <TextView
        android:id="@+id/timerText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textSize="48sp"
        android:textStyle="bold"
        app:layout_constraintBottom_toTopOf="@id/startButton"
        app:layout_constraintLeft_toLeftOf="parent"
        app:layout_constraintRight_toRightOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        android:text="25:00" />

    <Button
        android:id="@+id/startButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Start Focus Mode"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintLeft_toLeftOf="parent"
        app:layout_constraintRight_toRightOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <!-- Add other UI elements -->

</androidx.constraintlayout.widget.ConstraintLayout>
