package com.alps.minimal
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class AppItem(val label: String, val icon: Drawable, val packageName: String)
class AppAdapter(private val items: List<AppItem>, private val onClick: (AppItem) -> Unit) : RecyclerView.Adapter<AppAdapter.Holder>() {
 class Holder(view: View) : RecyclerView.ViewHolder(view) {
  val icon: ImageView = view.findViewById(R.id.appIcon)
  val label: TextView = view.findViewById(R.id.appLabel)
 }
 override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder =
  Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))
 override fun onBindViewHolder(holder: Holder, position: Int) {
  val item = items[position]
  holder.icon.setImageDrawable(item.icon)
  holder.label.text = item.label
  holder.itemView.setOnClickListener { onClick(item) }
 }
 override fun getItemCount() = items.size
}