package sa.factory.injazcareservices1.domain.services

import sa.factory.injazcareservices1.hardware.emv.MadaPaymentSession
import java.io.Serializable
import java.util.Locale

/**
 * Trade Specialties for on-demand home & facility maintenance
 */
enum class TradeSpecialty(val titleAr: String, val code: String) {
    PLUMBING("السباكة والتمديدات الصحية", "PLUMB"),
    ELECTRICAL("الكهرباء والتمديدات الذكية", "ELEC"),
    HVAC_COOLING("التكييف والتبريد المركزي والمخفي", "HVAC"),
    APPLIANCE_REPAIR("صيانة الأجهزة المنزلية", "APPL"),
    CARPENTRY("النجارة والتركيبات الخشبية", "CARP"),
    LOCKSMITH("الأقفال والأنظمة الأمنية الذكية", "LOCK")
}

/**
 * Saudi Certified Technical Skill Levels
 */
enum class TechnicianCertificationGrade(
    val titleAr: String,
    val hourlyRateSar: Double,
    val calloutFeeSar: Double
) {
    APPRENTICE("فني مساعد مرخص", 75.0, 50.0),
    JOURNEYMAN("فني مهني معتمد", 120.0, 75.0),
    MASTER_TECHNICIAN("كبير مهندسين حرفي", 180.0, 100.0)
}

/**
 * Technician profile representation
 */
data class TechnicianProfile(
    val technicianId: String,
    val fullNameAr: String,
    val trade: TradeSpecialty,
    val grade: TechnicianCertificationGrade,
    val licenseNumber: String, // e.g. "SADA-2026-9901"
    val latitude: Double,
    val longitude: Double,
    val rating: Double = 4.9,
    val isAvailable: Boolean = true
) : Serializable

/**
 * Lifecycle stages of a Saudi on-demand field service order
 */
enum class WorkOrderStatus {
    PENDING_ASSIGNMENT,
    DISPATCHED,
    EN_ROUTE,
    ARRIVED_ON_SITE,
    DIAGNOSTIC_COMPLETED,
    ESTIMATE_APPROVED,
    IN_PROGRESS,
    WORK_COMPLETED,
    PAYMENT_PENDING,
    MADA_PAID,
    CLOSED
}

/**
 * Replacement spare part or material item
 */
data class SparePartItem(
    val sku: String,
    val nameAr: String,
    val nameEn: String,
    val unitPriceSar: Double,
    val quantity: Int = 1,
    val warrantyMonths: Int = 12
) : Serializable {
    val lineTotalSar: Double
        get() = unitPriceSar * quantity
}

/**
 * Complete Service Work Order model
 */
data class WorkOrder(
    val orderId: String,
    val customerId: String,
    val customerNameAr: String,
    val customerPhone: String,
    val customerAddress: String,
    val customerLatitude: Double,
    val customerLongitude: Double,
    val trade: TradeSpecialty,
    val problemDescriptionAr: String,
    val status: WorkOrderStatus = WorkOrderStatus.PENDING_ASSIGNMENT,
    val technician: TechnicianProfile? = null,
    val estimatedHours: Double = 1.0,
    val actualHours: Double = 0.0,
    val spareParts: List<SparePartItem> = emptyList(),
    val calloutFeeSar: Double = 0.0,
    val laborFeeSar: Double = 0.0,
    val sparePartsTotalSar: Double = 0.0,
    val subtotalSar: Double = 0.0,
    val vatSar: Double = 0.0, // 15% ZATCA VAT
    val totalSar: Double = 0.0,
    val paymentTransactionId: String? = null,
    val paymentCardMasked: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null
) : Serializable

/**
 * Autonomous Field Technician Dispatch & Billing Engine
 */
class TechnicianDispatchEngine {

    companion object {
        const val ZATCA_VAT_RATE = 0.15 // 15% standard rate in KSA
        const val EARTH_RADIUS_KM = 6371.0
    }

    /**
     * Calculates distance between coordinates using Haversine formula
     */
    fun calculateHaversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Finds nearest available technician specializing in the requested trade
     */
    fun findNearestTechnician(
        customerLat: Double,
        customerLon: Double,
        trade: TradeSpecialty,
        pool: List<TechnicianProfile>,
        maxRadiusKm: Double = 35.0
    ): TechnicianProfile? {
        return pool.asSequence()
            .filter { it.isAvailable && it.trade == trade }
            .map { tech ->
                val distance = calculateHaversineDistanceKm(customerLat, customerLon, tech.latitude, tech.longitude)
                Pair(tech, distance)
            }
            .filter { it.second <= maxRadiusKm }
            .minBy { it.second }
            ?.first
    }

