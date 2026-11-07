package com.djportablepro.app.midi.mapping

/**
 * Vendor-ready example profiles for common DJ hardware.
 * These are not tied to a specific device driver but provide a starting point for
 * autodetection and custom mapping workflows.
 */
object ControllerProfilePresets {
    data class Preset(val name: String, val json: String)

    fun list(): List<Preset> = listOf(
        Preset(
            name = "Pioneer DJ preset",
            json = """
                {
                  "name": "Pioneer DJ preset",
                  "vendorId": 1001,
                  "productId": 2001,
                  "manufacturer": "Pioneer",
                  "productName": "DDJ-XP2",
                  "mappings": [
                    {"type":"note","status":144,"data1":0,"deck":"A","action":"PlayPause"},
                    {"type":"note","status":144,"data1":1,"deck":"A","action":"Cue"},
                    {"type":"note","status":144,"data1":2,"deck":"A","action":"Sync"},
                    {"type":"cc","status":176,"data1":0,"deck":"A","action":"Volume"},
                    {"type":"cc","status":176,"data1":1,"deck":"B","action":"Volume"}
                  ]
                }
            """.trimIndent(),
        ),
        Preset(
            name = "Denon preset",
            json = """
                {
                  "name": "Denon preset",
                  "vendorId": 1002,
                  "productId": 2002,
                  "manufacturer": "Denon",
                  "productName": "Prime",
                  "mappings": [
                    {"type":"note","status":144,"data1":4,"deck":"A","action":"PlayPause"},
                    {"type":"note","status":144,"data1":5,"deck":"A","action":"Cue"},
                    {"type":"note","status":144,"data1":6,"deck":"B","action":"PlayPause"},
                    {"type":"cc","status":176,"data1":10,"deck":"A","action":"Volume"},
                    {"type":"cc","status":176,"data1":11,"deck":"B","action":"Volume"}
                  ]
                }
            """.trimIndent(),
        ),
        Preset(
            name = "Numark preset",
            json = """
                {
                  "name": "Numark preset",
                  "vendorId": 1003,
                  "productId": 2003,
                  "manufacturer": "Numark",
                  "productName": "Mixstream",
                  "mappings": [
                    {"type":"note","status":144,"data1":8,"deck":"A","action":"PlayPause"},
                    {"type":"note","status":144,"data1":9,"deck":"A","action":"Cue"},
                    {"type":"cc","status":176,"data1":20,"deck":"A","action":"High"},
                    {"type":"cc","status":176,"data1":21,"deck":"B","action":"Mid"}
                  ]
                }
            """.trimIndent(),
        ),
    )
}
