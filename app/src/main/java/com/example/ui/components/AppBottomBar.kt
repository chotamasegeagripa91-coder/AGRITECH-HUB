package com.example.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.utils.AppStrings
import com.example.ui.viewmodel.AppScreen

sealed class BottomNavItem(
    val screen: AppScreen,
    val titleKey: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Dashboard : BottomNavItem(
        screen = AppScreen.DASHBOARD,
        titleKey = "nav_dashboard",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard
    )

    data object Materials : BottomNavItem(
        screen = AppScreen.MATERIALS,
        titleKey = "nav_materials",
        selectedIcon = Icons.Filled.Inventory,
        unselectedIcon = Icons.Outlined.Inventory2
    )

    data object Customers : BottomNavItem(
        screen = AppScreen.CUSTOMERS,
        titleKey = "nav_customers",
        selectedIcon = Icons.Filled.People,
        unselectedIcon = Icons.Outlined.People
    )

    data object NewQuote : BottomNavItem(
        screen = AppScreen.NEW_QUOTE,
        titleKey = "nav_new_quote",
        selectedIcon = Icons.Filled.PostAdd,
        unselectedIcon = Icons.Outlined.PostAdd
    )

    data object QuotesHistory : BottomNavItem(
        screen = AppScreen.QUOTES_HISTORY,
        titleKey = "nav_quotes",
        selectedIcon = Icons.Filled.ReceiptLong,
        unselectedIcon = Icons.Outlined.ReceiptLong
    )

    data object Settings : BottomNavItem(
        screen = AppScreen.SETTINGS,
        titleKey = "nav_settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

@Composable
fun AppBottomBar(
    currentScreen: AppScreen,
    language: String,
    onSelectScreen: (AppScreen) -> Unit
) {
    val items = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.Materials,
        BottomNavItem.Customers,
        BottomNavItem.NewQuote,
        BottomNavItem.QuotesHistory,
        BottomNavItem.Settings
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        items.forEach { item ->
            val isSelected = currentScreen == item.screen
            val title = AppStrings.t(item.titleKey, language)

            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelectScreen(item.screen) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = title,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = title,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag("nav_item_${item.screen.name.lowercase()}")
            )
        }
    }
}
