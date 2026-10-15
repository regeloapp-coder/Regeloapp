package hu.regelo.app

import hu.regelo.app.data.BookRepository
import hu.regelo.app.data.sanitizeSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookRepositoryFilterTest {
    @Test
    fun officialFilterValuesStayListBasedAndQuoteFree() {
        val repo = BookRepository()
        val values = repo.filterValues(listOf("b-2", "a-1", "b-2"))

        assertEquals(listOf("a-1", "b-2"), values)
        assertTrue(values.none { it.contains('"') || it.contains('(') || it.contains(')') })
    }

    @Test
    fun sanitizeSearchRemovesPostgrestSyntaxCharacters() {
        val cleaned = sanitizeSearch("hello%(world)\"*%:test")
        assertEquals("hello world test", cleaned)
    }
}
