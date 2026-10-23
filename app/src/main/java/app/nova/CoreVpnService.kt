package app.nova
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import io.nekohasekai.libbox.*

/** VPN-сервис на sing-box (libbox). Версия libbox закреплена в .github/workflows/build.yml */
class CoreVpnService : VpnService(), PlatformInterface {
    private var box: BoxService? = null
    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == "stop") { stop(); return START_NOT_STICKY }
        val link = i?.getStringExtra("link") ?: return START_NOT_STICKY
        Thread {
            runCatching {
                Libbox.setup(filesDir.path, cacheDir.path, cacheDir.path, false)
                box = Libbox.newService(ConfigBuilder.singbox(link), this).also { it.start() }
            }.onFailure { Log.e("Nova", "start failed", it); stop() }
        }.start()
        return START_STICKY
    }

    private fun stop() {
        runCatching { box?.close() }; box = null
        runCatching { tun?.close() }; tun = null
        stopSelf()
    }
    override fun onDestroy() { stop(); super.onDestroy() }

    override fun openTun(options: TunOptions): Int {
        tun = Builder().setSession("Nova").setMtu(9000).addAddress("172.19.0.1", 30)
            .addRoute("0.0.0.0", 0).addDnsServer("172.19.0.2").establish()
        return tun!!.fd
    }
    override fun autoDetectInterfaceControl(fd: Int) { protect(fd) }
    override fun usePlatformAutoDetectInterfaceControl() = true
    override fun writeLog(message: String?) { Log.i("sing-box", message ?: "") }
    override fun useProcFS() = android.os.Build.VERSION.SDK_INT < 29
    override fun findConnectionOwner(p: Int, sa: String?, sp: Int, da: String?, dp: Int): Int = -1
    override fun packageNameByUid(uid: Int): String = ""
    override fun uidByPackageName(name: String?): Int = -1
    override fun usePlatformDefaultInterfaceMonitor() = false
    override fun startDefaultInterfaceMonitor(l: InterfaceUpdateListener?) {}
    override fun closeDefaultInterfaceMonitor(l: InterfaceUpdateListener?) {}
    override fun usePlatformInterfaceGetter() = false
    override fun getInterfaces(): NetworkInterfaceIterator? = null
    override fun underNetworkExtension() = false
    override fun includeAllNetworks() = false
    override fun readWIFIState(): WIFIState? = null
    override fun clearDNSCache() {}
}
