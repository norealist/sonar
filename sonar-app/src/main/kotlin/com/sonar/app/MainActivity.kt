package com.sonar.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.res.Configuration
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonar.app.ui.SonarApp
import com.sonar.app.ui.SonarTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<PlayerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocaleHelper.applyLanguage(viewModel.settings.current.language)
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
    val settings by viewModel.settings.settings.collectAsStateWithLifecycle()

    val currentLocale = remember(settings.language) { LocaleHelper.getLocale(settings.language) }
    val localizedContext = remember(context, currentLocale) {
        val config = Configuration(context.resources.configuration)
        config.setLocale(currentLocale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(currentLocale))
        }
        context.createConfigurationContext(config)
    }
    val localizedConfiguration = remember(localizedContext) {
        localizedContext.resources.configuration
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
    ) {
        SonarTheme {
            SonarApp(
                viewModel = viewModel,
                screen = screen,
                onPickAudio = { picker.launch(null) },
                context = localizedContext,
            )
        }
    }
}
