package com.example.iptv.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.iptv.viewmodel.MainViewModel

@Composable
fun AppNav(vm: MainViewModel) {
    val nav = rememberNavController()

    // Tự mở kênh cuối khi khởi động (nếu bật trong Cài đặt)
    LaunchedEffect(Unit) {
        vm.resumeEvents.collect { nav.navigate("player") }
    }

    NavHost(
        navController = nav,
        startDestination = "home",
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable("home") {
            HomeScreen(
                vm,
                onPlay = { nav.navigate("player") },
                onSearch = { nav.navigate("search") },
                onSettings = { nav.navigate("settings") }
            )
        }
        composable("player") { PlayerScreen(vm, onBack = { nav.popBackStack() }) }
        composable("search") {
            SearchScreen(vm, onPlay = { nav.navigate("player") })
        }
        composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
    }
}
