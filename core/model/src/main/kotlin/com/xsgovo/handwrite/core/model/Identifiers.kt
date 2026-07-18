package com.xsgovo.handwrite.core.model

@JvmInline
value class DocumentId(val value: Long)

@JvmInline
value class PageId(val value: Long)

@JvmInline
value class ElementId(val value: Long)

@JvmInline
value class FolderId(val value: Long)

@JvmInline
value class ResourceId(val value: Long)

@JvmInline
value class OperationId(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
value class BrushId(val value: String) {
    init {
        require(value.isNotBlank())
    }

    companion object {
        val MONOLINE = BrushId("monoline")
        val PRESSURE_PEN = BrushId("pressure_pen")
        val HIGHLIGHTER = BrushId("highlighter")
    }
}
