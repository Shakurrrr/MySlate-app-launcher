package com.myslates.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.net.URL
import java.util.concurrent.Executors
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONObject
import kotlin.random.Random

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

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var isDrawerOpen = false
    private var isDragging = false
    private var isPanelOpen = false
    private var currentPage = 0
    private val maxAppsPerPage = 15 // 3x5 grid
    private val homeScreenApps = mutableListOf<AppObject?>()

    // Security components
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private val PARENTAL_PIN = "123456" // In production, store securely
    
    private val isTablet get() = resources.configuration.smallestScreenWidthDp >= 600
    private val HOME_ICON_DP   get() = if (isTablet) 120 else 80
    private val DRAWER_ICON_DP get() = if (isTablet) 110 else 70
    private val DOCK_ICON_DP   get() = if (isTablet) 100 else 60
    private val LABEL_SP       get() = if (isTablet) 18f else 14f
    private val TIME_SP        get() = if (isTablet) 96f else 72f
    private val DATE_SP        get() = if (isTablet) 28f else 20f

    private fun dp(dp: Int) = (dp * resources.displayMetrics.density).toInt()


    private val allowedApps = listOf(
        "com.android.settings",
        "com.ATS.MySlates",
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

        initializeSecurity()
        initializeViews()
        setupDragAndDrop()
        setupHomeGrid()
        loadApps()
        startTimeUpdater()
        setupSwipeGestures()

        // Initialize with empty grid
        repeat(maxAppsPerPage) { homeScreenApps.add(null) }
        homeGridAdapter.notifyDataSetChanged()
        
        // Apply tablet scaling
        applyTabletScaling()
        
        // Enable kiosk mode
        enableKioskMode()
    }
    
    private fun initializeSecurity() {
        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, LauncherDeviceAdminReceiver::class.java)
        
        // Request device admin if not already granted
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, 
                "Enable device admin to secure the launcher")
            startActivity(intent)
        }
        
        // Apply user restrictions
        applyUserRestrictions()
    }
    
    private fun applyUserRestrictions() {
        if (devicePolicyManager.isAdminActive(adminComponent)) {
            try {
                // Disable factory reset
                devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                
                // Disable adding accounts
                devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_MODIFY_ACCOUNTS)
                
                // Disable installing apps
                devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_APPS)
                
                // Disable uninstalling apps
                devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_UNINSTALL_APPS)
                
                // Disable USB file transfer
                devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_USB_FILE_TRANSFER)
                
                Log.d("Security", "User restrictions applied successfully")
            } catch (e: Exception) {
                Log.e("Security", "Failed to apply user restrictions", e)
            }
        }
    }
    
    private fun enableKioskMode() {
        try {
            if (devicePolicyManager.isAdminActive(adminComponent)) {
                // Set as device owner if possible
                if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
                    // Enable lock task mode
                    devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf(packageName))
                    startLockTask()
                    Log.d("Security", "Kiosk mode enabled")
                }
            }
        } catch (e: Exception) {
            Log.e("Security", "Failed to enable kiosk mode", e)
        }
    }
    
    private fun applyTabletScaling() {
        // Scale time text
        timeText.textSize = TIME_SP
        
        // Scale date and weather text
        dateText.textSize = DATE_SP
        weatherText.textSize = DATE_SP
        
        // Adjust margins for tablets
        val topInfoBar = findViewById<LinearLayout>(R.id.top_info_bar)
        val params = topInfoBar.layoutParams as FrameLayout.LayoutParams
        params.topMargin = if (isTablet) dp(80) else dp(64)
        params.marginStart = if (isTablet) dp(48) else dp(32)
        topInfoBar.layoutParams = params
        
        // Adjust grid margins
        val gridParams = homeGridRecyclerView.layoutParams as FrameLayout.LayoutParams
        gridParams.topMargin = if (isTablet) dp(200) else dp(160)
        gridParams.bottomMargin = if (isTablet) dp(200) else dp(160)
        gridParams.marginStart = if (isTablet) dp(48) else dp(32)
        gridParams.marginEnd = if (isTablet) dp(48) else dp(32)
        homeGridRecyclerView.layoutParams = gridParams
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

        // Initial setup
        appDrawer.post { appDrawer.translationY = appDrawer.height.toFloat() }
        blurOverlay.alpha = 0f
        blurOverlay.visibility = View.GONE
        removeAppZone.visibility = View.GONE
        
        // Start weather updates
        updateWeather()
    }

    private fun setupSwipeGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null || e2 == null) return false
                if (isDragging || isPanelOpen) return false
                
                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y
                
                return when {
                    Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(velocityY) > 800 -> {
                        if (deltaY > 0 && isDrawerOpen) {
                            slideDownDrawer(); true
                        } else if (deltaY < 0 && !isDrawerOpen) {
                            slideUpDrawer(); true
                        } else false
                    }
                    Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(velocityX) > 800 -> {
                        if (deltaX < 0) {
                            showRightPanel(); true
                        } else if (deltaX > 0) {
                            showLeftPanel(); true
                        } else false
                    }
                    else -> false
                }
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (e1 == null || e2 == null) return false
                if (isDragging) return false
                
                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y
                val minDistance = 80f

                return when {
                    Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(deltaY) > minDistance -> {
                        if (deltaY > 0 && isDrawerOpen && !isPanelOpen) {
                            slideDownDrawer(); true
                        } else if (deltaY < 0 && !isDrawerOpen && !isPanelOpen) {
                            slideUpDrawer(); true
                        } else false
                    }
                    Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > minDistance -> {
                        if (deltaX < 0 && !isDrawerOpen && !isPanelOpen) {
                            showRightPanel(); true
                        } else if (deltaX > 0 && !isDrawerOpen && !isPanelOpen) {
                            showLeftPanel(); true
                        } else false
                    }
                    else -> false
                }
            }
        })

        // Apply gesture detection globally
        val gestureListener = View.OnTouchListener { _, event ->
            if (!isDragging && !isPanelOpen) {
                gestureDetector.onTouchEvent(event)
            }
            false // Allow other touch events to be processed
        }

        rootLayout.setOnTouchListener(gestureListener)
        homeContainer.setOnTouchListener(gestureListener)
        
        // Also add to other key views for better coverage
        timeText.setOnTouchListener(gestureListener)
        dateText.setOnTouchListener(gestureListener)
        weatherText.setOnTouchListener(gestureListener)
    }

    private fun setupHomeGrid() {
        homeGridAdapter = HomeGridAdapter(
            context = this,
            apps = homeScreenApps,
            onAppClick = { app -> launchApp(app.packageName) },
            onAppLongClick = { app, position -> startHomeAppDrag(app, position) },
            onEmptySlotDrop = { position, app -> handleAppDropWithDuplicateCheck(position, app) },
            onAppDrag = { app, position -> startHomeAppDrag(app, position) }
        )

        homeGridRecyclerView.layoutManager = GridLayoutManager(this, 3) // Changed to 3 columns
        homeGridRecyclerView.adapter = homeGridAdapter
        homeGridRecyclerView.itemAnimator = null // Disable animations for smoother drag
    }

    private fun setupDragAndDrop() {
        // Global drag listener for the entire root layout
        rootLayout.setOnDragListener { _, event ->
            val dragData = event.localState as? DragData
            
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    Log.d("MainActivity", "Drag started globally")
                    isDragging = true
                    showDragFeedback()

                    if (isDrawerOpen && dragData?.isFromHomeScreen != true) {
                        slideDownDrawerForDrop()
                    }
                    Log.d("MainActivity", "DRAG STARTED - type=${event.clipDescription?.label}")
                    true
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    Log.d("MainActivity", "Drag ended globally")
                    isDragging = false
                    hideDragFeedback()

                    if (!event.result && dragData?.isFromHomeScreen == true && dragData.originalPosition >= 0) {
                        homeScreenApps[dragData.originalPosition] = dragData.app
                        homeGridAdapter.notifyItemChanged(dragData.originalPosition)
                        Log.d("MainActivity", "Restored app to original position: ${dragData.originalPosition}")
                    }
                    true
                }

                DragEvent.ACTION_DROP -> {
                    Log.d("MainActivity", "Drop on root layout")
                    if (dragData != null && !dragData.isFromHomeScreen) {
                        // Check for duplicates before dropping
                        if (!isAppAlreadyOnHomeScreen(dragData.app)) {
                            val emptySlot = findNextEmptySlot(0)
                            if (emptySlot != -1) {
                                homeScreenApps[emptySlot] = dragData.app
                                homeGridAdapter.notifyItemChanged(emptySlot)
                                Toast.makeText(this, "${dragData.app.label} added to home screen", Toast.LENGTH_SHORT).show()
                                return@setOnDragListener true
                            } else {
                                Toast.makeText(this, "Home screen is full", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this, "${dragData.app.label} is already on home screen", Toast.LENGTH_SHORT).show()
                        }
                    }
                    false
                }

                else -> false
            }
        }

        // Dedicated drop handler for REMOVE zone
        removeAppZone.setOnDragListener { _, event ->
            val dragData = event.localState as? DragData

            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    // Only accept drops from home screen
                    dragData?.isFromHomeScreen == true
                }

                DragEvent.ACTION_DRAG_ENTERED -> {
                    if (dragData?.isFromHomeScreen == true) {
                        removeAppZone.setBackgroundColor(Color.parseColor("#FF5722"))
                        removeAppZone.alpha = 1f
                        removeAppZone.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                    }
                    true
                }

                DragEvent.ACTION_DRAG_EXITED -> {
                    removeAppZone.setBackgroundColor(Color.TRANSPARENT)
                    removeAppZone.alpha = 0.7f
                    removeAppZone.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    true
                }

                DragEvent.ACTION_DROP -> {
                    if (dragData?.isFromHomeScreen == true) {
                        val pos = dragData.originalPosition
                        if (pos in homeScreenApps.indices) {
                            homeScreenApps[pos] = null
                            homeGridAdapter.notifyItemChanged(pos)
                            Toast.makeText(this, "${dragData.app.label} removed", Toast.LENGTH_SHORT).show()
                            Log.d("MainActivity", "App removed from position $pos")
                        }
                        true
                    } else {
                        false
                    }
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    removeAppZone.setBackgroundColor(Color.TRANSPARENT)
                    removeAppZone.alpha = 0.7f
                    removeAppZone.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
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
            .setDuration(200)
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

    private fun handleAppDropWithDuplicateCheck(position: Int, app: AppObject): Boolean {
        if (position !in homeScreenApps.indices) return false
        
        // Check for duplicates
        if (isAppAlreadyOnHomeScreen(app)) {
            Toast.makeText(this, "${app.label} is already on home screen", Toast.LENGTH_SHORT).show()
            return false
        if (homeScreenApps[position] == null) {
            homeScreenApps[position] = app
            homeGridAdapter.notifyItemChanged(position)
            return true
        }

        val next = findNextEmptySlot(position)
        if (next != -1) {
            homeScreenApps[next] = app
            homeGridAdapter.notifyItemChanged(next)
            return true
        }

        Toast.makeText(this, "No empty slot available", Toast.LENGTH_SHORT).show()
        return false
    }

    private fun isAppAlreadyOnHomeScreen(app: AppObject): Boolean {
        return homeScreenApps.any { existingApp ->
            existingApp?.packageName == app.packageName
        }
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

        val clipData = ClipData.newPlainText("drawer_app", app.packageName)
        val dragData = DragData(app, -1, false)
        val shadow = View.DragShadowBuilder(createDragShadow(app))
        appGridView.startDragAndDrop(clipData, shadow, dragData, View.DRAG_FLAG_GLOBAL)
    }

    private fun loadBottomBarApps() {
        bottomBar.removeAllViews()

        val bottomApps = listOf(
            "com.android.settings",
            "com.ATS.MySlates",
            "com.adobe.reader"
        )

        bottomApps.forEach { packageName ->
            try {
                val pm = packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val appLabel = pm.getApplicationLabel(appInfo).toString()
                val appIcon = pm.getApplicationIcon(packageName)

                val appView = createBottomBarAppView(appLabel, appIcon, packageName, false)
                bottomBar.addView(appView)
            } catch (e: Exception) {
                Log.e("MainActivity", "Bottom bar app not found: $packageName")
            }
        }
    }

    private fun createBottomBarAppView(label: String, icon: android.graphics.drawable.Drawable, packageName: String, allowCopy: Boolean = false): View {
        val appView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(16, 16, 16, 16)
            }
        }

        val iconView = ImageView(this).apply {
            setImageDrawable(icon)
            layoutParams = LinearLayout.LayoutParams(dpToPx(DOCK_ICON_DP), dpToPx(DOCK_ICON_DP))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.modern_icon_bg)
            clipToOutline = true
            elevation = dpToPx(4).toFloat()
        }

        val labelView = TextView(this).apply {
            text = label
            textSize = LABEL_SP
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            alpha = 0.9f
            setPadding(0, dp(8), 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END

        }

        appView.addView(iconView)
        appView.addView(labelView)

        // Click listener
        appView.setOnClickListener { 
            if (packageName == "com.android.settings") {
                showPinDialog { launchApp(packageName) }
            } else {
                launchApp(packageName)
            }
        }

        // Long click for drag
        appView.setOnLongClickListener {
            val app = AppObject(label, icon, packageName)
            
            if (allowCopy) {
                // Allow copying to home screen
                val clipData = ClipData.newPlainText("bottom_app", packageName)
                val dragData = DragData(app, -1, false)
                val shadow = View.DragShadowBuilder(createDragShadow(app))
                appView.startDragAndDrop(clipData, shadow, dragData, View.DRAG_FLAG_GLOBAL)
            } else {
                // Move from bottom bar to home screen
                if (!isAppAlreadyOnHomeScreen(app)) {
                    val emptySlot = findNextEmptySlot(0)
                    if (emptySlot != -1) {
                        homeScreenApps[emptySlot] = app
                        homeGridAdapter.notifyItemChanged(emptySlot)
                        
                        // Remove from bottom bar
                        bottomBar.removeView(appView)
                        Toast.makeText(this, "${app.label} moved to home screen", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Home screen is full", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "${app.label} is already on home screen", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }

        // Add modern touch feedback
        addModernTouchFeedback(appView)

        return appView
    }

    private fun showPinDialog(onSuccess: () -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null)
        val editText = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter 6-digit PIN"
            maxLines = 1
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        
        AlertDialog.Builder(this)
            .setTitle("Parental Control")
            .setMessage("Enter PIN to access Settings")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val enteredPin = editText.text.toString()
                if (enteredPin == PARENTAL_PIN) {
                    onSuccess()
                } else {
                    Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
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
            // Intercept Settings app
            if (packageName == "com.android.settings") {
                showPinDialog {
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    if (intent != null) {
                        startActivity(intent)
                    }
                }
                return
            }
            
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
        if (isPanelOpen) return
        isPanelOpen = true
        
        // Setup calendar content
        setupCalendarPanel()
        
        leftPanel.visibility = View.VISIBLE
        leftPanel.translationX = -leftPanel.width.toFloat()
        leftPanel.animate()
            .translationX(0f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
            
        // Add tap to close
        leftPanel.setOnClickListener { hidePanels() }
    }

    private fun showRightPanel() {
        if (isPanelOpen) return
        isPanelOpen = true
        
        // Setup interesting content
        setupInterestingPanel()
        
        rightPanel.visibility = View.VISIBLE
        rightPanel.translationX = rightPanel.width.toFloat()
        rightPanel.animate()
            .translationX(0f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
            
        // Add tap to close
        rightPanel.setOnClickListener { hidePanels() }
    }

    private fun hidePanels() {
        isPanelOpen = false
        
        if (leftPanel.visibility == View.VISIBLE) {
            leftPanel.animate()
                .translationX(-leftPanel.width.toFloat())
                .setDuration(250)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction { 
                    leftPanel.visibility = View.GONE
                    leftPanel.setOnClickListener(null)
                }
                .start()
        }
        if (rightPanel.visibility == View.VISIBLE) {
            rightPanel.animate()
                .translationX(rightPanel.width.toFloat())
                .setDuration(250)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction { 
                    rightPanel.visibility = View.GONE
                    rightPanel.setOnClickListener(null)
                }
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

    private fun setupCalendarPanel() {
        val scrollView = leftPanel as ScrollView
        val container = scrollView.getChildAt(0) as LinearLayout
        container.removeAllViews()
        
        // Calendar Header
        val headerText = TextView(this).apply {
            text = "Calendar"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dpToPx(24))
        }
        container.addView(headerText)
        
        // Current Month
        val calendar = Calendar.getInstance()
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val monthText = TextView(this).apply {
            text = monthFormat.format(calendar.time)
            textSize = 20f
            setTextColor(Color.parseColor("#CCCCCC"))
            setPadding(0, 0, 0, dpToPx(16))
        }
        container.addView(monthText)
        
        // Mini Calendar Grid
        val calendarGrid = GridLayout(this).apply {
            columnCount = 7
            rowCount = 7
            setPadding(0, 0, 0, dpToPx(24))
        }
        
        // Day headers
        val dayHeaders = arrayOf("S", "M", "T", "W", "T", "F", "S")
        dayHeaders.forEach { day ->
            val dayView = TextView(this).apply {
                text = day
                textSize = 14f
                setTextColor(Color.parseColor("#888888"))
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dpToPx(40)
                    height = dpToPx(40)
                    setMargins(2, 2, 2, 2)
                }
            }
            calendarGrid.addView(dayView)
        }
        
        // Calendar days
        val firstDay = calendar.clone() as Calendar
        firstDay.set(Calendar.DAY_OF_MONTH, 1)
        val startDay = firstDay.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = calendar.get(Calendar.DAY_OF_MONTH)
        
        // Empty cells before month starts
        repeat(startDay) {
            val emptyView = View(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dpToPx(40)
                    height = dpToPx(40)
                }
            }
            calendarGrid.addView(emptyView)
        }
        
        // Days of the month
        for (day in 1..daysInMonth) {
            val dayView = TextView(this).apply {
                text = day.toString()
                textSize = 14f
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dpToPx(40)
                    height = dpToPx(40)
                    setMargins(2, 2, 2, 2)
                }
                
                if (day == today) {
                    setTextColor(Color.WHITE)
                    setBackgroundResource(R.drawable.modern_icon_bg)
                    background.setTint(Color.parseColor("#2196F3"))
                } else {
                    setTextColor(Color.parseColor("#CCCCCC"))
                }
            }
            calendarGrid.addView(dayView)
        }
        
        container.addView(calendarGrid)
        
        // Upcoming Events (mock data)
        val eventsHeader = TextView(this).apply {
            text = "Upcoming Events"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dpToPx(16), 0, dpToPx(16))
        }
        container.addView(eventsHeader)
        
        val events = listOf(
            "Meeting at 2:00 PM",
            "Lunch with team at 12:30 PM",
            "Project deadline tomorrow"
        )
        
        events.forEach { event ->
            val eventView = TextView(this).apply {
                text = "• $event"
                textSize = 16f
                setTextColor(Color.parseColor("#AAAAAA"))
                setPadding(0, dpToPx(8), 0, dpToPx(8))
            }
            container.addView(eventView)
        }
    }
    
    private fun setupInterestingPanel() {
        val scrollView = rightPanel as ScrollView
        val container = scrollView.getChildAt(0) as LinearLayout
        container.removeAllViews()
        
        // Header
        val headerText = TextView(this).apply {
            text = "Quick Info"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dpToPx(24))
        }
        container.addView(headerText)
        
        // System Info Card
        val systemCard = createInfoCard("System Status", listOf(
            "Battery: ${getBatteryLevel()}%",
            "Storage: ${getStorageInfo()}",
            "Memory: ${getMemoryInfo()}"
        ))
        container.addView(systemCard)
        
        // Fun Facts Card
        val funFacts = listOf(
            "Did you know? Octopuses have three hearts!",
            "A group of flamingos is called a 'flamboyance'",
            "Honey never spoils - it's been found in ancient tombs!",
            "Bananas are berries, but strawberries aren't!",
            "A day on Venus is longer than its year!"
        )
        val randomFact = funFacts[Random.nextInt(funFacts.size)]
        val factCard = createInfoCard("Fun Fact", listOf(randomFact))
        container.addView(factCard)
        
        // Quick Actions Card
        val actionsCard = createInfoCard("Quick Actions", listOf(
            "📱 Device Settings",
            "🔋 Battery Optimization", 
            "📶 Network Settings",
            "🔊 Sound Settings"
        ))
        container.addView(actionsCard)
    }
    
    private fun createInfoCard(title: String, items: List<String>): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.modern_icon_bg)
            background.setTint(Color.parseColor("#33FFFFFF"))
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dpToPx(16))
            }
        }
        
        val titleView = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dpToPx(12))
        }
        card.addView(titleView)
        
        items.forEach { item ->
            val itemView = TextView(this).apply {
                text = item
                textSize = 14f
                setTextColor(Color.parseColor("#CCCCCC"))
                setPadding(0, dpToPx(4), 0, dpToPx(4))
            }
            card.addView(itemView)
        }
        
        return card
    }
    
    private fun getBatteryLevel(): Int {
        val batteryManager = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        return batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    
    private fun getStorageInfo(): String {
        val stat = android.os.StatFs(filesDir.path)
        val availableBytes = stat.availableBytes
        val totalBytes = stat.totalBytes
        val usedPercent = ((totalBytes - availableBytes) * 100 / totalBytes).toInt()
        return "${usedPercent}% used"
    }
    
    private fun getMemoryInfo(): String {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val usedPercent = ((memInfo.totalMem - memInfo.availMem) * 100 / memInfo.totalMem).toInt()
        return "${usedPercent}% used"
    }
    
    private fun updateWeather() {
        // Simulate weather data (in a real app, you'd use a weather API)
        val weatherConditions = listOf("☀", "⛅", "🌤", "🌦", "❄", "🌈")
        val temperatures = (15..30).random()
        val condition = weatherConditions.random()
        
        handler.post {
            weatherText.text = "$condition ${temperatures}°"
        }
        
        // Update weather every 30 minutes
        handler.postDelayed({ updateWeather() }, 30 * 60 * 1000)
    }
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onBackPressed() {
        when {
            isDrawerOpen -> slideDownDrawer()
            isPanelOpen -> hidePanels()
            else -> {
                // Don't allow back button to exit launcher in kiosk mode
                if (!devicePolicyManager.isLockTaskPermitted(packageName)) {
                    // Only allow exit if not in kiosk mode
                    super.onBackPressed()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        executor.shutdown()
    }
    
    override fun onStop() {
        super.onStop()
        // Prevent switching to other launchers
        if (devicePolicyManager.isLockTaskPermitted(packageName)) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        }
    }

}