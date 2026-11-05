package app.tasbih.counter.wear.update

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import app.tasbih.counter.shared.R
import app.tasbih.counter.shared.update.ApkInstaller
import app.tasbih.counter.wear.TasbihWearTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException

/** Installing from a visible screen is what lets Android show its confirmation. */
class WatchInstallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apk = WatchUpdateService.updateFile(this)
        setContent {
            TasbihWearTheme {
                val scope = rememberCoroutineScope()
                var message by remember { mutableIntStateOf(R.string.update_ready) }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                ) {
                    Text(stringResource(message), textAlign = TextAlign.Center)
                    Button(
                        onClick = {
                            when {
                                !apk.exists() || !ApkInstaller.isGenuine(this@WatchInstallActivity, apk) ->
                                    message = R.string.update_not_genuine
                                !ApkInstaller.canInstall(this@WatchInstallActivity) ->
                                    ApkInstaller.requestPermission(this@WatchInstallActivity)
                                else -> scope.launch(Dispatchers.IO) {
                                    try {
                                        ApkInstaller.install(this@WatchInstallActivity, apk)
                                    } catch (e: IOException) {
                                        message = R.string.update_install_failed
                                    }
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.update_install)) },
                    )
                }
            }
        }
    }
}
