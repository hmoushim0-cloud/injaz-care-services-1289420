package sa.factory.injazcareservices1

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sa.factory.injazcareservices1.domain.services.*
import sa.factory.injazcareservices1.hardware.emv.*

class InjazCareServicesFeatureTest {

    private lateinit var emvKernel: MadaEmvKernel
    private lateinit var dispatchEngine: TechnicianDispatchEngine

    @Before
    fun setUp() {
        emvKernel = MadaEmvKernel()
        dispatchEngine = TechnicianDispatchEngine()
    }

    @Test
    fun testIso7816ApduSerialization() {
        val selectCommand = emvKernel.buildSelectAidCommand(MadaEmvKernel.AID_MADA_DEBIT)
        val apduBytes = selectCommand.toByteArray()

        assertEquals(0x00.toByte(), selectCommand.cla)
        assertEquals(MadaEmvKernel.INS_SELECT, selectCommand.ins)
        assertEquals(0x04.toByte(), selectCommand.p1)
        assertEquals(0x00.toByte(), selectCommand.p2)

        // AID A000000228 is 5 bytes, so command length = 4 (header) + 1 (Lc) + 5 (data) + 1 (Le) = 11 bytes
        assertEquals(11, apduBytes.size)
        assertEquals(0x05.toByte(), apduBytes[4]) // Lc = 5
        assertEquals("00A4040005A00000022800", apduBytes.toHexString())
    }

    @Test
    fun testMadaBcdAmountEncodingAndDecoding() {
        val testAmount = 285.50
        val bcdBytes = emvKernel.formatAmountToBcd(testAmount)

        assertEquals(6, bcdBytes.size)
        // 28550 halalas -> 00 00 00 02 85 50
        assertEquals("000000028550", bcdBytes.toHexString())

        val decodedAmount = emvKernel.parseAmountFromBcd(bcdBytes)
        assertEquals(testAmount, decodedAmount, 0.001)

        // Edge case: zero halalas
        val zeroBcd = emvKernel.formatAmountToBcd(0.0)
        assertEquals("000000000000", zeroBcd.toHexString())
        assertEquals(0.0, emvKernel.parseAmountFromBcd(zeroBcd), 0.001)
    }

    @Test
    fun testBerTlvParserAndBuilder() {
        val tagAid = MadaEmvKernel.TAG_AID
        val aidValue = MadaEmvKernel.AID_MADA_DEBIT.hexToByteArray()

        val builtTlv = BerTlvParser.buildElement(tagAid, aidValue)
        val parsedList = BerTlvParser.parse(builtTlv)

        assertEquals(1, parsedList.size)
        val element = parsedList[0]
        assertEquals(tagAid, element.tag)
        assertEquals(aidValue.size, element.length)
        assertEquals(MadaEmvKernel.AID_MADA_DEBIT, element.valueHex)
    }

    @Test
    fun testTerminalFloorLimitRiskEvaluation() {
        // Below floor limit: 200 SAR <= 300 SAR
        val tvrBelow = emvKernel.evaluateTerminalRisk(200.0)
        assertEquals(0x00.toByte(), tvrBelow.byte4FloorLimitCheck)

        // Above floor limit: 450 SAR > 300 SAR
        val tvrAbove = emvKernel.evaluateTerminalRisk(450.0)
        assertEquals(0x80.toByte(), tvrAbove.byte4FloorLimitCheck)
    }

