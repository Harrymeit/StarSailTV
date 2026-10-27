package com.harrymeit.starsailtv.ui.home

import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.harrymeit.starsailtv.HistoryEntry
import com.harrymeit.starsailtv.data.model.*
import com.harrymeit.starsailtv.recommendation.RecommendationEngine
import com.harrymeit.starsailtv.ui.components.TvComponents

class HomeScreen(private val ui: TvComponents) {
    fun create(items: List<Media>, history: List<HistoryEntry>, favorites: List<Media>, onMedia: (Media) -> Unit, onResume: (HistoryEntry) -> Unit, onFavorite: (Media) -> Unit, onAll: (CatalogSort?) -> Unit, onHistory: () -> Unit): ScrollView {
        val column = ui.column()
        val featured = RecommendationEngine.popular(items).firstOrNull() ?: RecommendationEngine.recent(items).firstOrNull()
        val hero = ui.row().apply { setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12)); setBackgroundColor(ui.panel) }
        if (featured != null) {
            hero.addView(ui.poster(featured.posterUrl, 100, 136))
            val info = ui.column().apply { setPadding(ui.dp(20), 0, 0, 0) }
            info.addView(ui.label("本周精选 · 榜单推荐", 13f, ui.accent))
            info.addView(ui.label(featured.title, 27f))
            info.addView(ui.label(listOfNotNull(featured.doubanRating?.let { "豆瓣 %.1f".format(it) }, featured.year?.toString(), featured.category.label).joinToString(" · "), 14f, ui.muted))
            info.addView(ui.label(featured.description.ifEmpty { "发现好故事。选片后查询已配置片源，实际可播状态以查询结果为准。" }, 14f, ui.muted).apply { maxLines = 2 })
            val actions = ui.row()
            actions.addView(ui.button("查看详情", "hero-detail", true) { onMedia(featured) })
            actions.addView(ui.button(if (favorites.any { it.mediaId == featured.mediaId }) "取消收藏" else "加入收藏", "hero-favorite") { onFavorite(featured) })
            info.addView(actions); hero.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        } else {
            hero.addView(ui.label("星帆 TV\n让下一部好片，自己来到眼前。\n正在建立本地选片目录，也可以直接进入搜索。", 23f))
        }
        column.addView(hero)
        fun shelf(title: String, key: String, data: List<Media>, all: () -> Unit) {
            val heading = ui.row(); heading.addView(ui.label(title, 21f), LinearLayout.LayoutParams(0, -2, 1f)); heading.addView(ui.button("查看全部", "all:$key", action = all)); column.addView(heading)
            if (data.isEmpty()) { column.addView(ui.label("暂无内容；目录加载失败时可重试刷新。", 14f, ui.muted)); return }
            val cards = ui.row().apply { setPadding(ui.dp(4), ui.dp(6), ui.dp(4), ui.dp(10)) }
            data.forEach { media -> cards.addView(ui.card(media) { onMedia(media) }.apply { tag = "$key:${media.mediaId}" }, LinearLayout.LayoutParams(ui.dp(146), -2).apply { marginEnd = ui.dp(10) }) }
            column.addView(HorizontalScrollView(ui.context).apply { isFocusable = false; isHorizontalScrollBarEnabled = false; clipToPadding = false; addView(cards) })
        }
        val historyHeading = ui.row(); historyHeading.addView(ui.label("继续观看", 21f), LinearLayout.LayoutParams(0, -2, 1f)); historyHeading.addView(ui.button("查看全部", "all:history", action = onHistory)); column.addView(historyHeading)
        val resumeItems = history.sortedByDescending { it.updatedAt }.take(12)
        if (resumeItems.isEmpty()) column.addView(ui.label("播放后会在这里保留本机进度。", 14f, ui.muted))
        else {
            val cards = ui.row()
            resumeItems.forEach { h ->
                val m = Media("history:${h.playUrl}", h.title, posterUrl = h.cover, remarks = "已看 ${h.positionMs / 60000} 分钟")
                cards.addView(ui.card(m) { onResume(h) }, LinearLayout.LayoutParams(ui.dp(146), -2).apply { marginEnd = ui.dp(10) })
            }
            column.addView(HorizontalScrollView(ui.context).apply { isFocusable = false; addView(cards) })
        }
        shelf("最近更新 · 已缓存目录", "recent", RecommendationEngine.recent(items)) { onAll(CatalogSort.UPDATED) }
        shelf("热门推荐 · 榜单热度", "popular", RecommendationEngine.popular(items)) { onAll(CatalogSort.POPULARITY) }
        shelf("豆瓣高分 · 有来源的评分", "rated", RecommendationEngine.highRated(items)) { onAll(CatalogSort.RATING) }
        if (favorites.isNotEmpty()) shelf("我的收藏 · 本机", "favorites", favorites) { onAll(null) }
        column.addView(ui.label("基于 DpadTV 修改的非官方版本 · GPL-3.0 · 历史与收藏保存在本机", 11f, ui.muted))
        return ScrollView(ui.context).apply { isFocusable = false; clipToPadding = false; addView(column, ViewGroup.LayoutParams(-1, -2)); tag = "home-scroll" }
    }
}
