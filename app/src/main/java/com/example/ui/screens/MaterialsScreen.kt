package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.MaterialEntity
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.RoseError
import com.example.ui.utils.AppStrings
import com.example.ui.utils.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialsScreen(
    materials: List<MaterialEntity>,
    language: String,
    onAddMaterial: () -> Unit,
    onEditMaterial: (MaterialEntity) -> Unit,
    onDeleteMaterial: (MaterialEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val allCategoryLabel = if (language == "sw") "Zote" else "All"
    var selectedCategory by remember { mutableStateOf(allCategoryLabel) }
    var itemToDelete by remember { mutableStateOf<MaterialEntity?>(null) }

    LaunchedEffect(language) {
        if (selectedCategory == "Zote" || selectedCategory == "All") {
            selectedCategory = allCategoryLabel
        }
    }

    val categories = remember(materials, language) {
        val canonicals = listOf("Electrical", "Plumbing", "Construction")
        val otherCategories = materials.map { com.example.ui.utils.MaterialCategoryUtils.getCanonicalCategory(it.category, it.name) }
            .distinct()
            .filter { it !in canonicals && it.isNotBlank() }
        listOf(allCategoryLabel, "Electrical", "Plumbing", "Construction") + otherCategories
    }

    val filteredMaterials = remember(materials, searchQuery, selectedCategory, language) {
        materials.filter { mat ->
            val matchCat = if (selectedCategory == allCategoryLabel || selectedCategory == "All" || selectedCategory == "Zote") {
                true
            } else {
                com.example.ui.utils.MaterialCategoryUtils.matchesCategory(mat, selectedCategory)
            }
            val matchQuery = searchQuery.isBlank() ||
                    mat.name.contains(searchQuery, ignoreCase = true) ||
                    mat.category.contains(searchQuery, ignoreCase = true) ||
                    com.example.ui.utils.MaterialCategoryUtils.getCanonicalCategory(mat.category, mat.name).contains(searchQuery, ignoreCase = true)
            matchQuery && matchCat
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddMaterial,
                containerColor = AmberPrimary,
                contentColor = androidx.compose.ui.graphics.Color.Black,
                modifier = Modifier.testTag("add_material_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Material")
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (language == "sw") "Ongeza Kifaa" else "Add Item",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(if (language == "sw") "Tafuta kifaa kwa jina..." else "Search material by name...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("materials_search_input"),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    val selected = selectedCategory == cat
                    FilterChip(
                        selected = selected,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Summary text
            Text(
                text = if (language == "sw") "Jumla ya vifaa: ${filteredMaterials.size}" else "Total items: ${filteredMaterials.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            // List of Materials
            if (filteredMaterials.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (language == "sw") "Hakuna kifaa kilichopatikana." else "No materials found.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredMaterials, key = { it.id }) { item ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditMaterial(item) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val catName = com.example.ui.utils.MaterialCategoryUtils.getCanonicalCategory(item.category, item.name)
                                        Surface(
                                            color = when (catName) {
                                                "Electrical" -> AmberPrimary.copy(alpha = 0.15f)
                                                "Plumbing" -> androidx.compose.ui.graphics.Color(0xFF0288D1).copy(alpha = 0.15f)
                                                "Construction" -> androidx.compose.ui.graphics.Color(0xFFE65100).copy(alpha = 0.15f)
                                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            },
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = catName,
                                                color = when (catName) {
                                                    "Electrical" -> AmberPrimary
                                                    "Plumbing" -> androidx.compose.ui.graphics.Color(0xFF0288D1)
                                                    "Construction" -> androidx.compose.ui.graphics.Color(0xFFE65100)
                                                    else -> MaterialTheme.colorScheme.primary
                                                },
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Kipimo: ${item.unit}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = Formatters.formatCurrency(item.price),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                        Text(
                                            text = "kwa ${item.unit}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    IconButton(
                                        onClick = { onEditMaterial(item) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                    }

                                    IconButton(
                                        onClick = { itemToDelete = item },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete", tint = RoseError, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (itemToDelete != null) {
        val mat = itemToDelete ?: return
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(if (language == "sw") "Futa Kifaa?" else "Delete Material?") },
            text = { Text("Je, una uhakika unataka kufuta ${mat.name} kutoka kwenye orodha?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        itemToDelete = null
                        onDeleteMaterial(mat)
                    }
                ) {
                    Text(AppStrings.t("btn_delete", language), color = RoseError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(AppStrings.t("btn_cancel", language))
                }
            }
        )
    }
}
