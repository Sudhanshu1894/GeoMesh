package com.example.resilentnet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.resilentnet.ui.screens.ChatScreen
import com.example.resilentnet.ui.screens.DashboardScreen
import com.example.resilentnet.ui.screens.HazardMapScreen
import com.example.resilentnet.ui.screens.PeersScreen
import com.example.resilentnet.ui.theme.ResilentNetTheme
import com.example.resilentnet.ui.theme.ResilientCardBg
import com.example.resilentnet.ui.theme.ResilientCardBorder
import com.example.resilentnet.ui.theme.ResilientMainBg
import com.example.resilentnet.ui.theme.ResilientPrimaryGreen
import com.example.resilentnet.ui.theme.ResilientSoftGreen
import com.example.resilentnet.ui.theme.ResilientTextSecondary
import com.example.resilentnet.ui.viewmodel.MeshViewModel

enum class MainTab(val title: String, val icon: String) {
    HOME("Home", "⬡"),
    PEERS("Peers", "👥"),
    CHAT("Chat", "💬"),
    HAZARDS("Hazards", "⚠")
}

class MainActivity : ComponentActivity() {

    private val viewModel: MeshViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val notificationsGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: true
        if (!notificationsGranted) {
            Toast.makeText(this, "Notification permission is recommended for background networking.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestAppPermissions()

        setContent {
            ResilentNetTheme {
                MainAppScaffold(viewModel = viewModel)
            }
        }
    }

    private fun requestAppPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@Composable
fun MainAppScaffold(viewModel: MeshViewModel) {
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }

    // System BackHandler: if on secondary tab, return to Home tab
    BackHandler(enabled = selectedTab != MainTab.HOME) {
        selectedTab = MainTab.HOME
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ResilientMainBg,
        bottomBar = {
            NavigationBar(
                containerColor = ResilientCardBg,
                tonalElevation = 0.dp,
                modifier = Modifier.background(ResilientCardBg)
            ) {
                MainTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Text(
                                text = tab.icon,
                                fontSize = 18.sp
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ResilientPrimaryGreen,
                            selectedTextColor = ResilientPrimaryGreen,
                            indicatorColor = ResilientSoftGreen,
                            unselectedIconColor = ResilientTextSecondary,
                            unselectedTextColor = ResilientTextSecondary
                        )
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
            when (selectedTab) {
                MainTab.HOME -> DashboardScreen(viewModel = viewModel)
                MainTab.PEERS -> PeersScreen(
                    viewModel = viewModel,
                    onNavigateToChat = { selectedTab = MainTab.CHAT }
                )
                MainTab.CHAT -> ChatScreen(viewModel = viewModel)
                MainTab.HAZARDS -> HazardMapScreen(viewModel = viewModel)
            }
        }
    }
}