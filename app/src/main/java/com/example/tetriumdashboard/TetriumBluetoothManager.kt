package com.example.tetriumdashboard

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class TetriumBluetoothManager(private val context: Context) {
    private val bluetoothManager: BluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothSocket: BluetoothSocket? = null
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
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun connectToDevice(deviceAddress: String): Boolean {
        if (bluetoothAdapter == null) return false
        if (!hasPermissions()) return false

        try {
            val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)
            bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothAdapter.cancelDiscovery()
            bluetoothSocket?.connect()
            
            inputStream = bluetoothSocket?.inputStream
            outputStream = bluetoothSocket?.outputStream
            isConnected = true
            
            startListening()
            notifyState()
            return true
        } catch (e: SecurityException) {
            Log.e("BluetoothManager", "Security exception", e)
        } catch (e: IOException) {
            Log.e("BluetoothManager", "Connection failed", e)
            disconnect()
        }
        return false
    }

    fun getPairedDevices(): String {
        if (!hasPermissions() || bluetoothAdapter == null) return "[]"
        val devicesJson = org.json.JSONArray()
        try {
            val pairedDevices = bluetoothAdapter.bondedDevices
            for (device in pairedDevices) {
                val devObj = JSONObject()
                devObj.put("name", device.name ?: "Unknown")
                devObj.put("address", device.address)
                devicesJson.put(devObj)
            }
        } catch (e: SecurityException) {
            Log.e("BluetoothManager", "Security exception getting paired devices", e)
        }
        return devicesJson.toString()
    }

    fun disconnect() {
        listenJob?.cancel()
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e("BluetoothManager", "Error closing socket", e)
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
                    Log.e("BluetoothManager", "Input stream was disconnected", e)
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
            Log.e("BluetoothManager", "Failed to parse incoming data", e)
        }
    }

    fun sendCommand(command: String) {
        if (!isConnected || outputStream == null) return
        try {
            outputStream?.write(command.toByteArray())
        } catch (e: IOException) {
            Log.e("BluetoothManager", "Error sending data", e)
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

    private fun hasPermissions(): Boolean {
        // Simplified check, MainActivity will request these
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }
}
