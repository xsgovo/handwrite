package com.xsgovo.handwrite.feature.editor

import android.view.MotionEvent
import com.xsgovo.handwrite.core.model.SideButtonAction
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
}
