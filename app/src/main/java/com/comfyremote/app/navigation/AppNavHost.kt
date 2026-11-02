package com.comfyremote.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.comfyremote.app.data.repository.DraftTransfer
import com.comfyremote.app.feature.ai.AiSettingsScreen
import com.comfyremote.app.feature.ai.AiSettingsViewModel
import com.comfyremote.app.feature.connect.ConnectScreen
import com.comfyremote.app.feature.connect.ConnectViewModel
import com.comfyremote.app.feature.connect.ServerManagerScreen
import com.comfyremote.app.feature.generate.GenerateScreen
import com.comfyremote.app.feature.generate.GenerateViewModel
import com.comfyremote.app.feature.history.HistoryScreen
import com.comfyremote.app.feature.history.HistoryViewModel
import com.comfyremote.app.feature.settings.AppSettingsScreen
import com.comfyremote.app.feature.settings.AppSettingsViewModel
import com.comfyremote.app.feature.settings.CloudSettingsScreen
import com.comfyremote.app.feature.settings.CloudSettingsViewModel
import com.comfyremote.app.feature.settings.SettingsScreen
import com.comfyremote.app.feature.storyboard.StoryboardDetailScreen
import com.comfyremote.app.feature.storyboard.StoryboardScreen
import com.comfyremote.app.feature.storyboard.StoryboardViewModel
import com.comfyremote.app.feature.tasks.TaskDetailScreen
import com.comfyremote.app.feature.tasks.TasksScreen
import com.comfyremote.app.feature.tasks.TasksViewModel
import com.comfyremote.app.feature.update.UpdateScreen
import com.comfyremote.app.feature.update.UpdateViewModel
import com.comfyremote.app.feature.workflows.WorkflowsScreen
import com.comfyremote.app.feature.workflows.WorkflowsViewModel
import com.comfyremote.app.ui.L10n
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottom = route in setOf(Routes.CREATE, Routes.TASKS, Routes.HISTORY, Routes.STORYBOARD)

    fun openTransfer(transfer: DraftTransfer) {
        scope.launch {
            val source = transfer.serverUrl?.trim()?.removeSuffix("/").orEmpty()
            val ready = if (source.isBlank() || source == container.serverSession.url) {
                true
            } else {
                runCatching {
                    container.serverSession.connect(source)
                    container.serverRepository.remember(source)
                    container.serverRepository.saveLastUrl(source)
                }.isSuccess
            }
            if (ready) {
                navController.navigate(Routes.generate(transfer.workflowPath, transfer.workflowName))
            } else {
                container.draftTransferRepository.clear()
                navController.navigate(Routes.SERVERS)
            }
        }
    }

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
                    onCloudOnly = { navigateToCreate(navController) },
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
                HistoryScreen(vm, onReuse = ::openTransfer)
            }
            composable(Routes.STORYBOARD) {
                val vm: StoryboardViewModel = viewModel(factory = factory)
                StoryboardScreen(
                    viewModel = vm,
                    onOpenProject = { projectId -> navController.navigate(Routes.storyboard(projectId)) },
                )
            }
            composable(
                Routes.STORYBOARD_DETAIL_PATTERN,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { entry ->
                val vm: StoryboardViewModel = viewModel(factory = factory)
                StoryboardDetailScreen(
                    viewModel = vm,
                    projectId = entry.arguments?.getString("projectId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenShot = { path, name -> navController.navigate(Routes.generate(path, name)) },
                )
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
                    onOpenCloudVideo = { path, name ->
                        navController.navigate(Routes.generate(path, name))
                    },
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
                    onRetry = { newTaskId -> navController.navigate(Routes.task(newTaskId)) { launchSingleTop = true } },
                    onReuse = ::openTransfer,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    version = container.updateRepository.currentVersion(),
                    onBack = { navController.popBackStack() },
                    onApp = { navController.navigate(Routes.APP_SETTINGS) },
                    onServers = { navController.navigate(Routes.SERVERS) },
                    onAi = { navController.navigate(Routes.AI_SETTINGS) },
                    onCloud = { navController.navigate(Routes.CLOUD_SETTINGS) },
                    onUpdate = { navController.navigate(Routes.UPDATE) },
                )
            }
            composable(Routes.APP_SETTINGS) {
                val vm: AppSettingsViewModel = viewModel(factory = factory)
                AppSettingsScreen(vm) { navController.popBackStack() }
            }
            composable(Routes.CLOUD_SETTINGS) {
                val vm: CloudSettingsViewModel = viewModel(factory = factory)
                CloudSettingsScreen(vm) { navController.popBackStack() }
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
            label = { Text(L10n.t("结果", "Results")) },
        )
        NavigationBarItem(
            selected = current == Routes.STORYBOARD,
            onClick = { navigateTopLevel(navController, Routes.STORYBOARD) },
            icon = { Icon(Icons.Default.PlayArrow, null) },
            label = { Text(L10n.t("分镜", "Board")) },
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
