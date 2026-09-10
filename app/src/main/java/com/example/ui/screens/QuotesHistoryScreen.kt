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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.QuoteEntity
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.utils.AppStrings
import com.example.ui.utils.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotesHistoryScreen(
    quotes: List<QuoteEntity>,
    language: String,
    onSelectQuote: (QuoteEntity) -> Unit,
    onNewQuote: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Zote") }

    val filterOptions = listOf(
        "Zote",
        "Makadirio (Quotes)",
        "Ankara (Invoices)",
        "Zilizolipwa (Paid)",
        "Hazijalipwa (Unpaid)"
    )

    val filteredQuotes = remember(quotes, searchQuery, selectedFilter) {
        quotes.filter { q ->
            val matchQuery = searchQuery.isBlank() ||
                    q.customerName.contains(searchQuery, ignoreCase = true) ||
                    q.number.contains(searchQuery, ignoreCase = true) ||
                    q.customerLocation.contains(searchQuery, ignoreCase = true) ||
                    q.description.contains(searchQuery, ignoreCase = true)

            val matchFilter = when (selectedFilter) {
                "Makadirio (Quotes)" -> q.status == "quotation"
                "Ankara (Invoices)" -> q.status == "invoice"
                "Zilizolipwa (Paid)" -> q.paid
                "Hazijalipwa (Unpaid)" -> !q.paid
                else -> true
            }

            matchQuery && matchFilter
        }
    }

    val totalValue = remember(filteredQuotes) {
        filteredQuotes.sumOf { it.grandTotal }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewQuote,
                containerColor = AmberPrimary,
                contentColor = Color.Black,
                modifier = Modifier.testTag("history_new_quote_fab")
            ) {
                Icon(imageVector = Icons.Default.PostAdd, contentDescription = "New Quote")
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (language == "sw") "Makadirio Mapya" else "New Quote",
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

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(if (language == "sw") "Tafuta kwa namba, mteja, eneo..." else "Search by quote #, client, location...") },
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
                    .testTag("quotes_history_search_input"),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filterOptions.forEach { opt ->
                    val selected = selectedFilter == opt
                    FilterChip(
                        selected = selected,
                        onClick = { selectedFilter = opt },
                        label = { Text(opt, fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Summary Header (Count & Total sum)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (language == "sw") "Rekodi: ${filteredQuotes.size}" else "Records: ${filteredQuotes.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Jumla: ${Formatters.formatCurrency(totalValue)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredQuotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (language == "sw") "Hakuna rekodi zilizopatikana." else "No records found.",
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
                    items(filteredQuotes, key = { it.id }) { quote ->
                        val isInvoice = quote.status == "invoice"

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectQuote(quote) }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = quote.number,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = if (isInvoice) EmeraldSuccess.copy(alpha = 0.15f) else AmberPrimary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (isInvoice) "INVOICE" else "QUOTE",
                                                color = if (isInvoice) EmeraldSuccess else AmberPrimary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = quote.date,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = quote.customerName,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                )

                                if (quote.description.isNotBlank()) {
                                    Text(
                                        text = quote.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
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
                                    Surface(
                                        color = if (quote.paid) EmeraldSuccess.copy(alpha = 0.15f) else RoseError.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (quote.paid) Icons.Default.CheckCircle else Icons.Default.Pending,
                                                contentDescription = null,
                                                tint = if (quote.paid) EmeraldSuccess else RoseError,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (quote.paid) "IMELIPWA" else "HAIJALIPWA",
                                                color = if (quote.paid) EmeraldSuccess else RoseError,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Text(
                                        text = Formatters.formatCurrency(quote.grandTotal),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
