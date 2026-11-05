package app.tasbih.counter.mobile

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

data class ChosenSound(val uri: Uri, val name: String)

/**
 * Copies an audio file into the phone's Notifications folder. Android plays notification
 * sounds outside the app, where access to a picked file doesn't reach, so it needs a
 * copy that everything can read. Returns null if the copy fails.
 */
@RequiresApi(Build.VERSION_CODES.Q)
suspend fun importSound(context: Context, source: Uri): ChosenSound? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val fileName = try {
        resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (e: RuntimeException) {
        null
    } ?: "Tasbih sound"
    val details = ContentValues().apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Audio.Media.MIME_TYPE, resolver.getType(source) ?: "audio/mpeg")
        put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_NOTIFICATIONS}/Tasbih")
        put(MediaStore.Audio.Media.IS_NOTIFICATION, true)
        put(MediaStore.Audio.Media.IS_PENDING, 1)
    }
    val target = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    var copy: Uri? = null
    try {
        val created = resolver.insert(target, details) ?: return@withContext null
        copy = created
        resolver.openInputStream(source).use { input ->
            resolver.openOutputStream(created).use { output ->
                if (input == null || output == null) throw IOException("Couldn't open the sound")
                input.copyTo(output)
            }
        }
        resolver.update(created, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        ChosenSound(created, fileName.substringBeforeLast('.'))
    } catch (e: IOException) {
        copy?.let { resolver.delete(it, null, null) }
        null
    } catch (e: RuntimeException) {
        // Includes files whose type the media library refuses.
        copy?.let { resolver.delete(it, null, null) }
        null
    }
}
