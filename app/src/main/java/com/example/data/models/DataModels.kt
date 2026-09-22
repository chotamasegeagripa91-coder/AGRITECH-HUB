package com.example.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale
import java.util.UUID

@Entity(
    tableName = "materials",
    indices = [
        Index(value = ["internalCode"]),
        Index(value = ["category", "name", "unit"])
    ]
)
data class MaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val internalCode: String = "",
    val name: String,
    val unit: String = "Pcs",
    val price: Double = 0.0,
    val category: String = "Jumla (General)",
    val isDemo: Boolean = false,
    val userId: String = ""
) {
    companion object {
        fun generateInternalCode(): String {
            return "MAT-" + UUID.randomUUID().toString().replace("-", "").take(10).uppercase(Locale.ROOT)
        }
    }
}

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String,
    val location: String,
    val notes: String = ""
)

data class QuoteItem(
    val id: String,
    val materialId: Int? = null,
    val name: String,
    val unit: String = "Pcs",
    val price: Double = 0.0,
    val quantity: Double = 1.0,
    val total: Double = 0.0
)

@Entity(tableName = "quotes")
data class QuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val number: String,
    val date: String,
    val validUntil: String = "",
    val customerId: Int = 0,
    val customerName: String,
    val customerPhone: String,
    val customerLocation: String,
    val description: String,
    val itemsJson: String,
    val materialsTotal: Double,
    val labour: Double,
    val grandTotal: Double,
    val status: String = "quotation", // "quotation" or "invoice"
    val paid: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class QuoteDraft(
    val id: String = UUID.randomUUID().toString(),
    val number: String = "QTN-001",
    val date: String = "",
    val validUntil: String = "",
    val customerId: Int? = null,
    val customerName: String = "",
    val customerPhone: String = "",
    val customerLocation: String = "",
    val description: String = "",
    val itemsJson: String = "[]",
    val materialsTotal: Double = 0.0,
    val isLabourAutoCalculated: Boolean = true,
    val labour: Double = 0.0,
    val grandTotal: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
)

fun parseQuoteItemsFromJson(itemsJson: String): List<QuoteItem> {
    val list = mutableListOf<QuoteItem>()
    if (itemsJson.isBlank() || itemsJson == "[]") return list
    try {
        val arr = org.json.JSONArray(itemsJson)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                QuoteItem(
                    id = obj.optString("id", i.toString()),
                    materialId = if (obj.has("materialId") && !obj.isNull("materialId")) obj.optInt("materialId") else null,
                    name = obj.optString("name", "Item"),
                    unit = obj.optString("unit", "Pcs"),
                    price = obj.optDouble("price", 0.0),
                    quantity = obj.optDouble("quantity", 1.0),
                    total = obj.optDouble("total", 0.0)
                )
            )
        }
    } catch (e: Exception) {
        // Fallback for safety
    }
    return list
}

data class BusinessSettings(
    val name: String = "",
    val slogan: String = "",
    val phone1: String = "",
    val phone2: String = "",
    val email: String = "",
    val address: String = "",
    val currency: String = "TSh",
    val bankName: String = "",
    val bankAccountNumber: String = "",
    val bankAccountName: String = "",
    val lipaNumber: String = "",
    val mobileMoney: String = "",
    val logoPath: String = "",
    val signaturePath: String = "",
    val quotationTermsSw: String = "Haya makadirio (Quotation) ni halali kwa siku 14 pekee kuanzia tarehe iliyotolewa. Baada ya hapo, bei zinaweza kubadilika kulingana na hali ya soko.",
    val quotationTermsEn: String = "This quotation is valid for 14 days only from the date of issue. Thereafter, prices are subject to change according to market conditions.",
    val labourPercentage: Double = 40.0
)

enum class LicenseType(val code: String, val titleSw: String, val titleEn: String, val durationDays: Int?, val priceTsh: Int) {
    TRIAL("trial", "Jaribio Siku 30 (Trial)", "30 Days (Trial)", 30, 0),
    MONTHLY("monthly", "Mwezi 1 (Monthly)", "1 Month (Monthly)", 30, 5000),
    QUARTERLY("quarterly", "Miezi 3 (Quarterly)", "3 Months (Quarterly)", 90, 12000),
    SEMI_ANNUAL("semi_annual", "Miezi 6 (6 Months)", "6 Months", 180, 24000),
    YEARLY("yearly", "Mwaka 1 (Yearly)", "1 Year (Yearly)", 365, 40000),
    LIFETIME("lifetime", "Maisha Yote (Lifetime)", "Lifetime (No Expiry)", null, 60000);

    companion object {
        fun fromCode(code: String?): LicenseType = when (code?.lowercase(java.util.Locale.ROOT)) {
            "trial" -> TRIAL
            "monthly", "m", "month" -> MONTHLY
            "quarterly", "q", "3months" -> QUARTERLY
            "semi_annual", "semi-annual", "semiannual", "6months", "six_months", "half_year", "s", "h", "6" -> SEMI_ANNUAL
            "yearly", "y", "year", "annual" -> YEARLY
            "lifetime", "l", "life" -> LIFETIME
            else -> entries.find { it.code.equals(code, ignoreCase = true) } ?: LIFETIME
        }
    }
}

enum class LicenseStatus {
    TRIAL,
    TRIAL_EXPIRED,
    ACTIVE,
    DISABLED,
    REVOKED,
    EXPIRED,
    LICENSE_EXPIRED,
    INVALID_DEVICE,
    TAMPERED
}

data class LicenseInfo(
    val status: LicenseStatus,
    val installationId: String,
    val trialStartDate: String,
    val trialDaysUsed: Int,
    val trialDaysRemaining: Int,
    val isLicensed: Boolean,
    val licenseType: LicenseType? = null,
    val licenseKey: String = "",
    val customerName: String? = null,
    val activatedAt: String? = null,
    val expiresAt: String? = null,
    val daysRemaining: Int? = null,
    val isTampered: Boolean = false
)

data class UserAccount(
    val userId: String,
    val businessName: String,
    val ownerFullName: String,
    val phoneNumber: String,
    val email: String,
    val passwordHash: String,
    val passwordSalt: String,
    val isVerified: Boolean = false,
    val licenseType: LicenseType = LicenseType.TRIAL,
    val licenseStatus: LicenseStatus = LicenseStatus.ACTIVE,
    val accountStatus: AccountStatus = AccountStatus.ACTIVE,
    val trialStartDate: String = "",
    val trialExpiryDate: String = "",
    val paidLicenseStartDate: String? = null,
    val paidLicenseExpiryDate: String? = null,
    val paidLicenseKey: String? = null,
    val installationId: String = "",
    val sessionToken: String = "",
    val lastSyncTimestamp: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

enum class AccountStatus {
    ACTIVE,
    SUSPENDED,
    DISABLED,
    DELETED
}

data class CloudBackupPayload(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val userId: String = "",
    val business: BusinessSettings,
    val materials: List<MaterialEntity> = emptyList(),
    val customers: List<CustomerEntity> = emptyList(),
    val quotes: List<QuoteEntity> = emptyList(),
    val licenseInfo: LicenseInfo? = null
)

