package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.RoseError

@Composable
fun AdminBlockScreen(
    blockState: String,
    installationId: String,
    language: String,
    onContactSupport: (String) -> Unit
) {
    val isSw = language == "sw"
    
    // Status and Messages Setup
    val statusTitle = when (blockState.uppercase()) {
        "SUSPENDED" -> "ACCOUNT SUSPENDED BY ADMIN"
        "LOCKED" -> "ACCOUNT LOCKED BY ADMIN"
        else -> "ACCOUNT DISABLED BY ADMIN"
    }

    val statusSubtitleSw = when (blockState.uppercase()) {
        "SUSPENDED" -> "Akaunti yako imesitishwa kwa muda na Msimamizi wa Mfumo kwa sababu ya ukaguzi au ukiukaji wa masharti."
        "LOCKED" -> "Akaunti yako imefungwa na Msimamizi wa Mfumo ili kulinda usalama wa akaunti yako."
        else -> "Akaunti yako imezimwa na Msimamizi wa Mfumo. Tafadhali wasiliana na msaada ili kurejesha akaunti."
    }

    val statusSubtitleEn = when (blockState.uppercase()) {
        "SUSPENDED" -> "Your account has been temporarily suspended by the Administrator due to compliance reviews or policy violations."
        "LOCKED" -> "Your account has been locked by the Administrator to safeguard your business data."
        else -> "Your account has been disabled by the Administrator. Please contact support to reactivate your access."
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121212) // Pure dark mode background for premium lock-screen design
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // AGRITECH logo and Branding
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Text(
                    text = "AGRITECH HUB",
                    color = AmberPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
            }

            // Warning Icon Card
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFF24181A), shape = RoundedCornerShape(40.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (blockState.uppercase() == "LOCKED") Icons.Default.Lock else Icons.Default.Block,
                    contentDescription = null,
                    tint = RoseError,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Large status message (REQUIRED)
            Text(
                text = statusTitle,
                color = RoseError,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle translations for maximum clarity
            Text(
                text = if (isSw) statusSubtitleSw else statusSubtitleEn,
                color = Color.LightGray,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Support/Contact Info Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = if (isSw) "WASILIANA NA MSAADA" else "CONTACT SUPPORT",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Phone/WhatsApp support
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "+255 627 318 891",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Email support
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "chotamasegeagripa91@gmail.com",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    HorizontalDivider(color = Color(0xFF2E2E2E), modifier = Modifier.padding(vertical = 12.dp))

                    // Device/Installation ID (useful for support verification)
                    Text(
                        text = "Device ID: $installationId",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // WhatsApp Direct Assistance Action Button
            Button(
                onClick = {
                    val msg = if (isSw) {
                        "Habari AGRITECH HUB, Akaunti yangu imefungiwa (Status: $blockState). Namba ya Kifaa changu ni: $installationId"
                    } else {
                        "Hello AGRITECH HUB, My account has been locked/restricted (Status: $blockState). My Device ID is: $installationId"
                    }
                    onContactSupport(msg)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF25D366), // Standard Emerald WhatsApp Green
                    contentColor = Color.White
                )
            ) {
                Icon(imageVector = Icons.Default.Chat, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSw) "Wasiliana Kupitia WhatsApp" else "Contact Support via WhatsApp",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
