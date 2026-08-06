package com.example.healthheatv2

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.healthheatv2.data.AppDatabase
import com.example.healthheatv2.data.ProductRepository
import com.example.healthheatv2.network.RetrofitClient
import com.example.healthheatv2.ui.screens.*
import com.example.healthheatv2.ui.theme.AppTheme
import com.example.healthheatv2.ui.theme.LocalAppColors
import com.example.healthheatv2.ui.viewmodel.AuthViewModel
import com.example.healthheatv2.ui.viewmodel.ScannerViewModel
import com.example.healthheatv2.ui.viewmodel.ThemeViewModel

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object SearchHub : Screen("search_hub")
    object Scanner : Screen("scanner")
    object ManualSearch : Screen("manual_search")
    object Product : Screen("product")
    object History : Screen("history")
    object DetailedNutrition : Screen("detailed_nutrition")
}

data class BottomNavItem(
    val title: String,
    val route: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector
)

@Composable
fun App(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val themeViewModel: ThemeViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as android.app.Application
        )
    )
    val isDark by themeViewModel.isDark

    AppTheme(isDark = isDark) {
        val colors = LocalAppColors.current

        val database = AppDatabase.getDatabase(context)
        val repository = ProductRepository(
            productDao = database.productDao(),
            apiService = RetrofitClient.apiService
        )

        val scannerViewModel: ScannerViewModel = viewModel(
            factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ScannerViewModel(repository) as T
                }
            }
        )
        val authViewModel: AuthViewModel = viewModel()

        val bottomNavItems = listOf(
            BottomNavItem("Home", Screen.SearchHub.route, Icons.Filled.Home, Icons.Outlined.Home),
            BottomNavItem("History", Screen.History.route, Icons.Filled.History, Icons.Outlined.History)
        )

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        val showBottomBar = currentRoute in listOf(
            Screen.SearchHub.route,
            Screen.History.route
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.SearchHub.route,
                modifier = modifier.fillMaxSize()
            ) {
                composable(Screen.Auth.route) {
                    AuthScreen(
                        viewModel = authViewModel,
                        themeViewModel = themeViewModel,
                        onAuthSuccess = {
                            navController.navigate(Screen.SearchHub.route) {
                                popUpTo(Screen.Auth.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.SearchHub.route) {
                    SearchHubScreen(
                        viewModel = scannerViewModel,
                        authViewModel = authViewModel,
                        themeViewModel = themeViewModel,
                        onScanClick = {
                            scannerViewModel.resetState()
                            navController.navigate(Screen.Scanner.route)
                        },
                        onManualEntryClick = {
                            scannerViewModel.resetState()
                            navController.navigate(Screen.ManualSearch.route)
                        },
                        onViewAllHistoryClick = { navController.navigate(Screen.History.route) },
                        onProductSelected = { navController.navigate(Screen.Product.route) },
                        onLogout = {
                            navController.navigate(Screen.Auth.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.ManualSearch.route) {
                    ManualSearchScreen(
                        viewModel = scannerViewModel,
                        onBackClick = { navController.popBackStack() },
                        onSearchSuccess = {
                            navController.navigate(Screen.Product.route) {
                                popUpTo(Screen.ManualSearch.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Scanner.route) {
                    BarcodeScannerScreen(
                        viewModel = scannerViewModel,
                        onScanSuccess = {
                            navController.navigate(Screen.Product.route) {
                                popUpTo(Screen.Scanner.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Product.route) {
                    ProductScreen(
                        viewModel = scannerViewModel,
                        authViewModel = authViewModel,
                        onScanAnother = {
                            scannerViewModel.resetState()
                            navController.popBackStack(Screen.SearchHub.route, inclusive = false)
                        },
                        onViewDetails = {
                            navController.navigate(Screen.DetailedNutrition.route)
                        },
                        onLogout = {
                            navController.navigate(Screen.Auth.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.History.route) {
                    HistoryScreen(
                        viewModel = scannerViewModel,
                        authViewModel = authViewModel,
                        themeViewModel = themeViewModel,
                        onBackClick = { navController.popBackStack() },
                        onProductSelected = {
                            navController.navigate(Screen.Product.route)
                        },
                        onLogout = {
                            navController.navigate(Screen.Auth.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.DetailedNutrition.route) {
                    DetailedNutritionScreen(
                        viewModel = scannerViewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }

            // Bottom nav bar floating above content
            if (showBottomBar) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 32.dp, vertical = 24.dp)
                ) {
                    AppBottomNav(
                        items = bottomNavItems,
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppBottomNav(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val colors = LocalAppColors.current

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(32.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(32.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val isSelected = currentRoute == item.route
            val iconScale by animateFloatAsState(
                targetValue = if (isSelected) 1.1f else 1f,
                animationSpec = tween(250)
            )
            val bgAlpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = tween(250)
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { onNavigate(item.route) }
                    .background(
                        if (isSelected) colors.accentGreen else Color.Transparent,
                        RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = if (isSelected) 20.dp else 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) item.activeIcon else item.inactiveIcon,
                        contentDescription = item.title,
                        tint = if (isSelected) Color.White else colors.textSecondary,
                        modifier = Modifier
                            .size(22.dp)
                            .scale(iconScale)
                    )
                    if (isSelected) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}