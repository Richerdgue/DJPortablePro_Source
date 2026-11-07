package com.djportablepro.app.analysis

import kotlin.math.abs

/**
 * Professional Harmonic Key Analyzer and Camelot Wheel Mapping Service.
 * Provides key detection, Camelot conversion, and harmonic mix compatibility checking.
 */
class HarmonicKeyAnalyzer {

    data class KeyInfo(
        val musicalKey: String,
        val camelotCode: String,
        val isMajor: Boolean
    )

    private val keyMapping = mapOf(
        "Ab major" to KeyInfo("Ab Major", "4B", true),
        "A major" to KeyInfo("A Major", "11B", true),
        "A minor" to KeyInfo("A Minor", "8A", false),
        "Bb major" to KeyInfo("Bb Major", "6B", true),
        "B minor" to KeyInfo("B Minor", "10A", false),
        "B major" to KeyInfo("B Major", "1B", true),
        "C major" to KeyInfo("C Major", "8B", true),
        "C minor" to KeyInfo("C Minor", "5A", false),
        "Db major" to KeyInfo("Db Major", "3B", true),
        "D major" to KeyInfo("D Major", "10B", true),
        "D minor" to KeyInfo("D Minor", "7A", false),
        "Eb major" to KeyInfo("Eb Major", "5B", true),
        "E minor" to KeyInfo("E Minor", "9A", false),
        "E major" to KeyInfo("E Major", "12B", true),
        "F major" to KeyInfo("F Major", "7B", true),
        "F minor" to KeyInfo("F Minor", "4A", false),
        "Gb major" to KeyInfo("Gb Major", "2B", true),
        "G minor" to KeyInfo("G Minor", "3A", false),
        "G major" to KeyInfo("G Major", "9B", true)
    )

    fun analyzeKey(rawKey: String): KeyInfo {
        val normalized = rawKey.trim().lowercase()
        for ((key, info) in keyMapping) {
            if (key.lowercase() == normalized || info.camelotCode.lowercase() == normalized) {
                return info
            }
        }
        // Default fallback if unknown
        return KeyInfo(rawKey.ifBlank { "C Major" }, "8B", true)
    }

    /**
     * Checks if two Camelot codes are harmonically compatible for DJ mixing.
     * Compatible if same code, adjacent number (+/- 1) with same letter, or relative major/minor (same number, A <-> B).
     */
    fun isCompatible(camelot1: String, camelot2: String): Boolean {
        val info1 = parseCamelot(camelot1) ?: return false
        val info2 = parseCamelot(camelot2) ?: return false

        if (info1.number == info2.number && info1.letter != info2.letter) return true // Relative major/minor
        if (info1.letter == info2.letter) {
            val diff = abs(info1.number - info2.number)
            if (diff == 1 || diff == 11) return true // Adjacent on Camelot wheel
        }
        return info1.code == info2.code // Exact match
    }

    private data class CamelotParsed(val number: Int, val letter: Char, val code: String)

    private fun parseCamelot(code: String): CamelotParsed? {
        val clean = code.trim().uppercase()
        if (clean.length < 2) return null
        val numStr = clean.substring(0, clean.length - 1)
        val letter = clean.last()
        val num = numStr.toIntOrNull() ?: return null
        if (num !in 1..12 || (letter != 'A' && letter != 'B')) return null
        return CamelotParsed(num, letter, clean)
    }
}
