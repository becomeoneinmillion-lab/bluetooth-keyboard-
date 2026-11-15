package com.example.btkeyboardperipheral

import android.Manifest
import android.bluetooth.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var btnAdvertise: Button
    private lateinit var btnSendKey: Button

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDeviceService: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null

    // Standard HID Keyboard Report Descriptor
    private val keyboardReportDescriptor = byteArrayOf(
        0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x05, 0x07,
        0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01,
        0x75, 0x01, 0x95, 0x08, 0x81, 0x02, 0x95, 0x01,
        0x75, 0x08, 0x81, 0x01, 0x05, 0x07, 0x19, 0x00,
        0x29, 0x65, 0x15, 0x00, 0x25, 0x65, 0x75, 0x08,
        0x95, 0x06, 0x81, 0x00, 0xC0
    )

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(plug: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(plug, registered)
            runOnUiThread {
                if (registered) {
                    statusText.text = "Status: HID Registered & Advertising"
                    Toast.makeText(this@MainActivity, "HID Service Ready", Toast.LENGTH_SHORT).show()
                } else {
                    statusText.text = "Status: HID Unregistered"
                }
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)
            runOnUiThread {
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connectedHost = device
                        statusText.text = "Status: Connected to TV!"
                        btnSendKey.isEnabled = true
                        Toast.makeText(this@MainActivity, "Connected to Host", Toast.LENGTH_SHORT).show()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connectedHost = null
                        statusText.text = "Status: Disconnected"
                        btnSendKey.isEnabled = false
                    }
                }
            }
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDeviceService = proxy as BluetoothHidDevice
                registerHidApp()
            }
        }
        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDeviceService = null
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        btnAdvertise = findViewById(R.id.btnAdvertise)
        btnSendKey = findViewById(R.id.btnSendKey)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        btnAdvertise.setOnClickListener {
            checkPermissionsAndStart()
        }

        btnSendKey.setOnClickListener {
            sendKeyPress()
        }
    }

    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE), 101)
                return
            }
        }
        
        bluetoothAdapter?.getProfileProxy(this, profileListener, BluetoothProfile.HID_DEVICE)
    }

    private fun registerHidApp() {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Bluetooth Keyboard",
            "Custom Peripheral Keyboard",
            "CustomAndroid",
            BluetoothHidDevice.SUBCLASS_KEYBOARD,
            keyboardReportDescriptor
        )

        val qosOut = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 9, 11250, 11250
        )

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            hidDeviceService?.registerApp(sdp, null, qosOut, Runnable::run, hidCallback)
        }
    }

    private fun sendKeyPress() {
        val device = connectedHost ?: return
        // Standard HID report packet for pressing and releasing the 'Enter' key (KeyCode 0x28)
        val pressReport = byteArrayOf(0x00, 0x00, 0x28, 0x00, 0x00, 0x00, 0x00, 0x00)
        val releaseReport = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            hidDeviceService?.sendReport(device, 0, pressReport)
            hidDeviceService?.sendReport(device, 0, releaseReport)
            Toast.makeText(this, "Keystroke Sent!", Toast.LENGTH_SHORT).show()
        }
    }
}
