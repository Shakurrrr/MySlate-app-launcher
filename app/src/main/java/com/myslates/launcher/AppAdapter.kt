package com.myslates.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import android.content.ClipData
import android.content.ClipDescription
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.util.Log
import com.myslates.launcher.AppObject

class AppAdapter(private val context: Context, private var apps: List<AppObject>) : BaseAdapter() {
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var isDragging = false

    override fun getCount(): Int = apps.size

    override fun getItem(position: Int): Any = apps[position]

    override fun getItemId(position: Int): Long = position.toLong()

    fun updateData(newApps: List<AppObject>) {
        this.apps = newApps
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_app, parent, false)
        val app = apps[position]

        val iconView = view.findViewById<ImageView>(R.id.app_icon)
        val labelView = view.findViewById<TextView>(R.id.app_label)

        iconView.setImageDrawable(app.icon)
        labelView.text = app.label

        // Clear any existing listeners
        view.setOnTouchListener(null)
        view.setOnClickListener(null)
        view.setOnLongClickListener(null)
        
        // Set up long click for drag
        view.setOnLongClickListener { v ->
            Log.d("AppAdapter", "Long click detected for ${app.label}")
            startDragAndDrop(v, app)
            true
        }
        
        // Set up regular click for launching
        view.setOnClickListener {
            Log.d("AppAdapter", "Click detected for ${app.label}")
            launchApp(app.packageName)
        }
        
        return view
    }

    private fun startDragAndDrop(view: View, app: AppObject) {
        Log.d("AppAdapter", "Starting drag for ${app.label}")
        
        // Create clip data with the package name
        val item = ClipData.Item(app.packageName)
        val dragData = ClipData("app_data", arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), item)
        
        // Create drag shadow
        val dragShadowBuilder = object : View.DragShadowBuilder(view) {
            override fun onProvideShadowMetrics(size: android.graphics.Point, touch: android.graphics.Point) {
                val width = (view.width * 0.8f).toInt()
                val height = (view.height * 0.8f).toInt()
                size.set(width, height)
                touch.set(width / 2, height / 2)
            }
        }
        
        // Start drag and drop with the app object as local state
        val result = view.startDragAndDrop(dragData, dragShadowBuilder, app, View.DRAG_FLAG_GLOBAL)
        Log.d("AppAdapter", "Drag started: $result")
        
        if (result) {
            // Add haptic feedback
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            Log.d("AppAdapter", "Haptic feedback triggered")
        } else {
            Log.e("AppAdapter", "Failed to start drag and drop")
        }
    }

    private fun launchApp(packageName: String) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}