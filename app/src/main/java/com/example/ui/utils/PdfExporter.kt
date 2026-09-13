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
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 at 72dpi
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bgPaint = Paint()

        var y = 28f

        // 1. Top Header Background Banner (Dark Slate / Navy #0F172A)
        val headerHeight = 92f
        bgPaint.color = Color.rgb(15, 23, 42) // #0F172A Dark Navy
        canvas.drawRoundRect(RectF(28f, y, 567f, y + headerHeight), 6f, 6f, bgPaint)

        // Check if custom logo is available
        val logoBitmap = ImageUtils.loadLogoBitmap(businessSettings.logoPath, 160)
        var textLeft = 40f
        if (logoBitmap != null) {
            try {
                val logoSize = 54f
                val logoRect = RectF(40f, y + 18f, 40f + logoSize, y + 18f + logoSize)
                bgPaint.color = Color.WHITE
                canvas.drawRoundRect(RectF(38f, y + 16f, 42f + logoSize, y + 20f + logoSize), 6f, 6f, bgPaint)
                canvas.drawBitmap(logoBitmap, null, logoRect, paint)
                textLeft = 40f + logoSize + 14f
            } catch (e: Exception) {
                textLeft = 40f
            }
        }

        // Left Side: Company Name (Gold/Amber #F59E0B)
        paint.color = Color.rgb(245, 158, 11) // Amber 500
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 15f
        canvas.drawText(businessSettings.name.uppercase(), textLeft, y + 24f, paint)

        // Slogan (Italic Light Slate #94A3B8)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        paint.textSize = 9f
        paint.color = Color.rgb(148, 163, 184) // Slate 400
        canvas.drawText(businessSettings.slogan, textLeft, y + 38f, paint)

        // Contact info lines (Slate 300 #CBD5E1)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8.8f
        paint.color = Color.rgb(203, 213, 225) // Slate 300
        val phoneStr = "Tel: ${businessSettings.phone1}" + if (businessSettings.phone2.isNotBlank()) " / ${businessSettings.phone2}" else ""
        canvas.drawText(phoneStr, textLeft, y + 53f, paint)
        canvas.drawText("Email: ${businessSettings.email}", textLeft, y + 66f, paint)
        canvas.drawText("Loc: ${businessSettings.address}", textLeft, y + 79f, paint)

        // Right Side: Document Type & Metadata (Gold & White)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 17f
        paint.color = Color.rgb(245, 158, 11) // Amber 500
        val docTypeTitle = if (isInvoice) {
            if (isSw) "ANKARA RASMI" else "TAX INVOICE"
        } else {
            if (isSw) "MAKADIRIO" else "QUOTATION"
        }
        val docTypeWidth = paint.measureText(docTypeTitle)
        canvas.drawText(docTypeTitle, 552f - docTypeWidth, y + 24f, paint)

        // Doc Number (White)
        paint.textSize = 11f
        paint.color = Color.WHITE
        val numStr = quote.number
        val numWidth = paint.measureText(numStr)
        canvas.drawText(numStr, 552f - numWidth, y + 39f, paint)

        // Date & Validity (Slate 300)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9f
        paint.color = Color.rgb(203, 213, 225)
        val dateLine = if (isSw) "Tarehe: ${quote.date}" else "Date: ${quote.date}"
        canvas.drawText(dateLine, 552f - paint.measureText(dateLine), y + 53f, paint)

        if (!isInvoice && quote.validUntil.isNotBlank()) {
            val validLine = if (isSw) "Mwisho: ${quote.validUntil}" else "Valid: ${quote.validUntil}"
            canvas.drawText(validLine, 552f - paint.measureText(validLine), y + 66f, paint)
        }

        // Status pill on right (Emerald Green / Rose)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9f
        val statusText = if (isInvoice) {
            if (quote.paid) (if (isSw) "PAID / IMELIPWA" else "PAID") else (if (isSw) "UNPAID / HAIJALIPWA" else "UNPAID")
        } else {
            if (isSw) "ACTIVE / HAI" else "ACTIVE"
        }
        paint.color = if (isInvoice && !quote.paid) Color.rgb(239, 68, 68) else Color.rgb(16, 185, 129)
        canvas.drawText(statusText, 552f - paint.measureText(statusText), y + 81f, paint)

        y += headerHeight

        // Thin Gold Accent Line (#EAB308)
        bgPaint.color = Color.rgb(234, 179, 8) // Gold/Amber
        canvas.drawRect(28f, y, 567f, y + 3f, bgPaint)

        y += 12f

        // 2. Client / Bill To Card (#F8FAFC background with #E2E8F0 border)
        bgPaint.color = Color.rgb(248, 250, 252) // Slate 50
        val clientBoxHeight = if (quote.description.isNotBlank()) 66f else 54f
        canvas.drawRoundRect(RectF(28f, y, 567f, y + clientBoxHeight), 5f, 5f, bgPaint)

        paint.color = Color.rgb(226, 232, 240) // Slate 200 border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(RectF(28f, y, 567f, y + clientBoxHeight), 5f, 5f, paint)
        paint.style = Paint.Style.FILL

        // Client Title (Amber 700 / Gold #D97706)
        paint.color = Color.rgb(217, 119, 6) // Amber 600
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9f
        canvas.drawText(if (isSw) "MTEJA / CLIENT / BILL TO:" else "CLIENT / BILL TO:", 40f, y + 16f, paint)

        // Customer Name (Dark Slate #0F172A Bold)
        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 11.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(quote.customerName.uppercase(), 40f, y + 31f, paint)

        // Contact & Location info
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.rgb(71, 85, 105) // Slate 600
        paint.textSize = 9.2f
        val locParts = mutableListOf<String>()
        if (quote.customerPhone.isNotBlank()) locParts.add(quote.customerPhone)
        if (quote.customerLocation.isNotBlank()) locParts.add(quote.customerLocation)
        val customerInfoLine = locParts.joinToString(" • ")
        if (customerInfoLine.isNotBlank()) {
            canvas.drawText(customerInfoLine, 40f, y + 45f, paint)
        }

        if (quote.description.isNotBlank()) {
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            paint.textSize = 8.8f
            paint.color = Color.rgb(100, 116, 139)
            canvas.drawText("${if (isSw) "Kazi" else "Project"}: ${quote.description}", 40f, y + 59f, paint)
        }

        y += clientBoxHeight + 12f

        // 3. Items Table Header (Dark Navy #0F172A with Gold/Amber column headers)
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

        // Table Rows
        val maxItemsToShow = minOf(items.size, 20)
        for (i in 0 until maxItemsToShow) {
            val item = items[i]
            val rowHeight = 18.5f

            // Clean white or subtle light slate row
            if (i % 2 == 1) {
                bgPaint.color = Color.rgb(248, 250, 252)
                canvas.drawRect(28f, y, 567f, y + rowHeight, bgPaint)
            }

            // Bottom line for each row
            paint.color = Color.rgb(241, 245, 249)
            paint.strokeWidth = 0.5f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(28f, y + rowHeight, 567f, y + rowHeight, paint)
            paint.style = Paint.Style.FILL

            paint.color = Color.rgb(15, 23, 42) // Slate 900
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 9.2f

            canvas.drawText("${i + 1}", 36f, y + 13f, paint)
            val nameText = if (item.name.length > 38) item.name.substring(0, 35) + "..." else item.name
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

        // Labour / Workmanship Row (Highlighted in table)
        if (quote.labour > 0.0) {
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

        y += 14f

        // 4. Totals & Payment Details (Side-by-Side)
        val summaryBoxTop = y

        // Left Side: Payment Details Box ONLY for Invoice, Quotation Terms for Quotation
        bgPaint.color = Color.rgb(248, 250, 252) // Slate 50
        canvas.drawRoundRect(RectF(28f, summaryBoxTop, 310f, summaryBoxTop + 76f), 5f, 5f, bgPaint)

        paint.color = Color.rgb(226, 232, 240)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(RectF(28f, summaryBoxTop, 310f, summaryBoxTop + 76f), 5f, 5f, paint)
        paint.style = Paint.Style.FILL

        if (isInvoice) {
            paint.color = Color.rgb(15, 23, 42) // Dark Slate
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
            paint.color = Color.rgb(15, 23, 42) // Dark Slate
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

        // Right Side: Calculation Totals & Dark Grand Total Box
        paint.color = Color.rgb(71, 85, 105) // Slate 600
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        // Subtotals
        canvas.drawText(if (isSw) "Jumla ya Vifaa:" else "Materials Subtotal:", 335f, summaryBoxTop + 16f, paint)
        val matTotalStr = "${Formatters.formatNumber(quote.materialsTotal)} ${businessSettings.currency}"
        canvas.drawText(matTotalStr, 552f - paint.measureText(matTotalStr), summaryBoxTop + 16f, paint)

        canvas.drawText(if (isSw) "Gharama ya Ufundi:" else "Labour / Service:", 335f, summaryBoxTop + 32f, paint)
        val labourStr = "${Formatters.formatNumber(quote.labour)} ${businessSettings.currency}"
        canvas.drawText(labourStr, 552f - paint.measureText(labourStr), summaryBoxTop + 32f, paint)

        // Grand Total Box (Dark Slate #0F172A with Gold/Amber text)
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

        // 5. Bottom Gold Divider & Footer Note
        bgPaint.color = Color.rgb(234, 179, 8) // Amber Gold
        canvas.drawRect(28f, y, 567f, y + 2f, bgPaint)

        y += 14f

        // Left Footer: Thank you & inquiries
        paint.color = Color.rgb(180, 83, 9) // Amber 700
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

        doc.finishPage(page)

        // Clean file name
        val cleanNumber = quote.number.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val cleanCustomer = quote.customerName.replace(Regex("[^a-zA-Z0-9]"), "_").take(12)
        val fileName = "AGRITECH_${cleanNumber}_$cleanCustomer.pdf"

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
}
