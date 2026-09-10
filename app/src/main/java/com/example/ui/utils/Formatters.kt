package com.example.ui.utils

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    fun formatCurrency(amount: Double, currency: String = "TSh"): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US)
        formatter.maximumFractionDigits = 0
        return "$currency ${formatter.format(amount)}"
    }

    fun formatNumber(amount: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US)
        formatter.maximumFractionDigits = 0
        return formatter.format(amount)
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getCurrentDateFormatted(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(Date())
    }

    fun getFutureDateFormatted(daysInFuture: Int = 14): String {
        val futureMs = System.currentTimeMillis() + (daysInFuture.toLong() * 86400000L)
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(Date(futureMs))
    }
}
