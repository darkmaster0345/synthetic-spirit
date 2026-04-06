package com.example.syntheticspirit

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class DnsParsingTest {

    @Test
    fun testChecksumCalculation() {
        val data = byteArrayOf(
            0x45.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3c.toByte(),
            0x1c.toByte(), 0x46.toByte(), 0x40.toByte(), 0x00.toByte(),
            0x40.toByte(), 0x06.toByte(), 0x00.toByte(), 0x00.toByte(),
            0xac.toByte(), 0x10.toByte(), 0x0a.toByte(), 0x02.toByte(),
            0xac.toByte(), 0x10.toByte(), 0x0a.toByte(), 0x01.toByte()
        )

        var sum = 0
        var i = 0
        while (i < 20) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        val expected = (sum.inv() and 0xFFFF).toShort()

        assertEquals(expected, calculateChecksum(data, 0, 20))
    }

    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Short {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            val high = (data[i].toInt() and 0xFF) shl 8
            val low = if (i + 1 < offset + length) data[i + 1].toInt() and 0xFF else 0
            sum += (high or low)
            i += 2
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toShort()
    }
}
