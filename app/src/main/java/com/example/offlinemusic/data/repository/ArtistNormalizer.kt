package com.example.offlinemusic.data.repository

import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

data class ParsedArtistCredit(
    val creditedName: String,
    val canonicalName: String,
    val normalizedName: String,
    val role: String, // PRIMARY, FEATURED, REMIXER
    val order: Int
)

data class ArtistParseResult(
    val rawString: String,
    val primaryArtist: String,
    val artists: List<ParsedArtistCredit>,
    val isAmbiguous: Boolean,
    val confidence: Float
)

object ArtistNormalizer {

    // Regex pattern for featured artist indicators (e.g. "feat.", "(ft.", "[featuring", "with")
    private val FEAT_PATTERN = Pattern.compile(
        """(?i)\s*[\(\[]?\s*(?:feat\.|featuring|ft\.|with)\s+"""
    )

    // Regex pattern for multi-artist separators:
    // Slashes (/, //), Backslashes (\), Semicolons (;), Pipes (|), Pluses (+), Ampersands (&, and), vs/x, Commas (,)
    private val DELIMITER_REGEX = """(?i)\s*(?:/+|\++|;+|\|+|\\+)\s*|\s+(?:&|and|vs\.?|x)\s+|,\s*""".toRegex()

    // Known artist aliases mapping: lowercase normalized alias -> canonical artist name
    private val KNOWN_ALIASES = mapOf(
        "marshmello" to "Marshmello",
        "marshmellow" to "Marshmello",
        "the beatles" to "The Beatles",
        "beatles" to "The Beatles",
        "led zep" to "Led Zeppelin",
        "led zeppelin" to "Led Zeppelin",
        "pink floyd" to "Pink Floyd",
        "queen" to "Queen",
        "ac dc" to "AC/DC",
        "ac/dc" to "AC/DC",
        "guns n roses" to "Guns N' Roses",
        "guns n' roses" to "Guns N' Roses",
        "eminem" to "Eminem",
        "slim shady" to "Eminem",
        "tupac" to "2Pac",
        "2pac" to "2Pac",
        "makaveli" to "2Pac",
        "notorious big" to "The Notorious B.I.G.",
        "biggie" to "The Notorious B.I.G.",
        "biggie smalls" to "The Notorious B.I.G.",
        "kanye" to "Kanye West",
        "ye" to "Kanye West",
        "kanye west" to "Kanye West",
        "dr dre" to "Dr. Dre",
        "snoop dogg" to "Snoop Dogg",
        "the weeknd" to "The Weeknd",
        "weeknd" to "The Weeknd"
    )

    // Bands that should not be split on & or and or connectors
    private val KNOWN_GROUPS_WITH_CONNECTORS = setOf(
        "simon garfunkel",
        "hall oates",
        "daryl hall john oates",
        "earth wind fire",
        "kool the gang",
        "crosby stills nash",
        "crosby stills nash young",
        "florence the machine",
        "bob marley the wailers",
        "huey lewis the news",
        "tom petty the heartbreakers",
        "joan jett the blackhearts",
        "kc the sunshine band",
        "katrina the waves",
        "echo the bunnymen"
    )

    /**
     * Normalizes an artist name for consistent matching:
     * - Strips diacritics / accents (e.g., Beyoncé -> Beyonce)
     * - Trims excess whitespace
     * - Converts to lowercase
     * - Normalizes common punctuation
     */
    fun normalize(name: String): String {
        if (name.isBlank()) return ""

        // 1. Unicode decomposition and diacritic removal
        val decomposed = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
        val stripped = decomposed.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")

        // 2. Lowercase using invariant locale
        val lower = stripped.lowercase(Locale.ROOT)

        // 3. Remove punctuation except internal alphanumeric chars and spaces
        val cleanPunctuation = lower.replace("[^a-z0-9\\s]".toRegex(), " ")

        // 4. Collapse multiple spaces into single space
        return cleanPunctuation.replace("\\s+".toRegex(), " ").trim()
    }

    /**
     * Generates a deterministic unique ID for an artist from their normalized name
     */
    fun generateArtistId(name: String): String {
        val norm = normalize(name)
        return "artist_${norm.hashCode().toUInt().toString(16)}"
    }

    /**
     * Checks if a name matches a predefined canonical artist alias
     */
    fun isKnownAlias(name: String): Boolean {
        val norm = normalize(name)
        return KNOWN_ALIASES.containsKey(norm)
    }

