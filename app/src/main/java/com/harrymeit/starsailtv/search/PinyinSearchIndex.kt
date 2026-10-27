package com.harrymeit.starsailtv.search

import com.harrymeit.starsailtv.data.model.Media
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

object Pinyin {
    private val format = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE; toneType = HanyuPinyinToneType.WITHOUT_TONE; vCharType = HanyuPinyinVCharType.WITH_V
    }
    fun of(title: String): Pair<String, String> {
        val parts = title.mapNotNull { c ->
            if (c.isWhitespace() || !(c.isLetterOrDigit())) null
            else runCatching { PinyinHelper.toHanyuPinyinStringArray(c, format)?.firstOrNull() }.getOrNull() ?: c.lowercase()
        }
        return parts.joinToString("") to parts.joinToString("") { it.take(1) }
    }
    fun enrich(media: Media): Media {
        val (full, initials) = of(media.title)
        return media.copy(fullPinyin = full, pinyinInitials = initials)
    }
}

class PinyinSearchIndex(items: List<Media>) {
    private val entries = items.map { if (it.fullPinyin.isEmpty()) Pinyin.enrich(it) else it }
    fun suggest(query: String, limit: Int = 12): List<Media> {
        val q = Media.normalized(query)
        if (q.isEmpty()) return emptyList()
        return entries.mapNotNull { item ->
            val titles = (listOf(item.title) + item.aliases).map(Media::normalized)
            val aliasPinyin = item.aliases.map(Pinyin::of)
            val score = when {
                q in titles -> 0
                q == item.pinyinInitials || aliasPinyin.any { q == it.second } -> 1
                q == item.fullPinyin || aliasPinyin.any { q == it.first } -> 2
                titles.any { it.startsWith(q) } -> 3
                item.pinyinInitials.startsWith(q) || item.fullPinyin.startsWith(q) -> 4
                titles.any { q in it } || q in item.fullPinyin -> 5
                else -> return@mapNotNull null
            }
            score to item
        }.sortedWith(compareBy<Pair<Int, Media>> { it.first }.thenBy { it.second.title.length }.thenByDescending { it.second.year ?: 0 }.thenBy { it.second.mediaId })
            .take(limit).map { it.second }
    }
}
