package app.nova
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class SubResult(val title: String?, val servers: List<Server>, val used: Long, val total: Long, val expire: Long)

class SubRepo(private val http: OkHttpClient = OkHttpClient()) {
    /** UA v2rayN заставляет Remnawave и 3x-ui отдавать base64 со ссылками */
    suspend fun fetch(url: String, ua: String = "v2rayN/6.42"): SubResult = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).header("User-Agent", ua).build()).execute().use { r ->
            check(r.isSuccessful) { "HTTP ${r.code}" }
            val info = r.header("subscription-userinfo").orEmpty().split(";")
                .mapNotNull { it.trim().split("=").takeIf { p -> p.size == 2 }?.let { p -> p[0] to (p[1].toLongOrNull() ?: 0L) } }.toMap()
            val title = r.header("profile-title")?.let {
                if (it.startsWith("base64:")) String(Base64.decode(it.removePrefix("base64:"), Base64.DEFAULT)) else it
            }
            SubResult(title, Parser.content(r.body!!.string()),
                (info["upload"] ?: 0) + (info["download"] ?: 0), info["total"] ?: 0, info["expire"] ?: 0)
        }
    }
}
