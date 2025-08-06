package com.myslates.launcher

import android.content.ClipData
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat

class AppAdapter(private val context: Context, private var apps: List<AppObject>) : BaseAdapter() {

    override fun getCount(): Int = apps.size

    override fun getItem(position: Int): AppObject = apps[position]  // 👈 cast to AppObject

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

        // Apply rounded background and clip
        iconView.setImageDrawable(app.icon)
        iconView.scaleType = ImageView.ScaleType.CENTER_CROP
        iconView.background = ContextCompat.getDrawable(context, R.drawable.round_icon_bg)
        iconView.clipToOutline = true

        labelView.text = app.label

        // Enable drag-and-drop
        view.setOnLongClickListener {
            val clipData = ClipData.newPlainText("package", app.packageName)
            val shadow = View.DragShadowBuilder(view)
            view.startDragAndDrop(clipData, shadow, app, 0)
            true
        }

        return view
    }
}
