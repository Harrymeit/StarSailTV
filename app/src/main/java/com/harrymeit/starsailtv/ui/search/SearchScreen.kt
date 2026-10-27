package com.harrymeit.starsailtv.ui.search

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.harrymeit.starsailtv.data.model.Media
import com.harrymeit.starsailtv.search.PinyinSearchIndex
import com.harrymeit.starsailtv.ui.components.TvComponents
import kotlinx.coroutines.*

class SearchScreen(private val activity: Activity, private val ui: TvComponents, private val scope: CoroutineScope) {
    var query = ""
    private var debounce: Job? = null
    fun create(index: PinyinSearchIndex, history: List<String>, popular: List<Media>, onSearch: (String) -> Unit, onMedia: (Media) -> Unit): View {
        val row = ui.row().apply { gravity = Gravity.TOP }
        val left = ui.column().apply { setPadding(0, 0, ui.dp(20), 0) }
        val right = ui.column()
        val input = EditText(activity).apply {
            hint = "首字母 / 拼音 / 中文 / English"; setText(query); setTextColor(ui.text); setHintTextColor(ui.muted); textSize = 18f; isSingleLine = true
            showSoftInputOnFocus = false; imeOptions = EditorInfo.IME_ACTION_SEARCH; tag = "search-input"; setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8))
        }
        left.addView(input, LinearLayout.LayoutParams(-1, ui.dp(46)))
        left.addView(ui.label("遥控器键盘 · 首字母需先选择片名建议", 12f, ui.muted))
        fun submit() { if (query.isNotBlank()) onSearch(query.trim()) }
        val keyboard = GridLayout(activity).apply { columnCount = 6; rowCount = 6 }
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".forEach { c ->
            keyboard.addView(ui.button(c.toString(), "key:$c") { input.setText(input.text.toString() + c); input.setSelection(input.length()) }, GridLayout.LayoutParams().apply { width = ui.dp(43); height = ui.dp(35); setMargins(2, 2, 2, 2) })
        }
        left.addView(keyboard)
        val actions = ui.row()
        actions.addView(ui.button("删除", "search-delete") { input.setText(input.text.toString().dropLast(1)) })
        actions.addView(ui.button("清空", "search-clear") { input.setText("") })
        actions.addView(ui.button("搜索", "search-submit", true) { submit() })
        left.addView(actions)
        left.addView(ui.button("中文 / 系统输入", "search-ime") { input.showSoftInputOnFocus = true; input.requestFocus(); (activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) })
        fun suggestions(value: String) {
            right.removeAllViews()
            right.addView(ui.label(if (value.isBlank()) "最近搜索" else "本地片名建议", 21f))
            if (value.isBlank()) {
                if (history.isEmpty()) right.addView(ui.label("还没有搜索记录", 14f, ui.muted))
                history.take(8).forEach { title -> right.addView(ui.button(title, "history:$title") { onSearch(title) }) }
            } else {
                val matches = index.suggest(value, 8)
                if (matches.isEmpty()) right.addView(ui.label("目录未收录匹配片名。中文 / 英文可直接搜索；拼音请先补充目录。", 14f, ui.muted))
                matches.forEach { media ->
                    right.addView(ui.button(media.title + (media.year?.let { " · $it" } ?: " · 年份待补充"), "suggest:${media.mediaId}") { input.setText(media.title); onMedia(media) })
                }
            }
            right.addView(ui.label("热门搜索 · 榜单片名", 18f))
            popular.take(5).forEach { m -> right.addView(ui.button(m.title, "popular:${m.mediaId}") { onMedia(m) }) }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s?.toString().orEmpty(); debounce?.cancel(); debounce = scope.launch { delay(180); suggestions(query) } }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        input.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_SEARCH) { submit(); true } else false }
        suggestions(query)
        row.addView(left, LinearLayout.LayoutParams(ui.dp(310), -2))
        row.addView(ScrollView(activity).apply { isFocusable = false; addView(right) }, LinearLayout.LayoutParams(0, -1, 1f))
        return row
    }
    fun cancel() { debounce?.cancel() }
}
