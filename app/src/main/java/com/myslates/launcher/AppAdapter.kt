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
import com.myslates.launcher.AppObject

class AppAdapter(private val context: Context, private var apps: List<AppObject>) : BaseAdapter() {

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

        // Set up long click for drag and drop
        view.setOnLongClickListener { v ->
            val dragData = ClipData.newPlainText("app_package", app.packageName)
            val dragShadow = View.DragShadowBuilder(DraggedAppView(context, app.icon, app.label))
            
            v.startDragAndDrop(dragData, dragShadow, app, 0)
            true
        }


        //click listener to launch the app
        view.setOnClickListener {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                } else {
                    // log fallback
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return view
    }
}
