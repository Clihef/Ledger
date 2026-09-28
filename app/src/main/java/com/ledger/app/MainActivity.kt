package com.ledger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LedgerApp() }
    }
}

@Composable
fun LedgerApp(vm: LedgerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val current = nav.currentBackStackEntryAsState().value?.destination?.route ?: "home"
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    LedgerTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (current != "confirm") NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf(Triple("home", "首页", R.drawable.ic_nav_home), Triple("bills", "账单", R.drawable.ic_nav_bills), Triple("stats", "统计", R.drawable.ic_nav_stats), Triple("me", "我的", R.drawable.ic_nav_me)).forEach { (route, label, icon) ->
                        NavigationBarItem(selected = current == route, onClick = { nav.navigate(route) { launchSingleTop = true; popUpTo("home") } }, icon = { Icon(painterResource(icon), contentDescription = null) }, label = { Text(label) })
                    }
                }
            }
        ) { padding ->
            NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                composable("home") { HomeScreen(state, vm, onConfirm = { nav.navigate("confirm") }, onBills = { nav.navigate("bills") }) }
                composable("confirm") { ConfirmScreen(vm, onBack = { nav.popBackStack() }) }
                composable("bills") { BillsScreen(state, vm) }
                composable("stats") { StatsScreen(state, onSelection = { category, range -> vm.billCategory.value = category; vm.billRange.value = range; nav.navigate("bills") }) }
                composable("me") { MeScreen(state, vm) }
            }
        }
    }
}
