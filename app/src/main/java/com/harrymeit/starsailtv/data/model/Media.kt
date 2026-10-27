package com.harrymeit.starsailtv.data.model

import java.text.Normalizer
import java.util.Locale

enum class MediaCategory(val label: String) { MOVIE("电影"), TV("电视剧"), SHOW("综艺"), ANIME("动漫"), UNKNOWN("其他") }
enum class CatalogSort(val label: String) { UPDATED("最近更新"), POPULARITY("榜单热度"), RATING("豆瓣评分") }
data class SourceRef(val name: String, val url: String, val vodId: String)
data class Media(
    val mediaId: String,
    val title: String,
    val aliases: List<String> = emptyList(),
    val fullPinyin: String = "",
    val pinyinInitials: String = "",
    val year: Int? = null,
    val category: MediaCategory = MediaCategory.UNKNOWN,
    val genres: List<String> = emptyList(),
    val region: String = "",
    val updatedAt: Long? = null,
    val doubanRating: Double? = null,
    val doubanId: String? = null,
    val popularityScore: Double? = null,
    val posterUrl: String = "",
    val remarks: String = "",
    val description: String = "",
    val sourceRefs: List<SourceRef> = emptyList(),
    val ratingSource: String? = null,
) {
    companion object {
        fun normalized(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT).replace(Regex("[\\s\\p{Punct}，。！？：；、（）【】《》·]+"), "")
        fun id(title: String, year: Int?, category: MediaCategory): String = "${normalized(title)}|${year ?: "?"}|${category.name}"
    }
}
data class CatalogFilter(val category: MediaCategory? = null, val region: String = "全部", val genre: String = "全部", val year: String = "全部", val sort: CatalogSort = CatalogSort.UPDATED)
data class CatalogPage(val items: List<Media>, val total: Int, val page: Int, val hasMore: Boolean)
data class CatalogSnapshot(val items: List<Media>, val fetchedAt: Long = 0L, val sourcePages: Map<String, Int> = emptyMap(), val warnings: List<String> = emptyList())
