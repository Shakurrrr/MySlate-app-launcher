package com.myslates.launcher

import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity() {

    private lateinit var appDrawer: View
    private lateinit var rootLayout: View
    private lateinit var homeContainer: FrameLayout
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
    private lateinit var gestureDetector: GestureDetector
    private lateinit var bottomBar: LinearLayout
    private val droppedApps = mutableListOf<DroppedApp>()

    private val handler = Handler(Looper.getMainLooper())
    private var isDrawerOpen = false
    private var downX: Float = 0f
    private var downY: Float = 0f

    private val allowedApps = listOf(
        "com.ATS.MySlates.Parent",
        "com.ATS.MySlates",
        "com.ATS.MySlates.Teacher",
        "com.adobe.reader"
    )

    private fun launchApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "App not found: $packageName", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot launch app: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("Launcher", "Failed to launch $packageName", e)
        }
    }

    fun addTapEffect(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(100).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }
            }
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(R.style.Theme_MySlates_Dark)
        setContentView(R.layout.activity_main)

        appDrawer = findViewById(R.id.app_drawer)
        rootLayout = findViewById(R.id.root_layout)
        homeContainer = findViewById(R.id.home_container)
        blurOverlay = findViewById(R.id.blur_overlay)
        timeText = findViewById(R.id.text_time)
        dateText = findViewById(R.id.text_date)
        weatherText = findViewById(R.id.text_weather)
        appGridView = findViewById(R.id.app_grid)
        searchInput = findViewById(R.id.search_input)
        searchIcon = findViewById(R.id.search_icon)
        leftPanel = findViewById(R.id.left_panel)
        rightPanel = findViewById(R.id.right_panel)
        bottomBar = findViewById(R.id.bottom_bar)

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                downX = e.x
                downY = e.y
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (e1 == null || e2 == null) return false
                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y

                if (Math.abs(deltaY) > Math.abs(deltaX)) {
                    if (deltaY > 150 && isDrawerOpen) {
                        slideDownDrawer()
                        return true
                    } else if (deltaY < -150 && !isDrawerOpen) {
                        slideUpDrawer()
                        return true
                    }
                } else {
                    if (deltaX < -150) {
                        if (leftPanel.visibility == View.VISIBLE) hidePanels() else showRightPanel()
                        return true
                    } else if (deltaX > 150) {
                        if (rightPanel.visibility == View.VISIBLE) hidePanels() else showLeftPanel()
                        return true
                    }
                }
                return false
            }
        })

        val forwardTouchListener = View.OnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        listOf(rootLayout, appDrawer, homeContainer, leftPanel, rightPanel, appGridView).forEach {
            it.setOnTouchListener(forwardTouchListener)
        }

        appDrawer.post { appDrawer.translationY = appDrawer.height.toFloat() }
        blurOverlay.alpha = 0f
        blurOverlay.visibility = View.GONE
        weatherText.text = "\u2600 24\u00b0"

        handler.post(timeRunnable)
        setupDragAndDrop()

        searchInput.setOnTouchListener { v, _ ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }

        loadHomeApps(bottomBar)

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
    }

    private fun loadHomeApps(container: LinearLayout) {
        val pm = packageManager
        allowedApps.forEach { packageName ->
            try {
                val appIntent = pm.getLaunchIntentForPackage(packageName)
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val appLabel = pm.getApplicationLabel(appInfo).toString()
                val appIcon = pm.getApplicationIcon(packageName)

                val appView = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(8, 8, 8, 8)
                    }

                    val iconView = ImageView(this@MainActivity).apply {
                        setImageDrawable(appIcon)
                        layoutParams = LinearLayout.LayoutParams(96, 96)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        background = ContextCompat.getDrawable(this@MainActivity, R.drawable.round_icon_bg)
                        clipToOutline = true
                    }


                    val labelView = TextView(this@MainActivity).apply {
                        text = appLabel
                        textSize = 12f
                        gravity = Gravity.CENTER
                        setTextColor(android.graphics.Color.WHITE)
                    }

                    addView(iconView)
                    addView(labelView)

                    setOnLongClickListener {
                        val clipData = ClipData.newPlainText("package", packageName)
                        val shadow = View.DragShadowBuilder(this)
                        startDragAndDrop(clipData, shadow, AppObject(appLabel, appIcon, packageName), 0)
                        true
                    }
                }

                container.addView(appView)
            } catch (e: Exception) {
                Log.e("Launcher", "App not found: $packageName")
            }
        }
    }

    private fun setupDragAndDrop() {
        rootLayout.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    blurOverlay.visibility = View.VISIBLE
                    blurOverlay.alpha = 0.3f
                    homeContainer.alpha = 0.8f
                    true
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    blurOverlay.visibility = View.GONE
                    blurOverlay.alpha = 0f
                    homeContainer.alpha = 1f
                    true
                }

                DragEvent.ACTION_DROP -> {
                    val clipData = event.clipData
                    val packageName = clipData?.getItemAt(0)?.text?.toString() ?: return@setOnDragListener false
                    val appObject = event.localState as? AppObject ?: return@setOnDragListener false

                    // Snap to grid (4 columns)
                    val gridSize = dpToPx(96 + 32) // icon + margin
                    val snappedX = ((event.x / gridSize).toInt() * gridSize).toFloat()
                    val snappedY = ((event.y / gridSize).toInt() * gridSize).toFloat()

                    // Remove existing view if already added
                    val existing = droppedApps.find { it.packageName == packageName }
                    if (existing != null) {
                        existing.view?.let { homeContainer.removeView(it) }
                        droppedApps.remove(existing)
                    }

                    // Add new view
                    val droppedApp = DroppedApp(appObject.label, appObject.icon, packageName, snappedX, snappedY)
                    addDroppedAppToHomeScreen(droppedApp)
                    droppedApps.add(droppedApp)

                    true
                }

                else -> false
            }
        }
    }
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }


    private fun addDroppedAppToHomeScreen(app: DroppedApp) {
        val view = LayoutInflater.from(this).inflate(R.layout.home_app_item, null)
        val icon = view.findViewById<ImageView>(R.id.home_app_icon)
        val label = view.findViewById<TextView>(R.id.home_app_label)

        icon.setImageDrawable(app.icon)
        label.text = app.label

        // Setup drag again from home
        view.setOnLongClickListener {
            val clipData = ClipData.newPlainText("package", app.packageName)
            val shadow = View.DragShadowBuilder(view)
            view.startDragAndDrop(clipData, shadow, AppObject(app.label, app.icon, app.packageName), 0)
            true
        }

        // Setup tap
        view.setOnClickListener {
            launchApp(app.packageName)
        }

        // Add and store view
        app.view = view
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.leftMargin = app.x.toInt()
        layoutParams.topMargin = app.y.toInt()

        homeContainer.addView(view, layoutParams)
    }




    private fun showRemoveAppDialog(droppedApp: DroppedApp, appView: View) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Remove App")
        builder.setMessage("Remove ${droppedApp.label} from home screen?")
        builder.setPositiveButton("Remove") { _, _ ->
            homeContainer.removeView(appView)
            droppedApps.remove(droppedApp)
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    fun openSettings(view: View) {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error launching settings", e)
            Toast.makeText(this, "Unable to open Settings.", Toast.LENGTH_SHORT).show()
        }
    }


    private fun slideUpDrawer() {
        if (!isFinishing && !isDestroyed) {
            appDrawer.animate().translationY(0f).setDuration(300).start()
            blurOverlay.visibility = View.VISIBLE
            blurOverlay.animate().alpha(1f).setDuration(300).start()
            isDrawerOpen = true
        }
    }

    private fun slideDownDrawer() {
        if (!isFinishing && !isDestroyed) {
            appDrawer.animate().translationY(appDrawer.height.toFloat()).setDuration(300).start()
            blurOverlay.animate().alpha(0f).setDuration(300).withEndAction {
                blurOverlay.visibility = View.GONE
            }.start()
            searchInput.clearFocus()
            searchInput.setText("")
            isDrawerOpen = false
        }
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