package com.myslates.launcher

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class AppAdapter(
    private val context: Context,
    private val apps: List<AppObject>
) : BaseAdapter() {

    override fun getCount(): Int = apps.size

    override fun getItem(position: Int): Any = apps[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_app, parent, false)

        val appIcon = view.findViewById<ImageView>(R.id.app_icon)
        val appLabel = view.findViewById<TextView>(R.id.app_label)

        val app = apps[position]
        appIcon.setImageDrawable(app.icon)
        appLabel.text = app.label

        return view
    }
}
