package com.myslates.launcher

import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserManager
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
import java.text.SimpleDateFormat
import java.util.*
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

    private val handler = Handler(Looper.getMainLooper())
    private var isDrawerOpen = false
    private var isDragging = false
    private var isPanelOpen = false
    private var currentPage = 0
    private val maxAppsPerPage = 15 // 3x5 grid
    private val homeScreenApps = mutableListOf<AppObject?>()

    // Fast duplicate check (kept in lockstep with homeScreenApps)
    private val homePackages = mutableSetOf<String>()

    // Security components
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private val PARENTAL_PIN = "123456" // TODO: store securely

    private val isTablet get() = resources.configuration.smallestScreenWidthDp >= 600
    private val HOME_ICON_DP   get() = if (isTablet) 120 else 90
    private val DRAWER_ICON_DP get() = if (isTablet) 140 else 90
    private val DOCK_ICON_DP get() = if (isTablet) 140 else 90
    private val LABEL_SP       get() = if (isTablet) 18f else 14f
    private val TIME_SP        get() = if (isTablet) 100f else 80f
    private val DATE_SP        get() = if (isTablet) 40f else 28f

    private val allowedApps = listOf(
        "com.android.settings",
        "com.ATS.MySlates",
        "com.adobe.reader"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immersive mode
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
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
        homePackages.clear()
        homePackages.addAll(homeScreenApps.mapNotNull { it?.packageName })
        homeGridAdapter.notifyDataSetChanged()

        applyTabletScaling()

        // Kiosk disabled for safe testing on personal devices
        // enableKioskMode()
    }

    private fun initializeSecurity() {
        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, LauncherDeviceAdminReceiver::class.java)

        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enable device admin to secure the launcher")
            }
            startActivity(intent)
        }
        applyUserRestrictions()
    }

    private fun applyUserRestrictions() {
        if (!devicePolicyManager.isAdminActive(adminComponent)) return
        try {
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_MODIFY_ACCOUNTS)
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_APPS)
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_UNINSTALL_APPS)
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_USB_FILE_TRANSFER)
            Log.d("Security", "User restrictions applied successfully")
        } catch (e: Exception) {
            Log.e("Security", "Failed to apply user restrictions", e)
        }
    }

    //private fun enableKioskMode() {
    // try {
    //if (devicePolicyManager.isAdminActive(adminComponent)) {
    /// Set as device owner if possible
    //if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
    /// Enable lock task mode
    //devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf(packageName))
    //startLockTask()
    //Log.d("Security", "Kiosk mode enabled")
    //}
    //}
    //} catch (e: Exception) {
    // Log.e("Security", "Failed to enable kiosk mode", e)
    // }
    //}


    private fun applyTabletScaling() {
        timeText.textSize = TIME_SP
        dateText.textSize = DATE_SP
        weatherText.textSize = DATE_SP

        val topInfoBar = findViewById<LinearLayout>(R.id.top_info_bar)
        val params = topInfoBar.layoutParams as FrameLayout.LayoutParams
        params.topMargin = dpToPx(if (isTablet) 80 else 64)
        params.marginStart = dpToPx(if (isTablet) 48 else 32)
        topInfoBar.layoutParams = params

        val gridParams = homeGridRecyclerView.layoutParams as FrameLayout.LayoutParams
        gridParams.topMargin = dpToPx(if (isTablet) 200 else 160)
        gridParams.bottomMargin = dpToPx(if (isTablet) 200 else 160)
        gridParams.marginStart = dpToPx(if (isTablet) 48 else 32)
        gridParams.marginEnd = dpToPx(if (isTablet) 48 else 32)
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

        appDrawer.post { appDrawer.translationY = appDrawer.height.toFloat() }
        blurOverlay.alpha = 0f
        blurOverlay.visibility = View.GONE
        removeAppZone.visibility = View.GONE

        updateWeather()
    }

    private fun setupSwipeGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null || isDragging || isPanelOpen) return false
                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y
                return when {
                    Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(velocityY) > 800 -> {
                        if (deltaY > 0 && isDrawerOpen) { slideDownDrawer(); true }
                        else if (deltaY < 0 && !isDrawerOpen) { slideUpDrawer(); true }
                        else false
                    }
                    Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(velocityX) > 800 -> {
                        if (deltaX < 0) { showRightPanel(); true }
                        else if (deltaX > 0) { showLeftPanel(); true }
                        else false
                    }
                    else -> false
                }
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (e1 == null || isDragging) return false
                val deltaX = e2!!.x - e1.x
                val deltaY = e2.y - e1.y
                val minDistance = 80f
                return when {
                    Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(deltaY) > minDistance -> {
                        if (deltaY > 0 && isDrawerOpen && !isPanelOpen) { slideDownDrawer(); true }
                        else if (deltaY < 0 && !isDrawerOpen && !isPanelOpen) { slideUpDrawer(); true }
                        else false
                    }
                    Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > minDistance -> {
                        if (deltaX < 0 && !isDrawerOpen && !isPanelOpen) { showRightPanel(); true }
                        else if (deltaX > 0 && !isDrawerOpen && !isPanelOpen) { showLeftPanel(); true }
                        else false
                    }
                    else -> false
                }
            }
        })

        val gestureListener = View.OnTouchListener { _, event ->
            if (!isDragging && !isPanelOpen) gestureDetector.onTouchEvent(event)
            false
        }

        rootLayout.setOnTouchListener(gestureListener)
        homeContainer.setOnTouchListener(gestureListener)
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
        homeGridRecyclerView.layoutManager = GridLayoutManager(this, 3)
        homeGridRecyclerView.adapter = homeGridAdapter
        homeGridRecyclerView.itemAnimator = null
    }

    // ---------- DRAG & DROP ----------

    private fun setupDragAndDrop() {
        // Global drag listener (home surface)
        rootLayout.setOnDragListener { _, event ->
            val dragData = event.localState as? DragData
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    isDragging = true
                    showDragFeedback()
                    if (isDrawerOpen && dragData?.isFromHomeScreen != true) slideDownDrawerForDrop()
                    true
                }

                DragEvent.ACTION_DROP -> {
                    // Drop on the home surface: place in first empty slot (deduped)
                    if (dragData != null && !dragData.isFromHomeScreen) {
                        if (!isAppAlreadyOnHomeScreen(dragData.app)) {
                            val emptySlot = findNextEmptySlot(0)
                            if (emptySlot != -1) {
                                putAppAt(emptySlot, dragData.app)
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

                DragEvent.ACTION_DRAG_ENDED -> {
                    isDragging = false
                    hideDragFeedback()

                    // If drop succeeded and source was DOCK, remove the dock view
                    if (event.result && dragData?.source == DragData.Source.DOCK) {
                        dragData.sourceView?.let { bottomBar.removeView(it) }
                    }

                    // If a HOME-origin drag failed, restore the slot
                    if (!event.result && dragData?.isFromHomeScreen == true && dragData.originalPosition >= 0) {
                        putAppAt(dragData.originalPosition, dragData.app)
                    }
                    true
                }

                else -> false
            }
        }

        // Dock accepts drops (move to dock)
        bottomBar.setOnDragListener { _, event ->
            val dragData = event.localState as? DragData
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> { bottomBar.animate().alpha(0.7f).setDuration(150).start(); true }
                DragEvent.ACTION_DRAG_EXITED -> { bottomBar.animate().alpha(1f).setDuration(150).start(); true }
                DragEvent.ACTION_DROP -> {
                    bottomBar.animate().alpha(1f).setDuration(150).start()
                    if (dragData == null) return@setOnDragListener false

                    if (bottomBar.childCount >= 3) {
                        Toast.makeText(this, "Dock is full", Toast.LENGTH_SHORT).show()
                        return@setOnDragListener false
                    }

                    // Avoid duplicates in dock by tag (packageName)
                    if ((0 until bottomBar.childCount).any {
                            (bottomBar.getChildAt(it).tag as? String) == dragData.app.packageName
                        }) {
                        Toast.makeText(this, "Already in dock", Toast.LENGTH_SHORT).show()
                        return@setOnDragListener false
                    }

                    val newAppView = createBottomBarAppView(
                        dragData.app.label,
                        dragData.app.icon,
                        dragData.app.packageName
                    )
                    newAppView.tag = dragData.app.packageName
                    bottomBar.addView(newAppView)

                    // If it came from home, clear the old slot
                    if (dragData.isFromHomeScreen && dragData.originalPosition >= 0) {
                        clearSlot(dragData.originalPosition)
                    }

                    Toast.makeText(this, "${dragData.app.label} added to dock", Toast.LENGTH_SHORT).show()
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> { bottomBar.animate().alpha(1f).setDuration(150).start(); true }
                else -> false
            }
        }

        // Remove zone
        removeAppZone.setOnDragListener { _, event ->
            val dragData = event.localState as? DragData
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> dragData?.isFromHomeScreen == true
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
                        if (pos in homeScreenApps.indices) clearSlot(pos)
                        Toast.makeText(this, "${dragData.app.label} removed", Toast.LENGTH_SHORT).show()
                        true
                    } else false
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
        blurOverlay.visibility = View.VISIBLE
        blurOverlay.animate().alpha(0.5f).setDuration(200).start()
        removeAppZone.visibility = View.VISIBLE
        removeAppZone.alpha = 0f
        removeAppZone.animate().alpha(1f).setDuration(200).start()
        homeContainer.animate().alpha(0.8f).setDuration(200).start()
    }

    private fun hideDragFeedback() {
        blurOverlay.animate().alpha(0f).setDuration(200).withEndAction {
            blurOverlay.visibility = View.GONE
        }.start()
        removeAppZone.animate().alpha(0f).setDuration(200).withEndAction {
            removeAppZone.visibility = View.GONE
        }.start()
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
        val clipData = ClipData.newPlainText("home_app", "${app.packageName}|$position")
        val dragData = DragData(
            app = app,
            originalPosition = position,
            isFromHomeScreen = true,
            source = DragData.Source.HOME
        )
        val shadow = View.DragShadowBuilder(createDragShadow(app))
        homeGridRecyclerView.startDragAndDrop(clipData, shadow, dragData, View.DRAG_FLAG_GLOBAL)

        // Clear the slot while dragging
        clearSlot(position)
    }

    // ---------- HOME GRID HELPERS (duplicate-proof) ----------

    private fun isAppAlreadyOnHomeScreen(app: AppObject): Boolean =
        homePackages.contains(app.packageName)

    private fun putAppAt(position: Int, app: AppObject) {
        // If overwriting a different app, remove it from the set first
        homeScreenApps[position]?.let { existing ->
            homePackages.remove(existing.packageName)
        }
        homeScreenApps[position] = app
        homePackages.add(app.packageName)
        homeGridAdapter.notifyItemChanged(position)
    }

    private fun clearSlot(position: Int) {
        homeScreenApps[position]?.let { homePackages.remove(it.packageName) }
        homeScreenApps[position] = null
        homeGridAdapter.notifyItemChanged(position)
    }

    private fun handleAppDropWithDuplicateCheck(position: Int, app: AppObject): Boolean {
        if (position !in homeScreenApps.indices) return false
        if (isAppAlreadyOnHomeScreen(app)) {
            Toast.makeText(this, "${app.label} is already on home screen", Toast.LENGTH_SHORT).show()
            return false
        }
        if (homeScreenApps[position] == null) {
            putAppAt(position, app); return true
        }
        val next = findNextEmptySlot(position)
        if (next != -1) { putAppAt(next, app); return true }
        Toast.makeText(this, "No empty slot available", Toast.LENGTH_SHORT).show()
        return false
    }

    private fun findNextEmptySlot(startPosition: Int): Int {
        for (i in startPosition until homeScreenApps.size) if (homeScreenApps[i] == null) return i
        for (i in startPosition - 1 downTo 0) if (homeScreenApps[i] == null) return i
        return -1
    }

    private fun createDragShadow(app: AppObject): View {
        val dragView = LayoutInflater.from(this).inflate(R.layout.drag_shadow_item, null)
        val iconView = dragView.findViewById<ImageView>(R.id.drag_icon)
        val labelView = dragView.findViewById<TextView>(R.id.drag_label)
        iconView.setImageDrawable(app.icon)
        labelView.text = app.label
        dragView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        dragView.layout(0, 0, dragView.measuredWidth, dragView.measuredHeight)
        return dragView
    }

    // ---------- APPS / DRAWER / DOCK ----------

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
        appGridView.numColumns = 3
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

        loadBottomBarApps()
    }

    private fun handleDrawerAppDrag(app: AppObject) {
        val clipData = ClipData.newPlainText("drawer_app", app.packageName)
        val dragData = DragData(app, -1, false, DragData.Source.DRAWER)
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
                val appView = createBottomBarAppView(appLabel, appIcon, packageName)
                appView.tag = packageName
                bottomBar.addView(appView)
            } catch (e: Exception) {
                Log.e("MainActivity", "Bottom bar app not found: $packageName")
            }
        }
    }

    private fun createBottomBarAppView(
        label: String,
        icon: android.graphics.drawable.Drawable,
        packageName: String
    ): View {
        val appView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
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
            setPadding(0, dpToPx(8), 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        appView.addView(iconView)
        appView.addView(labelView)

        // Click → launch (with Settings gated)
        appView.setOnClickListener {
            if (packageName == "com.android.settings") {
                showPinDialog { launchApp(packageName) }
            } else {
                launchApp(packageName)
            }
        }

        // Long press → start drag (source = DOCK, pass the view so we can remove it on success)
        appView.setOnLongClickListener {
            val app = AppObject(label, icon, packageName)
            val clipData = ClipData.newPlainText("bottom_app", packageName)
            val dragData = DragData(app, -1, false, DragData.Source.DOCK, sourceView = appView)
            val shadow = View.DragShadowBuilder(createDragShadow(app))
            appView.startDragAndDrop(clipData, shadow, dragData, View.DRAG_FLAG_GLOBAL)
            true
        }

        addModernTouchFeedback(appView)
        return appView
    }

    // ---------- PIN / LAUNCH / PANELS / UTIL ----------

    private fun showPinDialog(onSuccess: () -> Unit) {
        val editText = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter 6-digit PIN"
            maxLines = 1
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }

        AlertDialog.Builder(this)
            .setTitle("Parental Control")
            .setMessage("Enter PIN to access Settings")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val enteredPin = editText.text.toString()
                if (enteredPin == PARENTAL_PIN) onSuccess()
                else Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    private fun addModernTouchFeedback(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.9f).scaleY(0.9f).alpha(0.7f).setDuration(100)
                        .setInterpolator(AccelerateDecelerateInterpolator()).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(100).start()
                }
            }
            false
        }
    }

    private fun launchApp(packageName: String) {
        try {
            if (packageName == "com.android.settings") {
                showPinDialog {
                    packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it) }
                }
                return
            }
            packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it) }
                ?: Toast.makeText(this, "App not found: $packageName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot launch app: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("MainActivity", "Failed to launch $packageName", e)
        }
    }

    private fun slideUpDrawer() {
        if (!isFinishing && !isDestroyed) {
            appDrawer.animate().translationY(0f).setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
            blurOverlay.visibility = View.VISIBLE
            blurOverlay.animate().alpha(0.7f).setDuration(300).start()
            isDrawerOpen = true
        }
    }

    private fun slideDownDrawer() {
        if (!isFinishing && !isDestroyed) {
            appDrawer.animate().translationY(appDrawer.height.toFloat()).setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
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
        setupCalendarPanel()
        leftPanel.visibility = View.VISIBLE
        leftPanel.translationX = -leftPanel.width.toFloat()
        leftPanel.animate().translationX(0f).setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator()).start()
        leftPanel.setOnClickListener { hidePanels() }
    }

    private fun showRightPanel() {
        if (isPanelOpen) return
        isPanelOpen = true
        setupInterestingPanel()
        rightPanel.visibility = View.VISIBLE
        rightPanel.translationX = rightPanel.width.toFloat()
        rightPanel.animate().translationX(0f).setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator()).start()
        rightPanel.setOnClickListener { hidePanels() }
    }

    private fun hidePanels() {
        isPanelOpen = false
        if (leftPanel.visibility == View.VISIBLE) {
            leftPanel.animate().translationX(-leftPanel.width.toFloat()).setDuration(250)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction { leftPanel.visibility = View.GONE; leftPanel.setOnClickListener(null) }
                .start()
        }
        if (rightPanel.visibility == View.VISIBLE) {
            rightPanel.animate().translationX(rightPanel.width.toFloat()).setDuration(250)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction { rightPanel.visibility = View.GONE; rightPanel.setOnClickListener(null) }
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
        val container = scrollView.getChildAt(0) as? LinearLayout ?: LinearLayout(this).also {
            it.orientation = LinearLayout.VERTICAL
            scrollView.addView(it)
        }
        container.removeAllViews()

        val headerText = TextView(this).apply {
            text = "Calendar"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dpToPx(24))
        }
        container.addView(headerText)

        val calendar = Calendar.getInstance()
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val monthText = TextView(this).apply {
            text = monthFormat.format(calendar.time)
            textSize = 20f
            setTextColor(Color.parseColor("#CCCCCC"))
            setPadding(0, 0, 0, dpToPx(16))
        }
        container.addView(monthText)

        val calendarGrid = GridLayout(this).apply {
            columnCount = 7
            rowCount = 7
            setPadding(0, 0, 0, dpToPx(24))
        }

        val dayHeaders = arrayOf("S", "M", "T", "W", "T", "F", "S")
        dayHeaders.forEach { day ->
            val dayView = TextView(this).apply {
                text = day
                textSize = 14f
                setTextColor(Color.parseColor("#888888"))
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dpToPx(40); height = dpToPx(40); setMargins(2, 2, 2, 2)
                }
            }
            calendarGrid.addView(dayView)
        }

        val firstDay = calendar.clone() as Calendar
        firstDay.set(Calendar.DAY_OF_MONTH, 1)
        val startDay = firstDay.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = calendar.get(Calendar.DAY_OF_MONTH)

        repeat(startDay) {
            val emptyView = View(this).apply {
                layoutParams = GridLayout.LayoutParams().apply { width = dpToPx(40); height = dpToPx(40) }
            }
            calendarGrid.addView(emptyView)
        }

        for (day in 1..daysInMonth) {
            val dayView = TextView(this).apply {
                text = day.toString()
                textSize = 14f
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dpToPx(40); height = dpToPx(40); setMargins(2, 2, 2, 2)
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

        val eventsHeader = TextView(this).apply {
            text = "Upcoming Events"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dpToPx(16), 0, dpToPx(16))
        }
        container.addView(eventsHeader)

        listOf(
            "Meeting at 2:00 PM",
            "Lunch with team at 12:30 PM",
            "Project deadline tomorrow"
        ).forEach { event ->
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
        val container = scrollView.getChildAt(0) as? LinearLayout ?: LinearLayout(this).also {
            it.orientation = LinearLayout.VERTICAL
            scrollView.addView(it)
        }
        container.removeAllViews()

        val headerText = TextView(this).apply {
            text = "Quick Info"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dpToPx(24))
        }
        container.addView(headerText)

        val systemCard = createInfoCard(
            "System Status", listOf(
                "Battery: ${getBatteryLevel()}%",
                "Storage: ${getStorageInfo()}",
                "Memory: ${getMemoryInfo()}"
            )
        )
        container.addView(systemCard)

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

        val actionsCard = createInfoCard(
            "Quick Actions", listOf(
                "📱 Device Settings",
                "🔋 Battery Optimization",
                "📶 Network Settings",
                "🔊 Sound Settings"
            )
        )
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
            ).apply { setMargins(0, 0, 0, dpToPx(16)) }
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
        return "$usedPercent% used"
    }

    private fun getMemoryInfo(): String {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val usedPercent = ((memInfo.totalMem - memInfo.availMem) * 100 / memInfo.totalMem).toInt()
        return "$usedPercent% used"
    }

    private fun updateWeather() {
        val weatherConditions = listOf("☀", "⛅", "🌤", "🌦", "❄", "🌈")
        val temperatures = (15..30).random()
        val condition = weatherConditions.random()
        handler.post { weatherText.text = "$condition ${temperatures}°" }
        handler.postDelayed({ updateWeather() }, 30 * 60 * 1000)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onBackPressed() {
        when {
            isDrawerOpen -> slideDownDrawer()
            isPanelOpen -> hidePanels()
            else -> {
                // If not in kiosk mode, allow back
                val am = getSystemService(android.app.ActivityManager::class.java)
                val inLockTask = am.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
                if (!inLockTask) super.onBackPressed()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onStop() {
        super.onStop()
        // If kiosk enabled and active, bounce back to HOME
        val am = getSystemService(android.app.ActivityManager::class.java)
        val inLockTask = am.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
        if (inLockTask) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        }
    }
}
