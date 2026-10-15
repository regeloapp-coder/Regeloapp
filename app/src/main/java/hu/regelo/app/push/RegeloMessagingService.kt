package hu.regelo.app.push

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import hu.regelo.app.AppGraph
import hu.regelo.app.MainActivity
import hu.regelo.app.R
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume

const val CHANNEL_COMMUNITY = "community_updates"

object PushTokens {
    fun createChannel(context: Context) {
        // minSdk 26: az értesítési csatorna mindig létezik.
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_COMMUNITY,
            context.getString(R.string.channel_community),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_community_desc) }
        nm.createNotificationChannel(channel)
    }

    private suspend fun fetchToken(): String? = suspendCancellableCoroutine { cont ->
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }.onFailure { cont.resume(null) }
    }

    /** Az eszköz FCM-tokenjének regisztrálása a felhasználóhoz (a szerver ide küldi a pusht). */
    suspend fun register(token: String? = null) {
        if (AppGraph.auth.userId == null) return
        val enabled = AppGraph.settings.current().notificationsEnabled
        val t = token ?: fetchToken() ?: return
        AppGraph.supabase.postgrest.rpc("regelo_register_push_token", buildJsonObject {
            put("p_token", t)
            put("p_enabled", enabled)
        })
        AppGraph.settings.setLastPushToken(t)
    }

    suspend fun unregister() {
        val uid = AppGraph.auth.userId ?: return
        val t = AppGraph.settings.lastPushToken() ?: return
        AppGraph.supabase.from("device_push_tokens").delete {
            filter {
                eq("user_id", uid)
                eq("token", t)
            }
        }
    }
}

class RegeloMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        AppGraph.init(applicationContext)
        AppGraph.appScope.launch { runCatching { PushTokens.register(token) } }
    }

    /** Előtérben a Firebase nem jeleníti meg magától az értesítést – itt tesszük ki. */
    @SuppressLint("MissingPermission")
    override fun onMessageReceived(message: RemoteMessage) {
        AppGraph.init(applicationContext)
        val title = message.notification?.title ?: message.data["title"] ?: "Regélő"
        val body = message.notification?.body ?: message.data["body"] ?: return
        val kind = message.data["kind"]
        if (kind == "MESSAGE") AppGraph.appScope.launch { runCatching { AppGraph.messages.refreshUnread() } }
        else AppGraph.social.bumpUnreadNotifications()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("push_kind", kind)
            putExtra("push_actor", message.data["actorId"])
            putExtra("push_book", message.data["bookId"])
            putExtra("push_chapter", message.data["chapterId"])
            putExtra("push_circle", message.data["circleId"])
            putExtra("push_challenge", message.data["challengeId"])
            putExtra("push_post", message.data["postId"])
        }
        val pending = PendingIntent.getActivity(
            this, (message.messageId ?: title).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_COMMUNITY)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.rust))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val tag = when {
            kind == "MESSAGE" -> "msg_${message.data["actorId"]}"
            kind == "NEW_CHAPTER" && message.data["bookId"] != null -> "chapter_${message.data["bookId"]}"
            kind == "OFFICIAL_UPDATE" && message.data["bookId"] != null -> "official_${message.data["bookId"]}"
            else -> message.data["notificationId"]
        }
        NotificationManagerCompat.from(this).notify(tag, 1, notification)
    }
}
