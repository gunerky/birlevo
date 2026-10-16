package com.example.contentstudio.media

data class PdfPageRef(val id: String, val source: Int, val page: Int, val rotation: Int = 0)

object PdfPagePlan {
    const val MAX_SOURCES = 8
    const val MAX_BYTES = 128L * 1024 * 1024
    const val MAX_PAGES = 2000

    /** One-based ranges in the current workspace order. No duplicates or partial acceptance. */
    fun selection(text: String, count: Int): List<Int>? {
        if (count !in 1..MAX_PAGES || text.length > 12_000) return null
        val normalized = text.map { character ->
            val digit = Character.digit(character, 10)
            if (digit >= 0) '0' + digit else when (character) {
                '،', '，' -> ','
                '–', '—', '−', '－' -> '-'
                else -> character
            }
        }.joinToString("")
        val result = mutableListOf<Int>()
        for (part in normalized.split(',')) {
            val match = Regex("\\s*([0-9]+)\\s*(?:-\\s*([0-9]+)\\s*)?").matchEntire(part) ?: return null
            val first = match.groupValues[1].toIntOrNull() ?: return null
            val last = match.groupValues[2].ifBlank { match.groupValues[1] }.toIntOrNull() ?: return null
            if (first !in 1..count || last !in first..count) return null
            for (index in first..last) {
                if (index - 1 in result) return null
                result.add(index - 1)
            }
        }
        return result.takeIf { it.isNotEmpty() }
    }

    fun validate(pages: List<PdfPageRef>, counts: List<Int>) {
        require(counts.size in 1..MAX_SOURCES && pages.size in 1..MAX_PAGES)
        require(pages.map { it.id }.distinct().size == pages.size)
        require(pages.all { it.source in counts.indices && it.page in 0 until counts[it.source] && it.rotation in listOf(0, 90, 180, 270) })
    }
}

enum class PdfPageIssue { INVALID, PROTECTED, COMPLEX, LIMIT, ACCESS, MEMORY, PROCESS, EXPORT, STORAGE }
class PdfPageException(val issue: PdfPageIssue) : IllegalArgumentException(issue.name)
