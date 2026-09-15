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
import com.example.ui.viewmodel.AppScreen

sealed class BottomNavItem(
    val screen: AppScreen,
    val titleSw: String,
    val titleEn: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Dashboard : BottomNavItem(
        screen = AppScreen.DASHBOARD,
        titleSw = "Dashibodi",
        titleEn = "Dashboard",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard
    )

    data object Materials : BottomNavItem(
        screen = AppScreen.MATERIALS,
        titleSw = "Vifaa",
        titleEn = "Materials",
        selectedIcon = Icons.Filled.Inventory2,
        unselectedIcon = Icons.Outlined.Inventory2
    )

    data object Customers : BottomNavItem(
        screen = AppScreen.CUSTOMERS,
        titleSw = "Wateja",
        titleEn = "Customers",
        selectedIcon = Icons.Filled.People,
        unselectedIcon = Icons.Outlined.People
    )

    data object NewQuote : BottomNavItem(
        screen = AppScreen.NEW_QUOTE,
        titleSw = "Makadirio",
        titleEn = "New Quote",
        selectedIcon = Icons.Filled.AddCircle,
        unselectedIcon = Icons.Outlined.AddCircle
    )

    data object QuotesHistory : BottomNavItem(
        screen = AppScreen.QUOTES_HISTORY,
        titleSw = "Ankara",
        titleEn = "Invoices",
        selectedIcon = Icons.Filled.ReceiptLong,
        unselectedIcon = Icons.Outlined.ReceiptLong
    )

    data object Settings : BottomNavItem(
        screen = AppScreen.SETTINGS,
        titleSw = "Mipangilio",
        titleEn = "Settings",
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
            val title = if (language == "sw") item.titleSw else item.titleEn

            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelectScreen(item.screen) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = title,
                        modifier = Modifier.size(20.dp)
                    )
                },
                label = {
                    Text(
                        text = title,
                        fontSize = 9.sp,
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
