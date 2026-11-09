package sa.factory.injazcareservices1.data.repository

import sa.factory.injazcareservices1.domain.repository.InjazCareRepository
import sa.factory.injazcareservices1.hardware.HardwareSensorDriver
import sa.factory.injazcareservices1.model.InjazCareItem
import sa.factory.injazcareservices1.model.InjazCareOrder
import sa.factory.injazcareservices1.model.InjazCareTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class InjazCareRepositoryImpl(
    private val sensorDriver: HardwareSensorDriver
) : InjazCareRepository {

    private val _catalog = MutableStateFlow<List<InjazCareItem>>(listOf(
        InjazCareItem("1", "إعلان فيديو باللهجة السعودية", "تقديم إعلاني أمام الكاميرا مع إتقان اللهجة السعودية لمدة 30-60 ثانية بدقة 4K", 150.0, "معتمد", "إنتاج إعلانات"),
        InjazCareItem("2", "حملة إعلانية فورية", "تصوير وتسليم إعلان خلال 24 ساعة مع تحديد الموقع عبر GPS", 280.0, "فوري", "تسويق مؤثرين"),
        InjazCareItem("3", "تصوير احترافي جديد", "جلسة تصوير إعلاني مع إضاءة احترافية ومونتاج أساسي", 95.0, "جديد", "تصوير احترافي")
    ))
    override val catalogFlow: Flow<List<InjazCareItem>> = _catalog.asStateFlow()

    private val _cart = MutableStateFlow<List<InjazCareItem>>(emptyList())
    override val activeCart: StateFlow<List<InjazCareItem>> = _cart.asStateFlow()

    override val telemetryFlow: Flow<InjazCareTelemetry> = sensorDriver.observePeripheralStream()
        .map { frame ->
            InjazCareTelemetry(
                peripheralName = frame.peripheralName,
                status = frame.connectionState,
                liveReading = frame.livePayload,
                batteryPct = 96,
                signalDbm = -54
            )
        }

    override fun addToCart(item: InjazCareItem) {
        val current = _cart.value.toMutableList()
        current.add(item)
        _cart.value = current
    }

    override fun removeFromCart(item: InjazCareItem) {
        val current = _cart.value.toMutableList()
        current.remove(item)
        _cart.value = current
    }

    override fun clearCart() {
        _cart.value = emptyList()
    }

    override suspend fun checkout(paymentMethod: String): InjazCareOrder {
        val currentItems = _cart.value.toList()
        val subtotal = currentItems.sumOf { it.priceSar }
        val vat = subtotal * 0.15
        val total = subtotal + vat
        val order = InjazCareOrder(
            orderId = "ORD-${System.currentTimeMillis() % 100000}",
            items = currentItems,
            subtotalSar = subtotal,
            vatSar = vat,
            totalSar = total,
            paymentMethod = paymentMethod
        )
        clearCart()
        return order
    }

    override suspend fun refreshCatalog() {
        // Offline-first sync logic
    }
}
