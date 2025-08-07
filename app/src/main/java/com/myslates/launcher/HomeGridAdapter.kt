package com.myslates.launcher

import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class HomeGridAdapter(
    private val context: Context,
    private val apps: MutableList<AppObject?>,
    private val onAppClick: (AppObject) -> Unit,
    private val onAppLongClick: (AppObject, Int) -> Unit,
    private val onEmptySlotDrop: (Int, AppObject) -> Boolean
) : RecyclerView.Adapter<HomeGridAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.home_grid_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = apps.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        
        if (app != null) {
            // Show app
            holder.iconView.setImageDrawable(app.icon)
            holder.labelView.text = app.label
            holder.iconView.visibility = View.VISIBLE
            holder.labelView.visibility = View.VISIBLE
            holder.emptySlot.visibility = View.GONE
            
            // Click listeners
            holder.itemView.setOnClickListener { onAppClick(app) }
            holder.itemView.setOnLongClickListener { 
                onAppLongClick(app, position)
                true
            }
            
            // Add modern touch feedback
            addTouchFeedback(holder.itemView)
            
        } else {
            // Show empty slot
            holder.iconView.visibility = View.GONE
            holder.labelView.visibility = View.GONE
            holder.emptySlot.visibility = View.VISIBLE
            
            holder.itemView.setOnClickListener(null)
            holder.itemView.setOnLongClickListener(null)
        }
        
        // Setup drag listener for all slots
        setupDragListener(holder, position)
    }

    private fun setupDragListener(holder: ViewHolder, position: Int) {
        holder.itemView.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    Log.d("HomeGridAdapter", "Drag started over position $position")
                    true
                }

                DragEvent.ACTION_DRAG_ENTERED -> {
                    Log.d("HomeGridAdapter", "Drag entered position $position")
                    // Highlight the slot
                    if (apps[position] == null) {
                        holder.emptySlot.setBackgroundColor(Color.parseColor("#4CAF50"))
                        holder.emptySlot.alpha = 0.7f
                    }
                    true
                }

                DragEvent.ACTION_DRAG_EXITED -> {
                    Log.d("HomeGridAdapter", "Drag exited position $position")
                    // Remove highlight
                    holder.emptySlot.setBackgroundColor(Color.TRANSPARENT)
                    holder.emptySlot.alpha = 0.3f
                    true
                }

                DragEvent.ACTION_DROP -> {
                    Log.d("HomeGridAdapter", "Drop at position $position")
                    
                    val clipData = event.clipData
                    val data = clipData?.getItemAt(0)?.text?.toString()
                    
                    if (data != null) {
                        val app = extractAppFromDragData(event, data)
                        if (app != null) {
                            val success = onEmptySlotDrop(position, app)
                            Log.d("HomeGridAdapter", "Drop result: $success")
                            
                            // Remove highlight
                            holder.emptySlot.setBackgroundColor(Color.TRANSPARENT)
                            holder.emptySlot.alpha = 0.3f
                            
                            return@setOnDragListener success
                        }
                    }
                    false
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    Log.d("HomeGridAdapter", "Drag ended over position $position")
                    // Remove any highlights
                    holder.emptySlot.setBackgroundColor(Color.TRANSPARENT)
                    holder.emptySlot.alpha = 0.3f
                    true
                }

                else -> false
            }
        }
    }

    private fun extractAppFromDragData(event: DragEvent, data: String): AppObject? {
        return try {
            val dragData = event.localState as? MainActivity.DragData
            dragData?.app
        } catch (e: Exception) {
            Log.e("HomeGridAdapter", "Error extracting drag data", e)
            null
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