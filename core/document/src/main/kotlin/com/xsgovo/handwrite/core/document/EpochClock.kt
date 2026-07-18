package com.xsgovo.handwrite.core.document

fun interface EpochClock {
    fun nowMillis(): Long
}
