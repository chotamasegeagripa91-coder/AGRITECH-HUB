package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.CustomerEntity
import com.example.ui.theme.RoseError
import com.example.ui.utils.AppStrings

@Composable
fun CustomerEditDialog(
    customer: CustomerEntity?,
    language: String,
    onDismiss: () -> Unit,
    onSave: (CustomerEntity) -> Unit
) {
    val isEdit = customer != null
    var name by remember { mutableStateOf(customer?.name ?: "") }
    var phone by remember { mutableStateOf(customer?.phone ?: "") }
    var location by remember { mutableStateOf(customer?.location ?: "") }
    var notes by remember { mutableStateOf(customer?.notes ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 12.dp)
                .imePadding(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEdit) {
                            if (language == "sw") "Hariri Taarifa za Mteja" else "Edit Customer"
                        } else {
                            if (language == "sw") "Sajili Mteja Mpya" else "Register New Customer"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (language == "sw") "Jina Kamili la Mteja" else "Customer Full Name") },
                    placeholder = { Text("Mfano: Mhandisi Juma Rashid") },
                    modifier = Modifier.fillMaxWidth().testTag("customer_name_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(if (language == "sw") "Namba ya Simu" else "Phone Number") },
                    placeholder = { Text("Mfano: +255 712 345 678") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().testTag("customer_phone_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(if (language == "sw") "Mahali / Eneo la Site" else "Location / Project Site") },
                    placeholder = { Text("Mfano: Mikocheni, Dar es Salaam") },
                    modifier = Modifier.fillMaxWidth().testTag("customer_location_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(if (language == "sw") "Maelezo ya Ziada (Hiari)" else "Notes (Optional)") },
                    placeholder = { Text("Mfano: Mradi wa nyumba ya makazi vyumba 4") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = RoseError,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(AppStrings.t("btn_cancel", language))
                    }

                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                errorMessage = if (language == "sw") "Tafadhali jaza jina la mteja." else "Please enter customer name."
                                return@Button
                            }
                            val updated = CustomerEntity(
                                id = customer?.id ?: 0,
                                name = name.trim(),
                                phone = phone.trim(),
                                location = location.trim(),
                                notes = notes.trim()
                            )
                            onSave(updated)
                        },
                        modifier = Modifier.weight(1.2f).testTag("customer_save_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(AppStrings.t("btn_save", language), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
