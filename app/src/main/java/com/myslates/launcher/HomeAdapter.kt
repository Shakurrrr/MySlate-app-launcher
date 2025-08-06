package com.myslates.launcher

import android.content.ClipDescription
import android.content.Context
import android.util.Log
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class HomeAdapter(
    private val context: Context,
    private val apps: MutableList<AppObject>,
    private val onDrop: (String, Int) -> Unit
) : RecyclerView.Adapter<HomeAdapter.HomeViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomeViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.home_app_item, parent, false)
        return HomeViewHolder(view)
    }

    override fun getItemCount(): Int = apps.size

    override fun onBindViewHolder(holder: HomeViewHolder, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label

        holder.itemView.setOnClickListener {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, "App not found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot launch app", Toast.LENGTH_SHORT).show()
                Log.e("HomeAdapter", "Launch failed", e)
            }
        }
    }

    inner class HomeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.home_icon)
        val label: TextView = itemView.findViewById(R.id.home_label)

        init {
            itemView.setOnDragListener { _, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> {
                        val valid = event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                        Log.d("DRAG", "ACTION_DRAG_STARTED valid=$valid")
                        valid
                    }

                    DragEvent.ACTION_DRAG_ENTERED -> {
                        itemView.animate().alpha(0.7f).setDuration(100).start()
                        Log.d("DRAG", "ACTION_DRAG_ENTERED")
                        true
                    }

                    DragEvent.ACTION_DRAG_EXITED -> {
                        itemView.animate().alpha(1.0f).setDuration(100).start()
                        Log.d("DRAG", "ACTION_DRAG_EXITED")
                        true
                    }

                    DragEvent.ACTION_DROP -> {
                        itemView.animate().alpha(1.0f).setDuration(100).start()
                        val pkgName = event.clipData.getItemAt(0).text.toString()
                        val dropPos = adapterPosition
                        Log.d("DRAG", "ACTION_DROP at $dropPos for $pkgName")
                        if (dropPos != RecyclerView.NO_POSITION) {
                            onDrop(pkgName, dropPos)
                            Toast.makeText(context, "App dropped: $pkgName", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }

                    DragEvent.ACTION_DRAG_ENDED -> {
                        itemView.animate().alpha(1.0f).setDuration(100).start()
                        Log.d("DRAG", "ACTION_DRAG_ENDED result=${event.result}")
                        true
                    }

                    else -> false
                }
            }
        }
    }
}