    @Test
    fun testHaversineNearestTechnicianDispatch() {
        val technicianPool = listOf(
            TechnicianProfile(
                technicianId = "TECH-01",
                fullNameAr = "فهد المطيري",
                trade = TradeSpecialty.PLUMBING,
                grade = TechnicianCertificationGrade.JOURNEYMAN,
                licenseNumber = "SADA-2026-101",
                latitude = 24.7743, // Al-Malqa, Riyadh
                longitude = 46.6385,
                isAvailable = true
            ),
            TechnicianProfile(
                technicianId = "TECH-02",
                fullNameAr = "سلطان الغامدي",
                trade = TradeSpecialty.PLUMBING,
                grade = TechnicianCertificationGrade.MASTER_TECHNICIAN,
                licenseNumber = "SADA-2026-202",
                latitude = 24.6900, // Al-Sulaimaniyah, Riyadh
                longitude = 46.7000,
                isAvailable = true
            ),
            TechnicianProfile(
                technicianId = "TECH-03",
                fullNameAr = "خالد الدوسري",
                trade = TradeSpecialty.ELECTRICAL,
                grade = TechnicianCertificationGrade.JOURNEYMAN,
                licenseNumber = "SADA-2026-303",
                latitude = 24.7700,
                longitude = 46.6350,
                isAvailable = true
            )
        )

        // Customer in Al-Sahafa, Riyadh (24.7800, 46.6400)
        val customerLat = 24.7800
        val customerLon = 46.6400

        val nearestPlumber = dispatchEngine.findNearestTechnician(
            customerLat = customerLat,
            customerLon = customerLon,
            trade = TradeSpecialty.PLUMBING,
            pool = technicianPool
        )

        assertNotNull(nearestPlumber)
        assertEquals("TECH-01", nearestPlumber?.technicianId)
        assertEquals("فهد المطيري", nearestPlumber?.fullNameAr)
    }

    @Test
    fun testEndToEndWorkOrderBillingAndMadaPaymentLifecycle() {
        val order = dispatchEngine.createWorkOrder(
            orderId = "WO-2026-9812",
            customerId = "CUST-441",
            customerNameAr = "عبدالعزيز بن ناصر",
            customerPhone = "+966501234567",
            customerAddress = "حي النرجس، الرياض",
            customerLat = 24.8210,
            customerLon = 46.6540,
            trade = TradeSpecialty.HVAC_COOLING,
            descriptionAr = "صيانة مكيف سبليت مركزي وتغيير مكثف التشغيل"
        )

        val masterHvacTech = TechnicianProfile(
            technicianId = "TECH-HVAC-99",
            fullNameAr = "المهندس بندر القحطاني",
            trade = TradeSpecialty.HVAC_COOLING,
            grade = TechnicianCertificationGrade.MASTER_TECHNICIAN, // Callout: 100 SAR, Hourly: 180 SAR
            licenseNumber = "SADA-2026-HVAC-88",
            latitude = 24.8100,
            longitude = 46.6500,
            isAvailable = true
        )

        // Assign technician
        val dispatchedOrder = dispatchEngine.assignTechnician(order, masterHvacTech)
        assertEquals(WorkOrderStatus.DISPATCHED, dispatchedOrder.status)
        assertEquals(100.0, dispatchedOrder.calloutFeeSar, 0.001)

        // Attach replacement spare parts
        val spareParts = listOf(
            SparePartItem("CAP-50UF", "مكثف تشغيل 50 ميكروفاراد أمريكي", "Dual Run Capacitor 50uF", 150.0, 1, 24),
            SparePartItem("CON-30A", "قاطع تلامسي كهربائي 30 أمبير", "Electrical Contactor 30A", 120.0, 1, 12)
        )
        val partsOrder = dispatchEngine.attachSpareParts(dispatchedOrder, spareParts)
        assertEquals(270.0, partsOrder.sparePartsTotalSar, 0.001)

        // Finalize 2.0 hours of labor
        val billedOrder = dispatchEngine.finalizeBilling(partsOrder, 2.0)
        // Subtotal = 100.0 (callout) + 360.0 (2 hrs * 180 SAR) + 270.0 (parts) = 730.0 SAR
        assertEquals(730.0, billedOrder.subtotalSar, 0.01)
        // ZATCA VAT 15% = 730 * 0.15 = 109.50 SAR
        assertEquals(109.50, billedOrder.vatSar, 0.01)
        // Total = 839.50 SAR
        assertEquals(839.50, billedOrder.totalSar, 0.01)
        assertEquals(WorkOrderStatus.PAYMENT_PENDING, billedOrder.status)

        // Process Mada contactless payment tap via EMV kernel
        val madaSession = emvKernel.processContactlessSession(MadaEmvKernel.AID_MADA_DEBIT, billedOrder.totalSar)
        assertTrue(madaSession.isContactlessApproved)
        assertEquals(839.50, madaSession.amountSar, 0.01)

        val paidOrder = dispatchEngine.recordMadaPayment(billedOrder, madaSession)
        assertEquals(WorkOrderStatus.MADA_PAID, paidOrder.status)
        assertEquals(madaSession.sessionId, paidOrder.paymentTransactionId)
        assertNotNull(paidOrder.closedAt)
    }
}
