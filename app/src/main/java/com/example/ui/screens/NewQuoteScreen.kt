package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.models.CustomerEntity
import com.example.data.models.MaterialEntity
import com.example.data.models.QuoteEntity
import com.example.data.models.QuoteItem
import com.example.ui.components.CustomerEditDialog
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.utils.AppStrings
import com.example.ui.utils.Formatters
import com.example.ui.utils.MaterialCategoryUtils
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewQuoteScreen(
    customers: List<CustomerEntity>,
    materials: List<MaterialEntity>,
    preselectedCustomer: CustomerEntity?,
    initialQuoteNumber: String = "QTN-001",
    language: String,
    onSaveQuote: (QuoteEntity) -> Unit,
    onAddCustomer: (CustomerEntity) -> Unit
) {
    val context = LocalContext.current

    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(preselectedCustomer ?: customers.firstOrNull()) }
    var quoteNumber by remember(initialQuoteNumber) { mutableStateOf(initialQuoteNumber) }
    var date by remember { mutableStateOf(Formatters.getCurrentDateFormatted()) }
    var validUntil by remember { mutableStateOf(Formatters.getFutureDateFormatted(14)) }
    var description by remember { mutableStateOf("") }
    var isLabourAutoCalculated by remember { mutableStateOf(true) }
    var labourCostText by remember { mutableStateOf("0") }

    val quoteItems = remember { mutableStateListOf<QuoteItem>() }

    var showMaterialPickerModal by remember { mutableStateOf(false) }
    var showCustomItemDialog by remember { mutableStateOf(false) }
    var showNewCustomerDialog by remember { mutableStateOf(false) }
    var customerMenuExpanded by remember { mutableStateOf(false) }

    // Live calculation: Labour Cost = Total Selected Materials Cost × 40%
    val materialsTotal = remember(quoteItems.toList()) {
        quoteItems.sumOf { it.total }
    }

    LaunchedEffect(materialsTotal, isLabourAutoCalculated) {
        if (isLabourAutoCalculated) {
            val autoLabour = (materialsTotal * 0.40).toLong()
            labourCostText = autoLabour.toString()
        }
    }

    val labourCost = remember(labourCostText) {
        labourCostText.toDoubleOrNull() ?: 0.0
    }
    val grandTotal = remember(materialsTotal, labourCost) {
        materialsTotal + labourCost
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp)
    ) {
        // 1. Header Section
        item {
            Text(
                text = if (language == "sw") "Tengeneza Makadirio ya Kazi (Quotation)" else "Create New Quotation",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // 2. Customer Selection Box
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (language == "sw") "Mteja wa Mradi:" else "Select Client:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        TextButton(
                            onClick = { showNewCustomerDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (language == "sw") "+ Mteja Mpya" else "+ New Client", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    ExposedDropdownMenuBox(
                        expanded = customerMenuExpanded,
                        onExpandedChange = { customerMenuExpanded = !customerMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCustomer?.name ?: if (language == "sw") "-- Chagua Mteja --" else "-- Select Client --",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = customerMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("customer_dropdown_selector"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = customerMenuExpanded,
                            onDismissRequest = { customerMenuExpanded = false }
                        ) {
                            customers.forEach { cust ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(cust.name, fontWeight = FontWeight.Bold)
                                            if (cust.phone.isNotBlank()) Text(cust.phone, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = {
                                        selectedCustomer = cust
                                        customerMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (selectedCustomer != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val cust = selectedCustomer!!
                        Text(
                            text = "Simu: ${cust.phone.ifEmpty { "Hamna" }} | Eneo: ${cust.location.ifEmpty { "Hamna" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 3. Quote Meta Info (Number, Date, Valid Until, Description)
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = quoteNumber,
                            onValueChange = { quoteNumber = it },
                            label = { Text("Namba") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = date,
                            onValueChange = { date = it },
                            label = { Text("Tarehe") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(if (language == "sw") "Maelezo ya Kazi / Mradi" else "Project / Work Description") },
                        placeholder = { Text("Mfano: Ufungaji wa Umeme Nyumba ya Vyumba 3") },
                        modifier = Modifier.fillMaxWidth().testTag("quote_description_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // 4. Materials Items Header & Action Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (language == "sw") "Orodha ya Vifaa (${quoteItems.size})" else "Quote Items (${quoteItems.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { showCustomItemDialog = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("+ Kifaa Maalum", fontSize = 11.sp)
                    }

                    Button(
                        onClick = { showMaterialPickerModal = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Color.Black),
                        modifier = Modifier.testTag("open_material_picker_btn")
                    ) {
                        Icon(imageVector = Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (language == "sw") "Chagua Vifaa" else "Pick Items", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 5. Materials Items List
        if (quoteItems.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Outlined.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (language == "sw") "Bado hujaongeza vifaa. Bonyeza kitufe cha 'Chagua Vifaa' kuongeza kutoka stoo." else "No items added yet. Tap 'Pick Items' to add from stock.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            itemsIndexed(quoteItems) { index, item ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}. ${item.name}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { quoteItems.removeAt(index) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Remove", tint = RoseError, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Quantity Controls (- / +)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (item.quantity > 1) {
                                            val newQty = item.quantity - 1
                                            quoteItems[index] = item.copy(quantity = newQty, total = newQty * item.price)
                                        }
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.RemoveCircleOutline, contentDescription = "Minus", modifier = Modifier.size(18.dp))
                                }

                                Text(
                                    text = "${item.quantity.toInt()} ${item.unit}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )

                                IconButton(
                                    onClick = {
                                        val newQty = item.quantity + 1
                                        quoteItems[index] = item.copy(quantity = newQty, total = newQty * item.price)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.AddCircleOutline, contentDescription = "Plus", modifier = Modifier.size(18.dp))
                                }
                            }

                            // Price & Total
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = Formatters.formatCurrency(item.total),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Text(
                                    text = "@ ${Formatters.formatCurrency(item.price)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Labour Cost Input
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (language == "sw") "Gharama ya Ufundi (Labour - 40% ya Vifaa):" else "Labour Cost (40% of Materials):",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (!isLabourAutoCalculated) {
                            TextButton(
                                onClick = {
                                    isLabourAutoCalculated = true
                                    val auto = (materialsTotal * 0.40).toLong()
                                    labourCostText = auto.toString()
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (language == "sw") "Hesabu 40%" else "Recalculate 40%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "40% Auto",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = labourCostText,
                        onValueChange = {
                            labourCostText = it
                            isLabourAutoCalculated = false
                        },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("quote_labour_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = { Text("TSh", modifier = Modifier.padding(end = 12.dp), fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }

        // 7. Live Totals Summary Card
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Jumla ya Vifaa (${quoteItems.size}):", style = MaterialTheme.typography.bodyMedium)
                        Text(text = Formatters.formatCurrency(materialsTotal), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = if (language == "sw") "Ufundi (Labour - 40%):" else "Labour Cost (40%):",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(text = Formatters.formatCurrency(labourCost), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "JUMLA KUU:", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
                        Text(
                            text = Formatters.formatCurrency(grandTotal),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 20.sp
                            )
                        )
                    }
                }
            }
        }

        // 8. Save Quotation Button
        item {
            Button(
                onClick = {
                    if (selectedCustomer == null) {
                        Toast.makeText(context, "Tafadhali chagua mteja kwanza.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (quoteItems.isEmpty()) {
                        Toast.makeText(context, "Tafadhali ongeza angalau kifaa kimoja.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // Convert items to JSON
                    val itemsArray = JSONArray()
                    for (it in quoteItems) {
                        val obj = JSONObject().apply {
                            put("id", it.id)
                            put("materialId", it.materialId ?: JSONObject.NULL)
                            put("name", it.name)
                            put("unit", it.unit)
                            put("price", it.price)
                            put("quantity", it.quantity)
                            put("total", it.total)
                        }
                        itemsArray.put(obj)
                    }

                    val newQuote = QuoteEntity(
                        number = quoteNumber,
                        date = date,
                        validUntil = validUntil,
                        customerId = selectedCustomer!!.id,
                        customerName = selectedCustomer!!.name,
                        customerPhone = selectedCustomer!!.phone,
                        customerLocation = selectedCustomer!!.location,
                        description = description.trim(),
                        itemsJson = itemsArray.toString(),
                        materialsTotal = materialsTotal,
                        labour = labourCost,
                        grandTotal = grandTotal,
                        status = "quotation",
                        paid = false
                    )

                    onSaveQuote(newQuote)
                    Toast.makeText(context, "Makadirio ya ${newQuote.number} yamehifadhiwa!", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_quote_final_btn"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Color.Black)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (language == "sw") "Kamilisha & Hifadhi Makadirio" else "Save Quotation",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }

    // Material Picker Bottomsheet/Dialog
    if (showMaterialPickerModal) {
        MaterialPickerDialog(
            materials = materials,
            language = language,
            onDismiss = { showMaterialPickerModal = false },
            onItemsSelected = { selectedList ->
                for (mat in selectedList) {
                    val existingIdx = quoteItems.indexOfFirst { it.materialId == mat.id }
                    if (existingIdx >= 0) {
                        val current = quoteItems[existingIdx]
                        val newQty = current.quantity + 1
                        quoteItems[existingIdx] = current.copy(quantity = newQty, total = newQty * current.price)
                    } else {
                        quoteItems.add(
                            QuoteItem(
                                id = UUID.randomUUID().toString(),
                                materialId = mat.id,
                                name = mat.name,
                                unit = mat.unit,
                                price = mat.price,
                                quantity = 1.0,
                                total = mat.price
                            )
                        )
                    }
                }
                showMaterialPickerModal = false
            }
        )
    }

    // Custom One-off Item Dialog
    if (showCustomItemDialog) {
        CustomItemDialog(
            language = language,
            onDismiss = { showCustomItemDialog = false },
            onAdd = { customItem ->
                quoteItems.add(customItem)
                showCustomItemDialog = false
            }
        )
    }

    // New Customer Dialog
    if (showNewCustomerDialog) {
        CustomerEditDialog(
            customer = null,
            language = language,
            onDismiss = { showNewCustomerDialog = false },
            onSave = { newCust ->
                onAddCustomer(newCust)
                selectedCustomer = newCust
                showNewCustomerDialog = false
            }
        )
    }
}

@Composable
fun MaterialPickerDialog(
    materials: List<MaterialEntity>,
    language: String,
    onDismiss: () -> Unit,
    onItemsSelected: (List<MaterialEntity>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val selectedMaterials = remember { mutableStateListOf<MaterialEntity>() }

    val categories = listOf("All", "Electrical", "Plumbing", "Construction")

    // Category counts for quick visual insight
    val electricalCount = remember(materials) { materials.count { MaterialCategoryUtils.matchesCategory(it, "Electrical") } }
    val plumbingCount = remember(materials) { materials.count { MaterialCategoryUtils.matchesCategory(it, "Plumbing") } }
    val constructionCount = remember(materials) { materials.count { MaterialCategoryUtils.matchesCategory(it, "Construction") } }

    val filtered = remember(materials, searchQuery, selectedCategory) {
        materials.filter { mat ->
            val matchesCategory = when (selectedCategory) {
                "All" -> true
                "Electrical" -> MaterialCategoryUtils.matchesCategory(mat, "Electrical")
                "Plumbing" -> MaterialCategoryUtils.matchesCategory(mat, "Plumbing")
                "Construction" -> MaterialCategoryUtils.matchesCategory(mat, "Construction")
                else -> true
            }
            if (!matchesCategory) return@filter false

            // The search bar searches strictly within the currently selected category
            if (searchQuery.isBlank()) true
            else {
                mat.name.contains(searchQuery, ignoreCase = true) ||
                        mat.category.contains(searchQuery, ignoreCase = true) ||
                        MaterialCategoryUtils.getCanonicalCategory(mat.category, mat.name).contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .padding(6.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (language == "sw") "Chagua Vifaa kutoka Stoo" else "Select Items from Inventory",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (language == "sw") "Chuja kwa aina ya vifaa na utafute" else "Filter by category and search",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search bar with active filter scope
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            if (selectedCategory == "All") {
                                if (language == "sw") "Tafuta vifaa vyote..." else "Search all inventory..."
                            } else {
                                if (language == "sw") "Tafuta vifaa vya $selectedCategory..." else "Search $selectedCategory items..."
                            }
                        )
                    },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Category Filter Chips: All, Electrical, Plumbing, Construction
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    categories.forEach { category ->
                        val isSelected = selectedCategory == category
                        val count = when (category) {
                            "All" -> materials.size
                            "Electrical" -> electricalCount
                            "Plumbing" -> plumbingCount
                            "Construction" -> constructionCount
                            else -> 0
                        }

                        val icon = when (category) {
                            "All" -> Icons.Default.Inventory2
                            "Electrical" -> Icons.Default.Bolt
                            "Plumbing" -> Icons.Default.WaterDrop
                            "Construction" -> Icons.Default.Construction
                            else -> Icons.Default.Category
                        }

                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            label = {
                                Text(
                                    text = "$category ($count)",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when (category) {
                                    "Electrical" -> AmberPrimary
                                    "Plumbing" -> Color(0xFF0288D1)
                                    "Construction" -> Color(0xFFE65100)
                                    else -> AmberPrimary
                                },
                                selectedLabelColor = Color.Black,
                                selectedLeadingIconColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Count & selection indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (language == "sw") "Vifaa vilivyopatikana: ${filtered.size}" else "Found: ${filtered.size} items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selectedMaterials.isNotEmpty()) {
                        Text(
                            text = if (language == "sw") "${selectedMaterials.size} vimechaguliwa" else "${selectedMaterials.size} selected",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = AmberPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Inventory items list
                if (filtered.isEmpty()) {
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
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    if (language == "sw") "Hakuna vifaa vilivyopatikana katika kundi la \"$selectedCategory\""
                                    else "No items found in category \"$selectedCategory\" for \"$searchQuery\""
                                } else {
                                    if (language == "sw") "Hakuna vifaa katika kundi hili"
                                    else "No items currently in \"$selectedCategory\""
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (selectedCategory != "All" || searchQuery.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        selectedCategory = "All"
                                        searchQuery = ""
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(if (language == "sw") "Onyesha Vifaa Vyote" else "Show All Items")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered, key = { it.id }) { mat ->
                            val isSelected = selectedMaterials.contains(mat)
                            val canonicalCategory = MaterialCategoryUtils.getCanonicalCategory(mat.category, mat.name)

                            val categoryColor = when (canonicalCategory) {
                                "Electrical" -> AmberPrimary
                                "Plumbing" -> Color(0xFF0288D1)
                                "Construction" -> Color(0xFFE65100)
                                else -> MaterialTheme.colorScheme.primary
                            }

                            Surface(
                                color = if (isSelected) AmberPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) selectedMaterials.remove(mat)
                                        else selectedMaterials.add(mat)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mat.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = categoryColor.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = canonicalCategory,
                                                    color = categoryColor,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "• ${mat.unit}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = Formatters.formatCurrency(mat.price),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            if (isSelected) selectedMaterials.remove(mat)
                                            else selectedMaterials.add(mat)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Add Selected Button
                Button(
                    onClick = { onItemsSelected(selectedMaterials.toList()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Color.Black)
                ) {
                    Text(
                        text = if (language == "sw") "Ongeza Vifaa Vilivyochaguliwa (${selectedMaterials.size})" else "Add Selected (${selectedMaterials.size})",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun CustomItemDialog(
    language: String,
    onDismiss: () -> Unit,
    onAdd: (QuoteItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("Pcs") }
    var priceText by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("1") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (language == "sw") "Ongeza Kifaa Maalum" else "Add Custom Item",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Jina la Kifaa") },
                    placeholder = { Text("Mfano: Earth Rod Copper Clad") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Kipimo") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("Idadi") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Bei ya Kizio (TSh)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Text("Ghairi")
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val price = priceText.toDoubleOrNull() ?: 0.0
                                val qty = qtyText.toDoubleOrNull() ?: 1.0
                                onAdd(
                                    QuoteItem(
                                        id = UUID.randomUUID().toString(),
                                        name = name.trim(),
                                        unit = unit.trim(),
                                        price = price,
                                        quantity = qty,
                                        total = price * qty
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Color.Black)
                    ) {
                        Text("Ongeza", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
