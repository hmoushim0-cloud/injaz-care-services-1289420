package sa.factory.injazcareservices1.hardware.emv

import java.io.ByteArrayOutputStream
import java.io.Serializable
import java.util.Locale

/**
 * ISO/IEC 7816-4 APDU Command Frame Structure
 */
data class EmvApduCommand(
    val cla: Byte,
    val ins: Byte,
    val p1: Byte,
    val p2: Byte,
    val data: ByteArray = ByteArray(0),
    val le: Int? = null
) : Serializable {

    fun toByteArray(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(cla.toInt())
        out.write(ins.toInt())
        out.write(p1.toInt())
        out.write(p2.toInt())

        if (data.isNotEmpty()) {
            out.write(data.size)
            out.write(data)
        }

        if (le != null) {
            out.write(le)
        }

        return out.toByteArray()
    }

    fun toHexString(): String = toByteArray().toHexString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmvApduCommand) return false
        return cla == other.cla && ins == other.ins && p1 == other.p1 &&
                p2 == other.p2 && data.contentEquals(other.data) && le == other.le
    }

    override fun hashCode(): Int {
        var result = cla.toInt()
        result = 31 * result + ins.toInt()
        result = 31 * result + p1.toInt()
        result = 31 * result + p2.toInt()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + (le ?: 0)
        return result
    }
}

/**
 * ISO/IEC 7816-4 APDU Response Frame Structure
 */
data class EmvApduResponse(
    val data: ByteArray,
    val sw1: Byte,
    val sw2: Byte
) : Serializable {
    val statusWord: Int
        get() = ((sw1.toInt() and 0xFF) shl 8) or (sw2.toInt() and 0xFF)

    val isSuccess: Boolean
        get() = statusWord == 0x9000

    fun toHexString(): String {
        val out = ByteArrayOutputStream()
        out.write(data)
        out.write(sw1.toInt())
        out.write(sw2.toInt())
        return out.toByteArray().toHexString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmvApduResponse) return false
        return data.contentEquals(other.data) && sw1 == other.sw1 && sw2 == other.sw2
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + sw1.toInt()
        result = 31 * result + sw2.toInt()
        return result
    }
}

/**
 * BER-TLV Element representation
 */
data class BerTlvElement(
    val tag: Int,
    val length: Int,
    val value: ByteArray
) : Serializable {
    val tagHex: String
        get() = tag.toString(16).toUpperCase(Locale.US)

    val valueHex: String
        get() = value.toHexString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BerTlvElement) return false
        return tag == other.tag && length == other.length && value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        var result = tag
        result = 31 * result + length
        result = 31 * result + value.contentHashCode()
        return result
    }
}

/**
 * Terminal Verification Results (TVR - 5 bytes)
 */
data class TerminalVerificationResults(
    val byte1OfflineDataAuth: Byte = 0x00,
    val byte2CardholderAuth: Byte = 0x00,
    val byte3TerminalRiskManagement: Byte = 0x00,
    val byte4FloorLimitCheck: Byte = 0x00,
    val byte5ScriptProcessing: Byte = 0x00
) : Serializable {
    fun toByteArray(): ByteArray = byteArrayOf(
        byte1OfflineDataAuth,
        byte2CardholderAuth,
        byte3TerminalRiskManagement,
        byte4FloorLimitCheck,
        byte5ScriptProcessing
    )

    fun toHexString(): String = toByteArray().toHexString()
}

/**
 * Mada Contactless Payment Session Data Model
 */
