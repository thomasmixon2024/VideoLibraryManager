package com.example.videolibrarymanager

import com.example.videolibrarymanager.data.VideoEntity
import org.junit.Assert.*
import org.junit.Test

class VideoEntityTest {

    @Test
    fun `default values are sensible`() {
        val e = VideoEntity(path = "/sdcard/test.mp4", name = "test.mp4")
        assertEquals(0L, e.id)
        assertEquals("Uncategorized", e.category)
        assertEquals(0L, e.duration)
        assertEquals("", e.resolution)
        assertEquals(0L, e.size)
        assertNull(e.thumbnailPath)
        assertNull(e.checksum)
        assertFalse(e.isCorrupt)
        assertNull(e.errorMessage)
        assertTrue(e.dateAdded > 0)
    }

    @Test
    fun `copy produces independent entity`() {
        val original = VideoEntity(path = "/a.mp4", name = "a.mp4")
        val copy     = original.copy(name = "b.mp4", isCorrupt = true)
        assertEquals("a.mp4", original.name)
        assertEquals("b.mp4", copy.name)
        assertFalse(original.isCorrupt)
        assertTrue(copy.isCorrupt)
    }

    @Test
    fun `resolution field stores arbitrary string`() {
        val e = VideoEntity(path = "/p.mp4", name = "p.mp4", resolution = "3840x2160")
        assertEquals("3840x2160", e.resolution)
    }
}
