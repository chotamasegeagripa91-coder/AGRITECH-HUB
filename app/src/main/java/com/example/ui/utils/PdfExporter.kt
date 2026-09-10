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

        var y = 30f

        // 1. Top Header Background Banner (Dark Slate / Navy #0F172A)
        bgPaint.color = Color.rgb(15, 23, 42) // #0F172A Dark Navy
        canvas.drawRoundRect(RectF(30f, y, 565f, y + 84f), 6f, 6f, bgPaint)

        // Check if custom logo is available
        val logoBitmap = ImageUtils.loadLogoBitmap(businessSettings.logoPath, 140)
        var textLeft = 42f
        if (logoBitmap != null) {
            try {
                val logoSize = 48f
                val logoRect = RectF(42f, y + 18f, 42f + logoSize, y + 18f + logoSize)
                bgPaint.color = Color.WHITE
                canvas.drawRoundRect(RectF(40f, y + 16f, 44f + logoSize, y + 20f + logoSize), 5f, 5f, bgPaint)
                canvas.drawBitmap(logoBitmap, null, logoRect, paint)
                textLeft = 42f + logoSize + 12f
            } catch (e: Exception) {
                textLeft = 42f
            }
        }

        // Left Side: Company Name (Gold/Amber #F59E0B)
        paint.color = Color.rgb(245, 158, 11) // Amber 500
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 13.5f
        canvas.drawText(businessSettings.name.uppercase(), textLeft, y + 22f, paint)

        // Slogan (Italic Light Slate #94A3B8)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        paint.textSize = 7.5f
        paint.color = Color.rgb(148, 163, 184) // Slate 400
        canvas.drawText(businessSettings.slogan, textLeft, y + 34f, paint)

        // Contact info lines (Slate 300 #CBD5E1)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7f
        paint.color = Color.rgb(203, 213, 225) // Slate 300
        canvas.drawText("Tel: ${businessSettings.phone1}" + if (businessSettings.phone2.isNotBlank()) " / ${businessSettings.phone2}" else "", textLeft, y + 46f, paint)
        canvas.drawText("Email: ${businessSettings.email}", textLeft, y + 57f, paint)
        canvas.drawText("Loc: ${businessSettings.address}", textLeft, y + 68f, paint)

        // Right Side: Document Type & Metadata (Gold & White)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 15f
        paint.color = Color.rgb(245, 158, 11) // Amber 500
        val docTypeTitle = if (isInvoice) {
            if (isSw) "ANKARA RASMI" else "TAX INVOICE"
        } else {
            if (isSw) "MAKADIRIO" else "QUOTATION"
        }
        val docTypeWidth = paint.measureText(docTypeTitle)
        canvas.drawText(docTypeTitle, 550f - docTypeWidth, y + 22f, paint)

        // Doc Number (White)
        paint.textSize = 9.5f
        paint.color = Color.WHITE
        val numStr = quote.number
        val numWidth = paint.measureText(numStr)
        canvas.drawText(numStr, 550f - numWidth, y + 35f, paint)

        // Date & Validity (Slate 300)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7.2f
        paint.color = Color.rgb(203, 213, 225)
        val dateLine = if (isSw) "Tarehe: ${quote.date}" else "Date: ${quote.date}"
        canvas.drawText(dateLine, 550f - paint.measureText(dateLine), y + 47f, paint)

        if (!isInvoice && quote.validUntil.isNotBlank()) {
            val validLine = if (isSw) "Mwisho: ${quote.validUntil}" else "Valid: ${quote.validUntil}"
            canvas.drawText(validLine, 550f - paint.measureText(validLine), y + 58f, paint)
        }

        // Status pill on right (Emerald Green / Rose)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 7.5f
        val statusText = if (isInvoice) {
            if (quote.paid) (if (isSw) "PAID / IMELIPWA" else "PAID") else (if (isSw) "UNPAID / HAIJALIPWA" else "UNPAID")
        } else {
            if (isSw) "ACTIVE / HAI" else "ACTIVE"
        }
        paint.color = if (isInvoice && !quote.paid) Color.rgb(239, 68, 68) else Color.rgb(16, 185, 129)
        canvas.drawText(statusText, 550f - paint.measureText(statusText), y + 72f, paint)

        y += 84f

        // Thin Gold Accent Line (#EAB308)
        bgPaint.color = Color.rgb(234, 179, 8) // Gold/Amber
        canvas.drawRect(30f, y, 565f, y + 3f, bgPaint)

        y += 12f

        // 2. Client / Bill To Card (#F8FAFC background with #E2E8F0 border)
        bgPaint.color = Color.rgb(248, 250, 252) // Slate 50
        val clientBoxHeight = if (quote.description.isNotBlank()) 58f else 48f
        canvas.drawRoundRect(RectF(30f, y, 565f, y + clientBoxHeight), 4f, 4f, bgPaint)

        paint.color = Color.rgb(226, 232, 240) // Slate 200 border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(RectF(30f, y, 565f, y + clientBoxHeight), 4f, 4f, paint)
        paint.style = Paint.Style.FILL

        // Client Title (Amber 700 / Gold #D97706)
        paint.color = Color.rgb(217, 119, 6) // Amber 600
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 7.5f
        canvas.drawText(if (isSw) "MTEJA / CLIENT / BILL TO:" else "CLIENT / BILL TO:", 42f, y + 14f, paint)

        // Customer Name (Dark Slate #0F172A Bold)
        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(quote.customerName.uppercase(), 42f, y + 27f, paint)

        // Contact & Location info
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.rgb(71, 85, 105) // Slate 600
        paint.textSize = 7.5f
        val locParts = mutableListOf<String>()
        if (quote.customerPhone.isNotBlank()) locParts.add(quote.customerPhone)
        if (quote.customerLocation.isNotBlank()) locParts.add(quote.customerLocation)
        val customerInfoLine = locParts.joinToString(" • ")
        if (customerInfoLine.isNotBlank()) {
            canvas.drawText(customerInfoLine, 42f, y + 39f, paint)
        }

        if (quote.description.isNotBlank()) {
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            paint.textSize = 7f
            paint.color = Color.rgb(100, 116, 139)
            canvas.drawText("${if (isSw) "Kazi" else "Project"}: ${quote.description}", 42f, y + 51f, paint)
        }

        y += clientBoxHeight + 12f

        // 3. Items Table Header (Dark Navy #0F172A with Gold/Amber column headers)
        bgPaint.color = Color.rgb(15, 23, 42) // Slate 900
        canvas.drawRoundRect(RectF(30f, y, 565f, y + 20f), 3f, 3f, bgPaint)

        paint.color = Color.rgb(245, 158, 11) // Amber 500
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 7.5f

        canvas.drawText("#", 38f, y + 13f, paint)
        canvas.drawText(if (isSw) "MAELEZO / VIFAA (DESCRIPTION)" else "DESCRIPTION / MATERIAL", 65f, y + 13f, paint)
        canvas.drawText(if (isSw) "IDADI / KIPIMO" else "QTY / UNIT", 330f, y + 13f, paint)
        canvas.drawText(if (isSw) "BEI (TZS)" else "PRICE (TZS)", 420f, y + 13f, paint)
        canvas.drawText(if (isSw) "JUMLA (TZS)" else "TOTAL (TZS)", 495f, y + 13f, paint)

        y += 20f

        // Table Rows
        val maxItemsToShow = minOf(items.size, 20)
        for (i in 0 until maxItemsToShow) {
            val item = items[i]
            val rowHeight = 16.5f

            // Clean white or subtle light slate row
            if (i % 2 == 1) {
                bgPaint.color = Color.rgb(248, 250, 252)
                canvas.drawRect(30f, y, 565f, y + rowHeight, bgPaint)
            }

            // Bottom line for each row
            paint.color = Color.rgb(241, 245, 249)
            paint.strokeWidth = 0.5f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(30f, y + rowHeight, 565f, y + rowHeight, paint)
            paint.style = Paint.Style.FILL

            paint.color = Color.rgb(15, 23, 42) // Slate 900
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 7.8f

            canvas.drawText("${i + 1}", 38f, y + 11.5f, paint)
            val nameText = if (item.name.length > 40) item.name.substring(0, 37) + "..." else item.name
            canvas.drawText(nameText, 65f, y + 11.5f, paint)

            val qtyUnit = "${item.quantity.toInt()} ${item.unit}"
            canvas.drawText(qtyUnit, 330f, y + 11.5f, paint)

            val priceStr = Formatters.formatNumber(item.price)
            canvas.drawText(priceStr, 420f, y + 11.5f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val totalStr = Formatters.formatNumber(item.total)
            canvas.drawText(totalStr, 495f, y + 11.5f, paint)

            y += rowHeight
        }

        // Labour / Workmanship Row (Highlighted in table)
        if (quote.labour > 0.0) {
            val labourRowHeight = 18f
            bgPaint.color = Color.rgb(254, 252, 232) // Amber 50
            canvas.drawRect(30f, y, 565f, y + labourRowHeight, bgPaint)

            paint.color = Color.rgb(226, 232, 240)
            paint.strokeWidth = 0.5f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(30f, y + labourRowHeight, 565f, y + labourRowHeight, paint)
            paint.style = Paint.Style.FILL

            paint.color = Color.rgb(180, 83, 9) // Amber 700
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 8.5f
            canvas.drawText("•", 38f, y + 12f, paint)

            paint.color = Color.rgb(15, 23, 42)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 7.8f
            val labourLabel = if (isSw) "Gharama ya Ufundi na Kazi (Labour & Installation Charges)" else "Labour, Workmanship & Installation Charges"
            canvas.drawText(labourLabel, 65f, y + 12f, paint)

            val labourTotalStr = Formatters.formatNumber(quote.labour)
            canvas.drawText(labourTotalStr, 495f, y + 12f, paint)

            y += labourRowHeight
        }

        // Table bottom border
        paint.color = Color.rgb(203, 213, 225)
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(30f, y, 565f, y, paint)
        paint.style = Paint.Style.FILL

        y += 14f

        // 4. Totals & Payment Details (Side-by-Side as in reference PDF)
        val summaryBoxTop = y

        // Left Side: Payment Details Box ONLY for Invoice, Quotation Terms for Quotation
        bgPaint.color = Color.rgb(248, 250, 252) // Slate 50
        canvas.drawRoundRect(RectF(30f, summaryBoxTop, 310f, summaryBoxTop + 68f), 4f, 4f, bgPaint)

        paint.color = Color.rgb(226, 232, 240)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(RectF(30f, summaryBoxTop, 310f, summaryBoxTop + 68f), 4f, 4f, paint)
        paint.style = Paint.Style.FILL

        if (isInvoice) {
            paint.color = Color.rgb(15, 23, 42) // Dark Slate
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 7.5f
            canvas.drawText(if (isSw) "AKAUNTI ZA MALIPO / PAYMENT DETAILS" else "PAYMENT ACCOUNT / BANK DETAILS", 40f, summaryBoxTop + 15f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 7.2f
            paint.color = Color.rgb(51, 65, 85)

            val lipaInfo = businessSettings.lipaNumber
            val bankInfo = "${businessSettings.bankName}: ${businessSettings.bankAccountNumber}"
            val accName = "(${businessSettings.bankAccountName})"

            var currentY = summaryBoxTop + 28f
            if (lipaInfo.isNotBlank()) {
                canvas.drawText(lipaInfo, 40f, currentY, paint)
                currentY += 12f
            }
            if (businessSettings.bankName.isNotBlank() || businessSettings.bankAccountNumber.isNotBlank()) {
                canvas.drawText(bankInfo, 40f, currentY, paint)
                currentY += 12f
            }
            if (businessSettings.bankAccountName.isNotBlank()) {
                canvas.drawText(accName, 40f, currentY, paint)
                currentY += 12f
            }
            if (businessSettings.mobileMoney.isNotBlank() && currentY <= summaryBoxTop + 62f) {
                canvas.drawText("Simu: ${businessSettings.mobileMoney}", 40f, currentY, paint)
            }
        } else {
            paint.color = Color.rgb(15, 23, 42) // Dark Slate
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 7.5f
            canvas.drawText(if (isSw) "MASHARTI YA MAKADIRIO" else "QUOTATION TERMS", 40f, summaryBoxTop + 15f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 6.5f
            paint.color = Color.rgb(71, 85, 105)

            val termsText = if (isSw)
                "Haya makadirio (Quotation) ni halali kwa siku 14 pekee kuanzia tarehe iliyotolewa. Baada ya hapo, bei zinaweza kubadilika kulingana na hali ya soko."
            else
                "This quotation is valid for 14 days only from the date of issue. Thereafter, prices are subject to change according to market conditions."
            val words = termsText.split(" ")
            var line = ""
            var termY = summaryBoxTop + 27f
            for (w in words) {
                if (paint.measureText(if (line.isEmpty()) w else "$line $w") < 260f) {
                    line = if (line.isEmpty()) w else "$line $w"
                } else {
                    canvas.drawText(line, 40f, termY, paint)
                    termY += 9.5f
                    line = w
                }
            }
            if (line.isNotEmpty() && termY <= summaryBoxTop + 62f) {
                canvas.drawText(line, 40f, termY, paint)
            }
        }

        // Right Side: Calculation Totals & Dark Grand Total Box
        paint.color = Color.rgb(71, 85, 105) // Slate 600
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        // Subtotals
        canvas.drawText(if (isSw) "Jumla ya Vifaa:" else "Materials Subtotal:", 340f, summaryBoxTop + 14f, paint)
        val matTotalStr = "${Formatters.formatNumber(quote.materialsTotal)} ${businessSettings.currency}"
        canvas.drawText(matTotalStr, 550f - paint.measureText(matTotalStr), summaryBoxTop + 14f, paint)

        canvas.drawText(if (isSw) "Gharama ya Ufundi:" else "Labour / Service:", 340f, summaryBoxTop + 28f, paint)
        val labourStr = "${Formatters.formatNumber(quote.labour)} ${businessSettings.currency}"
        canvas.drawText(labourStr, 550f - paint.measureText(labourStr), summaryBoxTop + 28f, paint)

        // Grand Total Box (Dark Slate #0F172A with Gold/Amber text)
        bgPaint.color = Color.rgb(15, 23, 42) // Slate 900
        canvas.drawRoundRect(RectF(335f, summaryBoxTop + 38f, 560f, summaryBoxTop + 68f), 4f, 4f, bgPaint)

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9f
        canvas.drawText(if (isSw) "JUMLA KUU:" else "GRAND TOTAL:", 345f, summaryBoxTop + 57f, paint)

        paint.color = Color.rgb(245, 158, 11) // Amber 500
        paint.textSize = 10f
        val grandStr = "${Formatters.formatNumber(quote.grandTotal)} ${businessSettings.currency}"
        canvas.drawText(grandStr, 550f - paint.measureText(grandStr), summaryBoxTop + 57f, paint)

        y = summaryBoxTop + 86f

        // 5. Bottom Gold Divider & Footer Note
        bgPaint.color = Color.rgb(234, 179, 8) // Amber Gold
        canvas.drawRect(30f, y, 565f, y + 2f, bgPaint)

        y += 14f

        // Left Footer: Thank you & inquiries
        paint.color = Color.rgb(180, 83, 9) // Amber 700
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 7.5f
        val thankYou = if (isSw) "Asante kwa kuchagua ${businessSettings.name}" else "Thank you for trusting ${businessSettings.name}"
        canvas.drawText(thankYou, 30f, y + 10f, paint)

        paint.color = Color.rgb(71, 85, 105)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 7f
        val inquiryPhone = if (businessSettings.phone1.isNotBlank()) businessSettings.phone1 else businessSettings.phone2
        canvas.drawText(if (isSw) "Mawasiliano na Malipo: $inquiryPhone" else "For payments & inquiries: $inquiryPhone", 30f, y + 22f, paint)
        canvas.drawText(businessSettings.name, 30f, y + 33f, paint)

        // Right Footer: Authorized Signature & Stamp
        val sigBitmap = ImageUtils.loadLogoBitmap(businessSettings.signaturePath, 140)
        if (sigBitmap != null) {
            try {
                val sigRect = RectF(440f, y - 12f, 520f, y + 18f)
                canvas.drawBitmap(sigBitmap, null, sigRect, paint)
            } catch (e: Exception) { }
        }

        paint.color = Color.rgb(148, 163, 184)
        paint.strokeWidth = 0.8f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(400f, y + 20f, 560f, y + 20f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.rgb(71, 85, 105)
        paint.textSize = 7f
        val signLabel = if (isSw) "Saini na Muhuri Rasmi" else "Authorized Signature & Stamp"
        canvas.drawText(signLabel, 400f + (160f - paint.measureText(signLabel)) / 2f, y + 31f, paint)

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
            context.startActivity(Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "PDF: ${file.name}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun openPdfFile(context: Context, file: File, language: String) {
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
            val chooserTitle = if (language == "sw") "Fungua PDF" else "Open PDF"
            context.startActivity(Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(
                context,
                if (language == "sw") "Faili limehifadhiwa: ${file.name}" else "File saved: ${file.name}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun sharePdfFile(context: Context, file: File, quoteNumber: String, language: String) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "AGRITECH - $quoteNumber")
                putExtra(Intent.EXTRA_TEXT, if (language == "sw") "Tazama nakala ya PDF ya $quoteNumber kutoka AGRITECH HUB." else "Please find attached the PDF for $quoteNumber from AGRITECH HUB.")
                clipData = ClipData.newRawUri("PDF", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            grantUriPermissions(context, intent, uri)
            val chooserTitle = if (language == "sw") "Tuma PDF" else "Share PDF"
            context.startActivity(Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Imeshindikana kushare PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun grantUriPermissions(context: Context, intent: Intent, uri: Uri) {
        try {
            val resInfoList = context.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        } catch (e: Exception) {
            // non-fatal fallback
        }
    }
}
