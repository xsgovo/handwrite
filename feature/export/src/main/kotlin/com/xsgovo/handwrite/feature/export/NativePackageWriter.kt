package com.xsgovo.handwrite.feature.export

import com.xsgovo.handwrite.core.document.BackgroundResourceRepository
import com.xsgovo.handwrite.core.document.StoredResource
import com.xsgovo.handwrite.core.model.DocumentSnapshot
import com.xsgovo.handwrite.core.model.DomainResult
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PageContent
import com.xsgovo.handwrite.core.model.StrokeElement
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class NativePackageWriter(
    private val resources: BackgroundResourceRepository,
) {
    suspend fun write(snapshot: DocumentSnapshot, output: OutputStream) {
        val assets = linkedMapOf<Long, StoredResource>()
        snapshot.pages.forEach { content ->
            val asset = content.page.background as? PageBackground.Asset ?: return@forEach
            val resource = resources.find(asset.resourceId)
            if (resource is DomainResult.Success) assets[asset.resourceId.value] = resource.value
        }
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest(snapshot, assets).toString().encodeToByteArray())
            zip.closeEntry()

            snapshot.pages.forEachIndexed { index, page ->
                zip.putNextEntry(ZipEntry("pages/${index + 1}.bin"))
                writePage(zip, page)
                zip.closeEntry()
            }
            assets.values.distinctBy(StoredResource::sha256).forEach { resource ->
                val source = File(resource.absolutePath)
                zip.putNextEntry(ZipEntry("resources/${resource.sha256}.${source.extension.ifBlank { "bin" }}"))
                source.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}

private fun manifest(snapshot: DocumentSnapshot, assets: Map<Long, StoredResource>) = buildJsonObject {
    put("format", "com.xsgovo.handwrite.package")
    put("version", 1)
    put("name", snapshot.document.name.value)
    put("createdAtEpochMillis", snapshot.document.createdAtEpochMillis)
    put("modifiedAtEpochMillis", snapshot.document.modifiedAtEpochMillis)
    put("pages", buildJsonArray {
        snapshot.pages.forEachIndexed { index, content ->
            add(buildJsonObject {
                put("file", "pages/${index + 1}.bin")
                put("width", content.page.size.width)
                put("height", content.page.size.height)
                put("orderKey", content.page.orderKey)
                put("background", backgroundJson(content.page.background, assets))
            })
        }
    })
}

private fun backgroundJson(background: PageBackground, assets: Map<Long, StoredResource>) = buildJsonObject {
    when (background) {
        is PageBackground.Solid -> {
            put("type", "solid")
            put("argb", background.argb)
        }
        PageBackground.Transparent -> put("type", "transparent")
        is PageBackground.Pattern -> {
            put("type", "pattern")
            put("pattern", background.type.name)
            put("argb", background.baseArgb)
        }
        is PageBackground.Asset -> {
            put("type", "asset")
            put("kind", background.kind.name)
            put("sha256", assets[background.resourceId.value]?.sha256 ?: "")
            background.pdfPageIndex?.let { put("pdfPageIndex", it) }
            put("scalePermille", background.transform.scalePermille)
            put("translationX", background.transform.translation.x)
            put("translationY", background.transform.translation.y)
            put("rotationMilliDegrees", background.transform.rotationMilliDegrees)
        }
    }
}

private fun writePage(output: OutputStream, page: PageContent) {
    val data = DataOutputStream(output)
    data.writeInt(PAGE_BINARY_MAGIC)
    data.writeInt(PAGE_BINARY_VERSION)
    val strokes = page.elements.filterIsInstance<StrokeElement>()
    data.writeInt(strokes.size)
    strokes.forEach { stroke ->
        data.writeLong(stroke.orderKey)
        data.writeUTF(stroke.style.id.value)
        data.writeInt(stroke.style.argb)
        data.writeInt(stroke.style.width)
        data.writeByte(stroke.style.blendMode.ordinal)
        data.writeByte(stroke.style.pressureSensitivity.ordinal)
        data.writeInt(stroke.samples.size)
        var previousX = 0
        var previousY = 0
        stroke.samples.forEach { sample ->
            data.writeInt(sample.point.x - previousX)
            data.writeInt(sample.point.y - previousY)
            data.writeShort(sample.pressure)
            data.writeInt(sample.elapsedMillis)
            data.writeShort(sample.tiltX ?: Short.MIN_VALUE.toInt())
            data.writeShort(sample.tiltY ?: Short.MIN_VALUE.toInt())
            previousX = sample.point.x
            previousY = sample.point.y
        }
    }
    data.flush()
}

private const val PAGE_BINARY_MAGIC = 0x48575047
private const val PAGE_BINARY_VERSION = 1
