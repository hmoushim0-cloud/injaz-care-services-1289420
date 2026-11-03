package sa.factory.injazcareservices1.hardware

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

data class HardwareTelemetryFrame(
    val peripheralName: String,
    val connectionState: String,
    val livePayload: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HardwareSensorDriver {
    private val peripheral = "Mada Contactless EMV L2 NFC + GPS Dispatch"

    fun observePeripheralStream(): Flow<HardwareTelemetryFrame> = flow {
        emit(HardwareTelemetryFrame(peripheral, "CONNECTING", "Handshake initializing..."))
        delay(300)
        emit(HardwareTelemetryFrame(peripheral, "ONLINE", "NFC: Mada Card Detected | GPS: 24.7136° N, 46.6753° E"))

        while (true) {
            delay(1500)
            val jitter = Random.nextInt(1, 10)
            emit(HardwareTelemetryFrame(peripheral, "ACTIVE_STREAMING", "NFC: Mada Card Detected | GPS: 24.7136° N, 46.6753° E | Frame #${System.currentTimeMillis() % 10000} (Jitter: +${jitter}ms)"))
        }
    }
}
