package app.vohcarb.catalog

import app.vohcarb.core.api.ApiClient
import kotlinx.serialization.Serializable

// Mirrors the backend's catalog DTOs (app.vohcarb.catalog.CatalogDtos). Timestamps and dates stay strings.

@Serializable
data class CategoryDto(val slug: String, val name: String, val description: String? = null, val wordCount: Long = 0)

@Serializable
data class CategoryRef(val slug: String, val name: String)

@Serializable
data class LevelDto(val level: Int, val name: String, val description: String, val wordCount: Long = 0)

@Serializable
data class PronunciationDto(val ipa: String, val variety: String? = null)

@Serializable
data class ExampleDto(val sentence: String, val source: String? = null)

@Serializable
data class SenseDto(
    val partOfSpeech: String,
    val definition: String,
    val category: String? = null,
    val examples: List<ExampleDto> = emptyList(),
)

@Serializable
data class WordSummaryDto(
    val slug: String,
    val lemma: String,
    val difficulty: Int? = null,
    val partsOfSpeech: List<String> = emptyList(),
    val shortDefinition: String? = null,
)

@Serializable
data class WordDetailDto(
    val slug: String,
    val lemma: String,
    val difficulty: Int? = null,
    val pronunciations: List<PronunciationDto> = emptyList(),
    val senses: List<SenseDto> = emptyList(),
    val categories: List<CategoryRef> = emptyList(),
)

@Serializable
data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
) {
    val isLast get() = page + 1 >= totalPages
}

@Serializable
data class WordOfTheDayDto(val date: String, val word: WordDetailDto)

/** Level names as the backend defines them (1 = everyday … 5 = rare). */
fun levelName(level: Int?): String? = when (level) {
    1 -> "Everyday"
    2 -> "Common"
    3 -> "Intermediate"
    4 -> "Advanced"
    5 -> "Rare"
    else -> null
}

class CatalogApi(private val api: ApiClient) {
    suspend fun categories(): List<CategoryDto> = api.get("/api/v1/categories")

    suspend fun levels(): List<LevelDto> = api.get("/api/v1/levels")

    suspend fun word(slug: String): WordDetailDto = api.get("/api/v1/words/$slug")

    suspend fun search(query: String?, page: Int = 0, size: Int = 30): PageResponse<WordSummaryDto> =
        api.get("/api/v1/words", "q" to query?.trim()?.lowercase()?.ifBlank { null }, "page" to page, "size" to size)

    suspend fun wordOfTheDay(): WordOfTheDayDto = api.get("/api/v1/word-of-the-day")
}
