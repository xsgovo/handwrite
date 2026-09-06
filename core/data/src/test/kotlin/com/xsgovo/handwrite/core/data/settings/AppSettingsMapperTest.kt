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
            pressureSensitivity = PressureSensitivity.OFF,
            colorSlots = listOf(0x00112233, 0xFFCCBBAA.toInt()),
            activeColorSlot = 1,
            pickerCandidates = listOf(0xFF101010.toInt(), 0xFF202020.toInt(), 0xFF303030.toInt(), 0xFF404040.toInt(), 0xFF505050.toInt(), 0xFF606060.toInt()),
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

    @Test
    fun malformedPickerCandidatesFallBackToDefaults() {
        val payload = AppSettingsPayload.newBuilder()
            .addPickerCandidates(1)
            .addPickerCandidates(2)
            .addPickerCandidates(3)
            .addPickerCandidates(4)
            .addPickerCandidates(5)
            .addPickerCandidates(6)
            .addPickerCandidates(7)
            .addPickerCandidates(8)
            .addPickerCandidates(9)
            .addPickerCandidates(10)
            .addPickerCandidates(11)
            .addPickerCandidates(12)
            .addPickerCandidates(13)
            .addPickerCandidates(14)
            .addPickerCandidates(15)
            .build()

        assertEquals(AppSettings().pickerCandidates, payload.toDomain().pickerCandidates)
    }
}
