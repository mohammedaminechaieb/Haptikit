package com.haptikit.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.haptikit.app.data.HaptiRepository
import com.haptikit.app.ui.AssignmentScreen
import com.haptikit.app.ui.PatternEditorScreen
import com.haptikit.app.ui.PatternListScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repository = HaptiRepository(applicationContext)

        setContent {
            val dark = isSystemInDarkTheme()
            val context = LocalContext.current
            val colors = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                dark -> darkColorScheme(primary = Color(0xFFFFB4A9), secondary = Color(0xFFE7BDB6))
                else -> lightColorScheme(primary = Color(0xFFB3261E), secondary = Color(0xFF775651))
            }
            MaterialTheme(colorScheme = colors) {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "patterns") {
                    composable("patterns") {
                        PatternListScreen(
                            repository = repository,
                            onCreateNew = { navController.navigate("editor/-1") },
                            onEditPattern = { id -> navController.navigate("editor/$id") },
                            onGoToAssignments = { navController.navigate("assignments") }
                        )
                    }
                    composable("editor/{patternId}") { backStackEntry ->
                        val patternId = backStackEntry.arguments?.getString("patternId")?.toLongOrNull() ?: -1L
                        PatternEditorScreen(
                            repository = repository,
                            patternId = patternId,
                            onDone = { navController.popBackStack() }
                        )
                    }
                    composable("assignments") {
                        AssignmentScreen(repository = repository, onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
