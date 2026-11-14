package com.shopmanager.app.data.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSizingTest {

    @Test fun scaledSizeKeepsAspectAndNeverUpscales() {
        assertEquals(1024 to 512, ImageSizing.scaledSize(2000, 1000))
        assertEquals(512 to 1024, ImageSizing.scaledSize(1000, 2000))
        assertEquals(800 to 600, ImageSizing.scaledSize(800, 600))
        assertEquals(1 to 1, ImageSizing.scaledSize(0, 5))
    }

    @Test fun sampleSizeIsPowerOfTwoKeepingTarget() {
        assertEquals(1, ImageSizing.sampleSize(1000, 500, 1024))
        assertEquals(2, ImageSizing.sampleSize(4000, 3000, 1024))
        assertEquals(4, ImageSizing.sampleSize(8000, 6000, 1024))
        assertEquals(1, ImageSizing.sampleSize(0, 0, 1024))
    }

    @Test fun qualityStepsDownToFloor() {
        assertEquals(70, ImageSizing.nextQuality(80))
        assertEquals(40, ImageSizing.nextQuality(45))
        assertEquals(40, ImageSizing.nextQuality(40))
    }

    @Test fun displaySizeFitsBox() {
        assertEquals(240 to 120, ImageSizing.displaySize(1000, 500, 240, 320))
        assertEquals(160 to 320, ImageSizing.displaySize(500, 1000, 240, 320))
    }
}
