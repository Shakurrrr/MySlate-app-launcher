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
    private val droppedApps = mutableListOf<DroppedApp>()

    private val handler = Handler(Looper.getMainLooper())
    private var isDrawerOpen = false
    private var downX: Float = 0f
    private var downY: Float = 0f

    private val allowedApps = listOf(
        "com.ATS.MySlates.Parent",
        "com.ATS.MySlates",
        "com.ATS.MySlates.Teacher",
        "com.adobe.reader",
        "com.android.settings",
        "com.android.dialer",
        "com.samsung.android.messaging",
        "com.android.chrome"
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

        appDrawer.post { appDrawer.translationY = appDrawer.height.toFloat() }
        blurOverlay.alpha = 0f
        blurOverlay.visibility = View.GONE
        weatherText.text = "\u2600 24\u00b0"

        handler.post(timeRunnable)
        
        // Set up drag and drop for the home screen
        setupDragAndDrop()

        searchInput.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }

        val callBtn: ImageView = findViewById(R.id.icon_call)
        val msgBtn: ImageView= findViewById(R.id.icon_message)
        val browserBtn: ImageView = findViewById(R.id.icon_browser)
        val settingsBtn: ImageView = findViewById(R.id.icon_settings)

        callBtn.setOnClickListener { launchApp("com.samsung.android.dialer") }
        msgBtn.setOnClickListener { launchApp("com.samsung.android.messaging") }
        browserBtn.setOnClickListener { launchApp("com.android.chrome") }
        settingsBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }


        val call = findViewById<ImageView>(R.id.icon_call)
        addTapEffect(call)
        call.setOnClickListener { launchApp("com.android.dialer") }


        val message = findViewById<ImageView>(R.id.icon_message)
        addTapEffect(message)
            message.setOnClickListener{launchApp("com.samsung.android.messaging")
        }

        val browser = findViewById<ImageView>(R.id.icon_browser)
        addTapEffect(browser)
        browser.setOnClickListener{launchApp("com.android.chrome") }

        val settings = findViewById<ImageView>(R.id.icon_settings)
        addTapEffect(settings)
        settings.setOnClickListener{launchApp("com.android.settings")
        }


        val forwardTouchListener = View.OnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        rootLayout.setOnTouchListener(forwardTouchListener)
        appDrawer.setOnTouchListener(forwardTouchListener)
        appGridView.setOnTouchListener(forwardTouchListener)
        searchIcon.setOnTouchListener(forwardTouchListener)
        leftPanel.setOnTouchListener(forwardTouchListener)
        rightPanel.setOnTouchListener(forwardTouchListener)

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

        findViewById<ImageView>(R.id.icon_call).setOnClickListener {
            launchApp("com.samsung.android.dialer")
        }

        findViewById<ImageView>(R.id.icon_message).setOnClickListener {
            launchApp("com.samsung.android.messaging")
        }

        findViewById<ImageView>(R.id.icon_browser).setOnClickListener {
            launchApp("com.android.chrome")
        }

        findViewById<ImageView>(R.id.icon_settings).setOnClickListener {
            launchApp("com.android.settings")
        }


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
                    deltaY > 150 && isDrawerOpen -> {
                        slideDownDrawer()
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
    }
    
    private fun setupDragAndDrop() {
        homeContainer.setOnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    // Check if this is an app being dragged
                    event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                }
                
                DragEvent.ACTION_DRAG_ENTERED -> {
                    // Visual feedback when drag enters the drop zone
                    homeContainer.alpha = 0.8f
                    true
                }
                
                DragEvent.ACTION_DRAG_EXITED -> {
                    // Remove visual feedback when drag exits
                    homeContainer.alpha = 1.0f
                    true
                }
                
                DragEvent.ACTION_DROP -> {
                    // Handle the drop
                    val clipData = event.clipData
                    if (clipData != null && clipData.itemCount > 0) {
                        val packageName = clipData.getItemAt(0).text.toString()
                        val appObject = event.localState as? AppObject
                        
                        if (appObject != null) {
                            // Create dropped app at the drop location
                            val droppedApp = DroppedApp(
                                appObject.label,
                                appObject.icon,
                                packageName,
                                event.x,
                                event.y
                            )
                            
                            addDroppedAppToHomeScreen(droppedApp)
                            droppedApps.add(droppedApp)
                            
                            // Close the app drawer
                            slideDownDrawer()
                        }
                    }
                    homeContainer.alpha = 1.0f
                    true
                }
                
                DragEvent.ACTION_DRAG_ENDED -> {
                    // Clean up
                    homeContainer.alpha = 1.0f
                    true
                }
                
                else -> false
            }
        }
    }
    
    private fun addDroppedAppToHomeScreen(droppedApp: DroppedApp) {
        val appView = LayoutInflater.from(this).inflate(R.layout.home_app_item, null)
        val iconView = appView.findViewById<ImageView>(R.id.home_app_icon)
        val labelView = appView.findViewById<TextView>(R.id.home_app_label)
        
        iconView.setImageDrawable(droppedApp.icon)
        labelView.text = droppedApp.label
        
        // Set click listener to launch the app
        appView.setOnClickListener {
            launchApp(droppedApp.packageName)
        }
        
        // Add tap effect
        addTapEffect(appView)
        
        // Set up long click for removal
        appView.setOnLongClickListener {
            showRemoveAppDialog(droppedApp, appView)
            true
        }
        
        // Position the app view
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        
        // Adjust position to center the view on the drop point
        layoutParams.leftMargin = (droppedApp.x - 40).toInt() // 40dp is roughly half the icon size
        layoutParams.topMargin = (droppedApp.y - 40).toInt()
        
        homeContainer.addView(appView, layoutParams)
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
