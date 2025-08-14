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
import android.util.Base64
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.math.min
import kotlin.random.Random


private object PasswordStore {
    private const val PREFS = "prefs_encrypted"
    private const val KEY_HASH = "pw_hash"
    private const val KEY_SALT = "pw_salt"
    private const val KEY_ITER = "pw_iter"
    private const val KEY_FAILS = "failed_attempts"
    private const val KEY_LOCK_UNTIL = "lock_until_epoch_ms"
    private const val DEFAULT_ITERS = 120_000

    private fun prefs(ctx: android.content.Context): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun hasPassword(ctx: android.content.Context): Boolean =
        prefs(ctx).contains(KEY_HASH)

    fun setPassword(ctx: android.content.Context, newPassword: CharArray, iterations: Int = DEFAULT_ITERS) {
        val salt = SecureRandom().generateSeed(32)
        val hash = pbkdf2(newPassword, salt, iterations)
        prefs(ctx).edit()
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putInt(KEY_ITER, iterations)
            .apply()
        newPassword.fill('\u0000')
        resetThrottle(ctx)
    }

    fun verify(ctx: android.content.Context, password: CharArray): Boolean {
        val p = prefs(ctx)
        val hashB64 = p.getString(KEY_HASH, null) ?: return false
        val saltB64 = p.getString(KEY_SALT, null) ?: return false
        val iters = p.getInt(KEY_ITER, DEFAULT_ITERS)
        val calc = pbkdf2(password, Base64.decode(saltB64, Base64.NO_WRAP), iters)
        val ok = MessageDigest.isEqual(calc, Base64.decode(hashB64, Base64.NO_WRAP))
        if (ok) resetThrottle(ctx) else recordFailure(ctx)
        password.fill('\u0000')
        return ok
    }

    fun isLocked(ctx: android.content.Context, now: Long = System.currentTimeMillis()): Boolean =
        prefs(ctx).getLong(KEY_LOCK_UNTIL, 0L) > now

    fun remainingLockMs(ctx: android.content.Context, now: Long = System.currentTimeMillis()): Long =
        (prefs(ctx).getLong(KEY_LOCK_UNTIL, 0L) - now).coerceAtLeast(0L)

    private fun recordFailure(ctx: android.content.Context) {
        val p = prefs(ctx)
        val fails = p.getInt(KEY_FAILS, 0) + 1
        val backoffSec = if (fails < 5) 0 else min(600, 30 shl (fails - 5)) // 30,60,120,... cap 600
        p.edit()
            .putInt(KEY_FAILS, fails)
            .putLong(KEY_LOCK_UNTIL, if (backoffSec == 0) 0L else System.currentTimeMillis() + backoffSec * 1000L)
            .apply()
    }

    private fun resetThrottle(ctx: android.content.Context) {
        prefs(ctx).edit()
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_LOCK_UNTIL, 0L)
            .apply()
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}

private object PasswordPolicy {
    fun strongEnough(pw: String): Boolean = pw.length >= 6 // tighten if desired
}

