package com.myslates.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {


    private lateinit var appDrawer: View
    private lateinit var rootLayout: FrameLayout
    private lateinit var homeContainer: FrameLayout
    private lateinit var blurOverlay: View
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var weatherText: TextView
    private lateinit var appGridView: GridView
    private lateinit var homeGridRecyclerView: RecyclerView
    private lateinit var searchInput: EditText
    private lateinit var searchIcon: ImageView
    private lateinit var adapter: AppAdapter
    private lateinit var homeGridAdapter: HomeGridAdapter
    private lateinit var allFilteredApps: List<AppObject>
    private lateinit var leftPanel: View
    private lateinit var rightPanel: View
    private lateinit var gestureDetector: GestureDetector
    private lateinit var bottomBar: LinearLayout
    private lateinit var removeAppZone: View
    private lateinit var pageIndicator: LinearLayout
    private lateinit var viewPager: androidx.viewpager2.widget.ViewPager2

    private val handler = Handler(Looper.getMainLooper())
    private var isDrawerOpen = false
    private var isDragging = false
    private var currentPage = 0
    private val maxAppsPerPage = 20 // 4x5 grid
    private val homeScreenApps = mutableListOf<AppObject?>()

    private val allowedApps = listOf(
        "com.ATS.MySlates.Parent",
        "com.ATS.MySlates",
        "com.ATS.MySlates.Teacher",
        "com.adobe.reader"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable immersive mode
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        setTheme(R.style.Theme_MySlates_Dark)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupDragAndDrop()
        setupHomeGrid()
        loadApps()
        startTimeUpdater()
        setupSwipeGestures()

        // Initialize with empty grid
        repeat(maxAppsPerPage) { homeScreenApps.add(null) }
        homeGridAdapter.notifyDataSetChanged()
    }

    private fun initializeViews() {
        appDrawer = findViewById(R.id.app_drawer)
        rootLayout = findViewById(R.id.root_layout)
        homeContainer = findViewById(R.id.home_container)
        blurOverlay = findViewById(R.id.blur_overlay)
        timeText = findViewById(R.id.text_time)
        dateText = findViewById(R.id.text_date)
        weatherText = findViewById(R.id.text_weather)
        appGridView = findViewById(R.id.app_grid)
        homeGridRecyclerView = findViewById(R.id.home_grid_recycler)
        searchInput = findViewById(R.id.search_input)
        searchIcon = findViewById(R.id.search_icon)
        leftPanel = findViewById(R.id.left_panel)
        rightPanel = findViewById(R.id.right_panel)
        bottomBar = findViewById(R.id.bottom_bar)
        removeAppZone = findViewById(R.id.remove_app_zone)
        pageIndicator = findViewById(R.id.page_indicator)



        homeContainer.isClickable = true
        homeContainer.isFocusable = true
        homeContainer.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }

        // Initial setup
        appDrawer.post { appDrawer.translationY = appDrawer.height.toFloat() }
        blurOverlay.alpha = 0f
        blurOverlay.visibility = View.GONE
        removeAppZone.visibility = View.GONE
        weatherText.text = "☀ 24°"
    }

    private fun setupSwipeGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (e1 == null || e2 == null) return false
                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y

                return when {
                    Math.abs(deltaY) > Math.abs(deltaX) -> {
                        if (deltaY > 150 && isDrawerOpen) {
                            slideDownDrawer(); true
                        } else if (deltaY < -150 && !isDrawerOpen) {
                            slideUpDrawer(); true
                        } else false
                    }

                    deltaX < -150 -> { showRightPanel(); true }
                    deltaX > 150 -> { showLeftPanel(); true }
                    else -> false
                }
            }
        })

        val listener = View.OnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        listOf(rootLayout, homeContainer, appDrawer, appGridView).forEach {
            it.setOnTouchListener(listener)
        }
    }

    private fun setupHomeGrid() {
        homeGridAdapter = HomeGridAdapter(
            context = this,
            apps = homeScreenApps,
            onAppClick = { app -> launchApp(app.packageName) },
            onAppLongClick = { app, position -> startHomeAppDrag(app, position) },
            onEmptySlotDrop = { position, app -> handleAppDrop(position, app) },
            onAppDrag = { app, position -> startHomeAppDrag(app, position) }
        )

        homeGridRecyclerView.layoutManager = GridLayoutManager(this, 4)
        homeGridRecyclerView.adapter = homeGridAdapter
        homeGridRecyclerView.itemAnimator = null // Disable animations for smoother drag
    }

    private fun setupDragAndDrop() {
        // Global drag listener for the entire root layout
        rootLayout.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    Log.d("MainActivity", "Drag started globally")
                    isDragging = true
                    showDragFeedback()

                    if (isDrawerOpen) slideDownDrawerForDrop()
                    true
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    Log.d("MainActivity", "Drag ended globally")
                    isDragging = false
                    hideDragFeedback()

                    val dragData = event.localState as? DragData
                    if (!event.result && dragData?.isFromHomeScreen == true && dragData.originalPosition >= 0) {
                        homeScreenApps[dragData.originalPosition] = dragData.app
                        homeGridAdapter.notifyItemChanged(dragData.originalPosition)
                        Log.d("MainActivity", "Restored app to original position: ${dragData.originalPosition}")
                    }
                    true
                }

                DragEvent.ACTION_DROP -> {
                    Log.d("MainActivity", "Unhandled drop on root layout")
                    false
                }

                else -> false
            }
        }

        // Dedicated drop handler for REMOVE zone
        removeAppZone.setOnDragListener { _, event ->
            val dragData = event.localState as? DragData

            when (event.action) {
                DragEvent.ACTION_DRAG_ENTERED -> {
                    removeAppZone.setBackgroundColor(Color.parseColor("#B00020")) // Danger red
                    removeAppZone.alpha = 1.0f
                    true
                }

                DragEvent.ACTION_DRAG_EXITED -> {
                    removeAppZone.setBackgroundColor(Color.TRANSPARENT)
                    removeAppZone.alpha = 0.8f
                    true
                }

                DragEvent.ACTION_DROP -> {
                    if (dragData != null && dragData.isFromHomeScreen) {
                        val position = dragData.originalPosition
                        if (position in homeScreenApps.indices) {
                            val removedLabel = homeScreenApps[position]?.label ?: "App"
                            homeScreenApps[position] = null
                            homeGridAdapter.notifyItemChanged(position)
                            Toast.makeText(this, "$removedLabel removed", Toast.LENGTH_SHORT).show()
                            Log.d("MainActivity", "Removed app from position $position")
                        }
                    }
                    true
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    removeAppZone.setBackgroundColor(Color.TRANSPARENT)
                    removeAppZone.alpha = 0.8f
                    true
                }

                else -> false
            }
        }
    }


    private fun showDragFeedback() {
        // Show blur overlay
        blurOverlay.visibility = View.VISIBLE
        blurOverlay.animate().alpha(0.5f).setDuration(200).start()

        // Show remove zone
        removeAppZone.visibility = View.VISIBLE
        removeAppZone.alpha = 0f
        removeAppZone.animate().alpha(1f).setDuration(200).start()

        // Dim home container slightly
        homeContainer.animate().alpha(0.8f).setDuration(200).start()
    }

    private fun hideDragFeedback() {
        // Hide blur overlay
        blurOverlay.animate().alpha(0f).setDuration(200).withEndAction {
            blurOverlay.visibility = View.GONE
        }.start()

        // Hide remove zone
        removeAppZone.animate().alpha(0f).setDuration(200).withEndAction {
            removeAppZone.visibility = View.GONE
        }.start()

        // Restore home container
        homeContainer.animate().alpha(1f).setDuration(200).start()
    }

    private fun slideDownDrawerForDrop() {
        appDrawer.animate()
            .translationY(appDrawer.height.toFloat())
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
        isDrawerOpen = false
    }

    private fun startHomeAppDrag(app: AppObject, position: Int) {
        Log.d("MainActivity", "Starting home app drag: ${app.label} at position $position")

        val clipData = ClipData.newPlainText("home_app", "${app.packageName}|$position")
        val dragData = DragData(app, position, true)


        val dragView = createDragShadow(app)
        val shadow = View.DragShadowBuilder(dragView)

        homeGridRecyclerView.startDragAndDrop(clipData, shadow, dragData, View.DRAG_FLAG_GLOBAL)

        homeScreenApps[position] = null
        homeGridAdapter.notifyItemChanged(position)
    }


    private fun handleAppDrop(position: Int, app: AppObject): Boolean {
        Log.d("MainActivity", "Handling app drop at position $position for ${app.label}")

        // Check if position is valid
        if (position < 0 || position >= homeScreenApps.size) {
            Log.e("MainActivity", "Invalid drop position: $position")
            return false
        }


        // If slot is occupied, find next empty slot
        var targetPosition = position
        if (homeScreenApps[targetPosition] != null) {
            targetPosition = findNextEmptySlot(position)
            if (targetPosition == -1) {
                Toast.makeText(this, "No empty slots available", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        // Place app in new position
        homeScreenApps[targetPosition] = app
        homeGridAdapter.notifyItemChanged(targetPosition)
        homeGridRecyclerView.post {
            homeGridAdapter.notifyDataSetChanged()
        }

        Log.d("MainActivity", "App ${app.label} placed at position $targetPosition")
        return true
    }


    private fun findNextEmptySlot(startPosition: Int): Int {
        // Look forward first
        for (i in startPosition until homeScreenApps.size) {
            if (homeScreenApps[i] == null) return i
        }
        // Look backward
        for (i in startPosition - 1 downTo 0) {
            if (homeScreenApps[i] == null) return i
        }
        return -1
    }

    private fun createDragShadow(app: AppObject): View {
        val dragView = LayoutInflater.from(this).inflate(R.layout.drag_shadow_item, null)
        val iconView = dragView.findViewById<ImageView>(R.id.drag_icon)
        val labelView = dragView.findViewById<TextView>(R.id.drag_label)

        iconView.setImageDrawable(app.icon)
        labelView.text = app.label

        // Measure and layout the view
        dragView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        dragView.layout(0, 0, dragView.measuredWidth, dragView.measuredHeight)

        return dragView
    }

    private fun loadApps() {
        val pm = packageManager
        allFilteredApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { allowedApps.contains(it.packageName) }
            .map {
                val label = pm.getApplicationLabel(it).toString()
                val icon = pm.getApplicationIcon(it)
                AppObject(label, icon, it.packageName)
            }

        adapter = AppAdapter(this, allFilteredApps) { app ->
            handleDrawerAppDrag(app)
        }
        appGridView.adapter = adapter

        // Setup search
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

        loadBottomBarApps()
    }

    private fun handleDrawerAppDrag(app: AppObject) {
        Log.d("MainActivity", "Handling drawer app drag: ${app.label}")

        // Find empty slot
        val emptySlot = findNextEmptySlot(0)
        if (emptySlot == -1) {
            Toast.makeText(this, "Home screen is full", Toast.LENGTH_SHORT).show()
            return
        }

        val clipData = ClipData.newPlainText("drawer_app", app.packageName)
        val dragData = DragData(app, -1, false)
        val shadow = View.DragShadowBuilder(createDragShadow(app))
        rootLayout.startDragAndDrop(clipData, shadow, dragData, View.DRAG_FLAG_GLOBAL)


        // Close drawer after successful drop
        handler.postDelayed({
            if (isDrawerOpen) {
                slideDownDrawer()
            }
        }, 300)
    }

    private fun loadBottomBarApps() {
        bottomBar.removeAllViews()

        val bottomApps = listOf(
            "com.ATS.MySlates.Parent",
            "com.ATS.MySlates",
            "com.ATS.MySlates.Teacher",
            "com.adobe.reader"
        )

        bottomApps.forEach { packageName ->
            try {
                val pm = packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val appLabel = pm.getApplicationLabel(appInfo).toString()
                val appIcon = pm.getApplicationIcon(packageName)

                val appView = createBottomBarAppView(appLabel, appIcon, packageName)
                bottomBar.addView(appView)
            } catch (e: Exception) {
                Log.e("MainActivity", "Bottom bar app not found: $packageName")
            }
        }
    }

    private fun createBottomBarAppView(label: String, icon: android.graphics.drawable.Drawable, packageName: String): View {
        val appView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(16, 16, 16, 16)
            }
        }

        val iconView = ImageView(this).apply {
            setImageDrawable(icon)
            layoutParams = LinearLayout.LayoutParams(dpToPx(56), dpToPx(56))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.modern_icon_bg)
            clipToOutline = true
            elevation = dpToPx(4).toFloat()
        }

        val labelView = TextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            alpha = 0.9f
        }

        appView.addView(iconView)
        appView.addView(labelView)

        // Click listener
        appView.setOnClickListener { launchApp(packageName) }

        // Long click for drag
        appView.setOnLongClickListener {
            val app = AppObject(label, icon, packageName)
            val clipData = ClipData.newPlainText("drawer_app", packageName)
            val shadow = View.DragShadowBuilder(createDragShadow(app))
            appView.startDragAndDrop(clipData, shadow, DragData(app, -1, false), View.DRAG_FLAG_GLOBAL)
            true
        }

        // Add modern touch feedback
        addModernTouchFeedback(appView)

        return appView
    }

    private fun addModernTouchFeedback(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.9f)
                        .scaleY(0.9f)
                        .alpha(0.7f)
                        .setDuration(100)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(100)
                        .start()
                }
            }
            false
        }
    }

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
            Log.e("MainActivity", "Failed to launch $packageName", e)
        }
    }

    private fun slideUpDrawer() {
        if (!isFinishing && !isDestroyed) {
            appDrawer.animate()
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
            blurOverlay.visibility = View.VISIBLE
            blurOverlay.animate().alpha(0.7f).setDuration(300).start()
            isDrawerOpen = true
        }
    }

    private fun slideDownDrawer() {
        if (!isFinishing && !isDestroyed) {
            appDrawer.animate()
                .translationY(appDrawer.height.toFloat())
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()

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
        leftPanel.animate()
            .translationX(0f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun showRightPanel() {
        rightPanel.visibility = View.VISIBLE
        rightPanel.translationX = rightPanel.width.toFloat()
        rightPanel.animate()
            .translationX(0f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun hidePanels() {
        if (leftPanel.visibility == View.VISIBLE) {
            leftPanel.animate()
                .translationX(-leftPanel.width.toFloat())
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction { leftPanel.visibility = View.GONE }
                .start()
        }
        if (rightPanel.visibility == View.VISIBLE) {
            rightPanel.animate()
                .translationX(rightPanel.width.toFloat())
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction { rightPanel.visibility = View.GONE }
                .start()
        }
    }

    private fun startTimeUpdater() {
        val timeRunnable = object : Runnable {
            override fun run() {
                val now = Calendar.getInstance().time
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                timeText.text = timeFormat.format(now)
                dateText.text = dateFormat.format(now)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timeRunnable)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
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
        handler.removeCallbacksAndMessages(null)
    }

}