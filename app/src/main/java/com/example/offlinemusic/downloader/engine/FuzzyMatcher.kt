package com.example.offlinemusic.downloader.engine

/**
 * High performance multi-tier fuzzy matching engine (matching lucida-flow fuzzy.rs)
 * Evaluates token coverage, character bigrams, and typo resilience to rank music search results.
 */
object FuzzyMatcher {

    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        var prevSpace = false

        for (c in s) {
            if (c.isLetterOrDigit()) {
                sb.append(c.lowercaseChar())
                prevSpace = false
            } else if (!prevSpace) {
                sb.append(' ')
                prevSpace = true
            }
        }
        return sb.toString().trim()
    }

    private fun tokenize(s: String): List<String> {
        return s.split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    /**
     * Character bigram Sørensen-Dice similarity (0.0 to 1.0)
     */
    fun diceBigramSimilarity(s1: String, s2: String): Double {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        if (s1 == s2) return 1.0

        if (s1.length < 2 || s2.length < 2) {
            return if (s1.contains(s2) || s2.contains(s1)) 0.8 else 0.0
        }

        val bigrams1 = HashSet<Pair<Char, Char>>()
        for (i in 0 until s1.length - 1) {
            bigrams1.add(Pair(s1[i], s1[i + 1]))
        }

        var intersection = 0
        val totalBigrams1 = bigrams1.size
        val totalBigrams2 = s2.length - 1
        val matched2 = HashSet<Pair<Char, Char>>()

        for (i in 0 until s2.length - 1) {
            val pair = Pair(s2[i], s2[i + 1])
            if (bigrams1.contains(pair) && matched2.add(pair)) {
                intersection++
            }
        }

        return (2.0 * intersection) / (totalBigrams1 + totalBigrams2).toDouble()
    }

    /**
     * Token Set Similarity: checks how many words from query exist in target
     */
    fun tokenCoverageScore(query: String, target: String): Double {
        val qTokens = tokenize(query)
        val tTokens = tokenize(target)

        if (qTokens.isEmpty()) return 0.0

        val tSet = tTokens.toHashSet()
        var matchedTokens = 0

        for (q in qTokens) {
            if (tSet.contains(q)) {
                matchedTokens++
            } else {
                var bestWordSim = 0.0
                for (t in tTokens) {
                    val sim = diceBigramSimilarity(q, t)
                    if (sim > bestWordSim) {
                        bestWordSim = sim
                    }
                }
                if (bestWordSim >= 0.70) {
                    matchedTokens++
                }
            }
        }

        return matchedTokens.toDouble() / qTokens.size.toDouble()
    }

    /**
     * Compute comprehensive match score between Spotify query and candidate track.
     */
    fun matchScore(
        queryTitle: String,
        queryArtist: String,
        targetTitle: String,
        targetArtist: String
    ): Double {
        val normQTitle = normalize(queryTitle)
        val normTTitle = normalize(targetTitle)
        val normQArtist = normalize(queryArtist)
        val normTArtist = normalize(targetArtist)

        val titleTokenScore = tokenCoverageScore(normQTitle, normTTitle)
        val titleDiceScore = diceBigramSimilarity(normQTitle, normTTitle)
        val titleScore = (titleTokenScore * 0.6) + (titleDiceScore * 0.4)

        val artistTokenScore = tokenCoverageScore(normQArtist, normTArtist)
        val artistDiceScore = diceBigramSimilarity(normQArtist, normTArtist)
        val artistScore = if (normQArtist.isEmpty()) 0.5 else (artistTokenScore * 0.5) + (artistDiceScore * 0.5)

        // Combined weighted score: Title has 65% weight, Artist has 35% weight
        return (titleScore * 0.65) + (artistScore * 0.35)
    }

    fun <T> findBestMatch(
        queryTitle: String,
        queryArtist: String,
        candidates: List<T>,
        minThreshold: Double = 0.50,
        extractor: (T) -> Pair<String, String>
    ): T? {
        var bestItem: T? = null
        var bestScore = minThreshold

        for (item in candidates) {
            val (title, artist) = extractor(item)
            val score = matchScore(queryTitle, queryArtist, title, artist)
            if (score > bestScore) {
                bestScore = score
                bestItem = item
            }
        }
        return bestItem
    }
}

