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

        // Set up touch handling for both click and drag
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    // Start long press timer
                    longPressRunnable = Runnable {
                        if (!isDragging) {
                            isDragging = true
                            startDragAndDrop(v, app)
                        }
                    }
                    longPressHandler.postDelayed(longPressRunnable!!, 500) // 500ms for long press
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Cancel long press if user moves finger too much
                    if (Math.abs(event.x - event.rawX) > 10 || Math.abs(event.y - event.rawY) > 10) {
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    if (!isDragging) {
                        // This is a regular click, launch the app
                        launchApp(app.packageName)
                    }
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    isDragging = false
                    true
                }
                else -> false
            }
        }

        return view
    }

    private fun startDragAndDrop(view: View, app: AppObject) {
        val dragData = ClipData.newPlainText("app_package", app.packageName)
        val dragShadow = View.DragShadowBuilder(DraggedAppView(context, app.icon, app.label))
        
        view.startDragAndDrop(dragData, dragShadow, app, 0)
        
        // Add haptic feedback
        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
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