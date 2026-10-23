package app.nova
import android.util.Base64
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

data class Server(val link: String, val proto: String, val name: String, val host: String,
                  val port: Int, val net: String, val sec: String, val ping: Int = 0)

object Parser {
    private val protos = setOf("vless", "vmess", "trojan", "ss", "hysteria2", "tuic", "wireguard")
    private fun b64(s: String) = String(Base64.decode(s.trim(), Base64.URL_SAFE or Base64.NO_PADDING))

    fun link(l: String): Server? = runCatching {
        val p = l.substringBefore("://").lowercase().let { if (it == "hy2") "hysteria2" else it }
        if (p !in protos) return null
        if (p == "vmess") {
            val j = JSONObject(b64(l.substringAfter("://")))
            return Server(l, p, j.optString("ps"), j.getString("add"), j.get("port").toString().toInt(),
                j.optString("net", "tcp"), j.optString("tls"))
        }
        val u = URI(l.replace("hy2://", "hysteria2://"))
        val q = (u.rawQuery ?: "").split("&").filter { "=" in it }
            .associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), "UTF-8") }
        Server(l, p, URLDecoder.decode(u.rawFragment ?: u.host, "UTF-8"), u.host, u.port,
            q["type"] ?: "tcp", q["security"] ?: "")
    }.getOrNull()

    /** base64-подписка 3x-ui / Remnawave или список ссылок построчно */
    fun content(text: String): List<Server> {
        var t = text.trim()
        if ("://" !in t) t = runCatching { b64(t) }.getOrDefault(t)
        return t.lines().mapNotNull { link(it.trim()) }
    }
}
