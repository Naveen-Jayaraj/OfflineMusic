package com.example.offlinemusic.data.model

enum class SortBy(val displayName: String) {
    TITLE("Song Title"),
    ARTIST("Artist Name"),
    ALBUM("Album / Folder"),
    YEAR("Release Year"),
    DURATION("Duration"),
    DATE_ADDED("Date Modified"),
    FILE_SIZE("File Size")
}

enum class SortOrder(val displayName: String) {
    ASCENDING("Ascending (A-Z, Old-New)"),
    DESCENDING("Descending (Z-A, New-Old)")
}

data class SortOption(
    val sortBy: SortBy = SortBy.TITLE,
    val sortOrder: SortOrder = SortOrder.ASCENDING
)
