package sa.factory.injazcareservices1.hardware

import sa.factory.injazcareservices1.hardware.emv.MadaEmvKernel
import sa.factory.injazcareservices1.hardware.emv.MadaPaymentSession
import sa.factory.injazcareservices1.hardware.emv.toHexString
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale
import kotlin.random.Random

data class HardwareTelemetryFrame(
    val peripheralName: String,
    val connectionState: String,
    val livePayload: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HardwareSensorDriver {
    private val peripheral = "Mada Contactless EMV L2 NFC + GPS Dispatch"
    private val emvKernel = MadaEmvKernel()

    fun getEmvKernel(): MadaEmvKernel = emvKernel

    fun simulateCardTap(amountSar: Double, aidHex: String = MadaEmvKernel.AID_MADA_DEBIT): MadaPaymentSession {
        return emvKernel.processContactlessSession(aidHex, amountSar)
    }

    fun observePeripheralStream(): Flow<HardwareTelemetryFrame> = flow {
        emit(HardwareTelemetryFrame(peripheral, "CONNECTING", "NFC ISO 14443-A Polling Active..."))
        delay(300)

        // Select Mada AID
        val selectApdu = emvKernel.buildSelectAidCommand(MadaEmvKernel.AID_MADA_DEBIT)
        val apduHex = selectApdu.toHexString()
        emit(HardwareTelemetryFrame(
            peripheral,
            "APDU_EXCHANGE",
            "SELECT AID: $apduHex -> Status: 9000 SW_SUCCESS (Mada Debit App Detected)"
        ))

        delay(400)
        emit(HardwareTelemetryFrame(
            peripheral,
            "ONLINE",
            "Mada Contactless L2 Ready | PosID: MADA-POS-9812 | GPS: 24.7136 N, 46.6753 E (Riyadh Dispatch Central)"
        ))

        var frameCounter = 1
        while (true) {
            delay(1500)
            val jitter = Random.nextInt(1, 10)
            val simulatedAmount = (Random.nextInt(50, 450) * 1.0)
            val bcdBytes = emvKernel.formatAmountToBcd(simulatedAmount)
            val bcdHex = bcdBytes.toHexString()

            val latJitter = 24.7136 + (Random.nextDouble() - 0.5) * 0.005
            val lonJitter = 46.6753 + (Random.nextDouble() - 0.5) * 0.005
            val locationStr = String.format(Locale.US, "%.4f N, %.4f E", latJitter, lonJitter)

            emit(HardwareTelemetryFrame(
                peripheral,
                "ACTIVE_STREAMING",
                "Mada NFC Standby | Tag 9F02 (BCD): $bcdHex | Tech GPS: $locationStr | Frame #$frameCounter (+${jitter}ms)"
            ))
            frameCounter++
        }
    }
}
