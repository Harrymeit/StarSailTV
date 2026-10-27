package com.harrymeit.starsailtv.data.repository

import com.harrymeit.starsailtv.data.model.*

object CatalogQueries {
    /** Unknown years never merge into a known year; a shared external ID is the only exception. */
    fun merge(items: List<Media>): List<Media> {
        val result = linkedMapOf<String, Media>()
        val externalKeys = mutableMapOf<String, String>()
        val knownKeys = mutableMapOf<String, MutableList<String>>()
        fun knownKey(m: Media): String? = m.year?.let { "${Media.normalized(m.title)}|$it|${m.category}" }
        items.forEach { item ->
            val sameKnownWork = knownKey(item)?.let { knownKeys[it] }?.firstOrNull { key ->
                val existing = result.getValue(key)
                existing.doubanId == null || item.doubanId == null || existing.doubanId == item.doubanId
            }
            val key = item.doubanId?.let { externalKeys[it] } ?: sameKnownWork ?: item.doubanId?.takeIf { it.isNotBlank() }?.let { "douban:$it" } ?: item.mediaId
            val old = result[key]
            result[key] = if (old == null) item else old.copy(
                year = old.year ?: item.year,
                category = if (old.category == MediaCategory.UNKNOWN) item.category else old.category,
                aliases = (old.aliases + item.aliases).distinct(),
                genres = (old.genres + item.genres).distinct(),
                region = old.region.ifEmpty { item.region },
                updatedAt = listOfNotNull(old.updatedAt, item.updatedAt).maxOrNull(),
                doubanRating = item.doubanRating ?: old.doubanRating,
                doubanId = old.doubanId ?: item.doubanId,
                ratingSource = item.ratingSource ?: old.ratingSource,
                popularityScore = item.popularityScore ?: old.popularityScore,
                posterUrl = old.posterUrl.ifEmpty { item.posterUrl },
                remarks = if ((item.updatedAt ?: 0) > (old.updatedAt ?: 0)) item.remarks else old.remarks.ifEmpty { item.remarks },
                description = old.description.ifEmpty { item.description },
                sourceRefs = (old.sourceRefs + item.sourceRefs).distinctBy { it.url to it.vodId },
            )
            val merged = result.getValue(key)
            merged.doubanId?.let { externalKeys[it] = key }
            knownKey(merged)?.let { work -> knownKeys.getOrPut(work) { mutableListOf() }.let { if (key !in it) it.add(key) } }
        }
        return result.values.toList()
    }

    fun regionMatches(region: String, filter: String): Boolean = when (filter) {
        "全部" -> true
        "中国" -> listOf("中国", "大陆", "内地", "香港", "台湾", "澳门").any { it in region }
        "欧美" -> listOf("美国", "英国", "法国", "德国", "加拿大", "意大利", "西班牙", "欧美").any { it in region }
        else -> filter in region
    }

    fun sorted(items: List<Media>, filter: CatalogFilter): List<Media> = items.filter { item ->
        (filter.category == null || item.category == filter.category) && regionMatches(item.region, filter.region) &&
            (filter.genre == "全部" || item.genres.any { filter.genre in it }) &&
            (filter.year == "全部" || if (filter.year == "更早") item.year?.let { it < 2024 } == true else item.year?.toString() == filter.year)
    }.sortedWith(when (filter.sort) {
        CatalogSort.UPDATED -> compareByDescending<Media> { it.updatedAt ?: Long.MIN_VALUE }
        CatalogSort.POPULARITY -> compareByDescending<Media> { it.popularityScore ?: Double.NEGATIVE_INFINITY }
        CatalogSort.RATING -> compareByDescending<Media> { it.doubanRating ?: Double.NEGATIVE_INFINITY }
    }.thenBy { it.mediaId })

    /** Sorting is over the entire cached snapshot, then pagination, never over a rendered page. */
    fun page(items: List<Media>, filter: CatalogFilter, page: Int, pageSize: Int = 24): CatalogPage {
        val sorted = sorted(items, filter)
        val p = page.coerceAtLeast(0)
        return CatalogPage(sorted.drop(p * pageSize).take(pageSize), sorted.size, p, (p + 1) * pageSize < sorted.size)
    }
}
