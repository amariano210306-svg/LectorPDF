package com.example.lectorpdf.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageGeometryTest {
    @Test
    fun roundTripWorksForEveryRightAngleRotation() {
        listOf(0, 90, 180, 270).forEach { rotation ->
            val transform = PdfPageTransform(
                pageWidth = 600f,
                pageHeight = 900f,
                crop = PdfNormalizedRect(.1f, .08f, .92f, .95f),
                rotation = rotation,
                viewportWidth = 1080f,
                viewportHeight = 1920f,
                fitMode = PdfFitMode.CONTENT,
                zoom = 2.15f,
                pan = PdfPoint(37f, -81f),
            )
            val source = PdfPoint(280f, 410f)
            val restored = transform.viewportToPdf(transform.pdfToViewport(source))
            assertEquals("x rotation $rotation", source.x, restored.x, .02f)
            assertEquals("y rotation $rotation", source.y, restored.y, .02f)
        }
    }

    @Test
    fun normalizedHighlightGeometryIsIndependentFromViewport() {
        val transform = PdfPageTransform(
            pageWidth = 500f,
            pageHeight = 1000f,
            crop = PdfNormalizedRect(.1f, .1f, .9f, .9f),
            rotation = 90,
            viewportWidth = 1600f,
            viewportHeight = 900f,
            fitMode = PdfFitMode.CONTENT,
        )
        val normalized = transform.normalizePdfRect(PdfRect(100f, 200f, 300f, 260f))
        assertEquals(.2f, normalized.left, .0001f)
        assertEquals(.2f, normalized.top, .0001f)
        assertEquals(.6f, normalized.right, .0001f)
        assertEquals(.26f, normalized.bottom, .0001f)
    }

    @Test
    fun fitContentUsesCroppedWidthAndPreservesAspectRatio() {
        val scale = pdfBaseScale(420f, 800f, 1200f, 700f, PdfFitMode.CONTENT, horizontalMargin = 12f)
        assertEquals(1176f / 420f, scale, .0001f)
        assertTrue(800f * scale > 700f)
    }

    @Test
    fun combinedCropRemainsValidAtExtremeManualInsets() {
        val crop = combineCrop(
            PdfNormalizedRect(.08f, .05f, .94f, .96f),
            PdfCropInsets(.3f, .3f, .3f, .3f),
        )
        assertTrue(crop.left < crop.right)
        assertTrue(crop.top < crop.bottom)
        assertTrue(crop.left >= .38f)
        assertTrue(crop.right <= .64f)
    }

    @Test
    fun automaticCropPolicyNeverCutsPastConservativeLimits() {
        val crop = conservativeDetectedCrop(.42f, .38f, .61f, .57f)
        assertEquals(.24f, crop.left, .0001f)
        assertEquals(.24f, crop.top, .0001f)
        assertEquals(.76f, crop.right, .0001f)
        assertEquals(.76f, crop.bottom, .0001f)
    }

    @Test
    fun cornerActionsStayInsideInsetAndDoNotReplaceCenterTap() {
        val common = arrayOf(1080f, 1920f, 24f, 160f)
        assertEquals(
            PdfQuickTapAction.THEME,
            pdfQuickTapAction(80f, 80f, common[0], common[1], common[2], common[3], true, true, true),
        )
        assertEquals(
            PdfQuickTapAction.BOOKMARK,
            pdfQuickTapAction(1000f, 80f, common[0], common[1], common[2], common[3], true, true, true),
        )
        assertEquals(
            PdfQuickTapAction.CENTER,
            pdfQuickTapAction(540f, 960f, common[0], common[1], common[2], common[3], true, true, true),
        )
        assertEquals(
            PdfQuickTapAction.NONE,
            pdfQuickTapAction(4f, 80f, common[0], common[1], common[2], common[3], true, true, true),
        )
    }
}
