package com.note.handwrite

import android.app.Application
import com.note.handwrite.data.InputSettingsRepository

class HandWriteApp : Application() {
    val inputSettingsRepository by lazy { InputSettingsRepository(this) }
}
