package io.github.tweeter042.alarmclock.core.data.repository

/**
 * Sort order for a newly inserted item: the caller's explicit [requested] value, or (for the
 * default 0) one past the current maximum so new items are appended at the end of the list.
 */
internal fun appendSortOrder(requested: Int, currentMax: Int?): Int =
    if (requested != 0) requested else (currentMax ?: -1) + 1

/**
 * Applies a user-chosen order: [ids] first (unknown ids and duplicates ignored), then every item
 * that was not mentioned, in its current order. Returns the items whose position changed, paired
 * with their new sort order (0-based, contiguous).
 */
internal fun <T> reorderedPositions(current: List<T>, ids: List<Long>, idOf: (T) -> Long, sortOf: (T) -> Int): List<Pair<T, Int>> {
    val byId = current.associateBy(idOf)
    val mentioned = LinkedHashSet<Long>()
    ids.forEach { if (it in byId) mentioned += it }
    val ordered = mentioned.map { byId.getValue(it) } + current.filter { idOf(it) !in mentioned }
    return ordered.mapIndexedNotNull { index, item -> if (sortOf(item) != index) item to index else null }
}

/** Splits a list of ids into chunks that stay well below SQLite's bound-variable limit. */
internal fun List<Long>.sqlChunks(): List<List<Long>> = distinct().chunked(SQL_CHUNK)

private const val SQL_CHUNK = 500

/** Orders [items] like [ids] (first occurrence wins); items whose id is not listed are dropped. */
internal fun <T> List<T>.inOrderOf(ids: List<Long>, idOf: (T) -> Long): List<T> {
    val position = HashMap<Long, Int>(ids.size)
    ids.forEachIndexed { index, id -> position.putIfAbsent(id, index) }
    return filter { idOf(it) in position }.sortedBy { position.getValue(idOf(it)) }
}
