package com.agsense.soilsensor7in1

data class SoilSensorReading(
    val temperatureC: Double,
    val moisturePercent: Double,
    val ecUsCm: Int,
    val salinityMgL: Int,
    val nitrogenMgKg: Int,
    val phosphorusMgKg: Int,
    val potassiumMgKg: Int,
    val ph: Double,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Builds a reading from 8 raw Modbus registers (confirmed against the
         * manufacturer app's readings), in order:
         *   0: temperature (raw/10 = °C, two's-complement signed)
         *   1: moisture    (raw/10 = %RH)
         *   2: EC          (raw = µS/cm)
         *   3: salinity    (raw = mg/L)
         *   4: nitrogen    (raw = mg/kg)
         *   5: phosphorus  (raw = mg/kg)
         *   6: potassium   (raw = mg/kg)
         *   7: pH          (raw/100 = pH)
         */
        fun fromRegisters(regs: IntArray): SoilSensorReading? {
            if (regs.size < 8) return null

            val rawTemp = regs[0]
            val signedTemp = if (rawTemp > 32767) rawTemp - 65536 else rawTemp

            return SoilSensorReading(
                temperatureC = signedTemp / 10.0,
                moisturePercent = regs[1] / 10.0,
                ecUsCm = regs[2],
                salinityMgL = regs[3],
                nitrogenMgKg = regs[4],
                phosphorusMgKg = regs[5],
                potassiumMgKg = regs[6],
                ph = regs[7] / 100.0
            )
        }
    }
}
