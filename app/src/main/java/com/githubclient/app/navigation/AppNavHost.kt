package com.githubclient.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.githubclient.app.ui.screens.account.AccountScreen
import com.githubclient.app.ui.screens.actions.ActionRunDetailScreen
import com.githubclient.app.ui.screens.actions.ActionsScreen
import com.githubclient.app.ui.screens.actions.WorkflowDispatchScreen
import com.githubclient.app.ui.screens.automation.AiModifyScreen
import com.githubclient.app.ui.screens.automation.AiSettingsScreen
import com.githubclient.app.ui.screens.automation.AutoCreateRepoScreen
import com.githubclient.app.ui.screens.automation.AutomationScreen
import com.githubclient.app.ui.screens.automation.CreateRepoScreen
import com.githubclient.app.ui.screens.automation.UploadScreen
import com.githubclient.app.ui.screens.automation.ZipUploadScreen
import com.githubclient.app.ui.screens.home.HomeScreen
import com.githubclient.app.ui.screens.issues.IssueDetailScreen
import com.githubclient.app.ui.screens.issues.IssuesScreen
import com.githubclient.app.ui.screens.login.LoginScreen
import com.githubclient.app.ui.screens.prompt.PromptScreen
import com.githubclient.app.ui.screens.pulls.PullRequestDetailScreen
import com.githubclient.app.ui.screens.pulls.PullRequestsScreen
import com.githubclient.app.ui.screens.releases.ReleasesScreen
import com.githubclient.app.ui.screens.repo.RepoScreen
import com.githubclient.app.ui.screens.search.SearchScreen
import com.githubclient.app.ui.screens.settings.TokenSettingsScreen

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val ACCOUNT = "account"
    const val SEARCH = "search"
    const val PROMPT = "prompt"
    const val AUTOMATION = "automation"
    const val CREATE_REPO = "automation/create_repo"
    const val AUTO_CREATE_REPO = "automation/auto_create_repo"
    const val UPLOAD = "automation/upload"
    const val ZIP_UPLOAD = "automation/zip_upload"
    const val AI_SETTINGS = "automation/ai_settings"
    const val AI_MODIFY = "automation/ai_modify"
    const val TOKEN_SETTINGS = "settings/token"
    const val REPO = "repo/{owner}/{name}"
    const val ACTIONS = "repo/{owner}/{name}/actions"
    const val DISPATCH = "repo/{owner}/{name}/actions/dispatch"
    const val ISSUES = "repo/{owner}/{name}/issues"
    const val ISSUE_DETAIL = "repo/{owner}/{name}/issues/{number}"
    const val PULLS = "repo/{owner}/{name}/pulls"
    const val PULL_DETAIL = "repo/{owner}/{name}/pulls/{number}"
    const val RELEASES = "repo/{owner}/{name}/releases"
    const val RUN_DETAIL = "repo/{owner}/{name}/actions/runs/{runId}"
    const val ARG_OWNER = "owner"
    const val ARG_NAME = "name"
    const val ARG_RUN_ID = "runId"
    const val ARG_NUMBER = "number"

    fun repo(owner: String, name: String) = "repo/$owner/$name"
    fun actions(owner: String, name: String) = "repo/$owner/$name/actions"
    fun dispatch(owner: String, name: String) = "repo/$owner/$name/actions/dispatch"
    fun issues(owner: String, name: String) = "repo/$owner/$name/issues"
    fun issueDetail(owner: String, name: String, number: Int) = "repo/$owner/$name/issues/$number"
    fun pulls(owner: String, name: String) = "repo/$owner/$name/pulls"
    fun pullDetail(owner: String, name: String, number: Int) = "repo/$owner/$name/pulls/$number"
    fun releases(owner: String, name: String) = "repo/$owner/$name/releases"
    fun runDetail(owner: String, name: String, runId: Long) = "repo/$owner/$name/actions/runs/$runId"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val sessionViewModel: SessionViewModel = hiltViewModel()
    LaunchedEffect(Unit) {
        sessionViewModel.checkExpiredOnStart()
        sessionViewModel.sessionExpired.collect {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenRepo = { owner, name -> navController.navigate(Routes.repo(owner, name)) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenAutomation = { navController.navigate(Routes.AUTOMATION) },
                onOpenCreateRepo = { navController.navigate(Routes.CREATE_REPO) },
                onOpenAccount = { navController.navigate(Routes.ACCOUNT) }
            )
        }
        composable(Routes.ACCOUNT) {
            AccountScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onOpenTokenSettings = { navController.navigate(Routes.TOKEN_SETTINGS) }
            )
        }
        composable(Routes.TOKEN_SETTINGS) {
            TokenSettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenRepo = { owner, name -> navController.navigate(Routes.repo(owner, name)) }
            )
        }
        composable(Routes.PROMPT) {
            PromptScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.AUTOMATION) {
            AutomationScreen(
                onBack = { navController.popBackStack() },
                onOpenAiSettings = { navController.navigate(Routes.AI_SETTINGS) },
                onOpenAutoCreateRepo = { navController.navigate(Routes.AUTO_CREATE_REPO) },
                onOpenManualCreateRepo = { navController.navigate(Routes.CREATE_REPO) },
                onOpenUpload = { navController.navigate(Routes.UPLOAD) },
                onOpenZipUpload = { navController.navigate(Routes.ZIP_UPLOAD) },
                onOpenAiModify = { navController.navigate(Routes.AI_MODIFY) }
            )
        }
        composable(Routes.CREATE_REPO) {
            CreateRepoScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.AUTO_CREATE_REPO) {
            AutoCreateRepoScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.UPLOAD) {
            UploadScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ZIP_UPLOAD) {
            ZipUploadScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.AI_SETTINGS) {
            AiSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.AI_MODIFY) {
            AiModifyScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.REPO,
            arguments = listOf(
                navArgument(Routes.ARG_OWNER) { type = NavType.StringType },
                navArgument(Routes.ARG_NAME) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString(Routes.ARG_OWNER).orEmpty()
            val name = backStackEntry.arguments?.getString(Routes.ARG_NAME).orEmpty()
            RepoScreen(
                owner = owner,
                name = name,
                onBack = { navController.popBackStack() },
                onOpenActions = { navController.navigate(Routes.actions(owner, name)) },
                onOpenIssues = { navController.navigate(Routes.issues(owner, name)) },
                onOpenPulls = { navController.navigate(Routes.pulls(owner, name)) },
                onOpenReleases = { navController.navigate(Routes.releases(owner, name)) },
                onDeleted = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.ACTIONS,
            arguments = listOf(
                navArgument(Routes.ARG_OWNER) { type = NavType.StringType },
                navArgument(Routes.ARG_NAME) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString(Routes.ARG_OWNER).orEmpty()
            val name = backStackEntry.arguments?.getString(Routes.ARG_NAME).orEmpty()
            ActionsScreen(
                owner = owner,
                name = name,
                onBack = { navController.popBackStack() },
                onOpenRun = { runId -> navController.navigate(Routes.runDetail(owner, name, runId)) },
                onOpenDispatch = { navController.navigate(Routes.dispatch(owner, name)) }
            )
        }
        composable(
            route = Routes.DISPATCH,
            arguments = listOf(
                navArgument(Routes.ARG_OWNER) { type = NavType.StringType },
                navArgument(Routes.ARG_NAME) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString(Routes.ARG_OWNER).orEmpty()
            val name = backStackEntry.arguments?.getString(Routes.ARG_NAME).orEmpty()
            WorkflowDispatchScreen(
                owner = owner,
                name = name,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.ISSUES,
            arguments = listOf(
                navArgument(Routes.ARG_OWNER) { type = NavType.StringType },
                navArgument(Routes.ARG_NAME) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString(Routes.ARG_OWNER).orEmpty()
            val name = backStackEntry.arguments?.getString(Routes.ARG_NAME).orEmpty()
            IssuesScreen(
                owner = owner,
                name = name,
                onBack = { navController.popBackStack() },
                onOpenIssue = { number -> navController.navigate(Routes.issueDetail(owner, name, number)) }
            )
        }
        composable(
            route = Routes.ISSUE_DETAIL,
            arguments = listOf(
                navArgument(Routes.ARG_OWNER) { type = NavType.StringType },
                navArgument(Routes.ARG_NAME) { type = NavType.StringType },
                navArgument(Routes.ARG_NUMBER) { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString(Routes.ARG_OWNER).orEmpty()
            val name = backStackEntry.arguments?.getString(Routes.ARG_NAME).orEmpty()
            val number = backStackEntry.arguments?.getInt(Routes.ARG_NUMBER) ?: 0
            IssueDetailScreen(
                owner = owner,
                name = name,
                number = number,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.PULLS,
            arguments = listOf(
                navArgument(Routes.ARG_OWNER) { type = NavType.StringType },
                navArgument(Routes.ARG_NAME) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString(Routes.ARG_OWNER).orEmpty()
            val name = backStackEntry.arguments?.getString(Routes.ARG_NAME).orEmpty()
            PullRequestsScreen(
                owner = owner,
                name = name,
                onBack = { navController.popBackStack() },
                onOpenPull = { number -> navController.navigate(Routes.pullDetail(owner, name, number)) }
            )
        }
        composable(
            route = Routes.PULL_DETAIL,
            arguments = listOf(
                navArgument(Routes.ARG_OWNER) { type = NavType.StringType },
                navArgument(Routes.ARG_NAME) { type = NavType.StringType },
                navArgument(Routes.ARG_NUMBER) { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString(Routes.ARG_OWNER).orEmpty()
            val name = backStackEntry.arguments?.getString(Routes.ARG_NAME).orEmpty()
            val number = backStackEntry.arguments?.getInt(Routes.ARG_NUMBER) ?: 0
            PullRequestDetailScreen(
                owner = owner,
                name = name,
                number = number,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.RELEASES,
            arguments = listOf(
                navArgument(Routes.ARG_OWNER) { type = NavType.StringType },
                navArgument(Routes.ARG_NAME) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString(Routes.ARG_OWNER).orEmpty()
            val name = backStackEntry.arguments?.getString(Routes.ARG_NAME).orEmpty()
            ReleasesScreen(
                owner = owner,
                name = name,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.RUN_DETAIL,
            arguments = listOf(
                navArgument(Routes.ARG_OWNER) { type = NavType.StringType },
                navArgument(Routes.ARG_NAME) { type = NavType.StringType },
                navArgument(Routes.ARG_RUN_ID) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString(Routes.ARG_OWNER).orEmpty()
            val name = backStackEntry.arguments?.getString(Routes.ARG_NAME).orEmpty()
            val runId = backStackEntry.arguments?.getLong(Routes.ARG_RUN_ID) ?: 0L
            ActionRunDetailScreen(
                owner = owner,
                name = name,
                runId = runId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
