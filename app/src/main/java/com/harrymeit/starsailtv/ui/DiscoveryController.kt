package com.harrymeit.starsailtv.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.harrymeit.starsailtv.*
import com.harrymeit.starsailtv.data.model.*
import com.harrymeit.starsailtv.data.repository.MetadataRepository
import com.harrymeit.starsailtv.recommendation.RecommendationEngine
import com.harrymeit.starsailtv.search.PinyinSearchIndex
import com.harrymeit.starsailtv.ui.catalog.CatalogScreen
import com.harrymeit.starsailtv.ui.components.TvComponents
import com.harrymeit.starsailtv.ui.home.HomeScreen
import com.harrymeit.starsailtv.ui.search.SearchScreen
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiscoveryController(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val prefs: Prefs,
    private val repository: MetadataRepository,
    images: () -> ImageLoader,
    private val display: (View, String) -> Unit,
    private val onSearch: (String) -> Unit,
    private val onPlay: (Media) -> Unit,
    private val onResume: (HistoryEntry) -> Unit,
    private val onHistory: () -> Unit,
    private val onSettings: () -> Unit,
    private val onSources: () -> Unit,
    private val onLive: () -> Unit,
) {
    private val ui = TvComponents(activity, images)
    private val searchScreen = SearchScreen(activity, ui, scope)
    private val catalogScreen = CatalogScreen(ui)
    private val homeScreen = HomeScreen(ui)
    private var page = "discover"
    private var detailParent = "discover"
    private var detailMedia: Media? = null
    private var root: LinearLayout? = null
    private var attached = false
    private var refreshJob: Job? = null
    private var statusLabel: TextView? = null
    private var lastAttempt = 0L
    private var favoritesOnly = false
    private val focus = mutableMapOf<String, String>()
    private val scroll = mutableMapOf<String, Int>()

    fun leave() {
        if (!attached) return
        root?.findFocus()?.tag?.toString()?.let { focus[page] = it }
        (root?.findViewWithTag<View>("home-scroll") as? ScrollView)?.let { scroll[page] = it.scrollY }
        searchScreen.cancel(); attached = false
    }
    fun home() { navigate("discover"); if (repository.cached().items.isEmpty() || System.currentTimeMillis() - repository.cached().fetchedAt > 6 * 3600_000L) refresh() }
    fun search() { navigate("search"); if (repository.cached().items.isEmpty()) refresh() }
    fun resume() { render() }
    fun back(): Boolean = when (page) {
        "discover" -> false
        "metadataDetail" -> { navigate(detailParent); true }
        else -> { home(); true }
    }
    private fun navigate(next: String) { leave(); page = next; render() }
    private fun detail(media: Media) { detailParent = page; detailMedia = media; navigate("metadataDetail") }
    fun open(media: Media) = detail(media)
    private fun launch(action: () -> Unit) { leave(); action() }
    fun resolve(query: String): String {
        val q = Media.normalized(query)
        val exact = PinyinSearchIndex(repository.cached().items).suggest(query).firstOrNull { q == it.pinyinInitials || q == it.fullPinyin }
        return exact?.title ?: query
    }
    private fun render() {
        attached = true
        val snapshot = repository.cached()
        val column = ui.column().apply { setBackgroundColor(ui.ink); setPadding(ui.dp(32), ui.dp(20), ui.dp(32), ui.dp(18)) }
        root = column
        val nav = ui.row()
        nav.addView(ui.label("星帆 TV", 23f, ui.accent).apply { setPadding(0, 0, ui.dp(12), 0) })
        fun nav(label: String, id: String, action: () -> Unit) { nav.addView(ui.button(label, "nav:$id", page == id, action)) }
        nav("首页", "discover") { home() }
        for (category in listOf(MediaCategory.MOVIE, MediaCategory.TV, MediaCategory.SHOW, MediaCategory.ANIME)) nav(category.label, category.name) {
            catalogScreen.filter = CatalogFilter(category = category); catalogScreen.page = 0; catalogScreen.selectedIndex = 0; favoritesOnly = false; navigate("catalog")
        }
        nav("搜索", "search") { search() }; nav("历史", "history") { launch(onHistory) }; nav("设置", "settings") { launch(onSettings) }
        val navButtons = (0 until nav.childCount).map { nav.getChildAt(it) }.filter { it.isFocusable }
        navButtons.forEachIndexed { index, view ->
            view.id = View.generateViewId()
            if (index > 0) { view.nextFocusLeftId = navButtons[index - 1].id; navButtons[index - 1].nextFocusRightId = view.id }
        }
        navButtons.firstOrNull()?.let { it.nextFocusLeftId = it.id }
        navButtons.lastOrNull()?.let { it.nextFocusRightId = it.id }
        column.addView(HorizontalScrollView(activity).apply { isFocusable = false; isHorizontalScrollBarEnabled = false; addView(nav) })
        val state = ui.row()
        statusLabel = ui.label(status(snapshot), 11f, ui.muted)
        state.addView(statusLabel, LinearLayout.LayoutParams(0, -2, 1f))
        state.addView(ui.button("刷新目录", "refresh") { refresh(force = true) })
        state.addView(ui.button("片源", "sources") { launch(onSources) })
        state.addView(ui.button("直播", "live") { launch(onLive) })
        column.addView(state)
        if (snapshot.warnings.isNotEmpty()) column.addView(ui.label(snapshot.warnings.joinToString(" · "), 11f, ui.muted).apply { maxLines = 1 })
        val body = when (page) {
            "search" -> searchScreen.create(PinyinSearchIndex(snapshot.items + repository.favorites()), prefs.searchHistory(), RecommendationEngine.popular(snapshot.items), { q -> launch { onSearch(resolve(q)) } }, { m -> launch { onSearch(m.title) } })
            "catalog" -> catalogScreen.create(if (favoritesOnly) snapshot.copy(items = repository.favorites()) else snapshot, { leave(); render() }, ::detail) { refresh(more = true, force = true) }
            "metadataDetail" -> metadataDetail(detailMedia!!)
            else -> homeScreen.create(snapshot.items, prefs.history(), repository.favorites(), ::detail, { h -> launch { onResume(h) } }, { m -> repository.toggleFavorite(m); leave(); render() }, { sort ->
                favoritesOnly = sort == null; catalogScreen.filter = CatalogFilter(sort = sort ?: CatalogSort.UPDATED); catalogScreen.page = 0; catalogScreen.selectedIndex = 0; navigate("catalog")
            }, { launch(onHistory) })
        }
        column.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        display(column, page)
        column.post {
            val remembered = focus[page]?.let { column.findViewWithTag<View>(it) }
            if (remembered != null) remembered.requestFocus() else column.findViewWithTag<View>(if (page == "search") "search-input" else "nav:discover")?.requestFocus()
            (body as? ScrollView)?.post { body.scrollTo(0, scroll[page] ?: 0) }
        }
    }
    private fun status(s: CatalogSnapshot): String {
        val stamp = if (s.fetchedAt == 0L) "尚未同步" else SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(Date(s.fetchedAt))
        return "缓存目录 ${s.items.size} 部 · $stamp · 仅缓存范围，不代表全站" + if (refreshJob?.isActive == true) " · 同步中…" else ""
    }
    private fun metadataDetail(m: Media): View {
        val body = ui.column(); val header = ui.row()
        header.addView(ui.poster(m.posterUrl, 145, 190))
        val info = ui.column().apply { setPadding(ui.dp(24), 0, 0, 0) }
        info.addView(ui.label(m.title, 28f)); info.addView(ui.label(listOfNotNull(m.year?.toString(), m.category.label, m.region.takeIf(String::isNotBlank)).joinToString(" · "), 16f, ui.muted))
        info.addView(ui.label(m.doubanRating?.let { "豆瓣 %.1f · %s".format(it, m.ratingSource) } ?: "暂无可核实的豆瓣评分", 16f, ui.accent))
        info.addView(ui.label(m.updatedAt?.let { "资源更新：" + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(it)) } ?: "资源站未提供更新时间", 14f, ui.muted))
        info.addView(ui.label(m.description.ifEmpty { "暂无简介。此处是选片目录，点击查找片源后才确认实际可播资源。" }, 15f, ui.muted).apply { maxLines = 5 })
        val actions = ui.row()
        actions.addView(ui.button("查找片源并选集", "detail-play", true) { launch { onPlay(m) } })
        actions.addView(ui.button(if (repository.isFavorite(m)) "取消收藏" else "加入收藏", "detail-favorite") { repository.toggleFavorite(m); leave(); render() })
        actions.addView(ui.button("返回", "detail-back") { back() })
        info.addView(actions); header.addView(info, LinearLayout.LayoutParams(0, -2, 1f)); body.addView(header)
        return ScrollView(activity).apply { isFocusable = false; addView(body) }
    }
    private fun refresh(more: Boolean = false, force: Boolean = false) {
        if (refreshJob?.isActive == true) return
        if (!force && System.currentTimeMillis() - lastAttempt < 120_000) return
        lastAttempt = System.currentTimeMillis()
        refreshJob = scope.launch {
            statusLabel?.text = "正在更新目录…本地缓存仍可浏览"
            try {
                repository.refresh(more)
                refreshJob = null
                if (attached) { leave(); render() }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { refreshJob = null; if (attached) statusLabel?.text = "网络异常，保留缓存 · 可点击刷新重试" }
        }
    }
}
