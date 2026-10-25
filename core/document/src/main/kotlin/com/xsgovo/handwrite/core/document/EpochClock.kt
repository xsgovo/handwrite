package com.xsgovo.handwrite.core.document

/** 可注入的时钟，为文档时间戳与元素 ID 生成提供统一时间来源。 */
fun interface EpochClock {
    fun nowMillis(): Long
}
