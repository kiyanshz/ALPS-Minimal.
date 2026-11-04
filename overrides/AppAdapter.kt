package com.alps.minimal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

data class AppItem(val label: String, val icon: Drawable, val packageName: String)

class AppAdapter(
    private val items: MutableList<AppItem>,
    private val onClick: (AppItem) -> Unit
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val label: TextView = view.findViewById(R.id.appLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.icon.setImageDrawable(resolveIcon(holder.itemView.context, item))
        holder.icon.scaleType = ImageView.ScaleType.FIT_CENTER
        holder.icon.clipToOutline = true
        holder.label.text = item.label
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
    }

    private fun resolveIcon(context: Context, item: AppItem): Drawable {
        val key = "${item.packageName} ${item.label}".lowercase(Locale.ROOT)
        val asset = when {
            listOf("maps", "map", "neshan", "navigation", "نقشه", "مسیریاب").any { key.contains(it) } -> "ios26_lg_maps"
            listOf("music", "spotify", "audio", "player", "موزیک", "موسیقی", "پخش").any { key.contains(it) } -> "ios26_lg_music"
            listOf("camera", "دوربین").any { key.contains(it) } -> "ios26_lg_camera"
            listOf("settings", "setting", "تنظیمات").any { key.contains(it) } -> "ios26_lg_settings"
            listOf("phone", "dialer", "contacts", "تلفن", "تماس", "مخاطبین").any { key.contains(it) } -> "ios26_lg_phone"
            listOf("message", "messaging", "sms", "whatsapp", "telegram", "تلگرام", "پیام").any { key.contains(it) } -> "ios26_lg_messages"
            else -> null
        } ?: return item.icon

        val id = context.resources.getIdentifier(asset, "drawable", context.packageName)
        return if (id != 0) context.getDrawable(id) ?: item.icon else item.icon
    }

    fun submitItems(next: List<AppItem>) {
        items.clear()
        items.addAll(next)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size
}
