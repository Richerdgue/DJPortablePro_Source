package com.djportablepro.app.midi.usb

import android.hardware.usb.*
import com.djportablepro.app.logging.DjLogger
import com.djportablepro.app.midi.decoder.MidiMessage
import java.util.concurrent.atomic.AtomicBoolean

class UsbMidiTransport(
    private val usbManager: UsbManager,
    private val onMessageReceived: (MidiMessage) -> Unit
) {
    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var inputEndpoint: UsbEndpoint? = null
    private var outputEndpoint: UsbEndpoint? = null
    
    private val isReading = AtomicBoolean(false)
    private var readThread: Thread? = null

    val isConnected: Boolean
        get() = connection != null && isReading.get()

    fun connect(device: UsbDevice): Boolean {
        DjLogger.d(UsbMidiConstants.TAG, "[USB_CONNECT] Connecting to device: ${device.deviceName}")
        
        var foundInterface: UsbInterface? = null
        var foundInputEp: UsbEndpoint? = null
        var foundOutputEp: UsbEndpoint? = null

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbMidiConstants.USB_CLASS_AUDIO &&
                iface.interfaceSubclass == UsbMidiConstants.USB_SUBCLASS_MIDISTREAMING) {
                
                for (j in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(j)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN) {
                            foundInputEp = ep
                        } else if (ep.direction == UsbConstants.USB_DIR_OUT) {
                            foundOutputEp = ep
                        }
                    }
                }
                if (foundInputEp != null) {
                    foundInterface = iface
                    break
                }
            }
        }

        if (foundInterface == null || foundInputEp == null) {
            DjLogger.e(UsbMidiConstants.TAG, "Could not find MIDI endpoints")
            return false
        }

        val conn = usbManager.openDevice(device)
        if (conn == null) {
            DjLogger.e(UsbMidiConstants.TAG, "Failed to open device connection")
            return false
        }

        if (!conn.claimInterface(foundInterface, true)) {
            DjLogger.e(UsbMidiConstants.TAG, "Failed to claim interface")
            conn.close()
            return false
        }

        this.connection = conn
        this.usbInterface = foundInterface
        this.inputEndpoint = foundInputEp
        this.outputEndpoint = foundOutputEp

        DjLogger.d(UsbMidiConstants.TAG, "[USB_CONNECT] USB MIDI Connected: Interface=${foundInterface.id}, In=${foundInputEp.address}, Out=${foundOutputEp?.address ?: "NONE"}")
        startReading()
        return true
    }

    private fun startReading() {
        if (isReading.getAndSet(true)) return

        readThread = Thread {
            val endpoint = inputEndpoint ?: return@Thread
            val conn = connection ?: return@Thread
            val buffer = ByteArray(endpoint.maxPacketSize)

            while (isReading.get()) {
                val bytesRead = conn.bulkTransfer(endpoint, buffer, buffer.size, 1000)
                if (bytesRead > 0) {
                    parseUsbMidiPackets(buffer, bytesRead)
                }
            }
        }.apply { 
            name = "UsbMidiReadThread"
            start() 
        }
    }

    fun sendRaw(data: ByteArray): Int {
        val conn = connection ?: return -1
        val ep = outputEndpoint ?: return -2
        return conn.bulkTransfer(ep, data, data.size, 1000)
    }

    private fun parseUsbMidiPackets(buffer: ByteArray, length: Int) {
        for (i in 0 until length step UsbMidiConstants.EVENT_PACKET_SIZE) {
            if (i + 3 >= length) break
            val cin = buffer[i].toInt() and 0x0F
            if (cin == 0) continue 
            
            val status = buffer[i + 1].toInt() and 0xFF
            val data1 = buffer[i + 2].toInt() and 0xFF
            val data2 = buffer[i + 3].toInt() and 0xFF
            
            onMessageReceived(MidiMessage(status, data1, data2))
        }
    }

    fun disconnect() {
        isReading.set(false)
        readThread?.join(1200)
        readThread = null

        connection?.apply {
            usbInterface?.let { releaseInterface(it) }
            close()
        }
        connection = null
        usbInterface = null
        inputEndpoint = null
        outputEndpoint = null
    }
}
