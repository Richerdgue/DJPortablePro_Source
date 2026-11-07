package com.djportablepro.app.midi.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.djportablepro.app.logging.DjLogger

class UsbMidiPermissionManager(
    private val context: Context,
    private val onPermissionResult: (UsbDevice, Boolean) -> Unit
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val actionPermission = "${context.packageName}.USB_PERMISSION"

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (actionPermission == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (device != null) {
                        DjLogger.d(UsbMidiConstants.TAG, "[USB_PERMISSION_RESULT] for ${device.deviceName}: $granted")
                        onPermissionResult(device, granted)
                    }
                }
            }
        }
    }

    fun requestPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            DjLogger.d(UsbMidiConstants.TAG, "Already has permission for ${device.deviceName}")
            onPermissionResult(device, true)
            return
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val permissionIntent = PendingIntent.getBroadcast(
            context, 
            0, 
            Intent(actionPermission), 
            flags
        )

        val filter = IntentFilter(actionPermission)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(permissionReceiver, filter)
        }

        DjLogger.d(UsbMidiConstants.TAG, "[USB_PERMISSION_REQUEST] for ${device.deviceName}")
        usbManager.requestPermission(device, permissionIntent)
    }

    fun unregister() {
        try {
            context.unregisterReceiver(permissionReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
    }
}
