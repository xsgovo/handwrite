package com.note.handwrite.util

/** Stateless palm heuristic. Callers confirm a match across multiple input frames. */
fun isPalmContact(
    majorMillimeters: Float,
    minorMillimeters: Float
): Boolean {
    val elongated = minorMillimeters > 0f && majorMillimeters / minorMillimeters >= 2.8f &&
        majorMillimeters >= 20f
    return majorMillimeters >= 28f || elongated
}
