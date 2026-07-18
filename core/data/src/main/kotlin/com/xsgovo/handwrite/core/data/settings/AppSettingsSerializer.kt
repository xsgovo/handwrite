package com.xsgovo.handwrite.core.data.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.xsgovo.handwrite.core.model.AppSettings
import java.io.InputStream
import java.io.OutputStream

object AppSettingsSerializer : Serializer<AppSettingsPayload> {
    override val defaultValue: AppSettingsPayload = AppSettings().toProto()

    override suspend fun readFrom(input: InputStream): AppSettingsPayload = try {
        AppSettingsPayload.parseFrom(input)
    } catch (exception: InvalidProtocolBufferException) {
        throw CorruptionException("Cannot read app settings", exception)
    }

    override suspend fun writeTo(t: AppSettingsPayload, output: OutputStream) {
        t.writeTo(output)
    }
}
