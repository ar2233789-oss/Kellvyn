package com.kellvyn.assistant.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class BinaryAndroidManifestTest {
    @Test
    fun writesWellFormedChunkBoundariesAndBalancedElements() {
        val bytes = BinaryAndroidManifest.create(
            packageName = "com.example.app",
            activityName = "com.example.app.MainActivity",
            appLabel = "Demo local",
            minSdk = 26,
            targetSdk = 35
        )

        assertEquals(0x0003, u16(bytes, 0))
        assertEquals(8, u16(bytes, 2))
        assertEquals(bytes.size, u32(bytes, 4))

        var offset = 8
        var starts = 0
        var ends = 0
        while (offset < bytes.size) {
            val type = u16(bytes, offset)
            val headerSize = u16(bytes, offset + 2)
            val chunkSize = u32(bytes, offset + 4)
            assertTrue("chunk header must be inside the file", headerSize >= 8)
            assertTrue("chunk size must include its header", chunkSize >= headerSize)
            assertTrue("chunk must not run past the XML document", offset + chunkSize <= bytes.size)
            if (type == 0x0102) starts++
            if (type == 0x0103) ends++
            offset += chunkSize
        }
        assertEquals(bytes.size, offset)
        assertEquals(starts, ends)
        assertTrue(starts >= 7)
    }

    @Test
    fun preservesInternetPermissionWithoutAddingResourceTable() {
        val bytes = BinaryAndroidManifest.create(
            packageName = "com.example.app",
            activityName = "com.example.app.MainActivity",
            appLabel = "Demo local",
            minSdk = 26,
            targetSdk = 35,
            permissions = listOf("android.permission.INTERNET")
        )
        val data = String(bytes, StandardCharsets.ISO_8859_1)
        assertTrue(data.contains("android.permission.INTERNET"))
        assertTrue(data.contains("uses-permission"))
        assertEquals(bytes.size, u32(bytes, 4))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPrivilegedManifestPermission() {
        BinaryAndroidManifest.create(
            packageName = "com.example.app",
            activityName = "com.example.app.MainActivity",
            appLabel = "Demo local",
            minSdk = 26,
            targetSdk = 35,
            permissions = listOf("android.permission.MANAGE_EXTERNAL_STORAGE")
        )
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
}
