package com.teju.classnow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.teju.classnow.ui.home.HomeScreen
import com.teju.classnow.ui.theme.ClassNowTheme
import com.teju.classnow.ui.timetable.TimetableScreen
import com.teju.classnow.ui.viewmodel.TimetableViewModel

import com.teju.classnow.ui.settings.AboutScreen
import com.teju.classnow.ui.settings.DeveloperProfileScreen
import com.teju.classnow.ui.settings.HelpDeskScreen
import com.teju.classnow.ui.settings.PrivacyPolicyScreen
import com.teju.classnow.ui.settings.SettingsScreen

class MainActivity : ComponentActivity() {
    private var navigateToState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            val viewModel: TimetableViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()

            ClassNowTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                var openPermissionsFromWidget by remember { mutableStateOf(false) }

                LaunchedEffect(navigateToState) {
                    navigateToState?.let { destination ->
                        if (destination == "permissions") {
                            openPermissionsFromWidget = true
                        } else {
                            navController.navigate(destination) {
                                launchSingleTop = true
                            }
                        }
                        navigateToState = null
                    }
                }

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            onManageTimetable = { navController.navigate("timetable") },
                            onOpenSettings = { navController.navigate("settings") },
                            initialShowPermissions = openPermissionsFromWidget,
                            onPermissionsHandled = { openPermissionsFromWidget = false }
                        )
                    }
                    composable("timetable") {
                        TimetableScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onNavigatePrivacyPolicy = { navController.navigate("privacy_policy") },
                            onNavigateHelpDesk = { navController.navigate("help_desk") },
                            onNavigateDeveloperProfile = { navController.navigate("developer_profile") },
                            onNavigateAbout = { navController.navigate("about_app") },
                            onNavigateManageTimetable = { navController.navigate("timetable") }
                        )
                    }
                    composable("privacy_policy") {
                        PrivacyPolicyScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("help_desk") {
                        HelpDeskScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("developer_profile") {
                        DeveloperProfileScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("about_app") {
                        AboutScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val dest = intent?.getStringExtra("navigate_to")
        if (dest != null) {
            navigateToState = dest
        }
    }
}
