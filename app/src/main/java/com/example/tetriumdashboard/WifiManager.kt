package com.example.tetriumdashboard

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class WifiManager {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    
    private var listenJob: Job? = null
    var onStateUpdated: ((String) -> Unit)? = null
    
    // ESP32 state
    var isConnected = false
    var currentPosition = 0
    var currentSpeed = 1200
    var isRunning = false
    var runtimeSeconds = 0
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun connectToDevice(ipAddress: String, port: Int): Boolean {
        coroutineScope.launch {
            try {
                socket = Socket()
                socket?.connect(InetSocketAddress(ipAddress, port), 5000)
                
                inputStream = socket?.getInputStream()
                outputStream = socket?.getOutputStream()
                isConnected = true
                
                startListening()
                notifyState()
            } catch (e: IOException) {
                Log.e("WifiManager", "Connection failed", e)
                disconnect()
            }
        }
        return true // Asynchronous, we return true to indicate connection attempt started
    }

    fun disconnect() {
        listenJob?.cancel()
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.e("WifiManager", "Error closing socket", e)
        }
        isConnected = false
        isRunning = false
        notifyState()
    }

    private fun startListening() {
        listenJob = coroutineScope.launch {
            val buffer = ByteArray(1024)
            var bytes: Int
            while (isConnected && inputStream != null) {
                try {
                    bytes = inputStream!!.read(buffer)
                    if (bytes > 0) {
                        val incomingMessage = String(buffer, 0, bytes)
                        handleIncomingData(incomingMessage)
                    }
                } catch (e: IOException) {
                    Log.e("WifiManager", "Input stream was disconnected", e)
                    disconnect()
                    break
                }
            }
        }
    }

    private fun handleIncomingData(data: String) {
        try {
            val jsonStrings = data.split("\n").filter { it.isNotBlank() }
            for (str in jsonStrings) {
                val json = JSONObject(str)
                if (json.has("pos")) currentPosition = json.getInt("pos")
                if (json.has("state")) isRunning = json.getString("state") == "running"
                if (json.has("runtime")) runtimeSeconds = json.getInt("runtime")
            }
            notifyState()
        } catch (e: Exception) {
            Log.e("WifiManager", "Failed to parse incoming data", e)
        }
    }

    fun sendCommand(command: String) {
        if (!isConnected || outputStream == null) return
        coroutineScope.launch {
            try {
                outputStream?.write(command.toByteArray())
                outputStream?.flush()
            } catch (e: IOException) {
                Log.e("WifiManager", "Error sending data", e)
            }
        }
    }

    fun notifyState() {
        val stateJson = JSONObject().apply {
            put("connected", isConnected)
            put("position", currentPosition)
            put("speed", currentSpeed)
            put("running", isRunning)
            put("runtime", runtimeSeconds)
        }
        onStateUpdated?.invoke(stateJson.toString())
    }
}
