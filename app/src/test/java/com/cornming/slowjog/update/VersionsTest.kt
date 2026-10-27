package com.cornming.slowjog.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {

    @Test
    fun laterBuildNumberIsNewer() {
        assertTrue(Versions.isNewer("v0.1.3", "0.1.2"))
    }

    @Test
    fun comparesNumbersNotText() {
        assertTrue(Versions.isNewer("0.1.10", "0.1.9"))
    }

    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(Versions.isNewer("v0.1.2", "0.1.2"))
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertFalse(Versions.isNewer("0.1.1", "0.1.2"))
    }

    @Test
    fun devBuildCountsAsZeroBuild() {
        assertTrue(Versions.isNewer("0.1.1", "0.1.0-dev"))
    }

    @Test
    fun missingPartsCountAsZero() {
        assertFalse(Versions.isNewer("0.1", "0.1.0"))
        assertTrue(Versions.isNewer("0.2", "0.1.9"))
    }

    @Test
    fun unparsableVersionIsNeverNewer() {
        assertFalse(Versions.isNewer("latest", "0.1.2"))
        assertFalse(Versions.isNewer("0.1.3", ""))
    }

    @Test
    fun parseStripsPrefixAndSuffix() {
        assertEquals(listOf(0, 1, 12), Versions.parse("v0.1.12"))
        assertEquals(listOf(0, 1, 0), Versions.parse("0.1.0-dev"))
        assertNull(Versions.parse("abc"))
    }
}
