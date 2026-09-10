package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.BusinessSettings
import com.example.data.models.QuoteEntity
import com.example.data.models.QuoteItem
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.utils.AppStrings
import com.example.ui.utils.Formatters
import com.example.ui.utils.PdfExporter
import org.json.JSONArray

@Composable
fun QuoteDetailsDialog(
    quote: QuoteEntity,
    businessSettings: BusinessSettings,
    language: String,
    onDismiss: () -> Unit,
    onConvertToInvoice: (id: Int, newNumber: String) -> Unit,
    onSetPaidStatus: (id: Int, paid: Boolean) -> Unit,
    onOpenPrintPreview: (QuoteEntity) -> Unit,
    onDelete: (QuoteEntity) -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Parse items from JSON
    val items = remember(quote.itemsJson) {
        val list = mutableListOf<QuoteItem>()
        try {
            val arr = JSONArray(quote.itemsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    QuoteItem(
                        id = obj.optString("id", i.toString()),
                        materialId = if (obj.has("materialId")) obj.optInt("materialId") else null,
                        name = obj.optString("name", "Item"),
                        unit = obj.optString("unit", "Pcs"),
                        price = obj.optDouble("price", 0.0),
                        quantity = obj.optDouble("quantity", 1.0),
                        total = obj.optDouble("total", 0.0)
                    )
                )
            }
        } catch (e: Exception) {
            // ignore
        }
        list
    }

    fun downloadPdf() {
        val file = PdfExporter.generateAndSavePdf(
            context = context,
            quote = quote,
            items = items,
            businessSettings = businessSettings,
            language = language
        )
        if (file != null) {
            Toast.makeText(context, if (language == "sw") "PDF imepakuliwa na kuhifadhiwa!" else "PDF generated and saved!", Toast.LENGTH_SHORT).show()
            PdfExporter.openOrSharePdf(context, file, if (quote.status == "invoice") "Ankara" else "Makadirio")
        } else {
            Toast.makeText(context, if (language == "sw") "Imeshindikana kutengeneza PDF." else "Failed to generate PDF.", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareViaWhatsApp() {
        val isInvoice = quote.status == "invoice"
        val sb = StringBuilder()
        sb.append("⚡ *${businessSettings.name}*\n")
        sb.append("${businessSettings.slogan}\n")
        sb.append("Simu: ${businessSettings.phone1} / ${businessSettings.phone2}\n\n")
        sb.append(if (isInvoice) "📄 *ANKARA RASMI YA MALIPO (INVOICE)*\n" else "📋 *MAKADIRIO YA VIFAA NA UFUNDI (QUOTATION)*\n")
        sb.append("Namba: *${quote.number}*\n")
        sb.append("Tarehe: ${quote.date}\n")
        sb.append("Mteja: *${quote.customerName}*\n")
        if (quote.customerLocation.isNotBlank()) sb.append("Mahali/Site: ${quote.customerLocation}\n")
        if (quote.description.isNotBlank()) sb.append("Maelezo ya Kazi: ${quote.description}\n")
        sb.append("\n--------------------------------\n")
        sb.append("*ORODHA YA VIFAA NA BEI:*\n")

        items.forEachIndexed { idx, item ->
            sb.append("${idx + 1}. *${item.name}*\n")
            sb.append("   ${item.quantity.toInt()} ${item.unit} @ ${Formatters.formatCurrency(item.price)} = *${Formatters.formatCurrency(item.total)}*\n")
        }

        sb.append("--------------------------------\n")
        sb.append("Jumla ya Vifaa: *${Formatters.formatCurrency(quote.materialsTotal)}*\n")
        sb.append("Gharama ya Ufundi (Labour): *${Formatters.formatCurrency(quote.labour)}*\n")
        sb.append("🏆 *JUMLA KUU:* *${Formatters.formatCurrency(quote.grandTotal)}*\n")
        sb.append("Hali ya Malipo: *${if (quote.paid) "IMELIPWA (PAID)" else "HAIJALIPWA (UNPAID)"}*\n\n")

        // Payment accounts info only on client invoices
        if (isInvoice) {
            val hasBank = businessSettings.bankName.isNotBlank() || businessSettings.bankAccountNumber.isNotBlank()
            val hasTill = businessSettings.lipaNumber.isNotBlank()
            val hasMobile = businessSettings.mobileMoney.isNotBlank()

            if (hasBank || hasTill || hasMobile) {
                sb.append("💳 *TAARIFA ZA MALIPO (PAYMENT ACCOUNT DETAILS):*\n")
                if (hasBank) {
                    sb.append("• Benki: ${businessSettings.bankName} - A/C: *${businessSettings.bankAccountNumber}*\n")
                    if (businessSettings.bankAccountName.isNotBlank()) sb.append("  Jina: ${businessSettings.bankAccountName}\n")
                }
                if (hasTill) sb.append("• Lipa Namba (Till): *${businessSettings.lipaNumber}*\n")
                if (hasMobile) sb.append("• Simu ya Malipo: *${businessSettings.mobileMoney}*\n")
                sb.append("--------------------------------\n\n")
            }
        } else {
            sb.append("📌 *MASHARTI:* Makadirio haya ni halali kulingana na bei za soko. Ankara rasmi ya malipo itatolewa baada ya makubaliano.\n")
            sb.append("--------------------------------\n\n")
        }

        sb.append("Asante kwa kuchagua ${businessSettings.name}! Ubora na Usalama Ndio Fahari Yetu.\n")
        sb.append("Powered by Agritech")

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, sb.toString())
            type = "text/plain"
        }
        try {
            context.startActivity(Intent.createChooser(sendIntent, "Tuma Makadirio kwa Mteja"))
        } catch (e: Exception) {
            Toast.makeText(context, "Imeshindikana kufungua programu ya kushare.", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = quote.number,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val isInvoice = quote.status == "invoice"
                            Surface(
                                color = if (isInvoice) EmeraldSuccess.copy(alpha = 0.15f) else AmberPrimary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isInvoice) "INVOICE" else "QUOTATION",
                                    color = if (isInvoice) EmeraldSuccess else AmberPrimary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Tarehe: ${quote.date}${if (quote.status != "invoice" && quote.validUntil.isNotBlank()) " | Mwisho: ${quote.validUntil}" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Customer & Description Summary
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = quote.customerName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (quote.customerPhone.isNotBlank()) {
                                Text(
                                    text = " • ${quote.customerPhone}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (quote.customerLocation.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = quote.customerLocation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (quote.description.isNotBlank()) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = quote.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Items list header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (language == "sw") "Vifaa vilivyomo (${items.size}):" else "Itemized Materials (${items.size}):",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Items List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(items) { index, item ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${index + 1}. ${item.name}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "${item.quantity.toInt()} ${item.unit} × ${Formatters.formatCurrency(item.price)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = Formatters.formatCurrency(item.total),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Totals Box
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Jumla ya Vifaa:", style = MaterialTheme.typography.bodySmall)
                            Text(text = Formatters.formatCurrency(quote.materialsTotal), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Gharama ya Ufundi (Labour):", style = MaterialTheme.typography.bodySmall)
                            Text(text = Formatters.formatCurrency(quote.labour), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "JUMLA KUU:",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = Formatters.formatCurrency(quote.grandTotal),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // WhatsApp Share
                    Button(
                        onClick = { shareViaWhatsApp() },
                        modifier = Modifier.weight(1f).testTag("quote_share_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(if (language == "sw") "WhatsApp" else "WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Direct Download PDF
                    Button(
                        onClick = { downloadPdf() },
                        modifier = Modifier.weight(1.1f).testTag("quote_download_pdf_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(if (language == "sw") "Pakua PDF" else "Download PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Print / PDF Preview
                    FilledTonalButton(
                        onClick = { onOpenPrintPreview(quote) },
                        modifier = Modifier.weight(1f).testTag("quote_print_preview_btn"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(if (language == "sw") "Preview" else "Preview", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Secondary Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (quote.status == "quotation") {
                        OutlinedButton(
                            onClick = {
                                val newInvNum = "INV-${quote.number.replace("QTN-", "").replace("QT-", "")}"
                                onConvertToInvoice(quote.id, newInvNum)
                            },
                            modifier = Modifier.weight(1f).testTag("convert_invoice_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (language == "sw") "Fanya Ankara" else "Make Invoice", fontSize = 11.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = { onSetPaidStatus(quote.id, !quote.paid) },
                        modifier = Modifier.weight(1f).testTag("toggle_paid_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (quote.paid) (if (language == "sw") "Weka Haijalipwa" else "Mark Unpaid") else (if (language == "sw") "Weka Imelipwa" else "Mark Paid"), fontSize = 11.sp)
                    }

                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(40.dp).testTag("delete_quote_btn")
                    ) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete", tint = RoseError)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (language == "sw") "Futa Makadirio?" else "Delete Quote?") },
            text = { Text(if (language == "sw") "Je, una uhakika unataka kufuta rekodi hii ya ${quote.number}?" else "Are you sure you want to delete ${quote.number}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(quote)
                    }
                ) {
                    Text(AppStrings.t("btn_delete", language), color = RoseError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(AppStrings.t("btn_cancel", language))
                }
            }
        )
    }
}
