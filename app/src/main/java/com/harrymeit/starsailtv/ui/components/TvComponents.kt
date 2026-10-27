package com.harrymeit.starsailtv.ui.components

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.harrymeit.starsailtv.ImageLoader
import com.harrymeit.starsailtv.data.model.Media

class TvComponents(val context: Context, private val images: () -> ImageLoader) {
    val ink = 0xFF10182D.toInt(); val panel = 0xFF1C2942.toInt(); val accent = 0xFF71E6D3.toInt(); val text = 0xFFF4F7FF.toInt(); val muted = 0xFFB5C2D8.toInt()
    fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
    fun label(value: String, size: Float = 16f, color: Int = text) = TextView(context).apply { this.text = value; textSize = size; setTextColor(color); setPadding(dp(4), dp(4), dp(4), dp(4)) }
    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
    fun row() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false; clipToPadding = false }
    fun focus(view: View, primary: Boolean = false) {
        view.isFocusable = true; view.isFocusableInTouchMode = true; view.isClickable = true
        fun background(focused: Boolean) = GradientDrawable().apply { setColor(if (primary) 0xFF205657.toInt() else panel); cornerRadius = dp(10).toFloat(); if (focused) setStroke(dp(3), accent) }
        view.background = background(false)
        view.onFocusChangeListener = View.OnFocusChangeListener { v, has ->
            v.background = background(has); v.animate().scaleX(if (has) 1.025f else 1f).scaleY(if (has) 1.025f else 1f).setDuration(100).start()
            if (has) v.requestRectangleOnScreen(Rect(0, 0, v.width, v.height), false)
        }
    }
    fun button(value: String, key: String = value, primary: Boolean = false, action: () -> Unit) = TextView(context).apply {
        text = value; textSize = 15f; setTextColor(this@TvComponents.text); gravity = Gravity.CENTER; minHeight = dp(42); setPadding(dp(14), dp(8), dp(14), dp(8)); tag = key; contentDescription = value
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
        focus(this, primary); setOnClickListener { action() }
    }
    fun poster(url: String, width: Int = 128, height: Int = 164): ImageView = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(width), dp(height)); scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(panel)
        contentDescription = "影片海报"; tag = url
        var requested = false
        fun loadWhenVisible() {
            if (requested || !isAttachedToWindow || !getGlobalVisibleRect(Rect())) return
            requested = true
            images().load(url, dp(width), dp(height)) { bitmap -> if (tag == url) { if (bitmap != null) setImageBitmap(bitmap) else contentDescription = "海报加载失败" } }
        }
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            private val listener = android.view.ViewTreeObserver.OnScrollChangedListener { loadWhenVisible() }
            override fun onViewAttachedToWindow(v: View) { viewTreeObserver.addOnScrollChangedListener(listener); post { loadWhenVisible() } }
            override fun onViewDetachedFromWindow(v: View) { if (viewTreeObserver.isAlive) viewTreeObserver.removeOnScrollChangedListener(listener) }
        })
    }
    fun card(media: Media, action: () -> Unit): View = column().apply {
        tag = "media:${media.mediaId}"; setPadding(dp(5), dp(5), dp(5), dp(7)); focus(this)
        addView(poster(media.posterUrl))
        addView(label(media.title, 14f).apply { maxLines = 2; minHeight = dp(38); maxWidth = dp(128) })
        addView(label(listOfNotNull(media.doubanRating?.let { "豆瓣 %.1f".format(it) }, media.year?.toString()).joinToString(" · ").ifEmpty { "年份 / 评分待补充" }, 11f, accent))
        addView(label(media.remarks.ifEmpty { if (media.sourceRefs.isEmpty()) "选片后查找片源" else "${media.sourceRefs.size} 个目录来源" }, 11f, muted).apply { maxLines = 1; maxWidth = dp(128) })
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS; setOnClickListener { action() }
    }
}
