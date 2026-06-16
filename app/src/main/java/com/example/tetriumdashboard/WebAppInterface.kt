package com.example.tetriumdashboard

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class WebAppInterface(
    private val context: Context,
    private val webView: WebView,
    private val bluetoothManager: TetriumBluetoothManager,
    private val wifiManager: WifiManager
) {
    init {
        val stateCallback: (String) -> Unit = { stateJson ->
            CoroutineScope(Dispatchers.Main).launch {
                webView.evaluateJavascript("javascript:if(window.updateTelemetry) window.updateTelemetry($stateJson)", null)
            }
        }
        bluetoothManager.onStateUpdated = stateCallback
        wifiManager.onStateUpdated = stateCallback
    }

    @JavascriptInterface
    fun requestState() {
        if (bluetoothManager.isConnected) {
            bluetoothManager.notifyState()
        } else if (wifiManager.isConnected) {
            wifiManager.notifyState()
        } else {
            // Send default disconnected state
            val stateJson = JSONObject().apply {
                put("connected", false)
                put("position", 0)
                put("speed", 0)
                put("running", false)
                put("runtime", 0)
            }
            CoroutineScope(Dispatchers.Main).launch {
                webView.evaluateJavascript("javascript:if(window.updateTelemetry) window.updateTelemetry($stateJson)", null)
            }
        }
    }

    @JavascriptInterface
    fun getPairedDevices(): String {
        return bluetoothManager.getPairedDevices()
    }

    @JavascriptInterface
    fun connectToDevice(address: String) {
        wifiManager.disconnect() // Ensure wifi is disconnected
        val success = bluetoothManager.connectToDevice(address)
        if (!success) {
            Toast.makeText(context, "Failed to connect to $address", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Bluetooth Connection Attempted!", Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun connectToWifi(ip: String, port: Int) {
        bluetoothManager.disconnect() // Ensure BT is disconnected
        wifiManager.connectToDevice(ip, port)
        Toast.makeText(context, "Connecting to $ip:$port...", Toast.LENGTH_SHORT).show()
    }

    private fun sendCommand(command: String) {
        val cmdStr = "$command\n"
        if (bluetoothManager.isConnected) {
            bluetoothManager.sendCommand(cmdStr)
        } else if (wifiManager.isConnected) {
            wifiManager.sendCommand(cmdStr)
        } else {
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "Not connected!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @JavascriptInterface
    fun startMotion(steps: Int, speed: Int, direction: String) {
        val payload = JSONObject().apply {
            put("cmd", "start")
            put("steps", steps)
            put("speed", speed)
            put("dir", direction)
        }
        sendCommand(payload.toString())
    }

    @JavascriptInterface
    fun stopMotion() {
        sendCommand("{\"cmd\":\"stop\"}")
    }

    @JavascriptInterface
    fun pauseMotion() {
        sendCommand("{\"cmd\":\"pause\"}")
    }

    @JavascriptInterface
    fun resumeMotion() {
        sendCommand("{\"cmd\":\"resume\"}")
    }

    @JavascriptInterface
    fun homeMotion() {
        sendCommand("{\"cmd\":\"home\"}")
    }

    @JavascriptInterface
    fun emergencyStop() {
        sendCommand("{\"cmd\":\"estop\"}")
    }

    @JavascriptInterface
    fun showToast(toast: String) {
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }
}
