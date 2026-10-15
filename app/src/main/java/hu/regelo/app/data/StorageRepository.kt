package hu.regelo.app.data

import hu.regelo.app.AppGraph
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType

/** Képfeltöltés a Supabase tárolóba: minden felhasználó a saját mappájába ír (RLS). */
class StorageRepository {
    object Buckets {
        const val COVERS = "book-covers"
        const val AVATARS = "avatars"
        const val BANNERS = "profile-banners"
        const val CHAPTER_IMAGES = "chapter-images"
    }

    suspend fun uploadJpeg(bucket: String, fileName: String, bytes: ByteArray): String {
        val uid = AppGraph.supabase.auth.currentUserOrNull()?.id ?: error("Bejelentkezés szükséges a feltöltéshez.")
        val path = "$uid/$fileName"
        val api = AppGraph.supabase.storage.from(bucket)
        api.upload(path, bytes) {
            upsert = true
            contentType = ContentType.Image.JPEG
        }
        return api.publicUrl(path)
    }
}
