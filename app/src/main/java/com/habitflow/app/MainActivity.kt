package com.habitflow.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.habitflow.app.notify.HabitWidget
import com.habitflow.app.notify.Scheduler
import com.habitflow.app.ui.CalendarScreen
import com.habitflow.app.ui.CategoriesScreen
import com.habitflow.app.ui.EditorScreen
import com.habitflow.app.ui.HabitTheme
import com.habitflow.app.ui.HomeScreen
import com.habitflow.app.ui.SettingsScreen
import com.habitflow.app.ui.StatsScreen
import com.habitflow.app.ui.isDarkNow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Scheduler.ensureChannel(this)
        setContent {
            val vm: AppVM = viewModel()
            val prefs = vm.prefs
            HabitTheme(prefs.theme, prefs.palette) {
                val dark = isDarkNow(prefs.theme)
                DisposableEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose { }
                }
                App(vm)
            }
        }
    }
}

private val Tabs = listOf("🏠" to "Home", "📅" to "Calendar", "📊" to "Stats", "🗂" to "Categories", "⚙️" to "Settings")

@Composable
private fun App(vm: AppVM) {
    val ctx = LocalContext.current
    val prefs = vm.prefs
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorId by rememberSaveable { mutableLongStateOf(0L) }

    fun openEditor(id: Long) { editorId = id; editorOpen = true }

    // Notification permission (Android 13+)
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    // Toasts from the view-model
    LaunchedEffect(Unit) { vm.toast.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() } }
    // New day / widget refresh when returning to the app
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshToday(); HabitWidget.refresh(ctx) }

    val bg by produceState<ImageBitmap?>(null, prefs.bgPath) {
        val path = prefs.bgPath
        value = if (path.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, o)
                var s = 1
                while (o.outWidth / s > 1400 || o.outHeight / s > 1400) s *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
            }.getOrNull()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        bg?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.82f)))
        }
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
                    Tabs.forEachIndexed { i, (icon, label) ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Text(icon, fontSize = 20.sp, modifier = Modifier.alpha(if (tab == i) 1f else 0.6f)) },
                            label = { Text(label, fontSize = 11.sp, maxLines = 1) }
                        )
                    }
                }
            },
            floatingActionButton = {
                if (tab == 0 || tab == 3) ExtendedFloatingActionButton(
                    onClick = { openEditor(0) },
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White,
                    text = { Text("New Habit") }, icon = { Text("＋", fontSize = 20.sp) }
                )
            }
        ) { inner ->
            val pad = PaddingValues(top = top, bottom = inner.calculateBottomPadding())
            when (tab) {
                0 -> HomeScreen(vm, pad) { openEditor(it) }
                1 -> CalendarScreen(vm, pad) { openEditor(it) }
                2 -> StatsScreen(vm, pad)
                3 -> CategoriesScreen(vm, pad) { openEditor(it) }
                else -> SettingsScreen(vm, pad)
            }
        }
        AnimatedVisibility(
            visible = editorOpen,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut()
        ) {
            EditorScreen(vm, editorId) { editorOpen = false }
        }
    }
}
