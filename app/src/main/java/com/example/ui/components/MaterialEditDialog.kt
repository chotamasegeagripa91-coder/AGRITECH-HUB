package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.MaterialEntity
import com.example.ui.theme.RoseError
import com.example.ui.utils.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialEditDialog(
    material: MaterialEntity?,
    language: String,
    onDismiss: () -> Unit,
    onSave: (MaterialEntity) -> Unit
) {
    val isEdit = material != null
    var name by remember { mutableStateOf(material?.name ?: "") }
    var selectedCategory by remember {
        mutableStateOf(
            if (material != null) com.example.ui.utils.MaterialCategoryUtils.getCanonicalCategory(material.category, material.name)
            else com.example.ui.utils.MaterialCategoryUtils.ELECTRICAL
        )
    }
    var selectedUnit by remember { mutableStateOf(material?.unit ?: "Pcs") }
    var priceText by remember { mutableStateOf(if (material != null && material.price > 0) material.price.toLong().toString() else "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var unitMenuExpanded by remember { mutableStateOf(false) }

    val categories = com.example.ui.utils.MaterialCategoryUtils.ITEM_CATEGORIES

    val units = listOf("Pcs", "Roll", "Mita", "Set", "Box", "Pkt", "Kg", "Bati")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 12.dp)
                .imePadding(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEdit) {
                            if (language == "sw") "Hariri Kifaa" else "Edit Material"
                        } else {
                            if (language == "sw") "Ongeza Kifaa Kipya" else "Add New Material"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (language == "sw") "Jina la Kifaa" else "Material Name") },
                    placeholder = { Text("Mfano: Cable 2.5mm Twin & Earth") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("material_name_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Category Dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryMenuExpanded,
                    onExpandedChange = { categoryMenuExpanded = !categoryMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (language == "sw") "Kundi la Kifaa" else "Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    selectedCategory = cat
                                    categoryMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Unit & Price in a row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Unit Dropdown
                    ExposedDropdownMenuBox(
                        expanded = unitMenuExpanded,
                        onExpandedChange = { unitMenuExpanded = !unitMenuExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedUnit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (language == "sw") "Kipimo" else "Unit") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitMenuExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = unitMenuExpanded,
                            onDismissRequest = { unitMenuExpanded = false }
                        ) {
                            units.forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u) },
                                    onClick = {
                                        selectedUnit = u
                                        unitMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Price
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text(if (language == "sw") "Bei (TSh)" else "Price (TSh)") },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.4f).testTag("material_price_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = RoseError,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(AppStrings.t("btn_cancel", language))
                    }

                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                errorMessage = if (language == "sw") "Tafadhali jaza jina la kifaa." else "Please enter material name."
                                return@Button
                            }
                            val priceVal = priceText.toDoubleOrNull() ?: 0.0
                            val updated = MaterialEntity(
                                id = material?.id ?: 0,
                                name = name.trim(),
                                category = selectedCategory,
                                unit = selectedUnit,
                                price = priceVal
                            )
                            onSave(updated)
                        },
                        modifier = Modifier.weight(1.2f).testTag("material_save_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(AppStrings.t("btn_save", language), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
