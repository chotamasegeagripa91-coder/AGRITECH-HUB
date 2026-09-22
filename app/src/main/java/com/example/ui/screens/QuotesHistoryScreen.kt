package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.QuoteDraft
import com.example.data.models.QuoteEntity
import com.example.data.models.parseQuoteItemsFromJson
import com.example.ui.utils.Formatters

private val AmberPrimary = Color(0xFFD97706)
private val EmeraldSuccess = Color(0xFF059669)
private val BlueAccent = Color(0xFF2563EB)

@Composable
fun QuotesHistoryScreen(
    quotes: List<QuoteEntity>,
    drafts: List<QuoteDraft> = emptyList(),
    language: String,
    onSelectQuote: (QuoteEntity) -> Unit,
    onSelectDraft: (QuoteDraft) -> Unit = {},
    onDeleteDraft: (QuoteDraft) -> Unit = {},
    onNewQuote: () -> Unit
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewQuote,
                containerColor = AmberPrimary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Quote")
            }
        }
    ) { paddingValues ->
        if (quotes.isEmpty() && drafts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (language == "sw") "Hakuna Makadirio Bado" else "No Quotes Yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Draft Quotations Section
                if (drafts.isNotEmpty()) {
                    item {
                        Surface(
                            color = AmberPrimary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EditNote,
                                        contentDescription = null,
                                        tint = AmberPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = if (language == "sw") "Rasimu za Makadirio" else "Draft Quotations",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        color = AmberPrimary,
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = drafts.size.toString(),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                            color = Color.Black,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (language == "sw") "Hazijahifadhiwa" else "Unsaved",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = AmberPrimary
                                )
                            }
                        }
                    }

                    items(drafts, key = { "draft_${it.id}" }) { draft ->
                        DraftQuoteCard(
                            draft = draft,
                            language = language,
                            onClick = { onSelectDraft(draft) },
                            onDelete = { onDeleteDraft(draft) }
                        )
                    }

                    if (quotes.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (language == "sw") "Makadirio & Ankara Zilizohifadhiwa" else "Saved Quotations & Invoices",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }

                // 2. Saved Quotes & Invoices Section
                items(quotes, key = { "quote_${it.id}" }) { quote ->
                    QuoteCard(quote = quote, language = language, onClick = { onSelectQuote(quote) })
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun DraftQuoteCard(
    draft: QuoteDraft,
    language: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        color = AmberPrimary.copy(alpha = 0.06f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.2.dp, AmberPrimary.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AmberPrimary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = draft.number,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            color = AmberPrimary,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (language == "sw") "RASIMU" else "DRAFT",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (draft.customerName.isNotBlank()) draft.customerName else (if (language == "sw") "Mteja Hajachaguliwa" else "No Client Selected"),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatters.formatCurrency(draft.grandTotal),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        color = AmberPrimary
                    )
                    Text(
                        text = draft.date.ifBlank { Formatters.getCurrentDateFormatted() },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = AmberPrimary.copy(alpha = 0.2f), thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(6.dp))

            val itemCount = parseQuoteItemsFromJson(draft.itemsJson).size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$itemCount vifaa • Labour: ${Formatters.formatCurrency(draft.labour)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Draft",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = if (language == "sw") "Endelea nayo" else "Resume",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = if (language == "sw") "Futa Rasimu?" else "Delete Draft?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (language == "sw")
                        "Je, una uhakika unataka kufuta rasimu hii ya ${draft.number}? Taarifa zake hazitahifadhiwa tena."
                    else
                        "Are you sure you want to delete draft ${draft.number}? Unsaved changes will be discarded."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White)
                ) {
                    Text(text = if (language == "sw") "Futa" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(text = if (language == "sw") "Ghairi" else "Cancel")
                }
            }
        )
    }
}

@Composable
private fun QuoteCard(
    quote: QuoteEntity,
    language: String,
    onClick: () -> Unit
) {
    val isInvoice = quote.status == "invoice"
    val icon = if (isInvoice) Icons.Default.Receipt else Icons.Default.Description
    val statusColor = if (isInvoice) EmeraldSuccess else BlueAccent

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = statusColor)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = quote.number,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = Formatters.formatCurrency(quote.grandTotal),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = AmberPrimary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = quote.customerName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = quote.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    if (isInvoice) {
                        Surface(
                            color = if (quote.paid) EmeraldSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (quote.paid) (if (language == "sw") "IMELIPWA" else "PAID") else (if (language == "sw") "HAJALIPA" else "UNPAID"),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (quote.paid) EmeraldSuccess else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
