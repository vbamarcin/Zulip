package com.mkras.zulip.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkras.zulip.core.realtime.EventServiceController
import com.mkras.zulip.NotificationNavigationTarget
import com.mkras.zulip.R
import com.mkras.zulip.presentation.auth.AuthViewModel
import com.mkras.zulip.presentation.auth.LoginScreen
import com.mkras.zulip.presentation.home.ZulipHomeScreen
import kotlinx.coroutines.delay

@Composable
fun ZulipRoot(
    notificationTarget: NotificationNavigationTarget? = null,
    onNotificationTargetConsumed: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    var showStartupSplash by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(3000)
        showStartupSplash = false
    }

    if (showStartupSplash) {
        StartupSplashScreen()
        return
    }

    if (!uiState.isReady) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val session = uiState.currentSession
    DisposableEffect(session?.serverUrl, session?.email) {
        if (session != null) {
            EventServiceController.start(context)
        } else {
            EventServiceController.stop(context)
        }

        onDispose {
            if (session == null) {
                EventServiceController.stop(context)
            }
        }
    }

    if (session == null) {
        LoginScreen(
            uiState = uiState,
            onServerUrlChange = viewModel::onServerUrlChange,
            onEmailChange = viewModel::onEmailChange,
            onSecretChange = viewModel::onSecretChange,
            onAuthTypeChange = viewModel::onAuthTypeChange,
            onSubmit = viewModel::submit
        )
    } else {
        ZulipHomeScreen(
            session = session,
            onLogout = viewModel::logout,
            initialCompactMode = viewModel.getCompactMode(),
            onSaveCompactMode = viewModel::saveCompactMode,
            initialFontScale = viewModel.getFontScale(),
            onSaveFontScale = viewModel::saveFontScale,
            initialMarkdownEnabled = viewModel.getMarkdownEnabled(),
            onSaveMarkdownEnabled = viewModel::saveMarkdownEnabled,
            initialNotificationsEnabled = viewModel.getNotificationsEnabled(),
            onSaveNotificationsEnabled = viewModel::saveNotificationsEnabled,
            initialDmNotificationsEnabled = viewModel.getDmNotificationsEnabled(),
            onSaveDmNotificationsEnabled = viewModel::saveDmNotificationsEnabled,
            initialChannelNotificationsEnabled = viewModel.getChannelNotificationsEnabled(),
            onSaveChannelNotificationsEnabled = viewModel::saveChannelNotificationsEnabled,
            onResetAllNotifications = viewModel::resetAllNotificationPreferences,
            isDirectMessageMuted = viewModel::isDirectMessageMuted,
            onSetDirectMessageMuted = viewModel::setDirectMessageMuted,
            isChannelMuted = viewModel::isChannelMuted,
            onSetChannelMuted = viewModel::setChannelMuted,
            isChannelDisabled = viewModel::isChannelDisabled,
            onSetChannelDisabled = viewModel::setChannelDisabled,
            getMutedChannels = viewModel::getMutedChannels,
            getDisabledChannels = viewModel::getDisabledChannels,
            notificationTarget = notificationTarget,
            onNotificationTargetConsumed = onNotificationTargetConsumed
        )
    }
}

@Composable
private fun StartupSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF081626), Color(0xFF0B1D32), Color(0xFF071222))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.ikona),
                contentDescription = "Logo firmy",
                modifier = Modifier.size(86.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Toya Zulip 2.0",
                color = Color(0xFFEAF2FF),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "SysADM Toya",
                color = Color(0xFFBFD3EE),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
