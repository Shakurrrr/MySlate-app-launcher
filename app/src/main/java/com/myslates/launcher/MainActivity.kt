package com.myslates.launcher

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*
import android.content.res.Configuration
import android.text.Editable
import android.text.TextWatcher

class MainActivity : AppCompatActivity() {

    private lateinit var appDrawer: View
    private lateinit var rootLayout: View
    private lateinit var blurOverlay: View
    private lateinit var gestureDetector: GestureDetector
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var weatherText: TextView
    private lateinit var appGridView: GridView
    private lateinit var searchInput: EditText
    private lateinit var searchIcon: ImageView
    private lateinit var adapter: AppAdapter
    private lateinit var allFilteredApps: List<AppObject>

    private val handler = Handler(Looper.getMainLooper())
    private var isDrawerOpen = false

    private val allowedApps = listOf(
        "com.ATS.MySlates.Parent",
        "com.ATS.MySlates",
        "com.ATS.MySlates.Teacher",
        "com.adobe.reader"
    )
    private val gestureForwarder = View.OnTouchListener { _, event ->
        gestureDetector.onTouchEvent(event)
        true
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Theme must be set before setContentView
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            setTheme(R.style.Theme_MySlates_Dark)
        } else {
            setTheme(R.style.Theme_MySlates_Dark)
        }

        setContentView(R.layout.activity_main)


        // Bind views
        appDrawer = findViewById(R.id.app_drawer)
        rootLayout = findViewById(R.id.root_layout)
        blurOverlay = findViewById(R.id.blur_overlay)
        timeText = findViewById(R.id.text_time)
        dateText = findViewById(R.id.text_date)
        weatherText = findViewById(R.id.text_weather)
        appGridView = findViewById(R.id.app_grid)
        searchInput = appDrawer.findViewById(R.id.search_input)
        searchIcon = appDrawer.findViewById(R.id.search_icon)


        rootLayout.setOnTouchListener(gestureForwarder)
        appDrawer.setOnTouchListener(gestureForwarder)
        appGridView.setOnTouchListener(gestureForwarder)
        searchInput.setOnTouchListener(gestureForwarder)
        searchIcon.setOnTouchListener(gestureForwarder)


        // Handle search
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val filtered = allFilteredApps.filter {
                    it.label.contains(s.toString(), ignoreCase = true)
                }
                adapter.updateData(filtered)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Initial drawer setup
        appDrawer.post {
            appDrawer.translationY = appDrawer.height.toFloat()
        }


        appDrawer.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        blurOverlay.alpha = 0f
        blurOverlay.visibility = View.GONE
        weatherText.text = "☀ 24°"

        // Clock
        handler.post(timeRunnable)

        // Gesture detection
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null || e2 == null) return false
                val deltaY = e1.y - e2.y
                return when {
                    deltaY > 100 && !isDrawerOpen -> {
                        slideUpDrawer(); true
                    }
                    deltaY < -100 && isDrawerOpen -> {
                        slideDownDrawer(); true
                    }
                    else -> false
                }
            }
        })

        rootLayout.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }


        // Load and filter apps
        val packageManager = packageManager
        val allApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val apps = allApps.filter { allowedApps.contains(it.packageName) }
            .map {
                val label = packageManager.getApplicationLabel(it).toString()
                val icon = packageManager.getApplicationIcon(it)
                AppObject(label, icon, it.packageName)
            }

        allFilteredApps = apps
        adapter = AppAdapter(this, allFilteredApps)
        appGridView.adapter = adapter
    }

    private fun slideUpDrawer() {
        appDrawer.animate()
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
        blurOverlay.visibility = View.VISIBLE
        blurOverlay.animate()
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
        isDrawerOpen = true
    }

    private fun slideDownDrawer() {
        appDrawer.animate()
            .translationY(appDrawer.height.toFloat())
            .setDuration(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
        blurOverlay.animate()
            .alpha(0f)
            .setDuration(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                blurOverlay.visibility = View.GONE
            }.start()
        isDrawerOpen = false
    }

    private val timeRunnable = object : Runnable {
        override fun run() {
            updateDateTime()
            handler.postDelayed(this, 1000)
        }
    }

    private fun updateDateTime() {
        val now = Calendar.getInstance().time
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        timeText.text = timeFormat.format(now)
        dateText.text = dateFormat.format(now)
    }

    override fun onBackPressed() {
        if (isDrawerOpen) {
            slideDownDrawer()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeRunnable)
    }
}
