package com.example

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.data.licensing.LicensingEngine
import com.example.data.models.LicenseType
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LicenseEngineTest {

    companion object {
        // Admin Private Key for testing signature creation in JVM test environment ONLY
        private const val TEST_ADMIN_PRIVATE_KEY =
            "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCz1jkPGaQ83p3v0FyhmvpFl1jMJF8/kyLdNBH5ZgpEwLh25az6aFo+ThqJmpLh66UoarH55L9mffgPHnmxTS/ttCzQtRVTypNs0XYZWCIZfSM/CZAHJo9hzx9BI8qUPtOn0vxRzKZjlNX9GuZmFkTAakL/qouyGGeXLc2CrcGE6rxDDQjXdViMSh7A4+zBocWumdBmuxxAAzMh7AEymB0n0R/I4HuGnTV8FkbOwzio8XqfQFDfM4Jaiqk8bFxXNuLNw4X9ezhRwPHVmVWS3pP1IthF/MFZOM4s4UJaHZBTHfWKq8BU38Dz3guIwvcqyvxp1y1FlQRJWT4An/uydoFJAgMBAAECggEAAOpUSL9kTOsDtDyJz46ViPtyh8YEthfTU6n/sz2EJTKIWPtOafff8hQmHfIIj0xd76/wrFTyGNGDkL6uPPq6/edZedWBo1/od3bIBBMwtGTfJiVqMWjSSkCNVMeMSC0g9i4U+6rv95HKXkFf8plwPESrSO9ugMci5qIW/RcrIw2viQzzF8VxFivwmHmJBK3asyWtMQbyW2XhT7b/xqh0y9hoabM3we4VlXeVmvO3RRJt+Cj86lT9nw7GFdPyQsjkKMXrJZa/M2JDicKe/tQ0XdnRvyyvSGledD5RxsF0SXGPK/Hi2nvJCLQcMbVB12UlOCZSuguySg7KRFIzXFvcAQKBgQDiy5JMQ7murKUVlNDccvOOINS9FjDnxFy7+Yab9+BBnchbiWu6rdpTOzkL263oTyKFV+t2hnGGlvyeluxwS2vMsdtlQ5Rk/ZUCj0U4AE+1uvR7XO/NNsEC8mmmb27lSajVi952AtAIgXWWMa17arNCrann4pj/l27dJYzYQvdXRwKBgQDK/qRk3HBmittgky7w7vzVS/4eK4DpAlobbBCTrbPnfmmXd6V+waxfhyFiTimP7qTPYl08otRwGUxTxCBpfCo47LsSr4SjXjXg1nBfb7rtZOAo/B1ksEsVPy0JI12irGmtEtPLPwNYuRCbSy3sFkP/iCfml4scL5lKv0YsWuLK7wKBgC9+KdjGpe+qP7fRPhusBszQmzwtlXgzaqgCjOnEcrXK8NYaZPDmzz12vW96RWTMjZIW4zwi00s3+xbKzCCH6r8mz6bZDA5J/BZZIkmj5w1LZT8gKydyO+D+Gpm75CEn169AZwmXdTESyyj0pjueEIP6EgZ5MUj00UaGQoRXIaypAoGAfksrcG0NqV6e2lj4DYJC31mmQpMCYvXJpeH39klN8qrdexU/a7uHSO/Dv2utxBTPiQ9DvuP3k77JlBwjpj1P75apVRjKRvHoR3hi9Z2ICQaHyDgC+ZANRqzFkjfkm771CG78Qil0JZEOWe/OJGI98A9/86E5NVIP50dVm2b4TxUCgYAsX433QxzBSOM8Z4guY+j1q9kxLqpM3w4XO4Bb+arplAolLiCtnAPPVBb7yQX9+zEfQ1kRpZDOhbZjG6MaruEfN+1RF9R333NNmnd8SgNnEn7p7CE/sNAzKRiJwFPzD+sAft0HaFuCKv+I2jjVi4CkQ5PJPJ9a+5XWGwwGwhdUiw=="

        private fun createAdminSignedLicense(
            customerName: String,
            licenseType: LicenseType,
            deviceId: String,
            days: Int
        ): String {
            val now = System.currentTimeMillis()
            val licId = "LIC-" + java.util.UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
            val payload = JSONObject().apply {
                put("id", licId)
                put("customer", customerName)
                put("type", licenseType.name)
                put("installId", deviceId)
                put("issuedAt", now)
                put("days", days)
            }
            val payloadBase64 = Base64.encodeToString(payload.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP).trim()

            val keyBytes = Base64.decode(TEST_ADMIN_PRIVATE_KEY, Base64.DEFAULT)
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val kf = KeyFactory.getInstance("RSA")
            val privKey = kf.generatePrivate(keySpec)

            val sig = Signature.getInstance("SHA256withRSA")
            sig.initSign(privKey)
            sig.update(payloadBase64.toByteArray(Charsets.UTF_8))
            val sigBytes = sig.sign()
            val sigBase64 = Base64.encodeToString(sigBytes, Base64.NO_WRAP).trim()

            return "$payloadBase64.$sigBase64"
        }
    }

    @Test
    fun testGenerateAndVerifyRsaLicenseFormat() {
        val customer = "zuu"
        val installId = "TECNO-A90D-640C"
        val code = createAdminSignedLicense(
            customerName = customer,
            licenseType = LicenseType.MONTHLY,
            deviceId = installId,
            days = 30
        )

        // 1. Structure check: BASE64_PAYLOAD.BASE64_SIGNATURE
        val parts = code.split(".")
        assertEquals(2, parts.size)
        assertTrue(parts[0].isNotBlank())
        assertTrue(parts[1].isNotBlank())

        // 2. Decode payload and check JSON fields
        val decodedJson = String(Base64.decode(parts[0], Base64.DEFAULT), Charsets.UTF_8)
        val json = JSONObject(decodedJson)

        assertTrue(json.has("id"))
        assertTrue(json.getString("id").startsWith("LIC-"))
        assertEquals("zuu", json.getString("customer"))
        assertEquals("MONTHLY", json.getString("type"))
        assertEquals("TECNO-A90D-640C", json.getString("installId"))
        assertTrue(json.has("issuedAt"))
        assertEquals(30, json.getInt("days"))

        // 3. Verification check via RSA Public Key in Customer App
        val result = LicensingEngine.verifyActivationCode(code, installId)
        assertTrue(result.valid)
        assertEquals(LicenseType.MONTHLY, result.licenseType)
        assertFalse(result.isLifetime)
        assertNotNull(result.expiresAt)
    }

    @Test
    fun testLifetimeLicenseDaysZero() {
        val code = createAdminSignedLicense(
            customerName = "Ali",
            licenseType = LicenseType.LIFETIME,
            deviceId = "SAMSUNG-1234-5678",
            days = 0
        )

        val payloadBase64 = code.split(".")[0]
        val decodedJson = String(Base64.decode(payloadBase64, Base64.DEFAULT), Charsets.UTF_8)
        val json = JSONObject(decodedJson)

        assertEquals("LIFETIME", json.getString("type"))
        assertEquals(0, json.getInt("days"))

        val result = LicensingEngine.verifyActivationCode(code, "SAMSUNG-1234-5678")
        assertTrue(result.valid)
        assertTrue(result.isLifetime)
        assertNull(result.expiresAt)
    }

    @Test
    fun testTamperedPayloadFailsVerification() {
        val code = createAdminSignedLicense(
            customerName = "TestUser",
            licenseType = LicenseType.MONTHLY,
            deviceId = "INFINIX-AAAA-BBBB",
            days = 30
        )

        val parts = code.split(".")
        // Tamper with payload (modify one character in base64)
        val tamperedPayload = parts[0].substring(0, parts[0].length - 4) + "AAAA"
        val tamperedCode = "$tamperedPayload.${parts[1]}"

        val result = LicensingEngine.verifyActivationCode(tamperedCode, "INFINIX-AAAA-BBBB")
        assertFalse(result.valid)
        assertTrue(result.message.contains("Sahihi ya leseni si sahihi") || result.message.contains("Payload Decode Error"))
    }

    @Test
    fun testDeviceMismatchFailsVerification() {
        val code = createAdminSignedLicense(
            customerName = "TestUser",
            licenseType = LicenseType.YEARLY,
            deviceId = "OPPO-DEVICE-ONE1",
            days = 365
        )

        val result = LicensingEngine.verifyActivationCode(code, "OPPO-DEVICE-TWO2")
        assertFalse(result.valid)
        assertTrue(result.message.contains("kifaa"))
    }

    @Test
    fun testFullActivationFlow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_agritech_prefs", Context.MODE_PRIVATE)

        val installId = LicensingEngine.getInstallationId(prefs)
        val code = createAdminSignedLicense(
            customerName = "Fundi Rashid",
            licenseType = LicenseType.YEARLY,
            deviceId = installId,
            days = 365
        )

        val (success, msg) = LicensingEngine.activateLicense(prefs, code, "Fundi Rashid")
        assertTrue(success)
        assertTrue(msg.contains("Imethibitishwa"))

        val info = LicensingEngine.getLicenseInfo(prefs)
        assertTrue(info.isLicensed)
        assertEquals(LicenseType.YEARLY, info.licenseType)
    }
}
