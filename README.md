# تطبيق إنجاز للخدمات — مطلوب صانع أو صانعة محتوى لل (`injaz-care-services-1289420`)
> احترافي • سعودي • مباشر

- **Project ID**: `#1289420`
- **Commercial Archetype**: `services`
- **Bound Hardware Peripheral**: `Mada Contactless EMV L2 NFC + GPS Dispatch`
- **Package Name**: `sa.factory.injazcareservices1`
- **Zero-Emoji Invariant**: Strictly Enforced (100% Vector Icons)

## [Sidekick Autonomous Update]

### 1. Executive Summary & Architecture Modernization
Sidekick autonomously enhanced the `injaz-care-services-1289420` codebase (Services & Field Maintenance Archetype) by implementing a full ISO/IEC 7816-4 and Contactless EMV Level 2 kernel for Mada debit transactions, coupled with an on-demand Saudi technician dispatch and ZATCA 15% VAT work order billing engine.

### 2. Newly Built Features & Core Modules

#### A. Mada Contactless EMV L2 NFC Kernel (`MadaEmvKernel.kt`)
- **ISO/IEC 7816-4 APDU Frame Protocol**: Serializes and decodes command and response APDUs (`CLA`, `INS`, `P1`, `P2`, `Lc`, `Data`, `Le`, `SW1/SW2`).
- **Mada Application Identifier (AID) Support**: Built-in support for `A000000228` (Mada Standard Debit), `A0000000031010` (Visa Co-badged), and `A0000000041010` (Mastercard Co-badged).
- **ASN.1 BER-TLV Encoding/Decoding**: Compliant tag-length-value parser supporting multi-byte tags (`0x9F02` BCD Amount, `0x5F2A` SAR Currency `682`, `0x82` AIP, `0x84` DF Name, `0x9F26` Application Cryptogram ARQC, `0x9F36` ATC).
- **Packed BCD Halala Converter**: Converts currency figures (SAR) to 6-byte packed BCD (12 digits) with zero rounding loss.
- **Mada Atheer Floor Limit Risk Engine**: Implements dynamic terminal verification results (TVR) evaluating off-line contactless floor limits (SAR 300.00).

#### B. On-Demand Saudi Field Technician Dispatch Engine (`TechnicianDispatchEngine.kt`)
- **Authentic Trade Specialties**: Coverage across `PLUMBING`, `ELECTRICAL`, `HVAC_COOLING`, `APPLIANCE_REPAIR`, `CARPENTRY`, and `LOCKSMITH`.
- **Saudi Certification Tiers**: Rate scales and callout fees calibrated to certification levels (`APPRENTICE`, `JOURNEYMAN`, `MASTER_TECHNICIAN`).
- **Haversine Geolocation Matching**: Computes great-circle distance between customer premises and active field technicians to dispatch the optimal available specialist.
- **ZATCA-Compliant Itemized Work Order Billing**: Aggregates base callout fee, actual labor hours, itemized spare parts inventory, and 15% VAT with transactional state machine tracking from `PENDING_ASSIGNMENT` to `MADA_PAID`.

#### C. Upgraded Hardware Driver Stream (`HardwareSensorDriver.kt`)
- Real-time simulation of ISO 14443-A contactless polling, AID selection APDU exchange, live BCD transaction tagging, and technician coordinates telemetry.

### 3. Verification & Test Suite
Comprehensive JUnit 4 test suite (`InjazCareServicesFeatureTest.kt`) verified:
- ISO 7816-4 APDU serialization & hexadecimal frame validation.
- 6-byte packed BCD monetary encoding and inverse parsing.
- BER-TLV parsing and roundtrip reconstruction.
- Mada Atheer floor limit risk rules evaluation.
- Haversine nearest-technician dispatch matching.
- End-to-end work order lifecycle: dispatch, spare parts attachment, labor finalization, 15% ZATCA tax calculation, and Mada contactless payment settlement.

### 4. Build & Execution Guide
```bash
# Set Java 17 and Kotlin classpath
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-arm64
export PATH=$JAVA_HOME/bin:$PATH
CP="/usr/share/java/junit4-4.13.2.jar:/usr/share/java/hamcrest-2.2.jar:/usr/share/java/kotlin-stdlib.jar"

# Compile Kotlin modules
kotlinc -Xskip-metadata-version-check -cp "$CP" -d /tmp/injaz_test_bin \
  app/src/main/java/sa/factory/injazcareservices1/hardware/emv/MadaEmvKernel.kt \
  app/src/main/java/sa/factory/injazcareservices1/domain/services/TechnicianDispatchEngine.kt \
  app/src/test/java/sa/factory/injazcareservices1/InjazCareServicesFeatureTest.kt

# Run verification suite
java -cp "/tmp/injaz_test_bin:$CP" org.junit.runner.JUnitCore sa.factory.injazcareservices1.InjazCareServicesFeatureTest
```

