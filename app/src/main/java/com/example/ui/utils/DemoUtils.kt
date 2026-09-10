package com.example.ui.utils

import com.example.data.models.CustomerEntity
import com.example.data.models.MaterialEntity
import com.example.data.models.QuoteEntity

/**
 * Utility to identify demo/sample records bundled in the APK.
 * Ensures sample records are NEVER uploaded to Firebase/Firestore as real user data.
 */
object DemoUtils {
    fun isDemoCustomer(customer: CustomerEntity): Boolean {
        val name = customer.name.lowercase()
        val notes = customer.notes.lowercase()
        return name.contains("demo") || name.contains("sample") || name.contains("mfano") ||
                notes.contains("demo") || notes.contains("sample") || notes.contains("mfano")
    }

    fun isDemoQuote(quote: QuoteEntity): Boolean {
        val num = quote.number.lowercase()
        val desc = quote.description.lowercase()
        val cName = quote.customerName.lowercase()
        return num.contains("demo") || num.contains("sample") ||
                desc.contains("demo") || desc.contains("sample") || desc.contains("mfano") ||
                cName.contains("demo") || cName.contains("sample") || cName.contains("mfano")
    }

    fun isDemoMaterial(material: MaterialEntity): Boolean {
        val name = material.name.lowercase()
        val cat = material.category.lowercase()
        return name.contains("[demo") || name.contains("[sample") || name.contains("demo / sample") ||
                cat.contains("demo") || cat.contains("sample")
    }
}
