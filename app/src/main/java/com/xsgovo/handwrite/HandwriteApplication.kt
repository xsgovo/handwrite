package com.xsgovo.handwrite

import android.app.Application
import com.xsgovo.handwrite.core.document.DurableCommandExecutor
import com.xsgovo.handwrite.core.document.BackgroundResourceRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class HandwriteApplication : Application() {
    @Inject
    lateinit var commandExecutor: DurableCommandExecutor

    @Inject
    lateinit var backgroundResources: BackgroundResourceRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            commandExecutor.recover()
            backgroundResources.pruneUnreferenced()
        }
    }
}