    /**
     * Resolves canonical name for an artist, checking known aliases
     */
    fun resolveCanonicalName(rawName: String): String {
        val norm = normalize(rawName)
        val aliasMatch = KNOWN_ALIASES[norm]
        if (aliasMatch != null) return aliasMatch

        // Clean display title casing, preserving known acronyms
        return rawName.trim().split("\\s+".toRegex()).joinToString(" ") { word ->
            if (word.length <= 3 && word.all { it.isUpperCase() }) {
                word
            } else {
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
        }
    }

    /**
     * Parses an artist string from audio metadata into individual credited artists.
     * Accurately parses multi-artist delimiters: "/", "\", ";", "|", "&", "feat.", "ft.", ",", etc.
     * Preserves credited name, canonical name, and order.
     */
    fun parseCredits(artistString: String): ArtistParseResult {
        val raw = artistString.trim()
        if (raw.isBlank() || raw.equals("Unknown Artist", ignoreCase = true) || raw.equals("Unknown", ignoreCase = true)) {
            val defaultArtist = "Unknown Artist"
            return ArtistParseResult(
                rawString = raw.ifBlank { defaultArtist },
                primaryArtist = defaultArtist,
                artists = listOf(
                    ParsedArtistCredit(
                        creditedName = defaultArtist,
                        canonicalName = defaultArtist,
                        normalizedName = normalize(defaultArtist),
                        role = "PRIMARY",
                        order = 0
                    )
                ),
                isAmbiguous = false,
                confidence = 1.0f
            )
        }

        val credits = mutableListOf<ParsedArtistCredit>()
        var order = 0

        // Check for "feat." or "featuring" first (clear semantic separation)
        val featParts = raw.split(FEAT_PATTERN.toRegex(), limit = 2)
        val primaryPart = featParts[0].trim()
        val featuredPart = if (featParts.size > 1) {
            featParts[1].replace("""[\)\]]+$""".toRegex(), "").trim()
        } else null

        // Parse primary artists (can contain delimiters like /, &, or commas)
        val primarySubParts = splitCollaborators(primaryPart)
        for (name in primarySubParts) {
            val cleanName = resolveCanonicalName(name)
            credits.add(
                ParsedArtistCredit(
                    creditedName = name.trim(),
                    canonicalName = cleanName,
                    normalizedName = normalize(cleanName),
                    role = "PRIMARY",
                    order = order++
                )
            )
        }

        // Parse featured artists
        if (featuredPart != null) {
            val featuredSubParts = splitCollaborators(featuredPart)
            for (name in featuredSubParts) {
                val cleanName = resolveCanonicalName(name)
                credits.add(
                    ParsedArtistCredit(
                        creditedName = name.trim(),
                        canonicalName = cleanName,
                        normalizedName = normalize(cleanName),
                        role = "FEATURED",
                        order = order++
                    )
                )
            }
        }

        // Determine ambiguity: If primary split resulted in complex combinations, flag for user review
        val isAmbiguous = primarySubParts.size > 2 || (primaryPart.contains(',') && primaryPart.contains('&'))
        val confidence = if (isAmbiguous) 0.65f else if (featuredPart != null) 0.95f else 0.90f
        val primaryArtist = credits.firstOrNull()?.canonicalName ?: raw

        return ArtistParseResult(
            rawString = raw,
            primaryArtist = primaryArtist,
            artists = credits,
            isAmbiguous = isAmbiguous,
            confidence = confidence
        )
    }

    /**
     * Splits collaborators while respecting connector bands (e.g. Simon & Garfunkel)
     * and punctuation-inclusive band names (e.g. AC/DC).
     */
    fun splitCollaborators(text: String): List<String> {
        val trimmed = text.trim()
        val norm = normalize(trimmed)
        if (norm in KNOWN_GROUPS_WITH_CONNECTORS) {
            return listOf(trimmed)
        }

        // Protect AC/DC from being split on the slash
        val safeText = trimmed.replace("(?i)\\bac/dc\\b".toRegex(), "__ACDC_ESCAPED__")

        val parts = safeText.split(DELIMITER_REGEX)
            .map { it.replace("__ACDC_ESCAPED__", "AC/DC").trim() }
            .filter { it.isNotEmpty() }

        return if (parts.isEmpty()) listOf(trimmed) else parts
    }

    /**
     * Calculates string similarity (Levenshtein distance ratio) between two strings
     */
    fun similarity(s1: String, s2: String): Float {
        val n1 = normalize(s1)
        val n2 = normalize(s2)
        if (n1 == n2) return 1.0f
        if (n1.isEmpty() || n2.isEmpty()) return 0.0f

        val len1 = n1.length
        val len2 = n2.length
        var prev = IntArray(len2 + 1) { it }
        var curr = IntArray(len2 + 1)

        for (i in 1..len1) {
            curr[0] = i
            for (j in 1..len2) {
                val cost = if (n1[i - 1] == n2[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }

        val distance = prev[len2]
        val maxLen = maxOf(len1, len2)
        return (maxLen - distance).toFloat() / maxLen.toFloat()
    }
}
