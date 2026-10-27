package com.harrymeit.starsailtv.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.harrymeit.starsailtv.*
import com.harrymeit.starsailtv.data.model.*
import com.harrymeit.starsailtv.data.repository.MetadataRepository
import com.harrymeit.starsailtv.search.PinyinSearchIndex
import com.harrymeit.starsailtv.ui.components.TvComponents
import com.harrymeit.starsailtv.ui.home.HomeScreen
import com.harrymeit.starsailtv.ui.search.SearchScreen
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoveryUiTest {
    private lateinit var activity: Activity
    private lateinit var ui: TvComponents
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val films = listOf(Media("earth", "流浪地球", year = 2019, updatedAt = 10, popularityScore = 3.0, doubanRating = 7.9, ratingSource = "豆瓣", category = MediaCategory.MOVIE), Media("sequel", "流浪地球2", year = 2023, updatedAt = 20, category = MediaCategory.MOVIE))
    @Before fun setUp() { activity = Robolectric.buildActivity(Activity::class.java).setup().get(); ui = TvComponents(activity) { ImageLoader(Api("http://127.0.0.1:9")) } }
    @After fun tearDown() { scope.cancel(); activity.finish() }
    private fun attach(view: View) { activity.setContentView(view); activity.window.decorView.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); activity.window.decorView.layout(0, 0, 1920, 1080); Shadows.shadowOf(Looper.getMainLooper()).idle() }
    private fun texts(view: View): List<String> = (if (view is TextView) listOf(view.text.toString()) else emptyList()) + if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList()
    private fun screenshot(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap)); File("../dist").mkdirs()
        File("../dist/$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun remoteKeyboardLldqSuggestsChineseAndSelectionUsesRealTitle() {
        var selected = ""
        val screen = SearchScreen(activity, ui, scope)
        val view = screen.create(PinyinSearchIndex(films), emptyList(), films, { selected = it }, { selected = it.title })
        attach(view)
        "LLDQ".forEach { view.findViewWithTag<View>("key:$it").performClick() }
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertEquals("LLDQ", view.findViewWithTag<EditText>("search-input").text.toString())
        val suggestion = view.findViewWithTag<View>("suggest:earth")
        assertNotNull(suggestion); screenshot(view, "search-lldq")
        suggestion.performClick(); assertEquals("流浪地球", selected)
    }
    @Test fun offlineSearchRetainsHistoryAndClearDeletesOnlyInput() {
        val screen = SearchScreen(activity, ui, scope)
        val view = screen.create(PinyinSearchIndex(emptyList()), listOf("星际穿越"), emptyList(), {}, {})
        attach(view); assertTrue(texts(view).contains("星际穿越"))
        view.findViewWithTag<View>("key:A").performClick(); view.findViewWithTag<View>("search-clear").performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertEquals("", screen.query); assertTrue(texts(view).contains("星际穿越"))
    }
    @Test fun homepageAlwaysExposesFourHonestSections() {
        val view = HomeScreen(ui).create(films, emptyList(), emptyList(), {}, {}, {}, {}, {})
        attach(view); val values = texts(view)
        assertTrue(values.contains("继续观看")); assertTrue(values.contains("最近更新 · 已缓存目录")); assertTrue(values.contains("热门推荐 · 榜单热度")); assertTrue(values.contains("豆瓣高分 · 有来源的评分"))
        screenshot(view, "home-preview")
    }
    @Test fun keyboardFocusRestoresAfterLeavingSearch() {
        val api = Api("http://127.0.0.1:9")
        val repository = MetadataRepository(activity, { api }, { emptyList() })
        repository.ingestSearch(listOf(SearchItem("源", "1", "流浪地球", "", "科幻片", "2019", "", "https://example.com")))
        var rendered: View? = null
        val controller = DiscoveryController(activity, scope, Prefs(activity), repository, { ImageLoader(api) }, { view, _ -> rendered = view; attach(view) }, {}, {}, {}, {}, {}, {}, {})
        controller.search(); Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(rendered!!.findViewWithTag<View>("key:L").requestFocus()); controller.leave(); controller.resume(); Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals("key:L", rendered!!.findFocus()?.tag)
        screenshot(rendered!!, "search-with-navigation")
    }
    @Test fun favoritesSurviveRepositoryRecreation() {
        val api = Api("http://127.0.0.1:9")
        val one = MetadataRepository(activity, { api }, { emptyList() })
        assertTrue(one.toggleFavorite(films[0]))
        val two = MetadataRepository(activity, { api }, { emptyList() })
        assertEquals("earth", two.favorites().single().mediaId)
        assertFalse(two.toggleFavorite(films[0])); assertTrue(two.favorites().isEmpty())
    }
}
