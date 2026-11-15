package app.tateyomi.reader

/**
 * Pairing rules for two-page spreads. Page indices are reading-order pages,
 * independent of whether the spread is displayed right-to-left (vertical
 * Japanese) or left-to-right (horizontal).
 */
object SpreadLayout {

    /** Number of pager items when two pages are shown at once. */
    fun count(totalPages: Int): Int = (totalPages.coerceAtLeast(1) + 1) / 2

    /** Reading-order page shown first in [spreadIndex]. */
    fun startPage(spreadIndex: Int, totalPages: Int): Int {
        val total = totalPages.coerceAtLeast(1)
        return (spreadIndex.coerceAtLeast(0) * 2).coerceAtMost(total - 1)
    }

    /** Reading-order page shown second in [spreadIndex], or null for a lone last page. */
    fun secondPage(spreadIndex: Int, totalPages: Int): Int? {
        val total = totalPages.coerceAtLeast(1)
        val second = startPage(spreadIndex, total) + 1
        return second.takeIf { it < total }
    }

    /** Spread index that contains [page]. */
    fun indexOfPage(page: Int): Int = page.coerceAtLeast(0) / 2
}
