package com.mohsen.rcclient

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.player.compose.RemoteDocumentPlayer
import androidx.compose.remote.player.core.RemoteDocument
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mohsen.rcclient.ui.theme.RcClientTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repo = RemoteUiRepo(baseUrl = "http://10.0.2.2:8080") // emulator -> host
        val favorites = FavoritesStore(this)

        setContent {
            RcClientTheme {
                AppNav(repo, favorites)
            }
        }
    }
}

@Composable
private fun AppNav(repo: RemoteUiRepo, favorites: FavoritesStore) {
    val nav = rememberNavController()

    NavHost(
        navController = nav,
        startDestination = "users",
        enterTransition = {
            slideInHorizontally(tween(350)) { it / 4 } + fadeIn(tween(350))
        },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(250)) },
        popExitTransition = {
            slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(300))
        },
    ) {
        composable("users") {
            RemoteScreen(
                load = { viewport ->
                    repo.usersList(viewport, SessionState.waves, favorites.ids())
                },
                onNamedAction = { name, value ->
                    when (name) {
                        "open_user" -> (value as? Int)?.let { nav.navigate("user/$it") }
                        "wave" -> SessionState.waves++
                    }
                },
            )
        }

        composable(
            route = "user/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType }),
        ) { backStack ->
            val id = backStack.arguments?.getInt("id") ?: return@composable
            RemoteScreen(
                load = { viewport ->
                    repo.userDetail(id, viewport, favorites.isFavorite(id))
                },
                onNamedAction = { name, _ ->
                    when (name) {
                        "back" -> nav.popBackStack()
                        "toggle_favorite" -> favorites.toggle(id)
                    }
                },
            )
        }
    }
}

private sealed interface RemoteUiState {
    data object Loading : RemoteUiState
    data class Error(val message: String) : RemoteUiState
    data class Ready(val document: CoreDocument) : RemoteUiState
}

@SuppressLint("RestrictedApi")
@Composable
private fun RemoteScreen(
    load: suspend (ViewportDp) -> ByteArray,
    onNamedAction: (name: String, value: Any?) -> Unit,
) {
    var state by remember { mutableStateOf<RemoteUiState>(RemoteUiState.Loading) }
    var retryToken by remember { mutableIntStateOf(0) }

    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    val viewport = ViewportDp(
        width = (windowSize.width / density).roundToInt(),
        height = (windowSize.height / density).roundToInt(),
        density = density,
    )

    LaunchedEffect(retryToken, viewport) {
        state = RemoteUiState.Loading
        state = try {
            RemoteUiState.Ready(RemoteDocument(load(viewport)).document)
        } catch (e: Exception) {
            Log.e("RemoteScreen", "Failed to load remote document", e)
            RemoteUiState.Error(e.message ?: "Couldn't reach the server")
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
            label = "remote-screen",
        ) { current ->
            when (current) {
                is RemoteUiState.Loading -> LoadingState()

                is RemoteUiState.Error -> ErrorState(current.message) { retryToken++ }

                is RemoteUiState.Ready -> {
                    RemoteDocumentPlayer(
                        document = current.document,
                        documentWidth = windowSize.width,
                        documentHeight = windowSize.height,
                        modifier = Modifier.fillMaxSize(),
                        onNamedAction = { name, value, _ -> onNamedAction(name, value) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    val pulse = rememberInfiniteTransition(label = "loading-pulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "loading-alpha",
    )
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 3.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Fetching remote UI…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(alpha),
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📡", fontSize = 42.sp)
        Spacer(Modifier.height(12.dp))
        Text("Can't reach the server", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) {
            Text("Try again")
        }
    }
}
