package com.harrymeit.starsailtv.ui.catalog

import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.harrymeit.starsailtv.data.model.*
import com.harrymeit.starsailtv.data.repository.CatalogQueries
import com.harrymeit.starsailtv.ui.components.TvComponents
import java.util.Calendar

class CatalogScreen(private val ui: TvComponents) {
    var filter = CatalogFilter()
    var page = 0
    var selectedIndex = 0
    fun create(snapshot: CatalogSnapshot, onChanged: () -> Unit, onMedia: (Media) -> Unit, onMore: () -> Unit): View {
        val column = ui.column()
        val filters = ui.row()
        fun choice(label: String, values: List<String>, selected: String, pick: (String) -> Unit) {
            filters.addView(ui.button("$label：$selected", "filter:$label") {
                AlertDialog.Builder(ui.context).setTitle(label).setSingleChoiceItems(values.toTypedArray(), values.indexOf(selected)) { dialog, index ->
                    pick(values[index]); page = 0; selectedIndex = 0; dialog.dismiss(); onChanged()
                }.setNegativeButton("取消", null).show()
            })
        }
        choice("地区", listOf("全部", "中国", "欧美", "韩国", "日本"), filter.region) { filter = filter.copy(region = it) }
        choice("类型", listOf("全部", "动作", "喜剧", "科幻", "悬疑", "爱情", "剧情", "恐怖"), filter.genre) { filter = filter.copy(genre = it) }
        val year = Calendar.getInstance().get(Calendar.YEAR)
        choice("年份", listOf("全部") + (year downTo 2024).map(Int::toString) + "更早", filter.year) { filter = filter.copy(year = it) }
        choice("排序", CatalogSort.entries.map { it.label }, filter.sort.label) { label -> filter = filter.copy(sort = CatalogSort.entries.first { it.label == label }) }
        column.addView(HorizontalScrollView(ui.context).apply { isFocusable = false; addView(filters) })
        val result = CatalogQueries.page(snapshot.items, filter, page)
        column.addView(ui.label("当前缓存目录 ${snapshot.items.size} 部，筛选后 ${result.total} 部 · 第 ${page + 1} 页 · 非全站排行", 12f, ui.muted))
        column.addView(ui.label(when (filter.sort) {
            CatalogSort.UPDATED -> "按资源站 vod_time 排序；缺少更新时间的作品放最后，不使用上映年份替代。"
            CatalogSort.POPULARITY -> "按已获取榜单的名次信号排序；无榜单信号的作品放最后。"
            CatalogSort.RATING -> "仅使用带明确豆瓣来源的评分；无评分作品放最后，不按同名强行补分。"
        }, 12f, ui.muted))
        if (result.items.isEmpty()) column.addView(ui.label("当前缓存中没有匹配作品。可放宽筛选或补充目录；不代表片源中不存在。", 18f))
        val grid = GridView(ui.context).apply {
            numColumns = 5; horizontalSpacing = ui.dp(10); verticalSpacing = ui.dp(12); stretchMode = GridView.STRETCH_COLUMN_WIDTH; clipToPadding = false; setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6)); tag = "catalog-grid"
        }
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = result.items.size
            override fun getItem(position: Int) = result.items[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = ui.card(result.items[position]) { selectedIndex = position; onMedia(result.items[position]) }.apply { isFocusable = false; isClickable = false; foreground = null }
        }
        grid.selector = android.graphics.drawable.StateListDrawable().apply { addState(intArrayOf(android.R.attr.state_focused), android.graphics.drawable.GradientDrawable().apply { setColor(0x2271E6D3); setStroke(ui.dp(3), ui.accent) }); addState(intArrayOf(android.R.attr.state_pressed), android.graphics.drawable.ColorDrawable(ui.accent)) }
        grid.setDrawSelectorOnTop(true)
        grid.setOnItemClickListener { _, _, pos, _ -> selectedIndex = pos; onMedia(result.items[pos]) }
        grid.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { selectedIndex = position }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        grid.setSelection(selectedIndex.coerceIn(0, (result.items.size - 1).coerceAtLeast(0)))
        column.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        val paging = ui.row()
        if (page > 0) paging.addView(ui.button("上一页", "page-prev") { page--; selectedIndex = 0; onChanged() })
        if (result.hasMore) paging.addView(ui.button("下一页", "page-next") { page++; selectedIndex = 0; onChanged() })
        paging.addView(ui.button("加载更多目录", "catalog-more", action = onMore))
        column.addView(paging)
        return column
    }
}
