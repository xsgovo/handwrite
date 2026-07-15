package com.note.handwrite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewmodel.compose.viewModel
import com.note.handwrite.ui.NoteScreen
import com.note.handwrite.ui.theme.HandWriteTheme
import com.note.handwrite.viewmodel.NoteViewModel

/** Temporary entry point kept minimal until the Compose screen is assembled. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.White.toArgb(), Color.White.toArgb())
        )
        setContent {
            HandWriteTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                    NoteScreen(viewModel = viewModel<NoteViewModel>())
                }
            }
        }
    }
}
