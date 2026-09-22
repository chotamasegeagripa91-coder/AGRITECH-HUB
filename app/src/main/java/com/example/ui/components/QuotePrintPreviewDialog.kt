package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.ui.utils.Formatters
import com.example.ui.utils.ImageUtils
import com.example.ui.utils.PdfExporter
import org.json.JSONArray

@Composable
fun QuotePrintPreviewDialog(
    quote: QuoteEntity,
    businessSettings: BusinessSettings,
    language: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val items = remember(quote.itemsJson) {
        val list = mutableListOf<QuoteItem>()
        try {
            val arr = JSONArray(quote.itemsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    QuoteItem(
                        id = obj.optString("id", i.toString()),
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

    val isInvoice = quote.status == "invoice"

    fun printDocument() {
        try {
            val html = generatePrintableHtml(quote, items, businessSettings, isInvoice, language)
            val webView = WebView(context)
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false

                override fun onPageFinished(view: WebView?, url: String?) {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                    val printAdapter = webView.createPrintDocumentAdapter("Agritech_${quote.number}")
                    val jobName = "${businessSettings.name} ${quote.number}"
                    printManager?.print(jobName, printAdapter, PrintAttributes.Builder().build())
                }
            }
            webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        } catch (e: Exception) {
            Toast.makeText(context, if (language == "sw") "Imeshindikana kufungua print manager: ${e.message}" else "Failed to open print manager: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.94f)
                .padding(6.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header action bar (Title + Close Button)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isInvoice) (if (language == "sw") "Ankara Rasmi (Print / PDF)" else "Official Invoice (Print / PDF)") else (if (language == "sw") "Makadirio Rasmi (Print / PDF)" else "Official Quotation (Print / PDF)"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons Row (Download PDF and Print)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val file = PdfExporter.generateAndSavePdf(
                                context = context,
                                quote = quote,
                                items = items,
                                businessSettings = businessSettings,
                                language = language
                            )
                            if (file != null) {
                                Toast.makeText(context, if (language == "sw") "PDF imepakuliwa!" else "PDF downloaded!", Toast.LENGTH_SHORT).show()
                                PdfExporter.openOrSharePdf(context, file, if (isInvoice) "Ankara" else "Makadirio")
                            } else {
                                Toast.makeText(context, if (language == "sw") "Imeshindikana kutengeneza PDF." else "Failed to generate PDF.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("download_pdf_preview_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = "Download PDF", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (language == "sw") "Pakua PDF" else "Download PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { printDocument() },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("execute_print_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = "Print", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Print", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Document Paper Container (White paper look)
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        val logoBitmap: Bitmap? = remember(businessSettings.logoPath) {
                            ImageUtils.loadLogoBitmap(businessSettings.logoPath, 120)
                        }

                        // 1. SECTION 1: COMPANY DETAILS (Independent dedicated banner at top)
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (logoBitmap != null) {
                                    Image(
                                        bitmap = logoBitmap.asImageBitmap(),
                                        contentDescription = "Company Logo",
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.White)
                                            .padding(2.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = businessSettings.name.uppercase(),
                                        color = AmberPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    if (businessSettings.slogan.isNotBlank()) {
                                        Text(
                                            text = businessSettings.slogan,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 9.sp,
                                            fontStyle = FontStyle.Italic
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Tel: ${businessSettings.phone1}" + if (businessSettings.phone2.isNotBlank()) " / ${businessSettings.phone2}" else "",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 9.sp
                                    )
                                    if (businessSettings.email.isNotBlank()) {
                                        Text(
                                            text = "Email: ${businessSettings.email}",
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 9.sp
                                        )
                                    }
                                    if (businessSettings.address.isNotBlank()) {
                                        Text(
                                            text = "Loc: ${businessSettings.address}",
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Gold accent line under company banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(AmberPrimary)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 2. SECTION 2: QUOTATION / INVOICE DETAILS (Dedicated independent section)
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (isInvoice) (if (language == "sw") "ANKARA RASMI" else "TAX INVOICE") else (if (language == "sw") "MAKADIRIO (QUOTATION)" else "OFFICIAL QUOTATION"),
                                        color = AmberPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = quote.number,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${if (language == "sw") "Tarehe" else "Date"}: ${quote.date}",
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 9.5.sp
                                        )
                                        if (quote.validUntil.isNotBlank()) {
                                            Text(
                                                text = "${if (language == "sw") "Mwisho" else "Valid Until"}: ${quote.validUntil}",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 9.sp
                                            )
                                        }
                                    }

                                    Surface(
                                        color = if (isInvoice && !quote.paid) RoseError.copy(alpha = 0.2f) else EmeraldSuccess.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, if (isInvoice && !quote.paid) RoseError else EmeraldSuccess)
                                    ) {
                                        Text(
                                            text = if (isInvoice) {
                                                if (quote.paid) (if (language == "sw") "PAID" else "PAID") else (if (language == "sw") "UNPAID" else "UNPAID")
                                            } else {
                                                if (language == "sw") "ACTIVE" else "ACTIVE"
                                            },
                                            color = if (isInvoice && !quote.paid) RoseError else EmeraldSuccess,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 3. SECTION 3: CUSTOMER DETAILS (Dedicated independent section)
                        Surface(
                            color = Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1.2f)) {
                                    Text(
                                        text = if (language == "sw") "MTEJA / CLIENT / BILL TO:" else "CLIENT / BILL TO:",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD97706)
                                    )
                                    Text(
                                        text = quote.customerName.uppercase(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    val locInfo = listOfNotNull(
                                        quote.customerPhone.takeIf { it.isNotBlank() },
                                        quote.customerLocation.takeIf { it.isNotBlank() }
                                    ).joinToString(" • ")
                                    if (locInfo.isNotBlank()) {
                                        Text(text = locInfo, fontSize = 10.sp, color = Color(0xFF475569))
                                    }
                                }
                                if (quote.description.isNotBlank()) {
                                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(0.8f)) {
                                        Text(
                                            text = if (language == "sw") "MAELEZO YA KAZI / MRADI:" else "PROJECT / WORK DESCRIPTION:",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF64748B)
                                        )
                                        Text(
                                            text = quote.description,
                                            fontSize = 10.sp,
                                            color = Color(0xFF0F172A),
                                            textAlign = TextAlign.End
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Table Header (Dark Navy #0F172A with Amber headers)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "#", color = AmberPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                            Text(text = if (language == "sw") "MAELEZO / VIFAA" else "DESCRIPTION / MATERIAL", color = AmberPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                            Text(text = if (language == "sw") "IDADI" else "QTY / UNIT", color = AmberPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp), textAlign = TextAlign.Center)
                            Text(text = if (language == "sw") "BEI (TZS)" else "PRICE (TZS)", color = AmberPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(65.dp), textAlign = TextAlign.End)
                            Text(text = if (language == "sw") "JUMLA (TZS)" else "TOTAL (TZS)", color = AmberPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(75.dp), textAlign = TextAlign.End)
                        }

                        // Table Rows
                        items.forEachIndexed { idx, item ->
                            val rowBg = if (idx % 2 == 0) Color.White else Color(0xFFF8FAFC)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBg)
                                    .border(BorderStroke(0.5.dp, Color(0xFFF1F5F9)))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "${idx + 1}", color = Color(0xFF64748B), fontSize = 9.sp, modifier = Modifier.width(22.dp))
                                Text(text = item.name, color = Color(0xFF0F172A), fontSize = 9.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.5f))
                                Text(text = "${item.quantity.toInt()} ${item.unit}", color = Color(0xFF475569), fontSize = 9.sp, modifier = Modifier.width(60.dp), textAlign = TextAlign.Center)
                                Text(text = Formatters.formatCurrency(item.price, "").trim(), color = Color(0xFF475569), fontSize = 9.sp, modifier = Modifier.width(65.dp), textAlign = TextAlign.End)
                                Text(text = Formatters.formatCurrency(item.total, "").trim(), color = Color(0xFF0F172A), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(75.dp), textAlign = TextAlign.End)
                            }
                        }

                        // Labour Row
                        if (quote.labour > 0.0) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFEFCE8))
                                    .border(BorderStroke(0.5.dp, Color(0xFFE2E8F0)))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "•", color = Color(0xFFB45309), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                                Text(
                                    text = if (language == "sw") "Gharama ya Ufundi na Kazi (Labour & Workmanship)" else "Labour, Workmanship & Installation Charges",
                                    color = Color(0xFF0F172A),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1.5f)
                                )
                                Text(text = "-", color = Color(0xFF64748B), fontSize = 9.sp, modifier = Modifier.width(60.dp), textAlign = TextAlign.Center)
                                Text(text = "-", color = Color(0xFF64748B), fontSize = 9.sp, modifier = Modifier.width(65.dp), textAlign = TextAlign.End)
                                Text(
                                    text = Formatters.formatCurrency(quote.labour, "").trim(),
                                    color = Color(0xFF0F172A),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(75.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Totals & Payment Accounts Side-by-Side (Payment details only for Invoice)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Left Box: Payment Details ONLY if Invoice, otherwise Quotation Terms
                            if (isInvoice) {
                                Surface(
                                    color = Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.weight(1.1f)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = if (language == "sw") "AKAUNTI ZA MALIPO / BANK DETAILS" else "PAYMENT ACCOUNT / BANK DETAILS",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        if (businessSettings.lipaNumber.isNotBlank()) {
                                            Text(text = businessSettings.lipaNumber, fontSize = 8.5.sp, color = Color(0xFF334155))
                                        }
                                        if (businessSettings.bankName.isNotBlank() || businessSettings.bankAccountNumber.isNotBlank()) {
                                            Text(text = "${businessSettings.bankName}: ${businessSettings.bankAccountNumber}", fontSize = 8.5.sp, color = Color(0xFF334155))
                                        }
                                        if (businessSettings.bankAccountName.isNotBlank()) {
                                            Text(text = "(${businessSettings.bankAccountName})", fontSize = 8.5.sp, color = Color(0xFF64748B))
                                        }
                                        if (businessSettings.mobileMoney.isNotBlank()) {
                                            Text(text = if (language == "sw") "Simu: ${businessSettings.mobileMoney}" else "Mobile: ${businessSettings.mobileMoney}", fontSize = 8.5.sp, color = Color(0xFF334155))
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    color = Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.weight(1.1f)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = if (language == "sw") "MASHARTI YA MAKADIRIO" else "QUOTATION TERMS",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = if (language == "sw")
                                                "Haya makadirio (Quotation) ni halali kwa siku 14 pekee kuanzia tarehe iliyotolewa. Baada ya hapo, bei zinaweza kubadilika kulingana na hali ya soko."
                                            else
                                                "This quotation is valid for 14 days only from the date of issue. Thereafter, prices are subject to change according to market conditions.",
                                            fontSize = 8.sp,
                                            color = Color(0xFF475569),
                                            lineHeight = 11.sp
                                        )
                                    }
                                }
                            }

                            // Right: Summary and Dark Grand Total Box
                            Column(
                                modifier = Modifier.weight(0.9f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = if (language == "sw") "Jumla ya Vifaa:" else "Materials Subtotal:", fontSize = 9.5.sp, color = Color(0xFF475569))
                                    Text(text = "${Formatters.formatCurrency(quote.materialsTotal, "").trim()} ${businessSettings.currency}", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = if (language == "sw") "Gharama ya Ufundi:" else "Labour / Service:", fontSize = 9.5.sp, color = Color(0xFF475569))
                                    Text(text = "${Formatters.formatCurrency(quote.labour, "").trim()} ${businessSettings.currency}", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Surface(
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = if (language == "sw") "JUMLA KUU:" else "GRAND TOTAL:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(text = "${Formatters.formatCurrency(quote.grandTotal, "").trim()} ${businessSettings.currency}", fontSize = 11.sp, fontWeight = FontWeight.Black, color = AmberPrimary)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(AmberPrimary)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Footer Note & Signature
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (language == "sw") "Asante kwa kuchagua ${businessSettings.name}" else "Thank you for trusting ${businessSettings.name}",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309)
                                )
                                val inquiryPhone = if (businessSettings.phone1.isNotBlank()) businessSettings.phone1 else businessSettings.phone2
                                Text(
                                    text = if (language == "sw") "Mawasiliano na Malipo: $inquiryPhone" else "For payments & inquiries: $inquiryPhone",
                                    fontSize = 8.sp,
                                    color = Color(0xFF64748B)
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Box(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .height(1.dp)
                                        .background(Color(0xFFCBD5E1))
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (language == "sw") "Saini na Muhuri Rasmi" else "Authorized Signature & Stamp",
                                    fontSize = 8.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun generatePrintableHtml(
    quote: QuoteEntity,
    items: List<QuoteItem>,
    business: BusinessSettings,
    isInvoice: Boolean,
    language: String = "sw"
): String {
    val isSw = language == "sw"
    val itemsRows = items.mapIndexed { idx, item ->
        """
        <tr style="background-color: ${if (idx % 2 == 0) "#ffffff" else "#f8fafc"}; page-break-inside: avoid;">
            <td style="padding: 7px 8px; border-bottom: 1px solid #e2e8f0; text-align: center; color: #64748b;">${idx + 1}</td>
            <td style="padding: 7px 8px; border-bottom: 1px solid #e2e8f0; font-weight: 500; color: #0f172a;">${item.name}</td>
            <td style="padding: 7px 8px; border-bottom: 1px solid #e2e8f0; text-align: center; color: #475569;">${item.quantity.toInt()} ${item.unit}</td>
            <td style="padding: 7px 8px; border-bottom: 1px solid #e2e8f0; text-align: right; color: #475569;">${Formatters.formatCurrency(item.price, "").trim()}</td>
            <td style="padding: 7px 8px; border-bottom: 1px solid #e2e8f0; text-align: right; font-weight: bold; color: #0f172a;">${Formatters.formatCurrency(item.total, "").trim()}</td>
        </tr>
        """.trimIndent()
    }.joinToString("\n")

    val labourRowHtml = if (quote.labour > 0.0) {
        """
        <tr style="background-color: #fefce8; border-bottom: 1px solid #e2e8f0; page-break-inside: avoid;">
            <td style="padding: 7px 8px; text-align: center; color: #b45309; font-weight: bold;">•</td>
            <td style="padding: 7px 8px; font-weight: bold; color: #0f172a;">${if (isSw) "Gharama ya Ufundi na Kazi (Labour & Installation Charges)" else "Labour, Workmanship & Installation Charges"}</td>
            <td style="padding: 7px 8px; text-align: center; color: #64748b;">-</td>
            <td style="padding: 7px 8px; text-align: right; color: #64748b;">-</td>
            <td style="padding: 7px 8px; text-align: right; font-weight: bold; color: #0f172a;">${Formatters.formatCurrency(quote.labour, "").trim()}</td>
        </tr>
        """.trimIndent()
    } else ""

    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="utf-8">
        <title>${quote.number}</title>
        <style>
            @page { margin: 15mm 12mm; size: auto; }
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; margin: 10px; color: #0f172a; font-size: 12px; }
            .company-banner { background: #0f172a; color: #ffffff; padding: 14px 18px; border-radius: 8px; }
            .brand-title { color: #f59e0b; font-size: 16px; font-weight: 900; margin: 0 0 2px 0; text-transform: uppercase; letter-spacing: 0.5px; }
            .brand-slogan { color: #94a3b8; font-size: 10.5px; font-style: italic; margin: 0 0 4px 0; }
            .brand-contacts { color: #cbd5e1; font-size: 10px; line-height: 1.4; margin: 0; }
            .gold-line { height: 3px; background: #eab308; margin: 6px 0 10px 0; border-radius: 2px; }
            .doc-section { background: #1e293b; color: #ffffff; padding: 10px 16px; border-radius: 6px; display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
            .doc-type { color: #f59e0b; font-size: 14px; font-weight: 900; margin: 0 0 2px 0; text-transform: uppercase; }
            .doc-number { color: #ffffff; font-size: 13px; font-weight: bold; margin: 0; }
            .doc-meta-right { text-align: right; }
            .doc-date { color: #cbd5e1; font-size: 10px; margin: 0; }
            .status-badge { display: inline-block; padding: 2px 6px; border-radius: 4px; border: 1px solid ${if (isInvoice && !quote.paid) "#ef4444" else "#10b981"}; color: ${if (isInvoice && !quote.paid) "#ef4444" else "#10b981"}; font-weight: bold; font-size: 9px; margin-top: 2px; }
            .client-box { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 10px 14px; margin-bottom: 12px; display: flex; justify-content: space-between; }
            .client-label { color: #d97706; font-size: 9.5px; font-weight: bold; margin-bottom: 3px; }
            .client-name { font-size: 12.5px; font-weight: bold; color: #0f172a; text-transform: uppercase; }
            .client-info { font-size: 10.5px; color: #475569; margin-top: 2px; }
            table.items { width: 100%; border-collapse: collapse; margin-top: 6px; font-size: 11px; }
            table.items thead { display: table-header-group; }
            table.items tr { page-break-inside: avoid; }
            table.items th { background: #0f172a; color: #f59e0b; padding: 7px 8px; text-align: left; font-size: 10.5px; text-transform: uppercase; }
            .bottom-section { display: flex; justify-content: space-between; margin-top: 14px; align-items: flex-start; page-break-inside: avoid; }
            .payment-box { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 10px 14px; width: 48%; }
            .payment-title { font-size: 10px; font-weight: bold; color: #0f172a; margin-bottom: 4px; }
            .totals-box { width: 46%; }
            .totals-table { width: 100%; font-size: 11px; }
            .totals-table td { padding: 3px 0; }
            .grand-total-banner { background: #0f172a; color: #ffffff; padding: 7px 10px; border-radius: 5px; display: flex; justify-content: space-between; font-weight: bold; font-size: 12px; margin-top: 5px; }
            .grand-total-amount { color: #f59e0b; font-size: 13px; font-weight: 900; }
            .footer-line { height: 2px; background: #eab308; margin: 16px 0 10px 0; }
            .footer-row { display: flex; justify-content: space-between; align-items: center; font-size: 9.5px; page-break-inside: avoid; }
            @media print {
                body { margin: 0; }
                .company-banner, .doc-section, .client-box, table.items, .bottom-section, .footer-row { page-break-inside: avoid; }
            }
        </style>
    </head>
    <body>
        <!-- 1. SECTION 1: COMPANY DETAILS -->
        <div class="company-banner">
            <div class="brand-title">${business.name}</div>
            ${if (business.slogan.isNotBlank()) "<div class='brand-slogan'>" + business.slogan + "</div>" else ""}
            <div class="brand-contacts">
                Tel: ${business.phone1}${if (business.phone2.isNotBlank()) " / " + business.phone2 else ""}<br>
                ${if (business.email.isNotBlank()) "Email: " + business.email + "<br>" else ""}
                ${if (business.address.isNotBlank()) "Loc: " + business.address else ""}
            </div>
        </div>

        <div class="gold-line"></div>

        <!-- 2. SECTION 2: QUOTATION / INVOICE DETAILS -->
        <div class="doc-section">
            <div>
                <div class="doc-type">${if (isInvoice) (if (isSw) "ANKARA RASMI" else "TAX INVOICE") else (if (isSw) "MAKADIRIO (QUOTATION)" else "OFFICIAL QUOTATION")}</div>
                <div class="doc-number">${quote.number}</div>
            </div>
            <div class="doc-meta-right">
                <div class="doc-date">${if (isSw) "Tarehe" else "Date"}: ${quote.date}</div>
                ${if (quote.validUntil.isNotBlank()) "<div class='doc-date'>" + (if (isSw) "Mwisho" else "Valid Until") + ": " + quote.validUntil + "</div>" else ""}
                <div class="status-badge">${if (isInvoice) (if (quote.paid) "PAID" else "UNPAID") else "ACTIVE"}</div>
            </div>
        </div>

        <!-- 3. SECTION 3: CUSTOMER DETAILS -->
        <div class="client-box">
            <div>
                <div class="client-label">${if (isSw) "MTEJA / CLIENT / BILL TO:" else "CLIENT / BILL TO:"}</div>
                <div class="client-name">${quote.customerName}</div>
                <div class="client-info">${listOfNotNull(quote.customerPhone.takeIf { it.isNotBlank() }, quote.customerLocation.takeIf { it.isNotBlank() }).joinToString(" • ")}</div>
            </div>
            ${if (quote.description.isNotBlank()) """
            <div style="text-align: right;">
                <div class="client-label">${if (isSw) "MAELEZO YA KAZI / MRADI:" else "PROJECT / WORK DESCRIPTION:"}</div>
                <div style="color: #0f172a; font-size: 10.5px;">${quote.description}</div>
            </div>
            """ else ""}
        </div>

        <!-- 4. MATERIALS TABLE -->
        <table class="items">
            <thead>
                <tr>
                    <th style="width: 25px; text-align: center;">#</th>
                    <th>${if (isSw) "MAELEZO / VIFAA" else "DESCRIPTION / MATERIAL"}</th>
                    <th style="width: 80px; text-align: center;">${if (isSw) "IDADI" else "QTY / UNIT"}</th>
                    <th style="width: 85px; text-align: right;">${if (isSw) "BEI (TZS)" else "PRICE (TZS)"}</th>
                    <th style="width: 95px; text-align: right;">${if (isSw) "JUMLA (TZS)" else "TOTAL (TZS)"}</th>
                </tr>
            </thead>
            <tbody>
                $itemsRows
                $labourRowHtml
            </tbody>
        </table>

        <!-- 5. BOTTOM SECTION (TERMS / ACCOUNTS & TOTALS) -->
        <div class="bottom-section">
            ${if (isInvoice) """
            <div class="payment-box">
                <div class="payment-title">${if (isSw) "AKAUNTI ZA MALIPO / PAYMENT DETAILS" else "PAYMENT ACCOUNT / BANK DETAILS"}</div>
                <div style="font-size: 9.5px; color: #475569; line-height: 1.4;">
                    ${if (business.lipaNumber.isNotBlank()) "<div>" + business.lipaNumber + "</div>" else ""}
                    ${if (business.bankName.isNotBlank() || business.bankAccountNumber.isNotBlank()) "<div>" + business.bankName + ": " + business.bankAccountNumber + "</div>" else ""}
                    ${if (business.bankAccountName.isNotBlank()) "<div>(" + business.bankAccountName + ")</div>" else ""}
                    ${if (business.mobileMoney.isNotBlank()) "<div>" + (if (isSw) "Simu: " else "Mobile: ") + business.mobileMoney + "</div>" else ""}
                </div>
            </div>
            """ else """
            <div class="payment-box">
                <div class="payment-title">${if (isSw) "MASHARTI YA MAKADIRIO" else "QUOTATION TERMS"}</div>
                <div style="font-size: 9px; color: #475569; line-height: 1.4;">
                    ${if (isSw) "Haya makadirio (Quotation) ni halali kwa siku 14 pekee kuanzia tarehe iliyotolewa. Baada ya hapo, bei zinaweza kubadilika kulingana na hali ya soko." else "This quotation is valid for 14 days only from the date of issue. Thereafter, prices are subject to change according to market conditions."}
                </div>
            </div>
            """}

            <div class="totals-box">
                <table class="totals-table">
                    <tr>
                        <td style="color: #475569;">${if (isSw) "Jumla ya Vifaa:" else "Materials Subtotal:"}</td>
                        <td style="text-align: right; font-weight: 600; color: #0f172a;">${Formatters.formatCurrency(quote.materialsTotal, "").trim()} ${business.currency}</td>
                    </tr>
                    <tr>
                        <td style="color: #475569;">${if (isSw) "Gharama ya Ufundi:" else "Labour / Service:"}</td>
                        <td style="text-align: right; font-weight: 600; color: #0f172a;">${Formatters.formatCurrency(quote.labour, "").trim()} ${business.currency}</td>
                    </tr>
                </table>
                <div class="grand-total-banner">
                    <span>${if (isSw) "JUMLA KUU:" else "GRAND TOTAL:"}</span>
                    <span class="grand-total-amount">${Formatters.formatCurrency(quote.grandTotal, "").trim()} ${business.currency}</span>
                </div>
            </div>
        </div>

        <div class="footer-line"></div>

        <!-- 6. FOOTER ROW -->
        <div class="footer-row">
            <div>
                <div style="color: #b45309; font-weight: bold;">${if (isSw) "Asante kwa kuchagua " + business.name else "Thank you for trusting " + business.name}</div>
                <div style="color: #64748b;">${if (isSw) "Mawasiliano na Malipo: " + (if (business.phone1.isNotBlank()) business.phone1 else business.phone2) else "For payments & inquiries: " + (if (business.phone1.isNotBlank()) business.phone1 else business.phone2)}</div>
            </div>
            <div style="text-align: right;">
                <div style="border-top: 1px solid #cbd5e1; width: 140px; margin-bottom: 3px; display: inline-block;"></div><br>
                <span style="color: #64748b;">${if (isSw) "Saini na Muhuri Rasmi" else "Authorized Signature & Stamp"}</span>
            </div>
        </div>
    </body>
    </html>
    """.trimIndent()
}
