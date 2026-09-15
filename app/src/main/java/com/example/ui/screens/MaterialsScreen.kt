package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.MaterialEntity
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.RoseError
import com.example.ui.utils.AppStrings
import com.example.ui.utils.ExcelImportReport
import com.example.ui.utils.Formatters
import com.example.ui.utils.MaterialCategoryUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialsScreen(
    materials: List<MaterialEntity>,
    language: String,
    onAddMaterial: () -> Unit,
    onEditMaterial: (MaterialEntity) -> Unit,
    onDeleteMaterial: (MaterialEntity) -> Unit,
    onDeleteAllMaterials: (() -> Unit)? = null,
    onDeleteMaterialsByCategory: ((String) -> Unit)? = null,
    onImportExcel: (suspend (InputStream) -> ExcelImportReport)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    val allCategoryLabel = if (language == "sw") "Zote" else "All"
    var selectedCategory by remember { mutableStateOf(allCategoryLabel) }
    var itemToDelete by remember { mutableStateOf<MaterialEntity?>(null) }
    
    var showDeleteMenu by remember { mutableStateOf(false) }
    var showDeleteAllConfirmDialog by remember { mutableStateOf(false) }
    var categoryToDeleteConfirm by remember { mutableStateOf<String?>(null) }
    
    var isImporting by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<ExcelImportReport?>(null) }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }

    val excelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && onImportExcel != null) {
            isImporting = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        withContext(Dispatchers.Main) {
                            isImporting = false
                            importErrorMessage = if (language == "sw")
                                "Imeshindikana kufungua faili la Excel lililochaguliwa."
                            else
                                "Could not open selected Excel file."
                        }
                        return@launch
                    }

                    val report = onImportExcel(inputStream)
                    withContext(Dispatchers.Main) {
                        isImporting = false
                        if (report.success) {
                            importResult = report
                        } else {
                            importErrorMessage = report.errorMessage ?: if (language == "sw")
                                "Hitilafu imetokea wakati wa kuingiza faili la Excel."
                            else
                                "An error occurred while importing Excel."
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isImporting = false
                        importErrorMessage = e.localizedMessage ?: if (language == "sw")
                            "Hitilafu wakati wa kusoma faili la Excel."
                        else
                            "Error reading Excel file."
                    }
                }
            }
        }
    }

    LaunchedEffect(language) {
        if (selectedCategory == "Zote" || selectedCategory == "All") {
            selectedCategory = allCategoryLabel
        }
    }

    val categories = remember(materials, language) {
        MaterialCategoryUtils.extractDistinctCategories(materials, allCategoryLabel)
    }

    // Map distinct categories to material counts
    val categoryCounts = remember(materials) {
        val map = mutableMapOf<String, Int>()
        map[MaterialCategoryUtils.ELECTRICAL] = materials.count { MaterialCategoryUtils.matchesCategory(it, MaterialCategoryUtils.ELECTRICAL) }
        map[MaterialCategoryUtils.PLUMBING] = materials.count { MaterialCategoryUtils.matchesCategory(it, MaterialCategoryUtils.PLUMBING) }
        map[MaterialCategoryUtils.CONSTRUCTION] = materials.count { MaterialCategoryUtils.matchesCategory(it, MaterialCategoryUtils.CONSTRUCTION) }
        
        val customCats = materials.map { MaterialCategoryUtils.getCanonicalCategory(it.category, it.name) }
            .distinct()
            .filter { it !in MaterialCategoryUtils.DEFAULT_CATEGORIES && it.isNotBlank() }
        
        for (c in customCats) {
            map[c] = materials.count { MaterialCategoryUtils.matchesCategory(it, c) }
        }
        map
    }

    val filteredMaterials = remember(materials, searchQuery, selectedCategory, language) {
        materials.filter { mat ->
            val matchCat = if (selectedCategory == allCategoryLabel || selectedCategory == "All" || selectedCategory == "Zote") {
                true
            } else {
                MaterialCategoryUtils.matchesCategory(mat, selectedCategory)
            }
            val matchQuery = searchQuery.isBlank() ||
                    mat.name.contains(searchQuery, ignoreCase = true) ||
                    mat.category.contains(searchQuery, ignoreCase = true) ||
                    MaterialCategoryUtils.getCanonicalCategory(mat.category, mat.name).contains(searchQuery, ignoreCase = true)
            matchQuery && matchCat
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddMaterial,
                containerColor = AmberPrimary,
                contentColor = Color.Black,
                modifier = Modifier.testTag("add_material_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Material")
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (language == "sw") "Ongeza Kifaa" else "Add Item",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
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
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                placeholder = {
                    Text(
                        if (language == "sw") "Tafuta kifaa kwa jina..." else "Search material by name...",
                        fontSize = 16.sp
                    )
                },
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val selected = selectedCategory == cat
                    FilterChip(
                        selected = selected,
                        onClick = { selectedCategory = cat },
                        label = {
                            Text(
                                text = cat,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Summary text, Delete Options Menu, and Import Excel actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (language == "sw") "Jumla: ${filteredMaterials.size}" else "Total: ${filteredMaterials.size}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Delete Options Dropdown / Button
                    if (materials.isNotEmpty() && (onDeleteAllMaterials != null || onDeleteMaterialsByCategory != null)) {
                        Box {
                            OutlinedButton(
                                onClick = { showDeleteMenu = true },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = RoseError
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, RoseError.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("delete_materials_menu_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = RoseError
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (language == "sw") "Futa Vifaa" else "Delete Options",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RoseError
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = RoseError
                                )
                            }

                            DropdownMenu(
                                expanded = showDeleteMenu,
                                onDismissRequest = { showDeleteMenu = false }
                            ) {
                                // 1. Delete All Materials
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = if (language == "sw") "Futa Vifaa Vyote (All)" else "Delete All Materials",
                                                fontWeight = FontWeight.Bold,
                                                color = RoseError
                                            )
                                            Text(
                                                text = if (language == "sw") "Vifaa ${materials.size} vyote kwenye mfumo" else "All ${materials.size} inventory items",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, tint = RoseError)
                                    },
                                    onClick = {
                                        showDeleteMenu = false
                                        showDeleteAllConfirmDialog = true
                                    }
                                )

                                HorizontalDivider()

                                // 2. Delete Electrical Materials
                                val electCount = categoryCounts[MaterialCategoryUtils.ELECTRICAL] ?: 0
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (language == "sw") "Futa Vifaa vya Umeme ($electCount)" else "Delete Electrical Materials ($electCount)",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.ElectricBolt, contentDescription = null, tint = AmberPrimary)
                                    },
                                    onClick = {
                                        showDeleteMenu = false
                                        categoryToDeleteConfirm = MaterialCategoryUtils.ELECTRICAL
                                    }
                                )

                                // 3. Delete Plumbing Materials
                                val plumbCount = categoryCounts[MaterialCategoryUtils.PLUMBING] ?: 0
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (language == "sw") "Futa Vifaa vya Mabomba ($plumbCount)" else "Delete Plumbing Materials ($plumbCount)",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.WaterDrop, contentDescription = null, tint = Color(0xFF0284C7))
                                    },
                                    onClick = {
                                        showDeleteMenu = false
                                        categoryToDeleteConfirm = MaterialCategoryUtils.PLUMBING
                                    }
                                )

                                // 4. Delete Construction Materials
                                val constCount = categoryCounts[MaterialCategoryUtils.CONSTRUCTION] ?: 0
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (language == "sw") "Futa Vifaa vya Ujenzi ($constCount)" else "Delete Construction Materials ($constCount)",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.Construction, contentDescription = null, tint = Color(0xFFD97706))
                                    },
                                    onClick = {
                                        showDeleteMenu = false
                                        categoryToDeleteConfirm = MaterialCategoryUtils.CONSTRUCTION
                                    }
                                )

                                // 5. Custom Categories Deletion
                                val customCategories = categoryCounts.keys.filter { it !in MaterialCategoryUtils.DEFAULT_CATEGORIES }
                                if (customCategories.isNotEmpty()) {
                                    HorizontalDivider()
                                    customCategories.forEach { customCat ->
                                        val count = categoryCounts[customCat] ?: 0
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = if (language == "sw") "Futa Vifaa vya $customCat ($count)" else "Delete $customCat Materials ($count)",
                                                    fontWeight = FontWeight.Medium
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(imageVector = Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            },
                                            onClick = {
                                                showDeleteMenu = false
                                                categoryToDeleteConfirm = customCat
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (onImportExcel != null) {
                        OutlinedButton(
                            onClick = {
                                excelPickerLauncher.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-excel",
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "*/*"
                                    )
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("import_excel_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (language == "sw") "Ingiza Excel" else "Import Excel",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // List of Materials
            if (filteredMaterials.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                if (language == "sw") "Hakuna kifaa kinacholingana na utafutaji." else "No materials match your search."
                            } else {
                                if (language == "sw") "Hakuna vifaa vilivyopo katika kundi hili." else "No materials found in this category."
                            },
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
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
                    items(filteredMaterials, key = { it.id }) { material ->
                        MaterialItemCard(
                            material = material,
                            language = language,
                            onEdit = { onEditMaterial(material) },
                            onDelete = { itemToDelete = material }
                        )
                    }
                }
            }
        }
    }

    // Single Item Delete Confirmation Dialog
    if (itemToDelete != null) {
        val mat = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = RoseError,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (language == "sw") "Futa Kifaa?" else "Delete Material?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (language == "sw")
                        "Je, una uhakika unataka kufuta \"${mat.name}\"? Kifaa hiki kitafutwa kabisa na hakitarudi hata baada ya Sync."
                    else
                        "Are you sure you want to delete \"${mat.name}\"? This item will be permanently removed."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMaterial(mat)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                    modifier = Modifier.testTag("confirm_delete_material_button")
                ) {
                    Text(
                        text = if (language == "sw") "Futa" else "Delete",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { itemToDelete = null },
                    modifier = Modifier.testTag("cancel_delete_material_button")
                ) {
                    Text(if (language == "sw") "Ghairi" else "Cancel")
                }
            }
        )
    }

    // Delete All Confirmation Dialog
    if (showDeleteAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = RoseError,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = if (language == "sw") "Futa Vifaa Vyote?" else "Delete All Materials?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (language == "sw")
                        "Je, una uhakika unataka kufuta vifaa vyote (${materials.size}) kwenye stoo yako? Utaweza kuanza upya na vifaa unavyovihitaji tu (kutoka Excel au kuingiza mwenyewe). Vifaa vilivyofutwa havitarejeshwa tena hata ukifanya Sync."
                    else
                        "Are you sure you want to delete all materials (${materials.size}) from your inventory? You can start fresh with only the materials you want (from Excel or manual entry). Deleted materials will not come back after synchronization."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllConfirmDialog = false
                        onDeleteAllMaterials?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                    modifier = Modifier.testTag("confirm_delete_all_materials_btn")
                ) {
                    Text(
                        text = if (language == "sw") "Ndio, Futa Vyote" else "Yes, Delete All",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAllConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_delete_all_materials_btn")
                ) {
                    Text(if (language == "sw") "Ghairi" else "Cancel")
                }
            }
        )
    }

    // Delete Category Confirmation Dialog
    if (categoryToDeleteConfirm != null) {
        val catName = categoryToDeleteConfirm!!
        val count = categoryCounts[catName] ?: 0
        AlertDialog(
            onDismissRequest = { categoryToDeleteConfirm = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = RoseError,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = if (language == "sw") "Futa Vifaa vya $catName?" else "Delete $catName Materials?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (language == "sw")
                        "Je, una uhakika unataka kufuta vifaa vyote vya kundi la \"$catName\" (vifaa $count)? Vifaa vilivyofutwa havitarejeshwa tena hata baada ya Sync."
                    else
                        "Are you sure you want to delete all \"$catName\" materials ($count items)? Deleted materials will remain deleted even after synchronization."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDel = categoryToDeleteConfirm
                        categoryToDeleteConfirm = null
                        if (toDel != null) {
                            onDeleteMaterialsByCategory?.invoke(toDel)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                    modifier = Modifier.testTag("confirm_delete_category_materials_btn")
                ) {
                    Text(
                        text = if (language == "sw") "Ndio, Futa Kundi Hili" else "Yes, Delete Category",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { categoryToDeleteConfirm = null },
                    modifier = Modifier.testTag("cancel_delete_category_materials_btn")
                ) {
                    Text(if (language == "sw") "Ghairi" else "Cancel")
                }
            }
        )
    }

    // Import Success Report Dialog
    if (importResult != null) {
        val report = importResult!!
        AlertDialog(
            onDismissRequest = { importResult = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AmberPrimary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = if (language == "sw") "Uingizaji Umekamilika" else "Import Completed",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (language == "sw")
                            "Vifaa vipya vilivyoongezwa: ${report.addedCount}"
                        else
                            "New materials added: ${report.addedCount}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (language == "sw")
                            "Vifaa vilivyoboreshwa: ${report.updatedCount}"
                        else
                            "Existing materials updated: ${report.updatedCount}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (report.failedCount > 0) {
                        Text(
                            text = if (language == "sw")
                                "Mistari iliyorukwa: ${report.failedCount}"
                            else
                                "Rows skipped/invalid: ${report.failedCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { importResult = null },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                    modifier = Modifier.testTag("import_success_ok_button")
                ) {
                    Text(if (language == "sw") "Sawa" else "OK", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Import Error Dialog
    if (importErrorMessage != null) {
        val err = importErrorMessage!!
        AlertDialog(
            onDismissRequest = { importErrorMessage = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = RoseError,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = if (language == "sw") "Hitilafu ya Uingizaji" else "Import Failed",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(err, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                TextButton(
                    onClick = { importErrorMessage = null },
                    modifier = Modifier.testTag("import_error_ok_button")
                ) {
                    Text(if (language == "sw") "Sawa" else "OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Importing Progress Dialog
    if (isImporting) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Text(
                        text = if (language == "sw") "Inasoma faili la Excel..." else "Importing Excel file...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        )
    }
}

@Composable
private fun MaterialItemCard(
    material: MaterialEntity,
    language: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .testTag("material_item_${material.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = material.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        lineHeight = 23.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = material.category,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "${material.unit} • ${Formatters.formatCurrency(material.price)}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = RoseError,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
