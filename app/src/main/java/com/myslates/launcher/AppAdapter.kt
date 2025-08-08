package com.myslates.launcher

import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.*
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat

class AppAdapter(
    private val context: Context,
    private var apps: List<AppObject>,
    private val onAppDrag: (AppObject) -> Unit
) : BaseAdapter() {

    override fun getCount(): Int = apps.size

    override fun getItem(position: Int): AppObject = apps[position]

    override fun getItemId(position: Int): Long = position.toLong()

    fun updateData(newApps: List<AppObject>) {
        this.apps = newApps
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val inflater = LayoutInflater.from(context)
        val view = convertView ?: inflater.inflate(R.layout.item_app, parent, false)

        val iconView = view.findViewById<ImageView>(R.id.app_icon)
        val labelView = view.findViewById<TextView>(R.id.app_label)

        val app = getItem(position)

        // Modern app icon styling
        iconView.apply {
            setImageDrawable(app.icon)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = ContextCompat.getDrawable(context, R.drawable.modern_icon_bg)
            clipToOutline = true
            elevation = 8f
        }

        labelView.text = app.label
        labelView.setTextColor(Color.WHITE)

        // Launch app on tap
        view.setOnClickListener {
            Log.d("AppAdapter", "Clicked on ${app.label}")
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                intent?.let { context.startActivity(it) }
            } catch (e: Exception) {
                Log.e("AppAdapter", "Failed to launch ${app.label}", e)
            }
        }

        // Start drag on long press
        view.setOnLongClickListener {
            Log.d("AppAdapter", "Long clicked on ${app.label}")

            val clipData = ClipData.newPlainText("package", app.packageName)
            val shadow = View.DragShadowBuilder(view)

            view.startDragAndDrop(
                clipData,
                shadow,
                app, // localState is AppObject
                View.DRAG_FLAG_GLOBAL
            )

            true
        }

        addTouchFeedback(view)

        return view
    }

    private fun addTouchFeedback(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.9f)
                        .scaleY(0.9f)
                        .alpha(0.7f)
                        .setDuration(100)
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
}
