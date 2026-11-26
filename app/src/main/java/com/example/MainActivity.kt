package com.example

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.media.AppTab
import com.example.media.BackgroundRestrictionManager
import com.example.media.DownloadStatus
import com.example.media.DownloadWorker
import com.example.media.MediaPlayerViewModel
import com.example.media.MediaType
import com.example.media.PlaybackManager
import com.example.media.PlaybackService
import com.example.ui.components.BackgroundShieldDialog
import com.example.ui.components.GeometricBackground
import com.example.ui.screens.BottomMiniPlayer
import com.example.ui.screens.DownloaderScreen
import com.example.ui.screens.FullScreenAudioPlayer
import com.example.ui.screens.MusicScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.screens.VideosScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MediaPlayerViewModel by viewModels()
    private val isInPipModeState = mutableStateOf(false)
    private var latestPipRatio: Rational = Rational(16, 9)
    private var latestPipRect: Rect? = null

    companion object {
        private const val ACTION_PIP_CONTROL = "com.example.media.ACTION_PIP_CONTROL"
        private const val EXTRA_PIP_COMMAND = "extra_pip_command"
        private const val CMD_REWIND = "REWIND"
        private const val CMD_TOGGLE = "TOGGLE"
        private const val CMD_FORWARD = "FORWARD"
    }

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_PIP_CONTROL) return
            when (intent.getStringExtra(EXTRA_PIP_COMMAND)) {
                CMD_REWIND -> PlaybackManager.skipBackward(10)
                CMD_TOGGLE -> PlaybackManager.togglePlayPause()
                CMD_FORWARD -> PlaybackManager.skipForward(10)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ContextCompat.registerReceiver(
            this,
            pipActionReceiver,
            IntentFilter(ACTION_PIP_CONTROL),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        handleNavigationIntent(intent)

        setContent {
            MyApplicationTheme {
                MainAppContent(
                    viewModel = viewModel,
                    isInPipMode = isInPipModeState.value,
                    onEnterPip = { enterPipIfSupported() },
                    onUpdatePipParams = { ratio, rect ->
                        if (ratio != null) latestPipRatio = ratio
                        if (rect != null) latestPipRect = rect
                        syncPipParams()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S) {
            if (viewModel.selectedVideo.value != null && PlaybackManager.isPlaying.value) {
                enterPipIfSupported()
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState.value = isInPictureInPictureMode
    }

    fun syncPipParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        val shouldAutoEnter = viewModel.selectedVideo.value != null && PlaybackManager.isPlaying.value
        runCatching {
            setPictureInPictureParams(
                buildPipParams(
                    isPlaying = PlaybackManager.isPlaying.value,
                    autoEnter = shouldAutoEnter
                )
            )
        }
    }

    private fun enterPipIfSupported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching {
            enterPictureInPictureMode(
                buildPipParams(
                    isPlaying = PlaybackManager.isPlaying.value,
                    autoEnter = true
                )
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(isPlaying: Boolean, autoEnter: Boolean): PictureInPictureParams {
        val rewindIntent = PendingIntent.getBroadcast(
            this,
            101,
            Intent(ACTION_PIP_CONTROL).setPackage(packageName).putExtra(EXTRA_PIP_COMMAND, CMD_REWIND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleIntent = PendingIntent.getBroadcast(
            this,
            102,
            Intent(ACTION_PIP_CONTROL).setPackage(packageName).putExtra(EXTRA_PIP_COMMAND, CMD_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val forwardIntent = PendingIntent.getBroadcast(
            this,
            103,
            Intent(ACTION_PIP_CONTROL).setPackage(packageName).putExtra(EXTRA_PIP_COMMAND, CMD_FORWARD),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actions = listOf(
            RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_rewind_10),
                "ترجيع 10 ث",
                "ترجيع 10 ثوانٍ",
                rewindIntent
            ),
            RemoteAction(
                Icon.createWithResource(
                    this,
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                ),
                if (isPlaying) "إيقاف" else "تشغيل",
                if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                toggleIntent
            ),
            RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_forward_10),
                "تقديم 10 ث",
                "تقديم 10 ثوانٍ",
                forwardIntent
            )
        )

        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(latestPipRatio)
            .setActions(actions)

        latestPipRect?.let { rect ->
            if (!rect.isEmpty) {
                builder.setSourceRectHint(rect)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
        }
        return builder.build()
    }

    private fun handleNavigationIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(DownloadWorker.EXTRA_OPEN_DOWNLOADS, false)) {
            viewModel.setTab(AppTab.DOWNLOADER)
            intent.removeExtra(DownloadWorker.EXTRA_OPEN_DOWNLOADS)
        }
        if (intent.getBooleanExtra(PlaybackService.EXTRA_OPEN_AUDIO_PLAYER, false)) {
            val current = PlaybackManager.currentMediaItem.value
            if (current != null && current.mediaType == MediaType.AUDIO) {
                viewModel.expandAudioPlayer(true)
            } else if (current != null && current.mediaType == MediaType.VIDEO) {
                viewModel.playVideo(current)
            }
            intent.removeExtra(PlaybackService.EXTRA_OPEN_AUDIO_PLAYER)
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipActionReceiver) }
        super.onDestroy()
        if (isFinishing && !PlaybackManager.hasActivePlayback()) {
            PlaybackManager.release()
        }
    }
}

@Composable
fun MainAppContent(
    viewModel: MediaPlayerViewModel,
    isInPipMode: Boolean = false,
    onEnterPip: () -> Unit = {},
    onUpdatePipParams: (Rational?, Rect?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentTab by viewModel.currentTab.collectAsState()
    val selectedVideo by viewModel.selectedVideo.collectAsState()
    val isAudioPlayerExpanded by viewModel.isAudioPlayerExpanded.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val currentItem by PlaybackManager.currentMediaItem.collectAsState()
    val isPlaying by PlaybackManager.isPlaying.collectAsState()

    var showBackgroundShieldDialog by remember { mutableStateOf(false) }
    var isBatteryProtected by remember {
        mutableStateOf(BackgroundRestrictionManager.getProtectionState(context).isBatteryOptimizationIgnored)
    }
    var restrictionBannerDismissed by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isBatteryProtected =
                    BackgroundRestrictionManager.getProtectionState(context).isBatteryOptimizationIgnored
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    var notificationBannerDismissed by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (!granted) {
            notificationBannerDismissed = false
        }
    }

    val requestNotificationsIfNeeded = remember(hasNotificationPermission) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(currentItem) {
        if (currentItem != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(isPlaying, selectedVideo, isInPipMode) {
        onUpdatePipParams(null, null)
    }

    val hasActiveBackgroundWork = isPlaying || activeDownloads.any {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
    }
    BackHandler(enabled = selectedVideo == null && !isAudioPlayerExpanded && hasActiveBackgroundWork) {
        activity?.moveTaskToBack(true)
    }

    if (showBackgroundShieldDialog) {
        BackgroundShieldDialog(
            onRequestNotificationPermission = requestNotificationsIfNeeded,
            onDismiss = {
                showBackgroundShieldDialog = false
                isBatteryProtected =
                    BackgroundRestrictionManager.getProtectionState(context).isBatteryOptimizationIgnored
            }
        )
    }

    if (selectedVideo != null) {
        VideoPlayerScreen(
            video = selectedVideo!!,
            onClose = { viewModel.closeVideoPlayer() },
            isInPipMode = isInPipMode,
            onEnterPip = onEnterPip,
            onPlayInBackgroundAudio = { viewModel.moveVideoToBackgroundAudio() },
            onUpdatePipParams = onUpdatePipParams,
            onAttachSubtitle = { uri, fileName ->
                selectedVideo?.id?.let { videoId ->
                    viewModel.updateVideoSubtitleUri(videoId, uri, fileName)
                }
            }
        )
    } else {
        GeometricBackground(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavigationRail = maxWidth >= 600.dp

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.statusBars,
                    bottomBar = {
                        if (!useNavigationRail) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                            ) {
                                if (currentItem != null) {
                                    BottomMiniPlayer(viewModel = viewModel)
                                }

                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    tonalElevation = 3.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("main_bottom_nav")
                                ) {
                                    NavigationBarItem(
                                        selected = currentTab == AppTab.VIDEOS,
                                        onClick = { viewModel.setTab(AppTab.VIDEOS) },
                                        icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Videos") },
                                        label = {
                                            Text(
                                                text = "فيديوهات",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (currentTab == AppTab.VIDEOS) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.testTag("nav_videos_tab")
                                    )

                                    NavigationBarItem(
                                        selected = currentTab == AppTab.MUSIC,
                                        onClick = { viewModel.setTab(AppTab.MUSIC) },
                                        icon = { Icon(Icons.Default.MusicNote, contentDescription = "Music") },
                                        label = {
                                            Text(
                                                text = "موسيقى",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (currentTab == AppTab.MUSIC) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            selectedTextColor = MaterialTheme.colorScheme.secondary,
                                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.testTag("nav_music_tab")
                                    )

                                    NavigationBarItem(
                                        selected = currentTab == AppTab.PLAYLISTS,
                                        onClick = { viewModel.setTab(AppTab.PLAYLISTS) },
                                        icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Playlists") },
                                        label = {
                                            Text(
                                                text = "قوائم التشغيل",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (currentTab == AppTab.PLAYLISTS) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                            selectedTextColor = MaterialTheme.colorScheme.tertiary,
                                            indicatorColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.testTag("nav_playlists_tab")
                                    )

                                    NavigationBarItem(
                                        selected = currentTab == AppTab.DOWNLOADER,
                                        onClick = { viewModel.setTab(AppTab.DOWNLOADER) },
                                        icon = { Icon(Icons.Default.CloudDownload, contentDescription = "Downloader") },
                                        label = {
                                            Text(
                                                text = "تحميل",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (currentTab == AppTab.DOWNLOADER) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.testTag("nav_downloader_tab")
                                    )
                                    NavigationBarItem(selected = false, onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://apps-showcase.pages.dev")).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }) }, icon = { Icon(Icons.Default.Apps, contentDescription = "تطبيقاتنا") }, label = { Text("تطبيقاتنا", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.testTag("nav_our_apps_tab"))
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useNavigationRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .testTag("main_navigation_rail")
                            ) {
                                Spacer(modifier = Modifier.weight(0.2f))

                                NavigationRailItem(
                                    selected = currentTab == AppTab.VIDEOS,
                                    onClick = { viewModel.setTab(AppTab.VIDEOS) },
                                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Videos") },
                                    label = { Text("فيديوهات", style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_videos_tab")
                                )

                                NavigationRailItem(
                                    selected = currentTab == AppTab.MUSIC,
                                    onClick = { viewModel.setTab(AppTab.MUSIC) },
                                    icon = { Icon(Icons.Default.MusicNote, contentDescription = "Music") },
                                    label = { Text("موسيقى", style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.secondary,
                                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_music_tab")
                                )

                                NavigationRailItem(
                                    selected = currentTab == AppTab.PLAYLISTS,
                                    onClick = { viewModel.setTab(AppTab.PLAYLISTS) },
                                    icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Playlists") },
                                    label = { Text("القوائم", style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.tertiary,
                                        indicatorColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_playlists_tab")
                                )

                                NavigationRailItem(
                                    selected = currentTab == AppTab.DOWNLOADER,
                                    onClick = { viewModel.setTab(AppTab.DOWNLOADER) },
                                    icon = { Icon(Icons.Default.CloudDownload, contentDescription = "Downloader") },
                                    label = { Text("تحميل", style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_downloader_tab")
                                )
                                NavigationRailItem(selected = false, onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://apps-showcase.pages.dev")).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }) }, icon = { Icon(Icons.Default.Apps, contentDescription = "تطبيقاتنا") }, label = { Text("تطبيقاتنا", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.testTag("nav_our_apps_rail_tab"))

                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }

                        val showNotifBanner = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !hasNotificationPermission &&
                            !notificationBannerDismissed
                        val showBatteryBanner = !isBatteryProtected && !restrictionBannerDismissed

                        val topNoticeBanner: (@Composable () -> Unit)? = when {
                            showBatteryBanner -> {
                                {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(16.dp),
                                        tonalElevation = 2.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Shield,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "فعّل حماية البطارية لاستمرار الخلفية",
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                TextButton(onClick = { showBackgroundShieldDialog = true }) {
                                                    Text("ضبط", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                                TextButton(onClick = { restrictionBannerDismissed = true }) {
                                                    Text("لاحقاً", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            showNotifBanner -> {
                                {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(16.dp),
                                        tonalElevation = 2.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.NotificationsActive,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "فعّل الإشعارات لعرض تحكم المشغّل",
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                TextButton(onClick = { requestNotificationsIfNeeded() }) {
                                                    Text("تفعيل", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                                TextButton(onClick = { notificationBannerDismissed = true }) {
                                                    Text("لاحقاً", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> null
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .widthIn(max = 920.dp)
                            ) {
                                when (currentTab) {
                                    AppTab.VIDEOS -> VideosScreen(
                                        viewModel = viewModel,
                                        isBatteryProtected = isBatteryProtected,
                                        onOpenBackgroundShield = { showBackgroundShieldDialog = true },
                                        headerNotice = topNoticeBanner
                                    )
                                    AppTab.MUSIC -> MusicScreen(
                                        viewModel = viewModel,
                                        isBatteryProtected = isBatteryProtected,
                                        onOpenBackgroundShield = { showBackgroundShieldDialog = true },
                                        headerNotice = topNoticeBanner
                                    )
                                    AppTab.PLAYLISTS -> PlaylistsScreen(
                                        viewModel = viewModel,
                                        headerNotice = topNoticeBanner
                                    )
                                    AppTab.DOWNLOADER -> DownloaderScreen(
                                        viewModel = viewModel,
                                        onRequestNotificationPermission = requestNotificationsIfNeeded,
                                        isBatteryProtected = isBatteryProtected,
                                        onOpenBackgroundShield = { showBackgroundShieldDialog = true },
                                        headerNotice = topNoticeBanner
                                    )
                                }
                            }

                            if (useNavigationRail && currentItem != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 920.dp)
                                        .windowInsetsPadding(WindowInsets.navigationBars)
                                        .padding(bottom = 8.dp)
                                ) {
                                    BottomMiniPlayer(viewModel = viewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isAudioPlayerExpanded && !isInPipMode) {
        BackHandler {
            viewModel.expandAudioPlayer(false)
        }
        FullScreenAudioPlayer(
            viewModel = viewModel,
            onDismiss = { viewModel.expandAudioPlayer(false) }
        )
    }
}
