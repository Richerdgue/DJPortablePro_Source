package com.djportablepro.app.midi.usb

import android.hardware.usb.UsbConstants

object UsbMidiConstants {
    const val USB_CLASS_AUDIO = UsbConstants.USB_CLASS_AUDIO
    const val USB_SUBCLASS_MIDISTREAMING = 3
    
    // USB MIDI Event Packet size
    const val EVENT_PACKET_SIZE = 4
    
    const val TAG = "USB_MIDI_TRANSPORT"
}
