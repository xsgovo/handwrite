package com.xsgovo.handwrite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.xsgovo.handwrite.core.designsystem.HandwriteTheme
import com.xsgovo.handwrite.feature.editor.EditorRoute
import com.xsgovo.handwrite.feature.library.LibraryRoute
import com.xsgovo.handwrite.feature.settings.SettingsRoute
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
private data class EditorDestination(
    val sessionId: String,
    val documentId: Long? = null,
)

@Serializable
private data object LibraryDestination

@Serializable
private data object SettingsDestination

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appViewModel: HandwriteAppViewModel = hiltViewModel()
            val settings by appViewModel.settings.collectAsState()
            val navController = rememberNavController()

            HandwriteTheme(themeMode = settings.themeMode) {
                NavHost(
                    navController = navController,
                    startDestination = EditorDestination(sessionId = UUID.randomUUID().toString()),
                ) {
                    composable<EditorDestination> { backStackEntry ->
                        val destination = backStackEntry.toRoute<EditorDestination>()
                        EditorRoute(
                            documentId = destination.documentId,
                            onLibrary = {
                                navController.navigate(LibraryDestination) {
                                    popUpTo<EditorDestination> { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            onSettings = { navController.navigate(SettingsDestination) { launchSingleTop = true } },
                            onExitApplication = ::finishAndRemoveTask,
                        )
                    }
                    composable<LibraryDestination> {
                        LibraryRoute(
                            onNewDocument = {
                                navController.navigate(EditorDestination(sessionId = UUID.randomUUID().toString()))
                            },
                            onOpenDocument = { documentId ->
                                navController.navigate(
                                    EditorDestination(
                                        sessionId = UUID.randomUUID().toString(),
                                        documentId = documentId,
                                    ),
                                )
                            },
                            onSettings = { navController.navigate(SettingsDestination) { launchSingleTop = true } },
                        )
                    }
                    composable<SettingsDestination> {
                        SettingsRoute(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
