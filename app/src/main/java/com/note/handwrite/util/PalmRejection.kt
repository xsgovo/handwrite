package com.note.handwrite.util

/** Stateless palm heuristic. Callers confirm a match across multiple input frames. */
fun isPalmContact(
    majorMillimeters: Float,
    minorMillimeters: Float,
    otherMajorMillimeters: Float?
): Boolean {
    val elongated = minorMillimeters > 0f && majorMillimeters / minorMillimeters >= 2.2f &&
        majorMillimeters >= 16f
    val muchLargerThanOther = otherMajorMillimeters != null &&
        majorMillimeters >= otherMajorMillimeters * 1.8f
    return majorMillimeters >= 24f || elongated || muchLargerThanOther
}
