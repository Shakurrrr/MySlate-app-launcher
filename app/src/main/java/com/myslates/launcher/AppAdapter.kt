package com.myslates.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.myslates.launcher.R
import com.myslates.launcher.AppObject

class AppAdapter(private val context: Context, private val apps: List<AppObject>) : BaseAdapter() {

    override fun getCount(): Int = apps.size

    override fun getItem(position: Int): Any = apps[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_app, parent, false)
        val app = apps[position]

        val iconView = view.findViewById<ImageView>(R.id.app_icon)
        val labelView = view.findViewById<TextView>(R.id.app_label)

        iconView.setImageDrawable(app.icon)
        labelView.text = app.label

        // ⬇️ Set click listener to launch the app
        view.setOnClickListener {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                } else {
                    // Optionally show a toast or log fallback
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return view
    }
}
