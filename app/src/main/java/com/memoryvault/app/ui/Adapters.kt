package com.memoryvault.app.ui

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.memoryvault.app.R
import com.memoryvault.app.data.Item
import com.memoryvault.app.data.Story
import com.memoryvault.app.databinding.ItemMediaBinding
import com.memoryvault.app.databinding.ItemPickBinding
import com.memoryvault.app.databinding.ItemStoryBinding
import java.io.File

/** The albums grid: one square tile per story. Supports drag-to-reorder through [move]. */
class AlbumsAdapter(private val onClick: (Story) -> Unit) : RecyclerView.Adapter<AlbumsAdapter.VH>() {

    class VH(val b: ItemStoryBinding) : RecyclerView.ViewHolder(b.root)

    val items = mutableListOf<Story>()
    var columns = 3
    var highlightId = -1L

    init {
        setHasStableIds(true)
    }

    fun submit(list: List<Story>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun clearHighlight() {
        if (highlightId != -1L) {
            highlightId = -1L
            notifyDataSetChanged()
        }
    }

    fun move(from: Int, to: Int) {
        if (from < 0 || to < 0 || from >= items.size || to >= items.size) return
        items.add(to, items.removeAt(from))
        notifyItemMoved(from, to)
    }

    override fun getItemCount() = items.size
    override fun getItemId(position: Int) = items[position].id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vh = VH(ItemStoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        vh.b.root.setOnClickListener {
            val p = vh.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(items[p])
        }
        return vh
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val s = items[position]
        h.b.name.text = s.name
        h.b.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (columns >= 4) 9.5f else 12.5f)
        h.b.badge.isVisible = s.coverVideo
        val lit = s.id == highlightId
        val ctx = h.b.root.context
        h.b.root.strokeColor = ContextCompat.getColor(ctx, if (lit) R.color.accent else R.color.stroke)
        h.b.root.strokeWidth = (if (lit) 2.5f else 1f).times(ctx.resources.displayMetrics.density).toInt()
        h.b.root.scaleX = if (lit) 1.05f else 1f
        h.b.root.scaleY = if (lit) 1.05f else 1f
        val src = s.coverThumb ?: s.coverPath
        if (src != null) {
            h.b.thumb.scaleType = ImageView.ScaleType.CENTER_CROP
            Glide.with(h.b.thumb).load(File(src)).centerCrop().into(h.b.thumb)
        } else {
            Glide.with(h.b.thumb).clear(h.b.thumb)
            h.b.thumb.scaleType = ImageView.ScaleType.CENTER
            h.b.thumb.setImageResource(R.drawable.ic_image)
        }
    }
}

/** Horizontal rows on the story page: memories (portrait cards) and tags (square cards). */
class ItemAdapter(
    private val widthDp: Int,
    private val aspect: Float,
    private val onClick: (Item) -> Unit
) : RecyclerView.Adapter<ItemAdapter.VH>() {

    class VH(val b: ItemMediaBinding) : RecyclerView.ViewHolder(b.root)

    private var items: List<Item> = emptyList()

    fun submit(list: List<Item>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val d = parent.resources.displayMetrics.density
        val w = (widthDp * d).toInt()
        b.root.layoutParams = RecyclerView.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = (12 * d).toInt()
        }
        b.card.layoutParams.height = (w * aspect).toInt()
        val vh = VH(b)
        b.root.setOnClickListener {
            val p = vh.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(items[p])
        }
        return vh
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        Glide.with(h.b.image).load(File(item.thumb ?: item.path)).centerCrop().into(h.b.image)
        h.b.badge.isVisible = item.video
        h.b.name.text = item.name.orEmpty()
        h.b.name.visibility = if (item.name.isNullOrBlank()) View.INVISIBLE else View.VISIBLE
    }
}

/** Grid of thumbnails used when choosing one of the story's own pictures. */
class PickAdapter(
    private val paths: List<String>,
    private val onPick: (Int, String) -> Unit
) : RecyclerView.Adapter<PickAdapter.VH>() {

    class VH(val b: ItemPickBinding) : RecyclerView.ViewHolder(b.root)

    override fun getItemCount() = paths.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vh = VH(ItemPickBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        vh.b.root.setOnClickListener {
            val p = vh.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onPick(p, paths[p])
        }
        return vh
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        Glide.with(h.b.img).load(File(paths[position])).centerCrop().into(h.b.img)
    }
}
