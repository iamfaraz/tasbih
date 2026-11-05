package app.tasbih.counter.shared.update

import java.net.URI

const val UPDATE_CHANNEL_PATH = "/tasbih/update/watch"

/** Far larger than either APK, so a bad response can't fill the device. */
const val MAX_APK_BYTES = 200L * 1024 * 1024

private const val RELEASE_PATH = "/iamfaraz/tasbih/releases/download/"

/** Whether [latest] is newer than [current], comparing versions such as 0.10.2 number by number. */
fun isNewerVersion(latest: String, current: String): Boolean {
    fun parts(version: String) = version.trim().removePrefix("v").split('.').map { it.toIntOrNull() }
    val new = parts(latest).map { it ?: return false }
    val old = parts(current).map { it ?: return false }
    for (index in 0 until maxOf(new.size, old.size)) {
        val difference = new.getOrElse(index) { 0 } - old.getOrElse(index) { 0 }
        if (difference != 0) return difference > 0
    }
    return false
}

/** Only this project's releases, over HTTPS, are ever downloaded. */
fun isTrustedDownload(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    return uri.scheme == "https" && uri.host == "github.com" && uri.path.orEmpty().startsWith(RELEASE_PATH)
}

/** Both must be signed, by exactly the same keys. */
fun sameSigners(installed: Set<String>, candidate: Set<String>): Boolean =
    installed.isNotEmpty() && installed == candidate
