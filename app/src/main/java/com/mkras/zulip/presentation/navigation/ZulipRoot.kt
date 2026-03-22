package com.mkras.zulip.presentation.navigation

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mkras.zulip.BuildConfig
import com.mkras.zulip.core.realtime.EventServiceController
import com.mkras.zulip.NotificationNavigationTarget
import com.mkras.zulip.R
import com.mkras.zulip.presentation.auth.AuthViewModel
import com.mkras.zulip.presentation.auth.LoginScreen
import com.mkras.zulip.presentation.home.ZulipHomeScreen
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay

@Composable
fun ZulipRoot(
    notificationTarget: NotificationNavigationTarget? = null,
    onNotificationTargetConsumed: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val lockTimeoutMs = 60_000L
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
    var biometricAuthenticated by remember(session?.serverUrl, session?.email) { mutableStateOf(false) }
    var isHandlingAttachment by remember { mutableStateOf(false) }
    val lastInteractionAtMs = remember { AtomicLong(System.currentTimeMillis()) }
    var backgroundedAtMs by remember { mutableStateOf<Long?>(null) }
    val biometricEnabled = session != null && viewModel.getBiometricLockEnabled()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, session) {
        val observer = LifecycleEventObserver { _, event ->
            if (session == null || !biometricEnabled) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    backgroundedAtMs = System.currentTimeMillis()
                }
                Lifecycle.Event.ON_RESUME -> {
                    val now = System.currentTimeMillis()
                    val backgroundTimeout = backgroundedAtMs?.let { now - it >= lockTimeoutMs } == true
                    val idleTimeout = now - lastInteractionAtMs.get() >= lockTimeoutMs
                    if (backgroundTimeout || idleTimeout) {
                        biometricAuthenticated = false
                    }
                    lastInteractionAtMs.set(now)
                    backgroundedAtMs = null
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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

    LaunchedEffect(session?.serverUrl, session?.email, biometricEnabled, biometricAuthenticated, isHandlingAttachment) {
        while (session != null && biometricEnabled && biometricAuthenticated && !isHandlingAttachment) {
            delay(5_000)
            if (System.currentTimeMillis() - lastInteractionAtMs.get() >= lockTimeoutMs) {
                biometricAuthenticated = false
                break
            }
        }
    }

    DisposableEffect(session?.serverUrl, session?.email, biometricEnabled) {
        val activity = context as? AppCompatActivity
        if (activity == null || session == null || !biometricEnabled) {
            onDispose { }
        } else {
            val window = activity.window
            val originalCallback = window.callback
            val trackingCallback = InteractionTrackingWindowCallback(originalCallback) {
                lastInteractionAtMs.set(System.currentTimeMillis())
            }
            window.callback = trackingCallback
            onDispose {
                if (window.callback === trackingCallback) {
                    window.callback = originalCallback
                }
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
        if (biometricEnabled && !biometricAuthenticated && !isHandlingAttachment) {
            BiometricLockScreen(
                onAuthenticated = {
                    biometricAuthenticated = true
                    lastInteractionAtMs.set(System.currentTimeMillis())
                    backgroundedAtMs = null
                },
                onLogout = viewModel::logout
            )
        } else {
            ZulipHomeScreen(
                session = session,
                onLogout = viewModel::logout,
                onAttachmentOperationStart = { isHandlingAttachment = true },
                onAttachmentOperationEnd = {
                    isHandlingAttachment = false
                    lastInteractionAtMs.set(System.currentTimeMillis())
                },
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
                onNotificationTargetConsumed = onNotificationTargetConsumed,
                initialBiometricLockEnabled = viewModel.getBiometricLockEnabled(),
                onSaveBiometricLockEnabled = viewModel::saveBiometricLockEnabled,
                initialAutoUpdateEnabled = viewModel.getAutoUpdateEnabled(),
                onSaveAutoUpdateEnabled = viewModel::saveAutoUpdateEnabled
            )
        }
    }
}

private class InteractionTrackingWindowCallback(
    private val delegate: Window.Callback,
    private val onInteraction: () -> Unit
) : Window.Callback by delegate {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        onInteraction()
        return delegate.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        onInteraction()
        return delegate.dispatchKeyEvent(event)
    }
}

@Composable
private fun BiometricLockScreen(
    onAuthenticated: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var promptNonce by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                promptNonce++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(promptNonce) {
        val activity = context as? AppCompatActivity
        if (activity == null) {
            onAuthenticated()
            return@LaunchedEffect
        }
        val biometricManager = BiometricManager.from(context)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            onAuthenticated()
            return@LaunchedEffect
        }
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onAuthenticated()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Never auto-logout on biometric error — user retries via resume or manual button.
                    // Keep lock screen active for all error types.
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Toya Zulip")
            .setSubtitle("Potwierd\u017a to\u017csamo\u015b\u0107 aby otworzy\u0107 aplikacj\u0119")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(promptInfo)
    }

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
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = Color(0xFF8CD9FF),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Aplikacja zablokowana",
                color = Color(0xFFEAF2FF),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Zweryfikuj to\u017csamo\u015b\u0107, aby kontynuowa\u0107",
                color = Color(0xFF9FB2CC),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onLogout) {
                Text("Wyloguj", color = Color(0xFF8CD9FF))
            }
            TextButton(onClick = { promptNonce++ }) {
                Text("Odblokuj ponownie", color = Color(0xFF8CD9FF))
            }
        }
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
                  text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                  color = Color(0xFFBFD3EE),
                  style = MaterialTheme.typography.labelMedium
              )
            Text(
                text = "SysADM Toya",
                color = Color(0xFFBFD3EE),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
