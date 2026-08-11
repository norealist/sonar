package com.sonar.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonar.app.ui.SonarApp
import com.sonar.app.ui.SonarTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<PlayerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        setContent { SonarContent(viewModel) }
    }
}

@Composable
private fun SonarContent(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::importDirectory)
    }
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    SonarTheme {
        SonarApp(
            viewModel = viewModel,
            screen = screen,
            onPickAudio = { picker.launch(null) },
            context = context,
        )
    }
}
