package com.example.ui.utils

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.models.BusinessSettings
import com.example.data.models.QuoteEntity
import com.example.data.models.QuoteItem
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object PdfExporter {

    fun generateAndSavePdf(
        context: Context,
        quote: QuoteEntity,
        items: List<QuoteItem>,
        businessSettings: BusinessSettings,
        language: String
    ): File? {
        val isInvoice = quote.status == "invoice"
        val isSw = language == "sw"

        val doc = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bgPaint = Paint()

        val docTypeTitle = if (isInvoice) {
            if (isSw) "ANKARA RASMI" else "TAX INVOICE"
        } else {
            if (isSw) "MAKADIRIO" else "QUOTATION"
        }

        val hasLabour = quote.labour > 0.0
        val hasDescription = quote.description.isNotBlank()

        // Pre-calculate pagination plan
        val pagePlans = calculatePagePlans(
            itemsCount = items.size,
            hasLabour = hasLabour,
            hasDescription = hasDescription
        )
        val totalPages = pagePlans.size

        for (plan in pagePlans) {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, plan.pageNumber).create() // A4 at 72dpi
            val page = doc.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            var y = 28f

            if (plan.isFirstPage) {
                // ==========================================
                // SECTION 1: COMPANY DETAILS (Dedicated top section)
                // ==========================================
                val headerY = y
                val logoBitmap = ImageUtils.loadLogoBitmap(businessSettings.logoPath, 200)
                var companyInfoLeft = 28f
                if (logoBitmap != null) {
                    try {
                        val aspect = logoBitmap.width.toFloat() / logoBitmap.height.toFloat()
                        val logoHeight = 56f
                        val logoWidth = logoHeight * aspect
                        val logoRect = RectF(28f, headerY, 28f + logoWidth, headerY + logoHeight)
                        canvas.drawBitmap(logoBitmap, null, logoRect, paint)
                        companyInfoLeft = 28f + logoWidth + 16f
                    } catch (e: Exception) {
                        companyInfoLeft = 28f
                    }
                }

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 15f
                paint.color = Color.rgb(15, 23, 42) // Dark Navy #0F172A
                canvas.drawText(businessSettings.name.uppercase(), companyInfoLeft, headerY + 16f, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                paint.textSize = 9f
                paint.color = Color.rgb(71, 85, 105) // Slate 600
                canvas.drawText(businessSettings.slogan, companyInfoLeft, headerY + 28f, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textSize = 8.8f
                paint.color = Color.rgb(51, 65, 85) // Slate 700

                val phoneStr = "Tel: ${businessSettings.phone1}" + if (businessSettings.phone2.isNotBlank()) " / ${businessSettings.phone2}" else ""
                canvas.drawText(phoneStr, companyInfoLeft, headerY + 41f, paint)
                canvas.drawText("Email: ${businessSettings.email}", companyInfoLeft, headerY + 52f, paint)
                canvas.drawText("Loc: ${businessSettings.address}", companyInfoLeft, headerY + 63f, paint)

                val companySectionHeight = 72f
                y += companySectionHeight + 14f

                // ==========================================
                // SECTION 2: QUOTATION / INVOICE DETAILS
                // ==========================================
                val docInfoHeight = 44f
                bgPaint.color = Color.rgb(15, 23, 42) // #0F172A Dark Navy
                canvas.drawRoundRect(RectF(28f, y, 567f, y + docInfoHeight), 5f, 5f, bgPaint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 14f
                paint.color = Color.rgb(245, 158, 11) // Amber 500
                canvas.drawText(docTypeTitle, 40f, y + 18f, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 9.5f
                paint.color = Color.WHITE
                canvas.drawText(quote.number, 40f, y + 32f, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textSize = 8.5f
                paint.color = Color.rgb(203, 213, 225) // Slate 300

                val dateLine = if (isSw) "Tarehe: ${quote.date}" else "Date: ${quote.date}"
                val dateWidth = paint.measureText(dateLine)
                canvas.drawText(dateLine, 552f - dateWidth, y + 15f, paint)

                var validityText = ""
                if (!isInvoice && quote.validUntil.isNotBlank()) {
                    validityText = if (isSw) "Mwisho: ${quote.validUntil}" else "Valid: ${quote.validUntil}"
                } else if (isInvoice) {
                    validityText = if (isSw) "Mwisho wa Malipo: ${quote.validUntil}" else "Due Date: ${quote.validUntil}"
                }

                if (validityText.isNotBlank()) {
                    canvas.drawText(validityText, 552f - paint.measureText(validityText), y + 26f, paint)
                }

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 8.5f
                val statusText = if (isInvoice) {
                    if (quote.paid) (if (isSw) "PAID / IMELIPWA" else "PAID") else (if (isSw) "UNPAID / HAIJALIPWA" else "UNPAID")
                } else {
                    if (isSw) "ACTIVE / HAI" else "ACTIVE"
                }
                paint.color = if (isInvoice && !quote.paid) Color.rgb(239, 68, 68) else Color.rgb(16, 185, 129)
                canvas.drawText(statusText, 552f - paint.measureText(statusText), y + 37f, paint)

                y += docInfoHeight + 10f

                // Gold Accent Line
                bgPaint.color = Color.rgb(234, 179, 8)
                canvas.drawRect(28f, y, 567f, y + 2f, bgPaint)

                y += 12f

                // ==========================================
                // SECTION 3: CUSTOMER DETAILS (CLIENT / BILL TO)
                // ==========================================
                bgPaint.color = Color.rgb(248, 250, 252)
                val clientBoxHeight = if (hasDescription) 66f else 54f
                canvas.drawRoundRect(RectF(28f, y, 567f, y + clientBoxHeight), 5f, 5f, bgPaint)

                paint.color = Color.rgb(226, 232, 240)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRoundRect(RectF(28f, y, 567f, y + clientBoxHeight), 5f, 5f, paint)
                paint.style = Paint.Style.FILL

                paint.color = Color.rgb(217, 119, 6)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 9f
                canvas.drawText(if (isSw) "MTEJA / CLIENT / BILL TO:" else "CLIENT / BILL TO:", 40f, y + 16f, paint)

                paint.color = Color.rgb(15, 23, 42)
                paint.textSize = 11.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(quote.customerName.uppercase(), 40f, y + 31f, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.color = Color.rgb(71, 85, 105)
                paint.textSize = 9.2f
                val locParts = mutableListOf<String>()
                if (quote.customerPhone.isNotBlank()) locParts.add(quote.customerPhone)
                if (quote.customerLocation.isNotBlank()) locParts.add(quote.customerLocation)
                val customerInfoLine = locParts.joinToString(" • ")
                if (customerInfoLine.isNotBlank()) {
                    canvas.drawText(customerInfoLine, 40f, y + 45f, paint)
                }

                if (hasDescription) {
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    paint.textSize = 8.8f
                    paint.color = Color.rgb(100, 116, 139)
                    canvas.drawText("${if (isSw) "Kazi" else "Project"}: ${quote.description}", 40f, y + 59f, paint)
                }

                y += clientBoxHeight + 12f

            } else {
                // ==========================================
                // CONTINUATION PAGE HEADER (Page 2, 3...)
                // ==========================================
                val continuationHeaderHeight = 26f
                bgPaint.color = Color.rgb(15, 23, 42)
                canvas.drawRoundRect(RectF(28f, y, 567f, y + continuationHeaderHeight), 4f, 4f, bgPaint)

                // Left: Company & Doc Number
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 9.5f
                paint.color = Color.rgb(245, 158, 11) // Amber 500
                canvas.drawText(businessSettings.name.uppercase(), 36f, y + 17f, paint)

                val compWidth = paint.measureText(businessSettings.name.uppercase())
                paint.color = Color.WHITE
                val docLabel = " • $docTypeTitle ${quote.number}"
                canvas.drawText(docLabel, 36f + compWidth, y + 17f, paint)

                // Right: Customer Name & Date
                paint.color = Color.rgb(203, 213, 225)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textSize = 8.5f
                val rightContText = "${quote.customerName.uppercase()} • ${quote.date}"
                canvas.drawText(rightContText, 555f - paint.measureText(rightContText), y + 17f, paint)

                y += continuationHeaderHeight + 6f

                // Accent line
                bgPaint.color = Color.rgb(234, 179, 8)
                canvas.drawRect(28f, y, 567f, y + 2f, bgPaint)

                y += 8f
            }

            // ==========================================
            // TABLE HEADER (Repeated on every page with items)
            // ==========================================
            if (plan.startIndex < plan.endIndex || (plan.startIndex >= items.size && plan.includesLabour)) {
                bgPaint.color = Color.rgb(15, 23, 42) // Slate 900
                canvas.drawRoundRect(RectF(28f, y, 567f, y + 22f), 4f, 4f, bgPaint)

                paint.color = Color.rgb(245, 158, 11) // Amber 500
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 9.2f

                canvas.drawText("#", 36f, y + 14.5f, paint)
                canvas.drawText(if (isSw) "MAELEZO / VIFAA (DESCRIPTION)" else "DESCRIPTION / MATERIAL", 62f, y + 14.5f, paint)
                canvas.drawText(if (isSw) "IDADI / KIPIMO" else "QTY / UNIT", 325f, y + 14.5f, paint)
                canvas.drawText(if (isSw) "BEI (TZS)" else "PRICE (TZS)", 415f, y + 14.5f, paint)
                canvas.drawText(if (isSw) "JUMLA (TZS)" else "TOTAL (TZS)", 490f, y + 14.5f, paint)

                y += 22f

                // ==========================================
                // TABLE ROWS FOR CURRENT PAGE
                // ==========================================
                for (i in plan.startIndex until plan.endIndex) {
                    val item = items[i]
                    val rowHeight = 18.5f

                    if (i % 2 == 1) {
                        bgPaint.color = Color.rgb(248, 250, 252)
                        canvas.drawRect(28f, y, 567f, y + rowHeight, bgPaint)
                    }

                    paint.color = Color.rgb(241, 245, 249)
                    paint.strokeWidth = 0.5f
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(28f, y + rowHeight, 567f, y + rowHeight, paint)
                    paint.style = Paint.Style.FILL

                    paint.color = Color.rgb(15, 23, 42)
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.textSize = 9.2f

                    canvas.drawText("${i + 1}", 36f, y + 13f, paint)
                    val nameText = if (item.name.length > 40) item.name.substring(0, 37) + "..." else item.name
                    canvas.drawText(nameText, 62f, y + 13f, paint)

                    val qtyUnit = "${item.quantity.toInt()} ${item.unit}"
                    canvas.drawText(qtyUnit, 325f, y + 13f, paint)

                    val priceStr = Formatters.formatNumber(item.price)
                    canvas.drawText(priceStr, 415f, y + 13f, paint)

                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    val totalStr = Formatters.formatNumber(item.total)
                    canvas.drawText(totalStr, 490f, y + 13f, paint)

                    y += rowHeight
                }

                // Labour / Workmanship Row (if included on this page)
                if (plan.includesLabour) {
                    val labourRowHeight = 21f
                    bgPaint.color = Color.rgb(254, 252, 232) // Amber 50
                    canvas.drawRect(28f, y, 567f, y + labourRowHeight, bgPaint)

                    paint.color = Color.rgb(226, 232, 240)
                    paint.strokeWidth = 0.5f
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(28f, y + labourRowHeight, 567f, y + labourRowHeight, paint)
                    paint.style = Paint.Style.FILL

                    paint.color = Color.rgb(180, 83, 9) // Amber 700
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 9.8f
                    canvas.drawText("•", 36f, y + 14.5f, paint)

                    paint.color = Color.rgb(15, 23, 42)
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 9.2f
                    val labourLabel = if (isSw) "Gharama ya Ufundi na Kazi (Labour & Installation Charges)" else "Labour, Workmanship & Installation Charges"
                    canvas.drawText(labourLabel, 62f, y + 14.5f, paint)

                    val labourTotalStr = Formatters.formatNumber(quote.labour)
                    canvas.drawText(labourTotalStr, 490f, y + 14.5f, paint)

                    y += labourRowHeight
                }

                // Table bottom border
                paint.color = Color.rgb(203, 213, 225)
                paint.strokeWidth = 1f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(28f, y, 567f, y, paint)
                paint.style = Paint.Style.FILL
            }

            // ==========================================
            // SUMMARY, TOTALS, TERMS & SIGNATURE (Final Page Section)
            // ==========================================
            if (plan.includesSummary) {
                y += 14f
                val summaryBoxTop = y

                // Left Side: Payment Details Box (for Invoice) or Quotation Terms (for Quotation)
                bgPaint.color = Color.rgb(248, 250, 252)
                canvas.drawRoundRect(RectF(28f, summaryBoxTop, 310f, summaryBoxTop + 76f), 5f, 5f, bgPaint)

                paint.color = Color.rgb(226, 232, 240)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRoundRect(RectF(28f, summaryBoxTop, 310f, summaryBoxTop + 76f), 5f, 5f, paint)
                paint.style = Paint.Style.FILL

                if (isInvoice) {
                    paint.color = Color.rgb(15, 23, 42)
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 9f
                    canvas.drawText(if (isSw) "AKAUNTI ZA MALIPO / PAYMENT DETAILS" else "PAYMENT ACCOUNT / BANK DETAILS", 38f, summaryBoxTop + 16f, paint)

                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.textSize = 8.8f
                    paint.color = Color.rgb(51, 65, 85)

                    val lipaInfo = businessSettings.lipaNumber
                    val bankInfo = "${businessSettings.bankName}: ${businessSettings.bankAccountNumber}"
                    val accName = "(${businessSettings.bankAccountName})"

                    var currentY = summaryBoxTop + 31f
                    if (lipaInfo.isNotBlank()) {
                        canvas.drawText(lipaInfo, 38f, currentY, paint)
                        currentY += 13f
                    }
                    if (businessSettings.bankName.isNotBlank() || businessSettings.bankAccountNumber.isNotBlank()) {
                        canvas.drawText(bankInfo, 38f, currentY, paint)
                        currentY += 13f
                    }
                    if (businessSettings.bankAccountName.isNotBlank()) {
                        canvas.drawText(accName, 38f, currentY, paint)
                        currentY += 13f
                    }
                    if (businessSettings.mobileMoney.isNotBlank() && currentY <= summaryBoxTop + 70f) {
                        canvas.drawText("Simu: ${businessSettings.mobileMoney}", 38f, currentY, paint)
                    }
                } else {
                    paint.color = Color.rgb(15, 23, 42)
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 9f
                    canvas.drawText(if (isSw) "MASHARTI YA MAKADIRIO" else "QUOTATION TERMS", 38f, summaryBoxTop + 16f, paint)

                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.textSize = 8.2f
                    paint.color = Color.rgb(71, 85, 105)

                    val termsText = if (isSw)
                        "Haya makadirio (Quotation) ni halali kwa siku 14 pekee kuanzia tarehe iliyotolewa. Baada ya hapo, bei zinaweza kubadilika kulingana na hali ya soko."
                    else
                        "This quotation is valid for 14 days only from the date of issue. Thereafter, prices are subject to change according to market conditions."
                    val words = termsText.split(" ")
                    var line = ""
                    var termY = summaryBoxTop + 30f
                    for (w in words) {
                        if (paint.measureText(if (line.isEmpty()) w else "$line $w") < 262f) {
                            line = if (line.isEmpty()) w else "$line $w"
                        } else {
                            canvas.drawText(line, 38f, termY, paint)
                            termY += 11.5f
                            line = w
                        }
                    }
                    if (line.isNotEmpty() && termY <= summaryBoxTop + 70f) {
                        canvas.drawText(line, 38f, termY, paint)
                    }
                }

                // Right Side: Calculation Totals & Grand Total Box
                paint.color = Color.rgb(71, 85, 105)
                paint.textSize = 9.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                canvas.drawText(if (isSw) "Jumla ya Vifaa:" else "Materials Subtotal:", 335f, summaryBoxTop + 16f, paint)
                val matTotalStr = "${Formatters.formatNumber(quote.materialsTotal)} ${businessSettings.currency}"
                canvas.drawText(matTotalStr, 552f - paint.measureText(matTotalStr), summaryBoxTop + 16f, paint)

                canvas.drawText(if (isSw) "Gharama ya Ufundi:" else "Labour / Service:", 335f, summaryBoxTop + 32f, paint)
                val labourStr = "${Formatters.formatNumber(quote.labour)} ${businessSettings.currency}"
                canvas.drawText(labourStr, 552f - paint.measureText(labourStr), summaryBoxTop + 32f, paint)

                bgPaint.color = Color.rgb(15, 23, 42) // Slate 900
                canvas.drawRoundRect(RectF(330f, summaryBoxTop + 42f, 567f, summaryBoxTop + 76f), 5f, 5f, bgPaint)

                paint.color = Color.WHITE
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 10.5f
                canvas.drawText(if (isSw) "JUMLA KUU:" else "GRAND TOTAL:", 340f, summaryBoxTop + 63f, paint)

                paint.color = Color.rgb(245, 158, 11) // Amber 500
                paint.textSize = 12f
                val grandStr = "${Formatters.formatNumber(quote.grandTotal)} ${businessSettings.currency}"
                canvas.drawText(grandStr, 555f - paint.measureText(grandStr), summaryBoxTop + 63f, paint)

                y = summaryBoxTop + 92f

                // Gold Divider & Footer Note
                bgPaint.color = Color.rgb(234, 179, 8)
                canvas.drawRect(28f, y, 567f, y + 2f, bgPaint)

                y += 14f

                // Left Footer: Thank you & inquiries
                paint.color = Color.rgb(180, 83, 9)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 9.2f
                val thankYou = if (isSw) "Asante kwa kuchagua ${businessSettings.name}" else "Thank you for trusting ${businessSettings.name}"
                canvas.drawText(thankYou, 28f, y + 11f, paint)

                paint.color = Color.rgb(71, 85, 105)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textSize = 8.5f
                val inquiryPhone = if (businessSettings.phone1.isNotBlank()) businessSettings.phone1 else businessSettings.phone2
                canvas.drawText(if (isSw) "Mawasiliano na Malipo: $inquiryPhone" else "For payments & inquiries: $inquiryPhone", 28f, y + 24f, paint)
                canvas.drawText(businessSettings.name, 28f, y + 36f, paint)

                // Right Footer: Authorized Signature & Stamp
                val sigBitmap = ImageUtils.loadLogoBitmap(businessSettings.signaturePath, 140)
                if (sigBitmap != null) {
                    try {
                        val sigRect = RectF(430f, y - 12f, 520f, y + 18f)
                        canvas.drawBitmap(sigBitmap, null, sigRect, paint)
                    } catch (e: Exception) { }
                }

                paint.color = Color.rgb(148, 163, 184)
                paint.strokeWidth = 0.8f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(390f, y + 22f, 567f, y + 22f, paint)
                paint.style = Paint.Style.FILL

                paint.color = Color.rgb(71, 85, 105)
                paint.textSize = 8.5f
                val signLabel = if (isSw) "Saini na Muhuri Rasmi" else "Authorized Signature & Stamp"
                canvas.drawText(signLabel, 390f + (177f - paint.measureText(signLabel)) / 2f, y + 34f, paint)
            }

            // ==========================================
            // PAGE NUMBER FOOTER (On EVERY page at the bottom)
            // ==========================================
            paint.color = Color.rgb(148, 163, 184) // Slate 400
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 8f

            val pageLabel = if (isSw) "Ukurasa ${plan.pageNumber} kati ya $totalPages" else "Page ${plan.pageNumber} of $totalPages"
            val pageLabelWidth = paint.measureText(pageLabel)
            canvas.drawText(pageLabel, 567f - pageLabelWidth, 822f, paint)

            val brandingFooter = "AGRITECH HUB • ${quote.number}"
            canvas.drawText(brandingFooter, 28f, 822f, paint)

            doc.finishPage(page)
        }

        // Clean customer-based file name (e.g. BABA_PRECIOUS_QTN-002.pdf or BABA_PRECIOUS_INV-002.pdf)
        val cleanCustomer = quote.customerName.ifBlank { "CUSTOMER" }
            .trim()
            .replace(Regex("[^a-zA-Z0-9]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .uppercase()

        val cleanNumber = quote.number.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = if (cleanNumber.startsWith("QTN", ignoreCase = true) || cleanNumber.startsWith("INV", ignoreCase = true)) {
            "${cleanCustomer}_${cleanNumber}.pdf"
        } else {
            val prefix = if (isInvoice) "INV" else "QTN"
            "${cleanCustomer}_${prefix}-${cleanNumber}.pdf"
        }

        var outputFile: File? = null
        try {
            // Save to app external files / documents
            val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            if (!docsDir.exists()) docsDir.mkdirs()
            val file = File(docsDir, fileName)
            val outputStream: OutputStream = FileOutputStream(file)
            doc.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            outputFile = file

            // Also copy to public Downloads if possible
            saveToPublicDownloads(context, file, fileName)

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            doc.close()
        }

        return outputFile
    }

    private fun saveToPublicDownloads(context: Context, srcFile: File, fileName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = resolver.insert(collection, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        srcFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            } else {
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (publicDownloads.exists() || publicDownloads.mkdirs()) {
                    val destFile = File(publicDownloads, fileName)
                    srcFile.copyTo(destFile, overwrite = true)
                }
            }
        } catch (e: Exception) {
            // non-fatal, file is already safe in app external documents directory
        }
    }

    fun openOrSharePdf(context: Context, file: File, title: String) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                clipData = ClipData.newRawUri("PDF", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            grantUriPermissions(context, intent, uri)

            val chooser = Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Error opening PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun sharePdfDirectly(context: Context, file: File, title: String) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                clipData = ClipData.newRawUri("PDF", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            grantUriPermissions(context, sendIntent, uri)

            val chooser = Intent.createChooser(sendIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun grantUriPermissions(context: Context, intent: Intent, uri: Uri) {
        val resInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            context.grantUriPermission(
                packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private data class PagePlan(
        val pageNumber: Int,
        val isFirstPage: Boolean,
        val startIndex: Int,
        val endIndex: Int,
        val includesLabour: Boolean,
        val includesSummary: Boolean
    )

    private fun calculatePagePlans(
        itemsCount: Int,
        hasLabour: Boolean,
        hasDescription: Boolean
    ): List<PagePlan> {
        val plans = mutableListOf<PagePlan>()
        var currentIndex = 0
        var pageNum = 1

        val summaryHeight = 150f
        val rowHeight = 18.5f
        val labourHeight = 21f
        val maxPageY = 780f

        while (currentIndex < itemsCount || (pageNum == 1 && itemsCount == 0)) {
            val isFirst = (pageNum == 1)
            val startY = if (isFirst) {
                28f + (72f + 14f) + (44f + 10f) + (2f + 12f) + (if (hasDescription) 66f else 54f) + 12f + 22f
            } else {
                84f
            }

            var currentY = startY
            val startIndex = currentIndex

            while (currentIndex < itemsCount) {
                val isLastItem = (currentIndex == itemsCount - 1)
                val neededForRest = rowHeight + (if (hasLabour) labourHeight else 0f) + summaryHeight

                if (isLastItem) {
                    if (currentY + neededForRest <= maxPageY) {
                        currentY += rowHeight
                        currentIndex++
                        break
                    } else if (currentY + rowHeight + (if (hasLabour) labourHeight else 0f) <= maxPageY) {
                        currentY += rowHeight
                        currentIndex++
                        break
                    } else if (currentY + rowHeight <= maxPageY) {
                        currentY += rowHeight
                        currentIndex++
                        break
                    } else {
                        break
                    }
                } else {
                    if (currentY + rowHeight <= maxPageY) {
                        currentY += rowHeight
                        currentIndex++
                    } else {
                        break
                    }
                }
            }

            var includesLabour = false
            if (currentIndex == itemsCount && hasLabour) {
                if (currentY + labourHeight + summaryHeight <= maxPageY) {
                    includesLabour = true
                    currentY += labourHeight
                } else if (currentY + labourHeight <= maxPageY) {
                    includesLabour = true
                    currentY += labourHeight
                }
            }

            var includesSummary = false
            if (currentIndex == itemsCount && (!hasLabour || includesLabour)) {
                if (currentY + summaryHeight <= maxPageY + 20f) {
                    includesSummary = true
                }
            }

            plans.add(
                PagePlan(
                    pageNumber = pageNum,
                    isFirstPage = isFirst,
                    startIndex = startIndex,
                    endIndex = currentIndex,
                    includesLabour = includesLabour,
                    includesSummary = includesSummary
                )
            )
            pageNum++
        }

        val lastPlan = plans.lastOrNull()
        if (lastPlan != null && !lastPlan.includesSummary) {
            val remainingLabour = hasLabour && !lastPlan.includesLabour
            plans.add(
                PagePlan(
                    pageNumber = pageNum,
                    isFirstPage = false,
                    startIndex = itemsCount,
                    endIndex = itemsCount,
                    includesLabour = remainingLabour,
                    includesSummary = true
                )
            )
        }

        return plans
    }
}
