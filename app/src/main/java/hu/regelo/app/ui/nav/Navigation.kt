package hu.regelo.app.ui.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

enum class Tab(val label: String) {
    LIBRARY("Könyvtár"), DISCOVER("Felfedezés"), WRITE("Írás"), COMMUNITY("Közösség"), ME("Én")
}

sealed interface Route {
    /** Teljes képernyős (alsó sáv nélküli) nézet: olvasó, beszélgetés, fejezetszerkesztő. */
    val fullScreen: Boolean get() = this is Reader || this is Conversation || this is ChapterEditor

    data class Root(val tab: Tab) : Route
    data class BookDetail(val bookId: String) : Route
    data class Reader(val bookId: String, val chapterId: String? = null, val paragraph: Int = -1) : Route
    data class Profile(val userId: String) : Route
    data class Conversation(val partnerId: String) : Route
    data object NewMessage : Route
    data class BookEditor(val bookId: String) : Route
    data class ChapterEditor(val bookId: String, val chapterId: String) : Route
    data class Import(val bookId: String?) : Route
    data object SeriesManager : Route
    data class SeriesDetail(val seriesId: String) : Route
    data class World(val bookId: String) : Route
    data class Stats(val bookId: String) : Route
    data object EditProfile : Route
    data object Settings : Route
    data class Legal(val kind: String) : Route
    data object Blocked : Route
    data class ReadingList(val ownerId: String, val listId: String) : Route
    data object Moderation : Route
    data class Follows(val userId: String, val followers: Boolean) : Route
    data class Search(val query: String = "", val genre: String? = null, val tag: String? = null, val people: Boolean = false) : Route
    data class CircleDetail(val circleId: String, val focusPostId: String? = null) : Route
    data class ChallengeDetail(val challengeId: String) : Route
    data object Notes : Route
}

/** Egyszerű navigáció fülenkénti visszaléptetési veremmel. */
class Navigator {
    var tab by mutableStateOf(Tab.LIBRARY)
        private set

    private val stacks: Map<Tab, SnapshotStateList<Route>> =
        Tab.entries.associateWith { mutableStateListOf(Route.Root(it)) }

    val stack: List<Route> get() = stacks.getValue(tab)
    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1 || tab != Tab.LIBRARY

    fun push(route: Route) {
        val s = stacks.getValue(tab)
        if (s.lastOrNull() != route) s.add(route)
    }

    fun replace(route: Route) {
        val s = stacks.getValue(tab)
        if (s.size > 1) s.removeAt(s.lastIndex)
        s.add(route)
    }

    fun back(): Boolean {
        val s = stacks.getValue(tab)
        return when {
            s.size > 1 -> { s.removeAt(s.lastIndex); true }
            tab != Tab.LIBRARY -> { tab = Tab.LIBRARY; true }
            else -> false
        }
    }

    fun select(newTab: Tab) {
        if (newTab == tab) {
            val s = stacks.getValue(tab)
            while (s.size > 1) s.removeAt(s.lastIndex)
        } else tab = newTab
    }

    fun open(newTab: Tab, route: Route? = null) {
        tab = newTab
        if (route != null) push(route)
    }

    fun reset() {
        stacks.forEach { (t, s) -> s.clear(); s.add(Route.Root(t)) }
        tab = Tab.LIBRARY
    }
}
