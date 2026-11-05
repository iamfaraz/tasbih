package app.tasbih.counter.mobile.ring

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tasbih.counter.mobile.CardShape
import app.tasbih.counter.mobile.Pill
import app.tasbih.counter.mobile.colors
import app.tasbih.counter.shared.ui.ringSummaryText
import app.tasbih.counter.shared.R as SharedR

@Composable
fun ringStatusText(status: RingStatus): String = ringSummaryText(status.toSummary())

@Composable
fun RingDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { RingStore.get(context) }
    val status by store.status.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        shape = CardShape,
        title = {
            Text(
                status.name ?: stringResource(SharedR.string.ring),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            if (status.paired) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(ringStatusText(status), color = colors.muted)
                    Text(stringResource(SharedR.string.ring_note), color = colors.muted, fontSize = 14.sp)
                    Pill(stringResource(SharedR.string.ring_forget)) {
                        RingService.stop(context)
                        store.unpair()
                    }
                }
            } else {
                RingScanner(onPick = { address, name ->
                    store.pair(address, name)
                    RingService.start(context)
                })
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
    )
}

private data class FoundDevice(val address: String, val name: String, val looksLikeRing: Boolean, val signal: Int)

@Composable
private fun RingScanner(onPick: (String, String) -> Unit) {
    val context = LocalContext.current
    val found = remember { mutableStateMapOf<String, FoundDevice>() }
    var permitted by remember { mutableStateOf(hasBluetoothAccess(context)) }
    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> permitted = granted.values.all { it } }

    if (permitted) {
        ScanEffect(context) { device -> found[device.address] = device }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!permitted) {
            Text(stringResource(SharedR.string.ring_permission))
            Pill(stringResource(SharedR.string.ring_allow)) { permissions.launch(bluetoothPermissions()) }
        } else {
            Text(stringResource(SharedR.string.ring_scanning), color = colors.muted)
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                found.values
                    .sortedWith(compareByDescending<FoundDevice> { it.looksLikeRing }.thenByDescending { it.signal })
                    .forEach { device ->
                        Text(
                            device.name,
                            fontWeight = if (device.looksLikeRing) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(device.address, device.name) }
                                .padding(vertical = 10.dp),
                        )
                    }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun ScanEffect(context: Context, onFound: (FoundDevice) -> Unit) {
    DisposableEffect(Unit) {
        val scanner = context.getSystemService(BluetoothManager::class.java).adapter?.bluetoothLeScanner
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: result.scanRecord?.deviceName ?: return
                val looksLikeRing = result.scanRecord?.serviceUuids.orEmpty()
                    .any { it.uuid.toString().startsWith(RingGattClient.SERVICE_PREFIX) }
                onFound(FoundDevice(result.device.address, name, looksLikeRing, result.rssi))
            }
        }
        // No filter: the ring's name varies by model and it may not advertise its service.
        scanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        onDispose { scanner?.stopScan(callback) }
    }
}

private fun bluetoothPermissions() =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun hasBluetoothAccess(context: Context) = bluetoothPermissions().all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}
