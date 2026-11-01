package com.comfyremote.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.comfyremote.app.AppContainer
import com.comfyremote.app.AppViewModel
import com.comfyremote.app.AppViewModelFactory
import com.comfyremote.app.feature.ai.AiSettingsScreen
import com.comfyremote.app.feature.ai.AiSettingsViewModel
import com.comfyremote.app.feature.connect.ConnectScreen
import com.comfyremote.app.feature.connect.ConnectViewModel
import com.comfyremote.app.feature.connect.ServerManagerScreen
import com.comfyremote.app.feature.generate.GenerateScreen
import com.comfyremote.app.feature.generate.GenerateViewModel
import com.comfyremote.app.feature.history.HistoryScreen
import com.comfyremote.app.feature.history.HistoryViewModel
import com.comfyremote.app.feature.settings.SettingsScreen
import com.comfyremote.app.feature.tasks.TaskDetailScreen
import com.comfyremote.app.feature.tasks.TasksScreen
import com.comfyremote.app.feature.tasks.TasksViewModel
import com.comfyremote.app.feature.update.UpdateScreen
import com.comfyremote.app.feature.update.UpdateViewModel
import com.comfyremote.app.feature.workflows.WorkflowsScreen
import com.comfyremote.app.feature.workflows.WorkflowsViewModel
import com.comfyremote.app.ui.L10n

@Composable
fun ComfyRemoteRoot(container: AppContainer) {
    val factory = AppViewModelFactory(container)
    val appViewModel: AppViewModel = viewModel(factory = factory)
    val appState by appViewModel.state.collectAsState()
    if (!appState.ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    AppNavHost(
        container = container,
        factory = factory,
        startDestination = if (appState.session != null) Routes.CREATE else Routes.CONNECT,
    )
}

@Composable
private fun AppNavHost(
    container: AppContainer,
    factory: AppViewModelFactory,
    startDestination: String,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottom = route in setOf(Routes.CREATE, Routes.TASKS, Routes.HISTORY)

    LaunchedEffect(navController) {
        container.navigationRequests.route.collect { requested ->
            if (requested != null) {
                navController.navigate(requested) { launchSingleTop = true }
                container.navigationRequests.consume(requested)
            }
        }
    }

    Scaffold(bottomBar = { if (showBottom) MainBottomBar(navController, route) }) { outerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(outerPadding),
        ) {
            composable(Routes.CONNECT) {
                val vm: ConnectViewModel = viewModel(factory = factory)
                ConnectScreen(
                    viewModel = vm,
                    onConnected = { navigateToCreate(navController) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.CREATE) {
                val vm: WorkflowsViewModel = viewModel(factory = factory)
                WorkflowsScreen(
                    viewModel = vm,
                    onOpenWorkflow = { navController.navigate(Routes.generate(it.path, it.name)) },
                    onServers = { navController.navigate(Routes.SERVERS) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.TASKS) {
                val vm: TasksViewModel = viewModel(factory = factory)
                TasksScreen(vm) { navController.navigate(Routes.task(it)) }
            }
            composable(Routes.HISTORY) {
                val vm: HistoryViewModel = viewModel(factory = factory)
                HistoryScreen(vm) { transfer -> navController.navigate(Routes.generate(transfer.workflowPath, transfer.workflowName)) }
            }
            composable(
                Routes.GENERATE_PATTERN,
                arguments = listOf(
                    navArgument("path") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType },
                )
            ) { entry ->
                val vm: GenerateViewModel = viewModel(factory = factory)
                GenerateScreen(
                    viewModel = vm,
                    workflowPath = entry.arguments?.getString("path").orEmpty(),
                    workflowName = entry.arguments?.getString("name").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenTask = { navController.navigate(Routes.task(it)) },
                )
            }
            composable(
                Routes.TASK_PATTERN,
                arguments = listOf(navArgument("taskId") { type = NavType.StringType })
            ) { entry ->
                val vm: TasksViewModel = viewModel(factory = factory)
                TaskDetailScreen(
                    viewModel = vm,
                    taskId = entry.arguments?.getString("taskId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onRetry = { navController.navigate(Routes.task(it)) },
                    onReuse = { transfer -> navController.navigate(Routes.generate(transfer.workflowPath, transfer.workflowName)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    version = container.updateRepository.currentVersion(),
                    onBack = { navController.popBackStack() },
                    onServers = { navController.navigate(Routes.SERVERS) },
                    onAi = { navController.navigate(Routes.AI_SETTINGS) },
                    onUpdate = { navController.navigate(Routes.UPDATE) },
                )
            }
            composable(Routes.SERVERS) {
                val vm: ConnectViewModel = viewModel(factory = factory)
                ServerManagerScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onConnected = { navigateToCreate(navController) },
                )
            }
            composable(Routes.AI_SETTINGS) {
                val vm: AiSettingsViewModel = viewModel(factory = factory)
                AiSettingsScreen(vm) { navController.popBackStack() }
            }
            composable(Routes.UPDATE) {
                val vm: UpdateViewModel = viewModel(factory = factory)
                UpdateScreen(vm) { navController.popBackStack() }
            }
        }
    }
}

@Composable
private fun MainBottomBar(navController: NavHostController, current: String?) {
    NavigationBar {
        NavigationBarItem(
            selected = current == Routes.CREATE,
            onClick = { navigateTopLevel(navController, Routes.CREATE) },
            icon = { Icon(Icons.Default.Add, null) },
            label = { Text(L10n.t("创作", "Create")) },
        )
        NavigationBarItem(
            selected = current == Routes.TASKS,
            onClick = { navigateTopLevel(navController, Routes.TASKS) },
            icon = { Icon(Icons.Default.Done, null) },
            label = { Text(L10n.t("任务", "Tasks")) },
        )
        NavigationBarItem(
            selected = current == Routes.HISTORY,
            onClick = { navigateTopLevel(navController, Routes.HISTORY) },
            icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
            label = { Text(L10n.t("历史", "History")) },
        )
    }
}

private fun navigateTopLevel(navController: NavHostController, route: String) {
    if (navController.currentDestination?.route == route) return
    navController.navigate(route) {
        popUpTo(Routes.CREATE) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun navigateToCreate(navController: NavHostController) {
    if (navController.popBackStack(Routes.CREATE, inclusive = false)) return
    navController.navigate(Routes.CREATE) {
        popUpTo(Routes.CONNECT) { inclusive = true }
        launchSingleTop = true
    }
}
