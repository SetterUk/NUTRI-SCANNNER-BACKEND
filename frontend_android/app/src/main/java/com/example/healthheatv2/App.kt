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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.example.healthheatv2.data.ProductRepository
import com.example.healthheatv2.network.RetrofitClient
import com.example.healthheatv2.ui.screens.*
import com.example.healthheatv2.ui.theme.AppTheme
import com.example.healthheatv2.ui.theme.LocalAppColors
import com.example.healthheatv2.ui.viewmodel.AuthViewModel
import com.example.healthheatv2.ui.viewmodel.ScannerViewModel
import com.example.healthheatv2.ui.viewmodel.ThemeViewModel

import com.example.healthheatv2.network.UserProfileResponse
import com.example.healthheatv2.services.calculateBMI
import com.example.healthheatv2.services.calculateBMR
import com.example.healthheatv2.services.calculateTDEE
import com.example.healthheatv2.services.calculateTargets

fun mapNetworkProfileToLocal(profile: UserProfileResponse?): com.example.healthheatv2.data.UserProfile {
    val age = profile?.age ?: 25
    val sex = profile?.gender ?: "Other"
    val heightCm = profile?.height?.toFloat() ?: 170f
    val weightKg = profile?.weightKg?.toFloat() ?: 70f
    val activityLevel = profile?.activityLevel ?: "sedentary"
    val primaryGoal = profile?.healthGoals ?: "general_health"
    val dietType = profile?.dietaryPreferences ?: "omnivore"
    
    val isMale = sex.equals("Male", ignoreCase = true) || sex.equals("Prefer not to say", ignoreCase = true)
    
    val bmi = calculateBMI(heightCm, weightKg)
    val bmr = calculateBMR(age, isMale, heightCm, weightKg)
    val tdee = calculateTDEE(bmr, activityLevel)
    val targets = calculateTargets(tdee, primaryGoal, dietType, weightKg)
    
    return com.example.healthheatv2.data.UserProfile(
        age = age,
        sex = sex,
        heightCm = heightCm,
        weightKg = weightKg,
        activityLevel = activityLevel,
        primaryGoal = primaryGoal,
        secondaryGoals = emptyList(),
        dietType = dietType,
        allergies = profile?.allergies ?: emptyList(),
        dietaryRestrictions = emptyList(),
        dislikedFoods = emptyList(),
        preferredCuisines = emptyList(),
        healthTags = profile?.healthTags ?: emptyList(),
        medicalReports = profile?.medicalReports ?: "",
        bmi = bmi,
        bmr = bmr,
        tdee = tdee,
        dailyCalories = targets.dailyCalories,
        dailyProtein = targets.dailyProtein,
        dailyCarbs = targets.dailyCarbs,
        dailyFat = targets.dailyFat,
        dailyFiber = targets.dailyFiber,
        dailyWater = targets.dailyWater,
        dailyIron = 18f,
        dailyCalcium = 1000f,
        dailyZinc = 11f,
        dailyB12 = 2.4f,
        dailyVitaminD = 600f,
        dailyFolate = 400f
    )
}

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object SearchHub : Screen("search_hub")
    object Scanner : Screen("scanner")
    object ManualSearch : Screen("manual_search")
    object Product : Screen("product")
    object FixMyNutrition : Screen("fix_my_nutrition")
    object History : Screen("history")
    object DetailedNutrition : Screen("detailed_nutrition")
    object Profile : Screen("profile")
    object Onboarding : Screen("onboarding")
    object FoodGuide : Screen("food_guide")
    object NutritionistChat : Screen("nutritionist_chat")
    object FoodGuideDetail : Screen("food_guide_detail/{foodId}") {
        fun createRoute(foodId: String) = "food_guide_detail/$foodId"
    }
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

        val userDatabase = com.example.healthheatv2.data.UserDatabase.getDatabase(context)
        val foodDatabase = com.example.healthheatv2.data.FoodDatabaseHelper(context)
        val repository = ProductRepository(
            productDao = userDatabase.productDao(),
            apiService = RetrofitClient.apiService
        )

        // ── Gemma 4 E2B: create once, shared across all screens ──────────────
        val gemmaManager = remember { com.example.healthheatv2.ai.GemmaInferenceManager(context) }
        val nanoCoach = remember(userDatabase) {
            com.example.healthheatv2.ai.NanoNutritionistCoach(
                context = context,
                nutritionDao = userDatabase.nutritionDao(),
                gemmaManager = gemmaManager
            )
        }
        // Cloud AI is default. Only initialize on-device Gemma if user previously enabled local model.
        LaunchedEffect(Unit) {
            if (nanoCoach.useLocalModel.value) {
                gemmaManager.initialize()
            }
        }

        val scannerViewModel: ScannerViewModel = viewModel(
            factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ScannerViewModel(repository) as T
                }
            }
        )
        val authViewModel: AuthViewModel = viewModel()
        val userProfile by authViewModel.userProfile.collectAsState()

        // Simple rule:
        // - Not logged in → Auth screen
        // - Logged in + first time ever → Onboarding (once)
        // - Logged in + has seen onboarding → SearchHub
        val isLoggedIn = authViewModel.getCurrentUser() != null
        val startDestination = when {
            !isLoggedIn                          -> Screen.Auth.route
            !authViewModel.hasSeenOnboarding()   -> Screen.Onboarding.route
            else                                 -> Screen.SearchHub.route
        }

        val bottomNavItems = listOf(
            BottomNavItem("Home", Screen.SearchHub.route, Icons.Filled.Home, Icons.Outlined.Home),
            BottomNavItem("Purity", Screen.FoodGuide.route, Icons.Filled.Science, Icons.Outlined.Science),
            BottomNavItem("Nutribot", Screen.NutritionistChat.route, Icons.Filled.SmartToy, Icons.Outlined.SmartToy),
            BottomNavItem("History", Screen.History.route, Icons.Filled.History, Icons.Outlined.History)
        )

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        val showBottomBar = currentRoute in listOf(
            Screen.SearchHub.route,
            Screen.FoodGuide.route,
            Screen.NutritionistChat.route,
            Screen.History.route
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = modifier.fillMaxSize()
            ) {
                composable(Screen.Auth.route) {
                    AuthScreen(
                        viewModel = authViewModel,
                        themeViewModel = themeViewModel,
                        onAuthSuccess = {
                            navController.navigate(Screen.Onboarding.route) {
                                popUpTo(Screen.Auth.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Onboarding.route) {
                    OnboardingScreen(
                        authViewModel = authViewModel,
                        onFinish = {
                            navController.navigate(Screen.SearchHub.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.SearchHub.route) {
                    val mappedProfile = mapNetworkProfileToLocal(userProfile)
                    val nutritionEngine = remember(mappedProfile) {
                        com.example.healthheatv2.services.NutritionEngine(userDatabase, mappedProfile)
                    }

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
                        onProfileClick = { navController.navigate(Screen.Profile.route) },
                        onLogout = {
                            navController.navigate(Screen.Auth.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onAskAIClick = { navController.navigate(Screen.NutritionistChat.route) }
                    )
                }

                composable(Screen.NutritionistChat.route) {
                    val profile = userProfile
                    if (profile != null) {
                        val mappedProfile = mapNetworkProfileToLocal(profile)
                        val nutritionEngine = com.example.healthheatv2.services.NutritionEngine(userDatabase, mappedProfile)
                        val voiceCoach = remember(mappedProfile) {
                            com.example.healthheatv2.ai.VoiceNutritionistCoach(context, nanoCoach)
                        }

                        com.example.healthheatv2.ui.screens.NutritionistChatScreen(
                            voiceCoach = voiceCoach,
                            nanoCoach = nanoCoach,
                            userProfile = mappedProfile,
                            nutritionEngine = nutritionEngine,
                            onFixMyNutritionClick = { gap ->
                                scannerViewModel.selectedGap = gap
                                navController.navigate(Screen.FixMyNutrition.route)
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
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
                    val mappedProfile = mapNetworkProfileToLocal(userProfile)
                    val nutritionEngine = remember(mappedProfile) {
                        com.example.healthheatv2.services.NutritionEngine(userDatabase, mappedProfile)
                    }
                    ProductScreen(
                        viewModel = scannerViewModel,
                        nutritionEngine = nutritionEngine,
                        nanoCoach = nanoCoach,
                        userProfile = mappedProfile,
                        onScanAnother = {
                            scannerViewModel.resetState()
                            navController.popBackStack(Screen.SearchHub.route, inclusive = false)
                        },
                        onViewDetails = {
                            navController.navigate(Screen.DetailedNutrition.route)
                        }
                    )
                }

                composable(Screen.FixMyNutrition.route) {
                    val gap = scannerViewModel.selectedGap
                    if (gap != null) {
                        val mappedProfile = mapNetworkProfileToLocal(userProfile)
                        val recEngine = remember(mappedProfile) {
                            com.example.healthheatv2.services.RecommendationEngine(userDatabase.nutritionDao(), mappedProfile)
                        }
                        FixMyNutritionSc(
                            gap = gap,
                            recommendationEngine = recEngine,
                            nanoCoach = nanoCoach,
                            userProfile = mappedProfile,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
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
                        onProfileClick = {
                            navController.navigate(Screen.Profile.route)
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

                composable(Screen.Profile.route) {
                    LaunchedEffect(Unit) {
                        authViewModel.fetchProfile()
                    }
                    ProfileScreen(
                        authViewModel = authViewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable(Screen.FoodGuide.route) {
                    FoodGuideListScreen(
                        onFoodSelected = { foodId ->
                            navController.navigate(Screen.FoodGuideDetail.createRoute(foodId))
                        },
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable(Screen.FoodGuideDetail.route) { backStackEntry ->
                    val foodId = backStackEntry.arguments?.getString("foodId") ?: ""
                    FoodGuideDetailScreen(
                        foodId = foodId,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }

            // Bottom nav bar floating above content
            val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            if (showBottomBar && !isImeVisible) {
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