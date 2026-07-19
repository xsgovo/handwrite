package com.xsgovo.handwrite.core.rendering

import com.xsgovo.handwrite.core.model.ImageFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class PageImageEncoderTest {
    @Test
    fun imageFormatsExposeMatchingShareMetadata() {
        assertEquals(PageImageFileFormat("image/png", "png"), ImageFormat.PNG.pageImageFileFormat())
        assertEquals(PageImageFileFormat("image/jpeg", "jpg"), ImageFormat.JPEG.pageImageFileFormat())
        assertEquals(PageImageFileFormat("image/webp", "webp"), ImageFormat.WEBP.pageImageFileFormat())
        assertEquals(PageImageFileFormat("image/webp", "webp"), ImageFormat.AUTO.pageImageFileFormat())
    }
}