// -----------------------------------------------------------------------------

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
    private val maxAppsPerPage = 6 // 2x3 grid
    private val homeScreenApps = mutableListOf<AppObject?>()

    // Fast duplicate check (kept in lockstep with homeScreenApps)
    private val homePackages = mutableSetOf<String>()

    // Security components
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private val PARENTAL_PIN = "123456" // legacy fallback if no admin password set

    // Kiosk pref
    private val PREFS by lazy { getSharedPreferences("launcher_prefs", MODE_PRIVATE) }
    private var kioskEnabled: Boolean
        get() = PREFS.getBoolean("kiosk_enabled", false)
        set(value) { PREFS.edit().putBoolean("kiosk_enabled", value).apply() }

    private val isTablet get() = resources.configuration.smallestScreenWidthDp >= 600
    private val DOCK_MAX = 2
    private val HOME_ICON_DP   get() = if (isTablet) 140 else 110
    private val DRAWER_ICON_DP get() = if (isTablet) 140 else 110
    private val DOCK_ICON_DP   get() = if (isTablet) 140 else 110
    private val LABEL_SP       get() = if (isTablet) 20f else 16f
    private val TIME_SP        get() = if (isTablet) 96f else 72f
    private val DATE_SP        get() = if (isTablet) 28f else 20f

    private val allowedApps = listOf(
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

        // Long-press clock → Admin panel (PIN/Password → Toggle kiosk)
        timeText.setOnLongClickListener { showAdminPanel(); true }

        // Initialize with empty grid
        repeat(maxAppsPerPage) { homeScreenApps.add(null) }
        homePackages.clear()
        homePackages.addAll(homeScreenApps.mapNotNull { it?.packageName })
        homeGridAdapter.notifyDataSetChanged()

        applyTabletScaling()

        // Safety: only re-enter kiosk if previously enabled
        if (kioskEnabled) enableKioskModeIfPermitted()
    }

    // ---------- SECURITY / KIOSK ----------

    private fun initializeSecurity() {
        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, LauncherDeviceAdminReceiver::class.java)

        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Enable device admin to secure the launcher"
            )
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

    private fun enableKioskModeIfPermitted(): Boolean {
        return try {
            if (devicePolicyManager.isAdminActive(adminComponent) &&
                devicePolicyManager.isDeviceOwnerApp(packageName)
            ) {
                devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf(packageName))
                startLockTask()
                true
            } else {
                Toast.makeText(this, "Device owner not granted. Kiosk unavailable.", Toast.LENGTH_LONG).show()
                false
            }
        } catch (e: Exception) {
            Log.e("Security", "Failed enabling kiosk", e)
            false
        }
    }

    private fun disableKioskModeIfActive() {
        try { stopLockTask() } catch (_: Exception) { /* ignore */ }
    }

    // ADDITIVE: centralized helper that accepts stored admin password or legacy PIN
    private fun verifyAdminSecret(input: String): Boolean {
        // Locked? short-circuit
        if (PasswordStore.isLocked(this)) {
            val seconds = PasswordStore.remainingLockMs(this) / 1000
            Toast.makeText(this, "Locked. Try again in ${seconds}s.", Toast.LENGTH_SHORT).show()
            return false
        }
        return if (PasswordStore.hasPassword(this)) {
            PasswordStore.verify(this, input.toCharArray())
        } else {
            val ok = input == PARENTAL_PIN
            if (!ok) {
                // light throttle (legacy path) to keep behavior predictable
                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
            }
            ok
        }
    }

    private fun showAdminPanel() {
        val pinInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = if (PasswordStore.hasPassword(this@MainActivity)) "Enter admin password" else "Enter 6-digit PIN"
            maxLines = 1
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            isEnabled = !PasswordStore.isLocked(this@MainActivity)
        }

        AlertDialog.Builder(this)
            .setTitle("Admin Access")
            .setView(pinInput)
            .setPositiveButton("Continue") { _, _ ->
                val ok = verifyAdminSecret(pinInput.text?.toString().orEmpty())
                if (!ok) return@setPositiveButton

                val container = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
                }

                val kioskSwitch = Switch(this).apply {
                    text = "Enable Kiosk Mode"
                    isChecked = kioskEnabled
                }
                container.addView(kioskSwitch)

                // ADDITIVE: Change password button inside Admin Panel
                val changePwBtn = Button(this).apply {
                    text = "Change Admin Password"
                }
                container.addView(changePwBtn)

                val infoText = TextView(this).apply {
                    setTextColor(Color.parseColor("#AAAAAA"))
                    textSize = 12f
                    text = if (PasswordStore.hasPassword(this@MainActivity))
                        "Password is stored securely on-device." else
                        "Using legacy PIN. Set an admin password to upgrade security."
                    setPadding(0, dpToPx(8), 0, 0)
                }
                container.addView(infoText)

                val adminDialog = AlertDialog.Builder(this)
                    .setTitle("Admin Panel")
                    .setView(container)
                    .setPositiveButton("Apply") { _, _ ->
                        val wantKiosk = kioskSwitch.isChecked
                        if (wantKiosk && !kioskEnabled) {
                            if (enableKioskModeIfPermitted()) {
                                kioskEnabled = true
                                Toast.makeText(this, "Kiosk enabled", Toast.LENGTH_SHORT).show()
                            }
                        } else if (!wantKiosk && kioskEnabled) {
                            disableKioskModeIfActive()
                            kioskEnabled = false
                            Toast.makeText(this, "Kiosk disabled", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Close", null)
                    .create()

                changePwBtn.setOnClickListener {
                    showChangePasswordDialog()
                }

                adminDialog.show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ADDITIVE: In-launcher Change Password dialog (local-only)
    private fun showChangePasswordDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }

        val currentInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Current password"
            maxLines = 1
            visibility = if (PasswordStore.hasPassword(this@MainActivity)) View.VISIBLE else View.GONE
        }
        val newInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "New password (min 6 chars)"
            maxLines = 1
        }
        val confirmInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Confirm new password"
            maxLines = 1
        }

        container.addView(currentInput)
        container.addView(spaceView(8))
        container.addView(newInput)
        container.addView(spaceView(8))
        container.addView(confirmInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Change Admin Password")
            .setView(container)
            .setPositiveButton("Save", null) // we override to keep dialog open on validation errors
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val saveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveBtn.setOnClickListener {
                // Basic validation
                val hasExisting = PasswordStore.hasPassword(this)
                val cur = currentInput.text.toString()
                val newPw = newInput.text.toString()
                val conf = confirmInput.text.toString()

                if (hasExisting && !verifyAdminSecret(cur)) {
                    // verifyAdminSecret already toasts; don’t dismiss
                    return@setOnClickListener
                }
                if (newPw != conf) {
                    Toast.makeText(this, "Passwords don’t match", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!PasswordPolicy.strongEnough(newPw)) {
                    Toast.makeText(this, "Password too weak (min 6 chars)", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                PasswordStore.setPassword(this, newPw.toCharArray())
                Toast.makeText(this, "Admin password changed", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun spaceView(dp: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(dp)
        )
    }

    // ---------- LAYOUT SCALING ----------

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

    // ---------- GESTURES ----------

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

    // ---------- HOME GRID ----------

    private fun setupHomeGrid() {
        homeGridAdapter = HomeGridAdapter(
            context = this,
            apps = homeScreenApps,
            onAppClick = { app -> launchApp(app.packageName) },
            onAppLongClick = { app, position -> startHomeAppDrag(app, position) },
            onEmptySlotDrop = { position, app -> handleAppDropWithDuplicateCheck(position, app) },
            onAppDrag = { app, position -> startHomeAppDrag(app, position) }
        )
        homeGridRecyclerView.layoutManager = GridLayoutManager(this, 2)
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
                    // Drop on the home surface: place in first empty slot if not duplicate
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

                    // If drop succeeded and source was DOCK, remove the dock view (move, not copy)
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

                    // Avoid duplicate items in dock by tag (packageName)
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
        clearSlot(position) // clear while dragging
    }

    // ---------- HOME GRID HELPERS (duplicate-proof) ----------

    private fun isAppAlreadyOnHomeScreen(app: AppObject): Boolean =
        homePackages.contains(app.packageName)

    private fun putAppAt(position: Int, app: AppObject) {
        homeScreenApps[position]?.let { existing -> homePackages.remove(existing.packageName) }
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
        if (homeScreenApps[position] == null) { putAppAt(position, app); return true }
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

        adapter = AppAdapter(this, allFilteredApps) { app -> handleDrawerAppDrag(app) }
        appGridView.numColumns = 2
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
            "com.ATS.MySlates",
            "com.adobe.reader"
        )
        bottomApps.take(DOCK_MAX).forEach { packageName ->
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

    private fun showPinDialog(onSuccess: () -> Unit) {
        val editText = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = if (PasswordStore.hasPassword(this@MainActivity)) "Enter admin password" else "Enter 6-digit PIN"
            maxLines = 1
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            isEnabled = !PasswordStore.isLocked(this@MainActivity)
        }

        AlertDialog.Builder(this)
            .setTitle("Parental Control")
            .setMessage(if (PasswordStore.hasPassword(this)) "Enter password to access Settings" else "Enter PIN to access Settings")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val entered = editText.text.toString()
                val ok = verifyAdminSecret(entered)
                if (ok) onSuccess()
                // errors are already surfaced inside verifyAdminSecret
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
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
                // Allow back only if not in lock task
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
