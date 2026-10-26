package hu.regelo.app

import hu.regelo.app.ui.screens.chapterIndexAfterSwipe
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderNavigationTest {
    @Test
    fun swipingLeftAdvancesAndRightGoesBack() {
        assertEquals(2, chapterIndexAfterSwipe(1, 4, -100f, 72f))
        assertEquals(0, chapterIndexAfterSwipe(1, 4, 100f, 72f))
    }

    @Test
    fun smallSwipesAndEndBoundariesDoNotChangeChapter() {
        assertEquals(1, chapterIndexAfterSwipe(1, 4, -50f, 72f))
        assertEquals(3, chapterIndexAfterSwipe(3, 4, -100f, 72f))
        assertEquals(0, chapterIndexAfterSwipe(0, 4, 100f, 72f))
        assertEquals(0, chapterIndexAfterSwipe(0, 0, 100f, 72f))
    }
}
