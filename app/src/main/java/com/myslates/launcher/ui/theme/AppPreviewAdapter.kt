package com.myslates.launcher.ui.theme

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.myslates.launcher.R
import com.myslates.launcher.data.PreviewApp
import com.myslates.launcher.data.IconRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class AppPreviewAdapter(
    private val context: Context,
    private val iconRepository: IconRepository,
    private val appsList: List<PreviewApp>
) : RecyclerView.Adapter<AppPreviewAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconImageView: ImageView = itemView.findViewById(R.id.app_icon)
        val labelTextView: TextView = itemView.findViewById(R.id.app_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(context).inflate(R.layout.item_app_preview, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = appsList[position]
        holder.labelTextView.text = app.label

        MainScope().launch {
            val drawable = iconRepository.getIcon(
                app.packageName,
                app.localFallbackResId
            )
            holder.iconImageView.setImageDrawable(drawable)
        }
    }

    override fun getItemCount(): Int = appsList.size
}