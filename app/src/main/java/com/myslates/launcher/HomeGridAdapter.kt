package com.myslates.launcher

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HomeGridAdapter(
    private val context: Context,
    private val apps: MutableList<AppObject?>,
    private val onAppClick: (AppObject) -> Unit,
    private val onAppLongClick: (AppObject, Int) -> Unit,
    private val onEmptySlotDrop: (Int, AppObject) -> Boolean,
    private val onAppDrag: (AppObject, Int) -> Unit
) : RecyclerView.Adapter<HomeGridAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.home_grid_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = apps.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]

        holder.itemView.setOnClickListener(null)
        holder.itemView.setOnLongClickListener(null)
        holder.itemView.setOnTouchListener(null)

        if (app != null) {
            holder.iconView.setImageDrawable(app.icon)
            holder.labelView.text = app.label
            holder.iconView.visibility = View.VISIBLE
            holder.labelView.visibility = View.VISIBLE
            holder.emptySlot.visibility = View.GONE
            
            // Apply tablet scaling
            val iconSize = if (context.resources.configuration.smallestScreenWidthDp >= 600) 100 else 80
            val layoutParams = holder.iconView.layoutParams
            layoutParams.width = (iconSize * context.resources.displayMetrics.density).toInt()
            layoutParams.height = (iconSize * context.resources.displayMetrics.density).toInt()
            holder.iconView.layoutParams = layoutParams
            
            val textSize = if (context.resources.configuration.smallestScreenWidthDp >= 600) 18f else 16f
            holder.labelView.textSize = textSize

            holder.itemView.setOnClickListener {
                Log.d("HomeGridAdapter", "Clicked ${app.label} at $position")
                onAppClick(app)
            }

            holder.itemView.setOnLongClickListener {
                Log.d("HomeGridAdapter", "Long press on ${app.label} at $position")
                onAppLongClick(app, position)
                onAppDrag(app, position)
                true
            }

            addTouchFeedback(holder.itemView)
        } else {
            holder.iconView.visibility = View.GONE
            holder.labelView.visibility = View.GONE
            holder.emptySlot.visibility = View.VISIBLE
        }

        setupDragListener(holder, position)
    }

    private fun setupDragListener(holder: ViewHolder, position: Int) {
        holder.itemView.setOnDragListener { _, event ->
            val dragData = event.localState as? DragData
            val draggedApp = dragData?.app

            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    Log.d("HomeGridAdapter", "Drag started at position $position")
                    true
                }

                DragEvent.ACTION_DRAG_ENTERED -> {
                    if (apps[position] == null) {
                        holder.emptySlot.setBackgroundColor(Color.parseColor("#4CAF50"))
                        holder.emptySlot.alpha = 0.7f
                        holder.itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
                        Log.d("HomeGridAdapter", "Drag entered empty slot at $position")
                    } else {
                        holder.itemView.animate().scaleX(0.95f).scaleY(0.95f).setDuration(150).start()
                    }
                    true
                }

                DragEvent.ACTION_DRAG_EXITED -> {
                    holder.emptySlot.setBackgroundColor(Color.TRANSPARENT)
                    holder.emptySlot.alpha = 0.3f
                    holder.itemView.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    Log.d("HomeGridAdapter", "Drag exited position $position")
                    true
                }

                DragEvent.ACTION_DROP -> {
                    holder.emptySlot.setBackgroundColor(Color.TRANSPARENT)
                    holder.emptySlot.alpha = 0.3f
                    holder.itemView.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    
                    if (draggedApp != null && apps[position] == null) {
                        Log.d("HomeGridAdapter", "Dropping ${draggedApp.label} at position $position")
                        val accepted = onEmptySlotDrop(position, draggedApp)
                        if (accepted) {
                            notifyItemChanged(position)
                            Log.d("HomeGridAdapter", "Drop accepted at position $position")
                        } else {
                            Log.d("HomeGridAdapter", "Drop rejected at position $position")
                        }
                        accepted
                    } else if (draggedApp != null && apps[position] != null) {
                        // Try to swap or find next empty slot
                        Log.d("HomeGridAdapter", "Position $position occupied, trying to find alternative")
                        false
                    } else {
                        Log.d("HomeGridAdapter", "Invalid drop at position $position")
                        false
                    }
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    holder.emptySlot.setBackgroundColor(Color.TRANSPARENT)
                    holder.emptySlot.alpha = 0.3f
                    holder.itemView.animate().scaleX(1f).scaleY(1f).setDuration(150).start()

                    if (!event.result && dragData?.isFromHomeScreen == true) {
                        val originalPosition = dragData.originalPosition
                        if (originalPosition >= 0 && originalPosition < apps.size && apps[originalPosition] == null) {
                            apps[originalPosition] = dragData.app
                            notifyItemChanged(originalPosition)
                            Log.d("HomeGridAdapter", "Restored app to original position $originalPosition")
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun addTouchFeedback(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .alpha(0.8f)
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

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconView: ImageView = itemView.findViewById(R.id.home_grid_icon)
        val labelView: TextView = itemView.findViewById(R.id.home_grid_label)
        val emptySlot: View = itemView.findViewById(R.id.empty_slot)
    }
}
