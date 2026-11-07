package com.djportablepro.app.midi.usb

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.djportablepro.app.logging.DjLogger

class UsbMidiDeviceDetector(private val context: Context) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    fun findMidiDevices(): List<UsbDevice> {
        val devices = usbManager.deviceList
        val midiDevices = mutableListOf<UsbDevice>()

        for (device in devices.values) {
            if (isMidiDevice(device)) {
                DjLogger.d(UsbMidiConstants.TAG, "Found MIDI candidate: ${device.deviceName} (VID: ${device.vendorId}, PID: ${device.productId})")
                midiDevices.add(device)
            }
        }
        return midiDevices
    }

    fun isMidiDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (isMidiInterface(usbInterface)) {
                return true
            }
        }
        return false
    }

    fun isMidiInterface(usbInterface: UsbInterface): Boolean {
        return usbInterface.interfaceClass == UsbMidiConstants.USB_CLASS_AUDIO &&
               usbInterface.interfaceSubclass == UsbMidiConstants.USB_SUBCLASS_MIDISTREAMING
    }
}
