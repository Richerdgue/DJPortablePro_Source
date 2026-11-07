package com.djportablepro.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class BpmAnalysisServiceTest {
    @Test
    fun `selectBestBpm prefers real analysis over fallback`() {
        val service = BpmAnalysisService()

        val result = service.selectBestBpm(
            trackName = "track-name",
            analyzedBpm = 128.0,
            fallbackBpm = 128.0,
        )

        assertEquals(128.0, result.bpm, 0.01)
        assertEquals("analysis", result.source)
    }

    @Test
    fun `extractBpmFromFilename extracts bpm embedded in middle or start of filename`() {
        val service = BpmAnalysisService()

        assertEquals(103.0, service.extractBpmFromFilename("Bacilos - Caraluna - 103bpm - JPR") ?: 0.0, 0.01)
        assertEquals(120.0, service.extractBpmFromFilename("120 - SIN TI - Natusha") ?: 0.0, 0.01)
        assertEquals(133.0, service.extractBpmFromFilename("--10 133 - SERGIO VARGAS - La Ventanita") ?: 0.0, 0.01)
    }
}
