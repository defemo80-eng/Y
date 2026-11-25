package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.ai.AiRepository
import com.example.data.ai.FirebaseAiRepository
import com.example.data.ai.configureAppCheck
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.ChatDepRepository
import com.example.ui.navigation.ChatDepRoutes
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ImageStudioScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ChatDepTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // PASO 1 obligatorio: configurar Firebase App Check antes de UI, ViewModels u operaciones Firebase
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        val db = AppDatabase.getInstance(applicationContext)
        val prefsRepo = UserPreferencesRepository(applicationContext)
        val repository = ChatDepRepository(db.chatDepDao(), prefsRepo)
        val aiRepository: AiRepository = FirebaseAiRepository(applicationContext)

        setContent {
            val preferences by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = UserPreferences()
            )
            val isDark = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !isDark
                    controller.isAppearanceLightNavigationBars = !isDark
                }
            }

            ChatDepTheme(themeMode = preferences.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ChatDepApp(
                        preferences = preferences,
                        repository = repository,
                        preferencesRepository = prefsRepo,
                        aiRepository = aiRepository
                    )
                }
            }
        }
    }
}

@Composable
fun ChatDepApp(
    preferences: UserPreferences,
    repository: ChatDepRepository,
    preferencesRepository: UserPreferencesRepository,
    aiRepository: AiRepository
) {
    val activeUserId = preferences.activeUserId
    val scope = rememberCoroutineScope()

    if (activeUserId.isNullOrBlank()) {
        val authViewModel: AuthViewModel = viewModel(
            factory = AuthViewModel.provideFactory(repository)
        )
        AuthScreen(viewModel = authViewModel)
    } else {
        key(activeUserId) {
            val navController = rememberNavController()
            val chatViewModel: ChatViewModel = viewModel(
                key = "chat_$activeUserId",
                factory = ChatViewModel.provideFactory(activeUserId, repository, aiRepository)
            )
            val settingsViewModel: SettingsViewModel = viewModel(
                key = "settings_$activeUserId",
                factory = SettingsViewModel.provideFactory(activeUserId, repository, preferencesRepository)
            )
            val currentUser by remember(activeUserId) {
                repository.observeUser(activeUserId)
            }.collectAsStateWithLifecycle(initialValue = null)

            NavHost(
                navController = navController,
                startDestination = ChatDepRoutes.ROUTE_CHAT
            ) {
                composable(ChatDepRoutes.ROUTE_CHAT) {
                    ChatScreen(
                        user = currentUser,
                        preferences = preferences,
                        chatViewModel = chatViewModel,
                        onToggleTheme = { nextMode ->
                            scope.launch { preferencesRepository.setThemeMode(nextMode) }
                        },
                        onOpenImageStudio = {
                            navController.navigate(ChatDepRoutes.ROUTE_IMAGE_STUDIO) {
                                launchSingleTop = true
                            }
                        },
                        onOpenSettings = {
                            navController.navigate(ChatDepRoutes.ROUTE_SETTINGS) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(ChatDepRoutes.ROUTE_IMAGE_STUDIO) {
                    ImageStudioScreen(
                        chatViewModel = chatViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ChatDepRoutes.ROUTE_SETTINGS) {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
