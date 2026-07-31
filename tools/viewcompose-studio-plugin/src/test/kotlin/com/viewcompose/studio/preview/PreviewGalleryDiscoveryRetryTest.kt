package com.viewcompose.studio.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PreviewGalleryDiscoveryRetryTest {
    @Test
    fun `startup discovery retries are bounded with increasing delays`() {
        assertEquals(500, galleryDiscoveryRetryDelayMillis(0))
        assertEquals(1_000, galleryDiscoveryRetryDelayMillis(1))
        assertEquals(2_000, galleryDiscoveryRetryDelayMillis(2))
        assertNull(galleryDiscoveryRetryDelayMillis(3))
        assertThrows(IllegalArgumentException::class.java) {
            galleryDiscoveryRetryDelayMillis(-1)
        }
    }
}
