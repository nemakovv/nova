package app.nova
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

val Pink = Color(0xFFEC4899); val Bg = Color(0xFF14090F); val Card = Color(0xFF211019)

class MainActivity : ComponentActivity() {
    private val repo = SubRepo()
    private var pending: String? = null
    private val perm = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) pending?.let { l -> startVpn(l) }
    }
    private fun startVpn(l: String) { startService(Intent(this, CoreVpnService::class.java).putExtra("link", l)) }
    private fun connect(l: String) { pending = l; val i = VpnService.prepare(this); if (i == null) startVpn(l) else perm.launch(i) }
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Pink, background = Bg, surface = Card)) {
                var servers by remember { mutableStateOf(listOf<Server>()) }
                var sel by remember { mutableStateOf<Server?>(null) }
                var on by remember { mutableStateOf(false) }
                var url by remember { mutableStateOf("") }
                var err by remember { mutableStateOf("") }
                Column(Modifier.fillMaxSize().background(Bg).padding(16.dp).statusBarsPadding()) {
                    Text("Nova", fontSize = 24.sp, color = Color.White)
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                        Box(Modifier.size(150.dp).clip(CircleShape).background(if (on) Pink else Card)
                            .clickable {
                                val srv = sel ?: servers.firstOrNull() ?: return@clickable
                                if (on) { startService(Intent(this@MainActivity, CoreVpnService::class.java).setAction("stop")); on = false }
                                else { connect(srv.link); on = true }
                            }, Alignment.Center) { Text("⏻", fontSize = 52.sp, color = Color.White) }
                    }
                    OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Ссылка подписки") })
                    Button({ lifecycleScope.launch {
                        runCatching { repo.fetch(url) }.onSuccess { servers = it.servers; err = "" }.onFailure { err = it.message ?: "Ошибка" }
                    } }, Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = ButtonDefaults.buttonColors(Pink)) { Text("Импортировать") }
                    if (err.isNotEmpty()) Text(err, color = Color(0xFFF87171))
                    LazyColumn { items(servers) { x ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (x == sel) Pink.copy(alpha = .25f) else Card).clickable { sel = x }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(x.name, color = Color.White); Text("${x.host}:${x.port} · ${x.net}", color = Color.Gray, fontSize = 12.sp) }
                            Text(x.proto.uppercase(), color = Pink, fontSize = 11.sp)
                        }
                    } }
                }
            }
        }
    }
}
