package com.game.reschange

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip

class AppListAdapter(
    private var apps: List<AppInfo>,
    private val onClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView         = view.findViewById(R.id.appIcon)
        val name: TextView          = view.findViewById(R.id.appName)
        val packageName: TextView   = view.findViewById(R.id.packageName)
        val modifiedIcon: ImageView = view.findViewById(R.id.modifiedIcon)
        val modDot: View            = view.findViewById(R.id.modDot)
        val scaleBadge: Chip        = view.findViewById(R.id.scaleBadge)
        val card: MaterialCardView  = view.findViewById(R.id.itemCard)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    fun submitList(newList: List<AppInfo>) {
        apps = newList
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.name
        holder.packageName.text = app.packageName

        val savedScale = ResChangePrefs.getScale(holder.itemView.context, app.packageName)
        val isModified = savedScale < 1.0f

        if (isModified) {
            val pct = "${(savedScale * 100).toInt()}%"
            holder.scaleBadge.text = pct
            holder.scaleBadge.visibility = View.VISIBLE
            holder.modDot.visibility     = View.VISIBLE
            holder.card.setCardBackgroundColor(Color.parseColor("#4D003730"))
            holder.name.setTextColor(Color.parseColor("#FF6FF7E8"))
        } else {
            holder.scaleBadge.visibility = View.GONE
            holder.modDot.visibility     = View.GONE
            holder.card.setCardBackgroundColor(Color.parseColor("#FF000000"))
            holder.name.setTextColor(Color.parseColor("#FFE8E8E8"))
        }

        holder.itemView.setOnClickListener { onClick(app) }
    }

    override fun getItemCount(): Int = apps.size
}
