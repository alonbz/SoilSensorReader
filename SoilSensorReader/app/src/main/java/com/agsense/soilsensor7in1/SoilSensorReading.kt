package com.agsense.soilsensor7in1

data class SoilSensorReading(
    val moisturePercent: Double,
    val temperatureC: Double,
    val ecUsCm: Int,
    val ph: Double,
    val nitrogenMgKg: Int,
    val phosphorusMgKg: Int,
    val potassiumMgKg: Int,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    companion object {
        /** Builds a reading from 7 raw Modbus registers, see ModbusRtu docs for the layout. */
        fun fromRegisters(regs: IntArray): SoilSensorReading? {
            if (regs.size < 7) return null

            val rawTemp = regs[1]
            val signedTemp = if (rawTemp > 32767) rawTemp - 65536 else rawTemp

            return SoilSensorReading(
                moisturePercent = regs[0] / 10.0,
                temperatureC = signedTemp / 10.0,
                ecUsCm = regs[2],
                ph = regs[3] / 10.0,
                nitrogenMgKg = regs[4],
                phosphorusMgKg = regs[5],
                potassiumMgKg = regs[6]
            )
        }
    }
}
