package com.haptikit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.haptikit.app.data.HaptiRepository
import com.haptikit.app.ui.AssignmentScreen
import com.haptikit.app.ui.PatternEditorScreen
import com.haptikit.app.ui.PatternListScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = HaptiRepository(applicationContext)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
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
                            AssignmentScreen(
                                repository = repository,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
