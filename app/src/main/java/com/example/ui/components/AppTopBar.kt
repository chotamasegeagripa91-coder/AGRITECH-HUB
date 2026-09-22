package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.LicenseInfo
import com.example.data.models.LicenseStatus
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.utils.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    businessName: String,
    licenseInfo: LicenseInfo,
    isDarkMode: Boolean,
    language: String,
    onToggleTheme: () -> Unit,
    onToggleLanguage: () -> Unit,
    onOpenLicense: () -> Unit,
    onLockApp: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { com.example.data.local.AppPreferences(context) }
    val businessLogoPath = remember { prefs.getBusinessSettings().logoPath }
    val logoBitmap = remember(businessLogoPath) {
        if (businessLogoPath.isNotBlank()) {
            com.example.ui.utils.ImageUtils.loadLogoBitmap(businessLogoPath, 80)
        } else {
            null
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Brand Name & Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (logoBitmap != null) {
                        Image(
                            bitmap = logoBitmap.asImageBitmap(),
                            contentDescription = "Logo",
                            modifier = Modifier
                                .height(40.dp)
                                .wrapContentWidth(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AmberPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Agritech Logo",
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = businessName.ifEmpty { "AGRITECH HUB" },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = AppStrings.t("app_subtitle", language),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                // Action buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // License Status Pill
                    val (badgeBg, badgeFg, badgeText) = when (licenseInfo.status) {
                        LicenseStatus.ACTIVE -> Triple(
                            EmeraldSuccess.copy(alpha = 0.15f),
                            EmeraldSuccess,
                            if (licenseInfo.daysRemaining != null) "PRO ${licenseInfo.daysRemaining}d" else "PRO"
                        )
                        LicenseStatus.TRIAL -> Triple(
                            AmberPrimary.copy(alpha = 0.15f),
                            AmberPrimary,
                            "${licenseInfo.trialDaysRemaining}d"
                        )
                        LicenseStatus.TRIAL_EXPIRED, LicenseStatus.LICENSE_EXPIRED -> Triple(
                            RoseError.copy(alpha = 0.15f),
                            RoseError,
                            "EXP"
                        )
                        LicenseStatus.TAMPERED -> Triple(
                            RoseError.copy(alpha = 0.15f),
                            RoseError,
                            "TAMPER"
                        )
                        else -> Triple(
                            RoseError.copy(alpha = 0.15f),
                            RoseError,
                            "LOCKED"
                        )
                    }

                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenLicense() }
                            .testTag("license_badge_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (licenseInfo.status == LicenseStatus.ACTIVE) Icons.Default.Verified else Icons.Default.Key,
                                contentDescription = "License",
                                tint = badgeFg,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = badgeText,
                                color = badgeFg,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Language Toggle
                    IconButton(
                        onClick = onToggleLanguage,
                        modifier = Modifier.size(36.dp).testTag("lang_toggle_btn")
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (language == "sw") "SW" else "EN",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Theme Toggle
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.size(36.dp).testTag("theme_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Lock Button
                    IconButton(
                        onClick = onLockApp,
                        modifier = Modifier.size(36.dp).testTag("lock_app_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock App",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
