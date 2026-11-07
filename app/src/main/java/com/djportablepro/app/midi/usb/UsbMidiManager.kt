package com.djportablepro.app.midi.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.djportablepro.app.midi.decoder.MidiProcessor
import com.djportablepro.app.midi.mapping.DeviceDescriptor
import java.util.concurrent.atomic.AtomicBoolean

class UsbMidiManager(
    private val context: Context,
    private val midiProcessor: MidiProcessor
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val detector = UsbMidiDeviceDetector(context)
    private var permissionManager: UsbMidiPermissionManager? = null
    private var transport: UsbMidiTransport? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val firstPacketReceived = AtomicBoolean(false)

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            
            if (device != null) {
                when (action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> handleAttach(device)
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> handleDetach(device)
                }
            }
        }
    }

    init {
        permissionManager = UsbMidiPermissionManager(context) { device, granted ->
            if (granted) processIfMidi(device)
        }
        
        transport = UsbMidiTransport(usbManager) { message ->
            if (firstPacketReceived.compareAndSet(false, true)) {
                mainHandler.post { Toast.makeText(context, "MIDI conectado", Toast.LENGTH_SHORT).show() }
            }
            midiProcessor.processRaw(message.status, message.data1, message.data2)
        }

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
    }

    fun startDiscovery() {
        usbManager.deviceList.values.forEach { if (detector.isMidiDevice(it)) handleAttach(it) }
    }

    fun sendMidi(status: Int, data1: Int, data2: Int) {
        val cin = (status shr 4) and 0x0F
        val packet = byteArrayOf(
            (cin and 0x0F).toByte(), // Cable 0, CIN
            (status and 0xFF).toByte(),
            (data1 and 0xFF).toByte(),
            (data2 and 0xFF).toByte()
        )
        transport?.sendRaw(packet)
    }

    private fun handleAttach(device: UsbDevice) {
        if (usbManager.hasPermission(device)) processIfMidi(device)
        else permissionManager?.requestPermission(device)
    }

    private fun processIfMidi(device: UsbDevice) {
        if (detector.isMidiDevice(device)) connectToDevice(device)
    }

    private fun connectToDevice(device: UsbDevice) {
        if (transport?.isConnected == true) return
        midiProcessor.selectProfileForDevice(
            DeviceDescriptor(
                vendorId = device.vendorId,
                productId = device.productId,
                manufacturer = device.manufacturerName ?: "",
                productName = device.productName ?: "",
            )
        )
        transport?.connect(device)
    }

    private fun handleDetach(device: UsbDevice) {
        transport?.disconnect()
        firstPacketReceived.set(false)
    }

    fun release() {
        transport?.disconnect()
        permissionManager?.unregister()
        try { context.unregisterReceiver(usbReceiver) } catch (e: Exception) {}
    }
}
