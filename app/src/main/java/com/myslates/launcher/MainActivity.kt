package com.myslates.launcher

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var appDrawer: View
    private lateinit var rootLayout: View
    private lateinit var blurOverlay: View
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var weatherText: TextView
    private lateinit var appGridView: GridView
    private lateinit var searchInput: EditText
    private lateinit var searchIcon: ImageView
    private lateinit var adapter: AppAdapter
    private lateinit var allFilteredApps: List<AppObject>
    private lateinit var leftPanel: View
    private lateinit var rightPanel: View
    private lateinit var closeLeftPanel: ImageView
    private lateinit var closeRightPanel: ImageView
    private lateinit var gestureDetector: GestureDetector

    private val handler = Handler(Looper.getMainLooper())
    private var isDrawerOpen = false
    private var downX: Float = 0f
    private var downY: Float = 0f

    private val allowedApps = listOf(
        "com.ATS.MySlates.Parent",
        "com.ATS.MySlates",
        "com.ATS.MySlates.Teacher",
        "com.adobe.reader",
        "com.android.settings"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(R.style.Theme_MySlates_Dark)
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
        leftPanel = findViewById(R.id.left_panel)
        rightPanel = findViewById(R.id.right_panel)
        closeLeftPanel = findViewById(R.id.close_left_panel)
        closeRightPanel = findViewById(R.id.close_right_panel)

        // Initial states
        appDrawer.post { appDrawer.translationY = appDrawer.height.toFloat() }
        blurOverlay.alpha = 0f
        blurOverlay.visibility = View.GONE
        weatherText.text = "☀ 24°"

        // Clock
        handler.post(timeRunnable)

        // Panel close buttons
        closeLeftPanel.setOnClickListener { hidePanels() }
        closeRightPanel.setOnClickListener { hidePanels() }

        // Search functionality
        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val filtered = allFilteredApps.filter {
                    it.label.contains(s.toString(), ignoreCase = true)
                }
                adapter.updateData(filtered)
            }
        })

        // Load apps
        val pm = packageManager
        allFilteredApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { allowedApps.contains(it.packageName) }
            .map {
                val label = pm.getApplicationLabel(it).toString()
                val icon = pm.getApplicationIcon(it)
                AppObject(label, icon, it.packageName)
            }
        adapter = AppAdapter(this, allFilteredApps)
        appGridView.adapter = adapter

        // Gesture detector
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                downX = e.x
                downY = e.y
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null || e2 == null) return false
                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y

                return when {
                    deltaY < -150 -> {
                        if (!isDrawerOpen) slideUpDrawer()
                        true
                    }
                    deltaY > 150 -> {
                        if (isDrawerOpen) slideDownDrawer()
                        true
                    }
                    deltaX < -150 -> {
                        if (leftPanel.visibility == View.VISIBLE) hidePanels()
                        else showRightPanel()
                        true
                    }
                    deltaX > 150 -> {
                        if (rightPanel.visibility == View.VISIBLE) hidePanels()
                        else showLeftPanel()
                        true
                    }
                    else -> false
                }
            }
        })

        // Attach to root
        rootLayout.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
        appDrawer.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
    }

    private fun slideUpDrawer() {
        appDrawer.animate().translationY(0f).setDuration(300).start()
        blurOverlay.visibility = View.VISIBLE
        blurOverlay.animate().alpha(1f).setDuration(300).start()
        isDrawerOpen = true
    }

    private fun slideDownDrawer() {
        appDrawer.animate().translationY(appDrawer.height.toFloat()).setDuration(300).start()
        blurOverlay.animate().alpha(0f).setDuration(300).withEndAction {
            blurOverlay.visibility = View.GONE
        }.start()
        isDrawerOpen = false
    }

    private fun showLeftPanel() {
        leftPanel.visibility = View.VISIBLE
        leftPanel.translationX = -leftPanel.width.toFloat()
        leftPanel.animate().translationX(0f).setDuration(300).start()
    }

    private fun showRightPanel() {
        rightPanel.visibility = View.VISIBLE
        rightPanel.translationX = rightPanel.width.toFloat()
        rightPanel.animate().translationX(0f).setDuration(300).start()
    }

    private fun hidePanels() {
        if (leftPanel.visibility == View.VISIBLE) {
            leftPanel.animate().translationX(-leftPanel.width.toFloat()).setDuration(300).withEndAction {
                leftPanel.visibility = View.GONE
            }.start()
        }
        if (rightPanel.visibility == View.VISIBLE) {
            rightPanel.animate().translationX(rightPanel.width.toFloat()).setDuration(300).withEndAction {
                rightPanel.visibility = View.GONE
            }.start()
        }
    }

    private val timeRunnable = object : Runnable {
        override fun run() {
            val now = Calendar.getInstance().time
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
            timeText.text = timeFormat.format(now)
            dateText.text = dateFormat.format(now)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBackPressed() {
        when {
            isDrawerOpen -> slideDownDrawer()
            leftPanel.visibility == View.VISIBLE || rightPanel.visibility == View.VISIBLE -> hidePanels()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeRunnable)
    }
}
