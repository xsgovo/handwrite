package com.xsgovo.handwrite.feature.editor

import android.view.MotionEvent
import com.xsgovo.handwrite.core.model.LogicalPoint
import com.xsgovo.handwrite.core.model.SideButtonAction
import com.xsgovo.handwrite.core.model.StrokeSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwriteCanvasTest {
    @Test
    fun temporaryEraserFollowsStylusSideButtonBits() {
        assertTrue(
            isTemporaryEraserEvent(
                sideButtonAction = SideButtonAction.TEMPORARY_ERASER,
                actionMasked = MotionEvent.ACTION_HOVER_MOVE,
                buttonState = MotionEvent.BUTTON_STYLUS_PRIMARY,
            ),
        )
        assertTrue(
            isTemporaryEraserEvent(
                sideButtonAction = SideButtonAction.TEMPORARY_ERASER,
                actionMasked = MotionEvent.ACTION_MOVE,
                buttonState = MotionEvent.BUTTON_STYLUS_SECONDARY,
            ),
        )
        assertFalse(
            isTemporaryEraserEvent(
                sideButtonAction = SideButtonAction.TEMPORARY_ERASER,
                actionMasked = MotionEvent.ACTION_HOVER_MOVE,
                buttonState = 0,
            ),
        )
        assertFalse(
            isTemporaryEraserEvent(
                sideButtonAction = SideButtonAction.TEMPORARY_ERASER,
                actionMasked = null,
                buttonState = MotionEvent.BUTTON_STYLUS_PRIMARY,
            ),
        )
    }

    @Test
    fun temporaryEraserResetsWhenPointerStreamEnds() {
        // 系统返回等交互会终结指针事件流，侧键释放事件可能不再送达：
        // 悬停退出与取消事件必须复位，工具栏不能停留在橡皮高亮上。
        listOf(MotionEvent.ACTION_HOVER_EXIT, MotionEvent.ACTION_CANCEL).forEach { action ->
            assertFalse(
                isTemporaryEraserEvent(
                    sideButtonAction = SideButtonAction.TEMPORARY_ERASER,
                    actionMasked = action,
                    buttonState = MotionEvent.BUTTON_STYLUS_PRIMARY,
                ),
            )
        }
    }

    @Test
    fun temporaryEraserIgnoresOtherSideButtonActions() {
        SideButtonAction.entries
            .filterNot { it == SideButtonAction.TEMPORARY_ERASER }
            .forEach { sideButtonAction ->
                assertFalse(
                    isTemporaryEraserEvent(
                        sideButtonAction = sideButtonAction,
                        actionMasked = MotionEvent.ACTION_HOVER_MOVE,
                        buttonState = MotionEvent.BUTTON_STYLUS_PRIMARY,
                    ),
                )
            }
    }

    @Test
    fun pixelEraseKeepsStrokeWhenPathMisses() {
        val samples = horizontalSamples(fromX = 100, toX = 200, y = 100)
        val pieces = splitStrokeByErasePath(samples, erasePath = listOf(LogicalPoint(900, 900)), hitRadius = 50f)
        assertEquals(listOf(samples), pieces)
    }

    @Test
    fun pixelEraseSplitsStrokeAtThePath() {
        // 擦除路径竖直穿过笔画中部：笔画切成左右两段，路径覆盖的样本不留在任何片段中。
        val samples = horizontalSamples(fromX = 100, toX = 400, y = 200)
        val erasePath = listOf(
            LogicalPoint(240, 100),
            LogicalPoint(260, 300),
        )
        val pieces = splitStrokeByErasePath(samples, erasePath, hitRadius = 25f)
        assertEquals(2, pieces.size)
        assertTrue(pieces[0].all { it.point.x < 230 })
        assertTrue(pieces[1].all { it.point.x > 270 })
        assertEquals(samples.first(), pieces[0].first())
        assertEquals(samples.last(), pieces[1].last())
    }

    @Test
    fun pixelEraseDropsShortRemaindersAndFullyErasedStrokes() {
        // 路径扫过整条笔画 → 无存留；只擦除靠端头的一个样本 → 单样本残余被丢弃。
        val samples = horizontalSamples(fromX = 100, toX = 400, y = 100)
        val fullyErased = splitStrokeByErasePath(
            samples,
            erasePath = listOf(LogicalPoint(250, 100)),
            hitRadius = 1_000f,
        )
        assertTrue(fullyErased.isEmpty())

        val tailSampleDropped = splitStrokeByErasePath(
            samples,
            erasePath = listOf(LogicalPoint(390, 100)),
            hitRadius = 5f,
        )
        assertEquals(1, tailSampleDropped.size)
        assertTrue(tailSampleDropped.single().all { it.point.x < 390 })
    }

    @Test
    fun pixelEraseHitRadiusIncludesStrokeHalfWidth() {
        // 样本点在路径之外，但距离小于橡皮半径加半个笔宽时同样被擦除。
        val samples = listOf(StrokeSample(point = LogicalPoint(100, 160)))
        val pieces = splitStrokeByErasePath(
            samples,
            erasePath = listOf(LogicalPoint(100, 100)),
            hitRadius = 80f,
        )
        assertTrue(pieces.isEmpty())
    }

    @Test
    fun pixelErasePreservesSamplePayload() {
        val stroke = StrokeSample(
            point = LogicalPoint(100, 100),
            pressure = 321,
            elapsedMillis = 42,
            tiltX = 11,
            tiltY = -7,
        )
        val samples = listOf(
            stroke,
            StrokeSample(point = LogicalPoint(110, 100)),
            StrokeSample(point = LogicalPoint(120, 100)),
            StrokeSample(point = LogicalPoint(130, 100)),
            StrokeSample(point = LogicalPoint(140, 100), pressure = 654),
        )
        val pieces = splitStrokeByErasePath(
            samples,
            erasePath = listOf(LogicalPoint(120, 100)),
            hitRadius = 5f,
        )
        assertEquals(2, pieces.size)
        assertEquals(stroke, pieces[0].first())
        assertEquals(654, pieces[1].last().pressure)
    }

    @Test
    fun pageDragOverscrollStartsAfterThePanRoomIsExhausted() {
        // 100% 缩放无平移余量：横向位移全部转为页面拖拽，方向以符号表示（向右为正）。
        assertEquals(-50f, pageDragOverscroll(deltaX = -50f, panX = 0f, panLimitX = 0f), 0f)
        assertEquals(50f, pageDragOverscroll(deltaX = 50f, panX = 0f, panLimitX = 0f), 0f)

        // 仍有平移余量时不产生拖拽；超出余量的部分才计入。
        assertEquals(0f, pageDragOverscroll(deltaX = -50f, panX = 80f, panLimitX = 100f), 0f)
        assertEquals(0f, pageDragOverscroll(deltaX = 30f, panX = -80f, panLimitX = 100f), 0f)

        // 页面已平移到左边界附近（pan 接近 -limit），仅越出余量的位移计入（带负号）。
        assertEquals(-10f, pageDragOverscroll(deltaX = -30f, panX = -80f, panLimitX = 100f), 0f)
        assertEquals(110f, pageDragOverscroll(deltaX = 130f, panX = 80f, panLimitX = 100f), 0f)
    }

    private fun horizontalSamples(fromX: Int, toX: Int, y: Int): List<StrokeSample> =
        (fromX..toX step 10).map { x -> StrokeSample(point = LogicalPoint(x, y)) }
}