    /**
     * Creates a new unassigned service request
     */
    fun createWorkOrder(
        orderId: String,
        customerId: String,
        customerNameAr: String,
        customerPhone: String,
        customerAddress: String,
        customerLat: Double,
        customerLon: Double,
        trade: TradeSpecialty,
        descriptionAr: String
    ): WorkOrder {
        return WorkOrder(
            orderId = orderId,
            customerId = customerId,
            customerNameAr = customerNameAr,
            customerPhone = customerPhone,
            customerAddress = customerAddress,
            customerLatitude = customerLat,
            customerLongitude = customerLon,
            trade = trade,
            problemDescriptionAr = descriptionAr,
            status = WorkOrderStatus.PENDING_ASSIGNMENT,
            createdAt = System.currentTimeMillis()
        )
    }

    /**
     * Assigns technician to work order and transitions status to DISPATCHED
     */
    fun assignTechnician(workOrder: WorkOrder, technician: TechnicianProfile): WorkOrder {
        require(technician.isAvailable) { "Technician is currently unavailable for dispatch" }
        require(technician.trade == workOrder.trade) { "Technician trade mismatch: expected ${workOrder.trade}, got ${technician.trade}" }

        return workOrder.copy(
            technician = technician,
            calloutFeeSar = technician.grade.calloutFeeSar,
            status = WorkOrderStatus.DISPATCHED
        )
    }

    /**
     * Updates work order state through operational lifecycle
     */
    fun updateStatus(workOrder: WorkOrder, newStatus: WorkOrderStatus): WorkOrder {
        return workOrder.copy(status = newStatus)
    }

    /**
     * Adds replacement spare parts to work order
     */
    fun attachSpareParts(workOrder: WorkOrder, parts: List<SparePartItem>): WorkOrder {
        val mergedParts = workOrder.spareParts + parts
        val sparePartsTotal = mergedParts.sumByDouble { it.lineTotalSar }
        return recalculateTotals(workOrder.copy(spareParts = mergedParts, sparePartsTotalSar = sparePartsTotal))
    }

    /**
     * Finalizes labor time and calculates exact billing with ZATCA 15% VAT
     */
    fun finalizeBilling(workOrder: WorkOrder, actualHours: Double): WorkOrder {
        val tech = workOrder.technician
        val hourlyRate = tech?.grade?.hourlyRateSar ?: 100.0
        val laborTotal = Math.round(actualHours * hourlyRate * 100.0) / 100.0

        val updatedOrder = workOrder.copy(
            actualHours = actualHours,
            laborFeeSar = laborTotal,
            status = WorkOrderStatus.PAYMENT_PENDING
        )
        return recalculateTotals(updatedOrder)
    }

    /**
     * Internal recalculation of subtotal, VAT, and grand total
     */
    private fun recalculateTotals(order: WorkOrder): WorkOrder {
        val subtotal = Math.round((order.calloutFeeSar + order.laborFeeSar + order.sparePartsTotalSar) * 100.0) / 100.0
        val vat = Math.round(subtotal * ZATCA_VAT_RATE * 100.0) / 100.0
        val total = Math.round((subtotal + vat) * 100.0) / 100.0

        return order.copy(
            subtotalSar = subtotal,
            vatSar = vat,
            totalSar = total
        )
    }

    /**
     * Records successful Mada contactless payment and closes the work order
     */
    fun recordMadaPayment(workOrder: WorkOrder, session: MadaPaymentSession): WorkOrder {
        require(session.isContactlessApproved) { "Contactless payment was declined by Mada network" }
        require(Math.abs(session.amountSar - workOrder.totalSar) < 0.05) {
            "Payment amount mismatch: expected SAR ${workOrder.totalSar}, received SAR ${session.amountSar}"
        }

        return workOrder.copy(
            status = WorkOrderStatus.MADA_PAID,
            paymentTransactionId = session.sessionId,
            paymentCardMasked = session.maskedPan,
            closedAt = System.currentTimeMillis()
        )
    }
}
