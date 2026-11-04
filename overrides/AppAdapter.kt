package com.alps.minimal
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

data class AppItem(val label: String, val icon: Drawable, val packageName: String)
class AppAdapter(private val items: List<AppItem>, private val onClick: (AppItem) -> Unit) : RecyclerView.Adapter<AppAdapter.Holder>() {
 class Holder(view: View) : RecyclerView.ViewHolder(view)
 override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder = Holder(View(parent.context))
 override fun onBindViewHolder(holder: Holder, position: Int) {}
 override fun getItemCount() = items.size
}