package hu.regelo.app

import hu.regelo.app.ui.screens.bookmarkToggleDescription
import hu.regelo.app.ui.screens.likeToggleDescription
import org.junit.Assert.assertEquals
import org.junit.Test

class BookDetailSemanticsTest {
    @Test
    fun bookmarkDescriptionAnnouncesTheAvailableAction() {
        assertEquals("Hozzáadás a könyvtárhoz", bookmarkToggleDescription(saved = false))
        assertEquals("Eltávolítás a könyvtárból", bookmarkToggleDescription(saved = true))
    }

    @Test
    fun likeDescriptionAnnouncesTheAvailableAction() {
        assertEquals("Kedvelés", likeToggleDescription(liked = false))
        assertEquals("Kedvelés visszavonása", likeToggleDescription(liked = true))
    }
}
