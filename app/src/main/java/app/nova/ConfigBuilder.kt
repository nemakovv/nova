package app.nova
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

/** Ссылка подписки -> JSON-конфиг sing-box (tun + прокси-outbound) */
object ConfigBuilder {
    private fun o(vararg p: Pair<String, Any?>) = JSONObject().also { j -> p.forEach { if (it.second != null) j.put(it.first, it.second) } }
    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")

    fun singbox(link: String): String = o(
        "log" to o("level" to "warn"),
        "dns" to o(
            "servers" to JSONArray().put(o("tag" to "remote", "address" to "https://1.1.1.1/dns-query", "detour" to "proxy"))
                .put(o("tag" to "local", "address" to "local", "detour" to "direct")),
            "rules" to JSONArray().put(o("outbound" to "any", "server" to "local"))),
        "inbounds" to JSONArray().put(o("type" to "tun", "tag" to "tun-in", "inet4_address" to "172.19.0.1/30",
            "auto_route" to true, "stack" to "mixed", "sniff" to true)),
        "outbounds" to JSONArray().put(outbound(link)).put(o("type" to "direct", "tag" to "direct")).put(o("type" to "dns", "tag" to "dns-out")),
        "route" to o("auto_detect_interface" to true, "final" to "proxy",
            "rules" to JSONArray().put(o("protocol" to "dns", "outbound" to "dns-out")))
    ).toString()

    private fun tls(sni: String, fp: String?, pbk: String?, sid: String?, alpn: String?, insecure: Boolean) = o(
        "enabled" to true, "server_name" to sni, "insecure" to insecure,
        "alpn" to alpn?.takeIf { it.isNotEmpty() }?.let { JSONArray(it.split(",")) },
        "utls" to fp?.takeIf { it.isNotEmpty() }?.let { o("enabled" to true, "fingerprint" to it) },
        "reality" to pbk?.takeIf { it.isNotEmpty() }?.let { o("enabled" to true, "public_key" to it, "short_id" to (sid ?: "")) })

    private fun transport(net: String, path: String?, host: String?, svc: String?): JSONObject? = when (net) {
        "ws" -> o("type" to "ws", "path" to (path ?: "/"), "headers" to host?.takeIf { it.isNotEmpty() }?.let { o("Host" to it) })
        "grpc" -> o("type" to "grpc", "service_name" to (svc ?: ""))
        "httpupgrade" -> o("type" to "httpupgrade", "path" to (path ?: "/"), "host" to host)
        else -> null
    }

    private fun outbound(link: String): JSONObject {
        val p = link.substringBefore("://").lowercase().let { if (it == "hy2") "hysteria2" else it }
        if (p == "vmess") {
            val j = JSONObject(String(Base64.decode(link.substringAfter("://").trim(), Base64.DEFAULT)))
            val host = j.optString("host")
            return o("type" to "vmess", "tag" to "proxy", "server" to j.getString("add"), "server_port" to j.get("port").toString().toInt(),
                "uuid" to j.getString("id"), "security" to j.optString("scy").ifEmpty { "auto" }, "alter_id" to j.optInt("aid", 0),
                "tls" to if (j.optString("tls") == "tls") tls(j.optString("sni").ifEmpty { host }.ifEmpty { j.getString("add") }, j.optString("fp"), null, null, j.optString("alpn"), false) else null,
                "transport" to transport(j.optString("net", "tcp"), j.optString("path"), host, j.optString("path")))
        }
        val u = URI(link.replace("hy2://", "hysteria2://"))
        val q = (u.rawQuery ?: "").split("&").filter { "=" in it }.associate { it.substringBefore("=") to dec(it.substringAfter("=")) }
        val sec = q["security"] ?: if (p == "vless") "none" else "tls"
        val t = if (sec == "tls" || sec == "reality")
            tls(q["sni"] ?: q["host"] ?: u.host, q["fp"], q["pbk"], q["sid"], q["alpn"], q["allowInsecure"] == "1" || q["insecure"] == "1") else null
        val tr = transport(q["type"] ?: "tcp", q["path"], q["host"], q["serviceName"])
        val ui = u.rawUserInfo ?: ""
        val base = arrayOf("tag" to "proxy", "server" to u.host, "server_port" to u.port)
        return when (p) {
            "vless" -> o("type" to "vless", *base, "uuid" to dec(ui), "flow" to q["flow"]?.takeIf { it.isNotEmpty() }, "tls" to t, "transport" to tr)
            "trojan" -> o("type" to "trojan", *base, "password" to dec(ui), "tls" to t, "transport" to tr)
            "ss" -> {
                val d = if (":" in ui) dec(ui) else String(Base64.decode(ui, Base64.URL_SAFE or Base64.NO_PADDING))
                o("type" to "shadowsocks", *base, "method" to d.substringBefore(":"), "password" to d.substringAfter(":"))
            }
            "hysteria2" -> o("type" to "hysteria2", *base, "password" to dec(ui), "tls" to t,
                "obfs" to q["obfs"]?.let { o("type" to it, "password" to q["obfs-password"]) })
            "tuic" -> o("type" to "tuic", *base, "uuid" to dec(ui.substringBefore(":")), "password" to dec(ui.substringAfter(":", "")),
                "congestion_control" to q["congestion_control"], "tls" to t)
            else -> error("Протокол не поддерживается: $p")
        }
    }
}
