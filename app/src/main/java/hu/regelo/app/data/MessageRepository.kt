package hu.regelo.app.data

import hu.regelo.app.util.orReport

import hu.regelo.app.AppGraph
import hu.regelo.app.util.newId
import hu.regelo.app.util.nowIso
import hu.regelo.app.util.parseIsoMs
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecordOrNull
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class Conversation(
    val partnerId: String,
    val partner: ProfileDto?,
    val last: MessageDto,
    val unread: Int,
)

/** Privát üzenetek: beérkezett / elküldött, valós idejű frissítés (Realtime + tartalék lekérdezés). */
class MessageRepository {
    private val db get() = AppGraph.supabase

    private val _incoming = MutableSharedFlow<MessageDto>(extraBufferCapacity = 64)
    /** Új, nekem érkezett üzenetek (valós időben). */
    val incoming: SharedFlow<MessageDto> = _incoming.asSharedFlow()

    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    private var channel: RealtimeChannel? = null
    private var jobs = mutableListOf<Job>()

    fun start(userId: String) {
        stop(resetCounts = false)
        jobs += AppGraph.appScope.launch {
            runCatching { refreshUnread() }
            runCatching { AppGraph.social.refreshUnreadNotifications() }
            try {
                val ch = db.channel("inbox-$userId")
                channel = ch
                val messages = ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "community_messages"
                    filter("recipient_id", FilterOperator.EQ, userId)
                }
                val notifications = ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "community_notifications"
                    filter("recipient_id", FilterOperator.EQ, userId)
                }
                jobs += AppGraph.appScope.launch {
                    messages.collect { action ->
                        action.decodeRecordOrNull<MessageDto>()?.let {
                            _incoming.emit(it)
                            _unread.value += 1
                        }
                    }
                }
                jobs += AppGraph.appScope.launch {
                    notifications.collect { action ->
                        val n = action.decodeRecordOrNull<NotificationDto>()
                        if (n != null && n.kind != "MESSAGE") AppGraph.social.bumpUnreadNotifications()
                    }
                }
                ch.subscribe()
            } catch (_: Exception) {
                // Realtime nélkül is működik: a lenti tartalék lekérdezés frissít.
            }
        }
        // Tartalék: percenként frissíti az olvasatlan számlálót.
        jobs += AppGraph.appScope.launch {
            while (isActive) {
                delay(60.seconds)
                if (AppGraph.network.currentlyOnline()) {
                    runCatching { refreshUnread() }
                    runCatching { AppGraph.social.refreshUnreadNotifications() }
                }
            }
        }
    }

    fun stop(resetCounts: Boolean = true) {
        jobs.forEach { it.cancel() }
        jobs.clear()
        val ch = channel
        channel = null
        if (ch != null) AppGraph.appScope.launch {
            runCatching { db.realtime.removeChannel(ch) }
            runCatching { db.realtime.disconnect() }
        }
        if (resetCounts) _unread.value = 0
    }

    /** Háttérbe kerüléskor bontjuk a valós idejű kapcsolatot (a push értesítések ilyenkor is jönnek). */
    fun pause() = stop(resetCounts = false)

    fun resume() {
        val uid = AppGraph.auth.userId ?: return
        start(uid)
    }

    suspend fun refreshUnread() {
        val me = AppGraph.auth.userId ?: return
        _unread.value = db.from("community_messages").select(Columns.raw("id")) {
            head = true
            count(Count.EXACT)
            filter {
                eq("recipient_id", me)
                exact("read_at", null)
            }
        }.countOrNull()?.toInt() ?: 0
    }

    suspend fun conversations(): List<Conversation> {
        val me = AppGraph.auth.userId ?: return emptyList()
        val list = db.from("community_messages").select {
            filter {
                or {
                    eq("sender_id", me)
                    eq("recipient_id", me)
                }
            }
            order("created_at", Order.DESCENDING)
            limit(500)
        }.decodeList<MessageDto>()
        val blocked = runCatching { AppGraph.social.blockedIds() }.orReport("MessageRepository", emptySet())
        val grouped = list.groupBy { if (it.senderId == me) it.recipientId else it.senderId }.filterKeys { it !in blocked }
        val profiles = runCatching { AppGraph.profiles.getMany(grouped.keys) }.orReport("MessageRepository", emptyMap())
        _unread.value = list.count { it.recipientId == me && it.readAt == null }
        return grouped.map { (partner, msgs) ->
            Conversation(partner, profiles[partner], msgs.first(), msgs.count { it.recipientId == me && it.readAt == null })
        }.sortedByDescending { parseIsoMs(it.last.createdAt) }
    }

    suspend fun thread(partnerId: String): List<MessageDto> {
        val me = AppGraph.auth.requireUserId()
        return db.from("community_messages").select {
            filter {
                or {
                    and {
                        eq("sender_id", me)
                        eq("recipient_id", partnerId)
                    }
                    and {
                        eq("sender_id", partnerId)
                        eq("recipient_id", me)
                    }
                }
            }
            // A legutóbbi 500 üzenet (újabbtól régebbig lekérve), időrendbe fordítva.
            order("created_at", Order.DESCENDING)
            limit(500)
        }.decodeList<MessageDto>().reversed()
    }

    suspend fun send(partnerId: String, body: String, id: String = newId()): MessageDto {
        val me = AppGraph.auth.requireUserId()
        return db.from("community_messages").insert(MessageInsert(id, me, partnerId, body.trim().take(5000))) {
            select()
        }.decodeSingle()
    }

    suspend fun markRead(partnerId: String) {
        val me = AppGraph.auth.userId ?: return
        db.from("community_messages").update({ set("read_at", nowIso()) }) {
            filter {
                eq("recipient_id", me)
                eq("sender_id", partnerId)
                exact("read_at", null)
            }
        }
        runCatching { refreshUnread() }
    }
}
