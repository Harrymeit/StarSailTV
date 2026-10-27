package com.harrymeit.starsailtv.recommendation

import com.harrymeit.starsailtv.data.model.*
import com.harrymeit.starsailtv.data.repository.CatalogQueries

object RecommendationEngine {
    fun recent(items: List<Media>) = CatalogQueries.sorted(items.filter { it.updatedAt != null }, CatalogFilter(sort = CatalogSort.UPDATED)).take(16)
    fun popular(items: List<Media>) = CatalogQueries.sorted(items.filter { it.popularityScore != null }, CatalogFilter(sort = CatalogSort.POPULARITY)).take(16)
    fun highRated(items: List<Media>) = CatalogQueries.sorted(items.filter { it.doubanRating != null && it.ratingSource != null }, CatalogFilter(sort = CatalogSort.RATING)).take(16)
}
