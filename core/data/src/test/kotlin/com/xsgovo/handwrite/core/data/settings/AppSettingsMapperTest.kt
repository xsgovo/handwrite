package com.xsgovo.handwrite.core.data.settings

import com.xsgovo.handwrite.core.model.AppSettings
import com.xsgovo.handwrite.core.model.BackBehavior
import com.xsgovo.handwrite.core.model.CompressionQuality
import com.xsgovo.handwrite.core.model.ExportResolution
import com.xsgovo.handwrite.core.model.ImageFormat
import com.xsgovo.handwrite.core.model.InputMode
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PatternType
import com.xsgovo.handwrite.core.model.PressureSensitivity
import com.xsgovo.handwrite.core.model.SideButtonAction
import com.xsgovo.handwrite.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsMapperTest {
    @Test
    fun settingsRoundTripPreservesUserChoices() {
        val settings = AppSettings(
            inputMode = InputMode.STYLUS,
            themeMode = ThemeMode.DARK,
            imageFormat = ImageFormat.WEBP,
            exportResolution = ExportResolution.HIGH,
            compressionQuality = CompressionQuality.HIGH,
            backBehavior = BackBehavior.OPEN_LIBRARY,
            sideButtonAction = SideButtonAction.UNDO,
            pressureSensitivity = PressureSensitivity.LOW,
            colorSlots = listOf(0x00112233, 0xFFCCBBAA.toInt()),
            activeColorSlot = 1,
            widthSteps = listOf(12, 73, 94),
            activeWidthSlot = 1,
            defaultBackground = PageBackground.Pattern(PatternType.LINED),
        )

        assertEquals(settings, settings.toProto().toDomain())
    }

    @Test
    fun emptyPayloadUsesValidDomainDefaults() {
        assertEquals(AppSettings(), AppSettingsPayload.getDefaultInstance().toDomain())
    }
}
