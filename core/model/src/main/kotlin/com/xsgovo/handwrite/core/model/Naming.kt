package com.xsgovo.handwrite.core.model

import java.text.Normalizer
import java.util.Locale

@ConsistentCopyVisibility
data class DisplayName private constructor(
    val value: String,
    val normalizedKey: String,
) {
    companion object {
        const val MAX_CODE_POINTS = 100

        fun create(raw: String): NameResult {
            val display = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC)
            if (display.isBlank()) return NameResult.Invalid(NameProblem.BLANK)
            if (display.codePointCount(0, display.length) > MAX_CODE_POINTS) {
                return NameResult.Invalid(NameProblem.TOO_LONG)
            }
            if (display.any { it == '/' || it == '\\' || Character.isISOControl(it) }) {
                return NameResult.Invalid(NameProblem.FORBIDDEN_CHARACTER)
            }
            val key = Normalizer.normalize(display, Normalizer.Form.NFKC)
                .lowercase(Locale.ROOT)
            return NameResult.Valid(DisplayName(display, key))
        }
    }
}

sealed interface NameResult {
    data class Valid(val name: DisplayName) : NameResult

    data class Invalid(val problem: NameProblem) : NameResult
}

enum class NameProblem {
    BLANK,
    TOO_LONG,
    FORBIDDEN_CHARACTER,
}