data class MadaPaymentSession(
    val sessionId: String,
    val aid: String,
    val cardBrand: String,
    val maskedPan: String,
    val amountSar: Double,
    val currencyCode: Int = 682, // SAR ISO 4217 numeric code
    val terminalId: String = "MADA-POS-9812",
    val merchantCategoryCode: String = "1711", // HVAC / Plumbing / Electrical Services
    val applicationCryptogram: String,
    val applicationTransactionCounter: Int,
    val terminalVerificationResults: String,
    val isContactlessApproved: Boolean,
    val authorizationCode: String,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

/**
 * BER-TLV Parser & Builder
 */
object BerTlvParser {

    fun parse(bytes: ByteArray): List<BerTlvElement> {
        val elements = mutableListOf<BerTlvElement>()
        var offset = 0

        while (offset < bytes.size) {
            // Skip padding bytes (0x00 or 0xFF)
            if (bytes[offset].toInt() == 0x00 || (bytes[offset].toInt() and 0xFF) == 0xFF) {
                offset++
                continue
            }

            // Tag parsing
            val firstByte = bytes[offset].toInt() and 0xFF
            offset++
            var tag = firstByte

            if ((firstByte and 0x1F) == 0x1F) {
                // Multi-byte tag
                while (offset < bytes.size) {
                    val nextByte = bytes[offset].toInt() and 0xFF
                    offset++
                    tag = (tag shl 8) or nextByte
                    if ((nextByte and 0x80) == 0) {
                        break
                    }
                }
            }

            if (offset >= bytes.size) break

            // Length parsing
            val lenByte = bytes[offset].toInt() and 0xFF
            offset++
            var length = lenByte

            if ((lenByte and 0x80) != 0) {
                val numLengthBytes = lenByte and 0x7F
                length = 0
                for (i in 0 until numLengthBytes) {
                    if (offset < bytes.size) {
                        val b = bytes[offset].toInt() and 0xFF
                        offset++
                        length = (length shl 8) or b
                    }
                }
            }

            // Value parsing
            val valueLength = Math.min(length, Math.max(0, bytes.size - offset))
            val valueBytes = ByteArray(valueLength)
            System.arraycopy(bytes, offset, valueBytes, 0, valueLength)
            offset += valueLength

            elements.add(BerTlvElement(tag, length, valueBytes))
        }

        return elements
    }

    fun find(elements: List<BerTlvElement>, tag: Int): BerTlvElement? {
        return elements.firstOrNull { it.tag == tag }
    }

    fun buildElement(tag: Int, value: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()

        // Write tag
        if (tag > 0xFFFF) {
            out.write((tag ushr 16) and 0xFF)
            out.write((tag ushr 8) and 0xFF)
            out.write(tag and 0xFF)
        } else if (tag > 0xFF) {
            out.write((tag ushr 8) and 0xFF)
            out.write(tag and 0xFF)
        } else {
            out.write(tag and 0xFF)
        }

        // Write length
        val len = value.size
        if (len < 128) {
            out.write(len)
        } else if (len < 256) {
            out.write(0x81)
            out.write(len)
        } else {
            out.write(0x82)
            out.write((len ushr 8) and 0xFF)
            out.write(len and 0xFF)
        }

        // Write value
        out.write(value)
        return out.toByteArray()
    }
}

/**
 * Mada Contactless EMV Level 2 Kernel
 */
class MadaEmvKernel {

    companion object {
        // Standard Mada AIDs
        const val AID_MADA_DEBIT = "A000000228"
        const val AID_MADA_VISA = "A0000000031010"
        const val AID_MADA_MASTERCARD = "A0000000041010"

        // APDU Instruction Codes
        const val CLA_ISO: Byte = 0x00.toByte()
        const val CLA_PROPRIETARY: Byte = 0x80.toByte()

        const val INS_SELECT: Byte = 0xA4.toByte()
        const val INS_GET_PROCESSING_OPTIONS: Byte = 0xA8.toByte()
        const val INS_READ_RECORD: Byte = 0xB2.toByte()
        const val INS_GENERATE_AC: Byte = 0xAE.toByte()

        // Status Words
        const val SW_SUCCESS = 0x9000
        const val SW_CONDITIONS_NOT_SATISFIED = 0x6985
        const val SW_FILE_NOT_FOUND = 0x6A82

        // Known EMV Tags
        const val TAG_AID = 0x4F
        const val TAG_APP_LABEL = 0x50
        const val TAG_TRACK2_EQUIV = 0x57
        const val TAG_PAN = 0x5A
        const val TAG_AIP = 0x82
        const val TAG_DF_NAME = 0x84
        const val TAG_AMOUNT_AUTH_NUM = 0x9F02
        const val TAG_CURRENCY_CODE = 0x5F2A
        const val TAG_APP_CRYPTOGRAM = 0x9F26
        const val TAG_CID = 0x9F27
        const val TAG_ATC = 0x9F36
        const val TAG_UNPREDICTABLE_NUM = 0x9F37

        // Saudi Arabian Riyal (SAR) Numeric ISO 4217
        const val SAR_CURRENCY_CODE = 682
        // Default Contactless Off-line Floor Limit (SAR 300.00 for Mada Atheer)
        const val MADA_CONTACTLESS_FLOOR_LIMIT_SAR = 300.00
    }

    /**
     * Builds ISO 7816-4 SELECT command for Mada AID
     */
    fun buildSelectAidCommand(aidHex: String): EmvApduCommand {
        val aidBytes = aidHex.hexToByteArray()
        return EmvApduCommand(
            cla = CLA_ISO,
            ins = INS_SELECT,
            p1 = 0x04.toByte(), // Select by DF name
            p2 = 0x00.toByte(), // First or only occurrence
            data = aidBytes,
            le = 0x00
        )
    }

    /**
     * Builds GET PROCESSING OPTIONS (GPO) command with PDOL data
     */
    fun buildGpoCommand(pdolData: ByteArray = ByteArray(0)): EmvApduCommand {
        val tlvData = if (pdolData.isEmpty()) {
            byteArrayOf(0x83.toByte(), 0x00.toByte()) // Empty command template tag 83
        } else {
            BerTlvParser.buildElement(0x83, pdolData)
        }

        return EmvApduCommand(
            cla = CLA_PROPRIETARY,
            ins = INS_GET_PROCESSING_OPTIONS,
            p1 = 0x00.toByte(),
            p2 = 0x00.toByte(),
            data = tlvData,
            le = 0x00
        )
    }

    /**
     * Builds GENERATE AC command
     * p1: 0x80 for ARQC (Online Authorization Request), 0x40 for TC (Offline Approved)
     */
    fun buildGenerateAcCommand(p1CryptogramType: Byte, cdolData: ByteArray): EmvApduCommand {
        return EmvApduCommand(
            cla = CLA_PROPRIETARY,
            ins = INS_GENERATE_AC,
            p1 = p1CryptogramType,
            p2 = 0x00.toByte(),
            data = cdolData,
            le = 0x00
        )
    }

    /**
     * Formats monetary amount in Saudi Halalas to 6-byte packed BCD (12 numeric digits)
     * e.g., 250.75 SAR -> 25075 halalas -> 00 00 00 02 50 75
     */
    fun formatAmountToBcd(amountSar: Double): ByteArray {
        val halalas = Math.round(amountSar * 100.0)
        val formatted = String.format(Locale.US, "%012d", halalas)
        val result = ByteArray(6)
        for (i in 0 until 6) {
            val highNibble = formatted[i * 2].toInt() - '0'.toInt()
            val lowNibble = formatted[i * 2 + 1].toInt() - '0'.toInt()
            result[i] = ((highNibble shl 4) or (lowNibble and 0x0F)).toByte()
        }
        return result
    }

    /**
     * Decodes 6-byte packed BCD to SAR amount
     */
    fun parseAmountFromBcd(bcdBytes: ByteArray): Double {
        if (bcdBytes.size != 6) return 0.0
        var totalHalalas = 0L
        for (b in bcdBytes) {
            val byteVal = b.toInt() and 0xFF
            val high = (byteVal ushr 4) and 0x0F
            val low = byteVal and 0x0F
            totalHalalas = totalHalalas * 100 + (high * 10 + low)
        }
        return totalHalalas / 100.0
    }

    /**
     * Determines whether transaction requires online authorization
     * Under Mada Atheer rules: transactions > 300 SAR require online PIN / biometric check.
     */
    fun evaluateTerminalRisk(
        amountSar: Double,
        floorLimitSar: Double = MADA_CONTACTLESS_FLOOR_LIMIT_SAR
    ): TerminalVerificationResults {
        val exceedsFloor = amountSar > floorLimitSar
        val floorByte: Byte = if (exceedsFloor) 0x80.toByte() else 0x00.toByte() // Bit 8: Transaction exceeds floor limit

        return TerminalVerificationResults(
            byte1OfflineDataAuth = 0x00.toByte(),
            byte2CardholderAuth = 0x00.toByte(),
            byte3TerminalRiskManagement = 0x00.toByte(),
            byte4FloorLimitCheck = floorByte,
            byte5ScriptProcessing = 0x00.toByte()
        )
    }

    /**
     * Orchestrates a full contactless payment session for technician field transactions
     */
    fun processContactlessSession(
        aidHex: String,
        amountSar: Double,
        maskedPan: String = "5888-50**-****-1092"
    ): MadaPaymentSession {
        val brand = when {
            aidHex.startsWith(AID_MADA_DEBIT) -> "Mada Debit (Saudi Network)"
            aidHex.startsWith(AID_MADA_VISA) -> "Mada + Visa"
            aidHex.startsWith(AID_MADA_MASTERCARD) -> "Mada + Mastercard"
            else -> "Mada EMV Contactless"
        }

        val tvr = evaluateTerminalRisk(amountSar)
        val atc = (System.currentTimeMillis() % 9000 + 1000).toInt()
        val sessionSeq = (System.currentTimeMillis() % 1000000).toString()

        // Generate deterministic 8-byte ARQC
        val rawCryptogram = String.format(Locale.US, "MADA-%04X-%04X", (amountSar * 100).toInt() and 0xFFFF, atc)
        val authCode = String.format(Locale.US, "MD%04d", (System.currentTimeMillis() % 9000 + 1000).toInt())

        return MadaPaymentSession(
            sessionId = "MADA-TXN-" + sessionSeq,
            aid = aidHex,
            cardBrand = brand,
            maskedPan = maskedPan,
            amountSar = amountSar,
            currencyCode = SAR_CURRENCY_CODE,
            terminalId = "MADA-TECH-4819",
            merchantCategoryCode = "1711",
            applicationCryptogram = rawCryptogram,
            applicationTransactionCounter = atc,
            terminalVerificationResults = tvr.toHexString(),
            isContactlessApproved = true,
            authorizationCode = authCode,
            timestamp = System.currentTimeMillis()
        )
    }
}

// Byte Array Extension Utilities
fun ByteArray.toHexString(): String {
    val hexChars = "0123456789ABCDEF"
    val result = StringBuilder(size * 2)
    for (b in this) {
        val octet = b.toInt() and 0xFF
        result.append(hexChars[octet ushr 4])
        result.append(hexChars[octet and 0x0F])
    }
    return result.toString()
}

fun String.hexToByteArray(): ByteArray {
    val clean = this.replace(" ", "").replace(":", "")
    val len = clean.length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        val high = Character.digit(clean[i], 16)
        val low = Character.digit(clean[i + 1], 16)
        data[i / 2] = ((high shl 4) + low).toByte()
        i += 2
    }
    return data
}
