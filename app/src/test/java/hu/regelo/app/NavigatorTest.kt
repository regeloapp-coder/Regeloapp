package hu.regelo.app

import hu.regelo.app.ui.nav.Navigator
import hu.regelo.app.ui.nav.Route
import hu.regelo.app.ui.nav.Tab
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigatorTest {
    @Test
    fun authoringDestinationsStayOnWriteTabWhenSwitchingTabs() {
        val nav = Navigator()

        nav.select(Tab.WRITE)
        nav.push(Route.BookEditor("book-1"))
        nav.select(Tab.ME)
        nav.select(Tab.WRITE)

        assertEquals(Tab.WRITE, nav.tab)
        assertEquals(Route.BookEditor("book-1"), nav.current)
        assertEquals(true, nav.back())
        assertEquals(Route.Root(Tab.WRITE), nav.current)
    }

    @Test
    fun openingOwnWorkFromProfileUsesWriteTabAndKeepsEditorBackStack() {
        val nav = Navigator()
        nav.select(Tab.ME)

        nav.open(Tab.WRITE, Route.BookEditor("book-1"))

        assertEquals(Tab.WRITE, nav.tab)
        assertEquals(Route.BookEditor("book-1"), nav.current)
        assertEquals(true, nav.back())
        assertEquals(Route.Root(Tab.WRITE), nav.current)
    }
}
