package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.data.models.CustomerEntity
import com.example.data.models.QuoteEntity
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.utils.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    customers: List<CustomerEntity>,
    quotes: List<QuoteEntity>,
    language: String,
    onAddCustomer: () -> Unit,
    onEditCustomer: (CustomerEntity) -> Unit,
    onDeleteCustomer: (CustomerEntity) -> Unit,
    onNewQuoteForCustomer: (CustomerEntity) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var customerToDelete by remember { mutableStateOf<CustomerEntity?>(null) }

    val filteredCustomers = remember(customers, searchQuery) {
        if (searchQuery.isBlank()) customers
        else customers.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.phone.contains(searchQuery, ignoreCase = true) ||
                    it.location.contains(searchQuery, ignoreCase = true)
        }
    }

    fun makeCall(phone: String) {
        if (phone.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.replace(" ", "")}"))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Imeshindikana kupiga simu.", Toast.LENGTH_SHORT).show()
        }
    }

    fun openWhatsApp(phone: String) {
        if (phone.isBlank()) return
        try {
            val cleanPhone = phone.replace("+", "").replace(" ", "")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanPhone"))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "WhatsApp haipatikani.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCustomer,
                containerColor = AmberPrimary,
                contentColor = Color.Black,
                modifier = Modifier.testTag("add_customer_fab")
            ) {
                Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Add Customer")
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (language == "sw") "Ongeza Mteja" else "Add Client",
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

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(if (language == "sw") "Tafuta mteja kwa jina au namba..." else "Search client by name or phone...") },
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
                    .testTag("customers_search_input"),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (language == "sw") "Jumla ya Wateja: ${filteredCustomers.size}" else "Total Clients: ${filteredCustomers.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredCustomers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (language == "sw") "Hakuna mteja aliyepatikana." else "No clients found.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredCustomers, key = { it.id }) { customer ->
                        val customerQuotesCount = quotes.count { it.customerId == customer.id }

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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = customer.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        if (customer.phone.isNotBlank()) {
                                            Text(
                                                text = "Simu: ${customer.phone}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (customer.location.isNotBlank()) {
                                            Text(
                                                text = "Eneo: ${customer.location}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row {
                                        if (customer.phone.isNotBlank()) {
                                            IconButton(
                                                onClick = { makeCall(customer.phone) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.Call, contentDescription = "Call", tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
                                            }

                                            IconButton(
                                                onClick = { openWhatsApp(customer.phone) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.Chat, contentDescription = "WhatsApp", tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
                                            }
                                        }

                                        IconButton(
                                            onClick = { onEditCustomer(customer) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                                        }

                                        IconButton(
                                            onClick = { customerToDelete = customer },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete", tint = RoseError, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }

                                if (customer.notes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = customer.notes,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Miradi: $customerQuotesCount",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Button(
                                        onClick = { onNewQuoteForCustomer(customer) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = if (language == "sw") "Tengeneza Makadirio" else "New Quote", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (customerToDelete != null) {
        val cust = customerToDelete ?: return
        AlertDialog(
            onDismissRequest = { customerToDelete = null },
            title = { Text(if (language == "sw") "Futa Mteja?" else "Delete Customer?") },
            text = { Text("Je, una uhakika unataka kufuta taarifa za mteja: ${cust.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        customerToDelete = null
                        onDeleteCustomer(cust)
                    }
                ) {
                    Text(AppStrings.t("btn_delete", language), color = RoseError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { customerToDelete = null }) {
                    Text(AppStrings.t("btn_cancel", language))
                }
            }
        )
    }
}
