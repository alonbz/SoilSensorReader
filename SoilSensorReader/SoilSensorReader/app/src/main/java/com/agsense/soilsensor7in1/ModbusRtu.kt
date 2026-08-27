package com.agsense.soilsensor7in1

/**
 * Minimal Modbus RTU implementation covering only what this sensor needs:
 * function code 0x03 (Read Holding Registers).
 *
 * Used here for a Chinese "8-in-1" soil sensor (temperature / moisture / EC /
 * salinity / N / P / K / pH) that exposes an RS485-over-USB (CH340/CP210x/
 * FTDI) interface. Confirmed against the manufacturer's own app:
 *   - Slave address: 1
 *   - Baud rate: 9600 8N1
 *   - Registers 0x0000..0x0007 hold, in order: temperature, moisture, EC,
 *     salinity, nitrogen, phosphorus, potassium, pH.
 *     See SoilSensorReading.fromRegisters() for the exact scaling of each.
 */
object ModbusRtu {

    private const val FUNCTION_READ_HOLDING_REGISTERS = 0x03

    /** Standard Modbus CRC-16 (poly 0xA001, init 0xFFFF). */
    fun crc16(data: ByteArray, length: Int = data.size): Int {
        var crc = 0xFFFF
        for (pos in 0 until length) {
            crc = crc xor (data[pos].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 0x0001 != 0) {
                    (crc ushr 1) xor 0xA001
                } else {
                    crc ushr 1
                }
            }
        }
        return crc and 0xFFFF
    }

    /**
     * Builds a "Read Holding Registers" request frame:
     * [slaveAddr][0x03][startRegHi][startRegLo][quantityHi][quantityLo][crcLo][crcHi]
     */
    fun buildReadHoldingRegistersRequest(slaveAddr: Int, startReg: Int, quantity: Int): ByteArray {
        val frame = byteArrayOf(
            slaveAddr.toByte(),
            FUNCTION_READ_HOLDING_REGISTERS.toByte(),
            ((startReg shr 8) and 0xFF).toByte(),
            (startReg and 0xFF).toByte(),
            ((quantity shr 8) and 0xFF).toByte(),
            (quantity and 0xFF).toByte()
        )
        val crc = crc16(frame)
        return frame + byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte())
    }

    /**
     * Parses a "Read Holding Registers" response.
     * Returns the register values, or null if the frame is too short, is an
     * exception response, or fails the CRC check.
     */
    fun parseReadHoldingRegistersResponse(data: ByteArray): IntArray? {
        if (data.size < 5) return null

        val function = data[1].toInt() and 0xFF
        if (function != FUNCTION_READ_HOLDING_REGISTERS) return null // includes 0x83 exception replies

        val byteCount = data[2].toInt() and 0xFF
        val expectedLength = 3 + byteCount + 2
        if (data.size < expectedLength) return null

        val crcLo = data[3 + byteCount].toInt() and 0xFF
        val crcHi = data[3 + byteCount + 1].toInt() and 0xFF
        val crcReceived = (crcHi shl 8) or crcLo
        val crcCalculated = crc16(data, 3 + byteCount)
        if (crcReceived != crcCalculated) return null

        val registerCount = byteCount / 2
        val registers = IntArray(registerCount)
        for (i in 0 until registerCount) {
            val hi = data[3 + i * 2].toInt() and 0xFF
            val lo = data[3 + i * 2 + 1].toInt() and 0xFF
            registers[i] = (hi shl 8) or lo
        }
        return registers
    }
}
