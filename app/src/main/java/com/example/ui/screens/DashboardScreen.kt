package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.CustomerEntity
import com.example.data.models.LicenseInfo
import com.example.data.models.LicenseStatus
import com.example.data.models.MaterialEntity
import com.example.data.models.QuoteEntity
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.utils.AppStrings
import com.example.ui.utils.Formatters
import com.example.ui.viewmodel.AppScreen

@Composable
fun DashboardScreen(
    quotes: List<QuoteEntity>,
    materials: List<MaterialEntity>,
    customers: List<CustomerEntity>,
    licenseInfo: LicenseInfo,
    language: String,
    onNavigate: (AppScreen) -> Unit,
    onSelectQuote: (QuoteEntity) -> Unit,
    onOpenLicense: () -> Unit,
    lastSyncTime: Long = 0L,
    isSyncing: Boolean = false,
    onSyncCloud: () -> Unit = {}
) {
    val totalQuotesCount = quotes.count { it.status == "quotation" }
    val totalInvoicesCount = quotes.count { it.status == "invoice" }
    val totalLabourCharges = quotes.sumOf { it.labour }
    val paidLabourCharges = quotes.filter { it.paid }.sumOf { it.labour }
    val totalProjectValue = quotes.sumOf { it.grandTotal }
    val paidProjectValue = quotes.filter { it.paid }.sumOf { it.grandTotal }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
    ) {
        // 1. License / Trial Status Card
        item {
            val isLicensed = licenseInfo.status == LicenseStatus.ACTIVE
            val isTrial = licenseInfo.status == LicenseStatus.TRIAL
            val isExpired = licenseInfo.status == LicenseStatus.TRIAL_EXPIRED || licenseInfo.status == LicenseStatus.LICENSE_EXPIRED

            val bannerBg = if (isLicensed) EmeraldSuccess.copy(alpha = 0.12f) else if (isTrial) AmberPrimary.copy(alpha = 0.12f) else RoseError.copy(alpha = 0.12f)
            val bannerBorder = if (isLicensed) EmeraldSuccess else if (isTrial) AmberPrimary else RoseError

            val typeText = when {
                isTrial -> "TRIAL"
                licenseInfo.licenseType != null -> licenseInfo.licenseType.name
                else -> "TRIAL"
            }
            val statusText = when (licenseInfo.status) {
                LicenseStatus.ACTIVE, LicenseStatus.TRIAL -> "ACTIVE"
                LicenseStatus.TRIAL_EXPIRED, LicenseStatus.LICENSE_EXPIRED -> "EXPIRED"
                LicenseStatus.TAMPERED -> "TAMPERED"
            }
            val daysRemainingText = when {
                isTrial -> "${licenseInfo.trialDaysRemaining}"
                licenseInfo.daysRemaining != null -> "${licenseInfo.daysRemaining}"
                isLicensed -> if (language == "sw") "Bila Kikomo (Lifetime)" else "Lifetime"
                else -> "0"
            }

            Surface(
                color = bannerBg,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, bannerBorder.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenLicense() }
                    .testTag("dashboard_license_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bannerBorder),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isLicensed) Icons.Default.Verified else if (isTrial) Icons.Default.Timer else Icons.Default.KeyOff,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "LICENSE",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = bannerBorder.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                color = bannerBorder,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Type: $typeText",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Status: $statusText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Days Remaining: $daysRemainingText",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = bannerBorder
                                )
                            )
                            Text(
                                text = if (isExpired) {
                                    if (language == "sw") "Bofya kununua / kuwasha" else "Tap to purchase / activate"
                                } else {
                                    if (language == "sw") "Bofya kuona maelezo" else "Tap for details"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Cloud Sync Status Bar
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isSyncing) Icons.Default.Sync else Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = if (isSyncing) AmberPrimary else EmeraldSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (language == "sw") "Hifadhi ya Wingu (Cloud Sync)" else "Cloud Backup & Sync",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isSyncing) {
                                    if (language == "sw") "Inasawazisha na wingu..." else "Syncing to cloud..."
                                } else if (lastSyncTime > 0) {
                                    val timeStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSyncTime))
                                    if (language == "sw") "Imesawazishwa: $timeStr" else "Last synced: $timeStr"
                                } else {
                                    if (language == "sw") "Inafanya kazi bila intaneti (Offline-First)" else "Offline-First ready"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    TextButton(
                        onClick = onSyncCloud,
                        enabled = !isSyncing,
                        modifier = Modifier.testTag("dashboard_sync_now_btn")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = if (language == "sw") "Sawazisha" else "Sync Now",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = AmberPrimary
                            )
                        }
                    }
                }
            }
        }

        // 2. Summary Stat Cards Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Top Row (2 Cards)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        title = AppStrings.t("stat_total_quotes", language),
                        value = "$totalQuotesCount",
                        subValue = "Miradi ya Makadirio",
                        icon = Icons.Default.RequestQuote,
                        color = AmberPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = AppStrings.t("stat_total_invoices", language),
                        value = "$totalInvoicesCount",
                        subValue = "Ankara Zilizokamilika",
                        icon = Icons.Default.Receipt,
                        color = EmeraldSuccess,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Middle Row (2 Cards)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        title = AppStrings.t("stat_materials_count", language),
                        value = "${materials.size}",
                        subValue = "Kwenye Hifadhi (Stoo)",
                        icon = Icons.Default.Inventory2,
                        color = CyanAccent,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = AppStrings.t("stat_customers_count", language),
                        value = "${customers.size}",
                        subValue = "Wateja & Mafundi",
                        icon = Icons.Default.PeopleAlt,
                        color = Color(0xFFA855F7),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Bottom Row: Two Prominent Project Value Cards (Labour Charges & Total Project Value)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Labour Charges Card
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_labour_charges_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = AppStrings.t("stat_labour_charges", language),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AmberPrimary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = null,
                                        tint = AmberPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = Formatters.formatCurrency(totalLabourCharges),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                            Text(
                                text = if (language == "sw") "Zilizolipwa: ${Formatters.formatCurrency(paidLabourCharges)}" else "Paid: ${Formatters.formatCurrency(paidLabourCharges)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = EmeraldSuccess,
                                maxLines = 1
                            )
                        }
                    }

                    // 2. Total Project Value Card (Materials + Labour)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_total_project_value_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = AppStrings.t("stat_total_project_value", language),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(EmeraldSuccess.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = Formatters.formatCurrency(totalProjectValue),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                            Text(
                                text = if (language == "sw") "Vifaa + Ufundi (${Formatters.formatCurrency(paidProjectValue)})" else "Materials + Labour (${Formatters.formatCurrency(paidProjectValue)})",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // 3. Quick Action Buttons
        item {
            Text(
                text = if (language == "sw") "Vitendo vya Haraka:" else "Quick Actions:",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionButton(
                    title = if (language == "sw") "Makadirio\nMapya" else "New\nQuote",
                    icon = Icons.Default.PostAdd,
                    color = AmberPrimary,
                    modifier = Modifier.weight(1f).testTag("quick_new_quote_btn"),
                    onClick = { onNavigate(AppScreen.NEW_QUOTE) }
                )
                QuickActionButton(
                    title = if (language == "sw") "Ongeza\nMteja" else "Add\nCustomer",
                    icon = Icons.Default.PersonAdd,
                    color = Color(0xFFA855F7),
                    modifier = Modifier.weight(1f).testTag("quick_add_customer_btn"),
                    onClick = { onNavigate(AppScreen.CUSTOMERS) }
                )
                QuickActionButton(
                    title = if (language == "sw") "Hifadhi ya\nVifaa" else "Materials\nStock",
                    icon = Icons.Default.Inventory,
                    color = CyanAccent,
                    modifier = Modifier.weight(1f).testTag("quick_materials_btn"),
                    onClick = { onNavigate(AppScreen.MATERIALS) }
                )
                QuickActionButton(
                    title = if (language == "sw") "Historia ya\nAnkara" else "Invoices\nHistory",
                    icon = Icons.Default.ReceiptLong,
                    color = EmeraldSuccess,
                    modifier = Modifier.weight(1f).testTag("quick_quotes_btn"),
                    onClick = { onNavigate(AppScreen.QUOTES_HISTORY) }
                )
            }
        }

        // 4. Recent Quotations Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AppStrings.t("recent_activity", language),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (quotes.isNotEmpty()) {
                    TextButton(onClick = { onNavigate(AppScreen.QUOTES_HISTORY) }) {
                        Text(
                            text = if (language == "sw") "Tazama Zote" else "View All",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (quotes.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = AppStrings.t("no_recent_activity", language),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(quotes.take(5), key = { it.id }) { quote ->
                val isInvoice = quote.status == "invoice"
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectQuote(quote) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isInvoice) EmeraldSuccess.copy(alpha = 0.2f) else AmberPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isInvoice) Icons.Default.Receipt else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = if (isInvoice) EmeraldSuccess else AmberPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = quote.customerName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1
                                )
                                Text(
                                    text = "${quote.number} • ${quote.date}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = Formatters.formatCurrency(quote.grandTotal),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                color = if (quote.paid) EmeraldSuccess.copy(alpha = 0.15f) else RoseError.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (quote.paid) "PAID" else "UNPAID",
                                    color = if (quote.paid) EmeraldSuccess else RoseError,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Powered by Agritech Footer
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Powered by Agritech",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = AmberPrimary.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subValue: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subValue,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun QuickActionButton(
    title: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                ),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 12.sp
            )
        }
    }
}
