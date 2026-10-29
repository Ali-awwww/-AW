package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.media.AppTab
import com.example.media.MediaPlayerViewModel
import com.example.media.MediaType
import com.example.media.PlaybackManager
import com.example.ui.screens.BottomMiniPlayer
import com.example.ui.screens.FullScreenAudioPlayer
import com.example.ui.screens.MusicScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.screens.VideosScreen
import com.example.ui.components.GeometricBackground
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    private val viewModel: MediaPlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            PlaybackManager.release()
        }
    }
}

@Composable
fun MainAppContent(viewModel: MediaPlayerViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val selectedVideo by viewModel.selectedVideo.collectAsState()
    val isAudioPlayerExpanded by viewModel.isAudioPlayerExpanded.collectAsState()
    val currentItem by PlaybackManager.currentMediaItem.collectAsState()

    // If video player is active, show it full-bleed
    if (selectedVideo != null) {
        VideoPlayerScreen(
            video = selectedVideo!!,
            onClose = { viewModel.closeVideoPlayer() }
        )
    } else {
        GeometricBackground(
            modifier = Modifier.fillMaxSize()
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.statusBars,
                bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    // Mini player shown if audio is loaded or video audio is continuing
                    if (currentItem != null) {
                        BottomMiniPlayer(viewModel = viewModel)
                    }

                    // Bottom Navigation Bar
                    NavigationBar(
                        containerColor = DarkSurfaceElevated,
                        contentColor = TextPrimary,
                        tonalElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(0.dp))
                            .testTag("main_bottom_nav")
                    ) {
                        // Videos Tab
                        NavigationBarItem(
                            selected = currentTab == AppTab.VIDEOS,
                            onClick = { viewModel.setTab(AppTab.VIDEOS) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = "Videos"
                                )
                            },
                            label = {
                                Text(
                                    text = "فيديوهات",
                                    fontWeight = if (currentTab == AppTab.VIDEOS) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = NeonCyan,
                                indicatorColor = NeonCyan,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_videos_tab")
                        )

                        // Music Tab
                        NavigationBarItem(
                            selected = currentTab == AppTab.MUSIC,
                            onClick = { viewModel.setTab(AppTab.MUSIC) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Music"
                                )
                            },
                            label = {
                                Text(
                                    text = "موسيقى",
                                    fontWeight = if (currentTab == AppTab.MUSIC) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = NeonViolet,
                                indicatorColor = NeonViolet,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_music_tab")
                        )

                        // Playlists Tab
                        NavigationBarItem(
                            selected = currentTab == AppTab.PLAYLISTS,
                            onClick = { viewModel.setTab(AppTab.PLAYLISTS) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.QueueMusic,
                                    contentDescription = "Playlists"
                                )
                            },
                            label = {
                                Text(
                                    text = "قوائم التشغيل",
                                    fontWeight = if (currentTab == AppTab.PLAYLISTS) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = NeonPink,
                                indicatorColor = NeonPink,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_playlists_tab")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    AppTab.VIDEOS -> VideosScreen(viewModel = viewModel)
                    AppTab.MUSIC -> MusicScreen(viewModel = viewModel)
                    AppTab.PLAYLISTS -> PlaylistsScreen(viewModel = viewModel)
                }
            }
        }
    }
}

// Full Screen Audio Player Dialog
if (isAudioPlayerExpanded) {
    FullScreenAudioPlayer(
        viewModel = viewModel,
        onDismiss = { viewModel.expandAudioPlayer(false) }
    )
}
}
