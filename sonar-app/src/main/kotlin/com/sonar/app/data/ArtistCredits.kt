package com.sonar.app.data

private val ArtistSeparator = Regex(
    "\\s*(?:,|&|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)\\s*",
    RegexOption.IGNORE_CASE,
)

fun parseArtistCredits(rawArtist: String): List<String> =
    rawArtist
        .split(ArtistSeparator)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy(::normalizeArtistName)
        .ifEmpty { listOf(rawArtist.trim()) }

fun primaryArtist(rawArtist: String): String =
    parseArtistCredits(rawArtist).firstOrNull().orEmpty()

fun trackIncludesArtist(rawArtist: String, artist: String): Boolean {
    val selected = normalizeArtistName(artist)
    return selected.isNotEmpty() && parseArtistCredits(rawArtist).any {
        normalizeArtistName(it) == selected
    }
}

fun isPrimaryArtist(rawArtist: String, artist: String): Boolean =
    normalizeArtistName(primaryArtist(rawArtist)) == normalizeArtistName(artist)

private fun normalizeArtistName(value: String): String =
    value.trim().replace(Regex("\\s+"), " ").lowercase()
