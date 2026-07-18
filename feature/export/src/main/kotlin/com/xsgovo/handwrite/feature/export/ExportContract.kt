package com.xsgovo.handwrite.feature.export

enum class DocumentExportFormat {
    PAGE_IMAGE,
    LONG_IMAGE,
    HYBRID_PDF,
    NATIVE_PACKAGE,
}

internal const val EXPORT_WORK_NAME = "document-export"
internal const val KEY_DOCUMENT_ID = "document_id"
internal const val KEY_DESTINATION_URI = "destination_uri"
internal const val KEY_EXPORT_FORMAT = "export_format"
internal const val KEY_IMAGE_FORMAT = "image_format"
internal const val KEY_EXPORT_RESOLUTION = "export_resolution"
internal const val KEY_COMPRESSION_QUALITY = "compression_quality"
