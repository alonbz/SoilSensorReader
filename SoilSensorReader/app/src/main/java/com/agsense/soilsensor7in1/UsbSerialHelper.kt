package com.agsense.soilsensor7in1

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

/**
 * Manages the USB connection to the soil sensor and polls it periodically
 * using Modbus RTU "Read Holding Registers" requests.
 *
 * Usage: create one instance, call register() in onCreate/onResume and
 * unregister() in onDestroy/onPause. Call findAndConnect() to (re)attempt
 * a connection, e.g. from a "Connect" button.
 */
class UsbSerialHelper(
    private val context: Context,
    private val slaveAddress: Int = 1,
    private val baudRate: Int = 9600,
    private val pollIntervalMs: Long = 2000L,
    private val onReadingReceived: (SoilSensorReading) -> Unit,
    private val onStatus: (String) -> Unit
) {
    companion object {
        private const val ACTION_USB_PERMISSION = "com.agsense.soilsensor7in1.USB_PERMISSION"
        private const val REGISTER_COUNT = 7
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var serialPort: UsbSerialPort? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null
    private var receiverRegistered = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val device: UsbDevice? = getDeviceExtra(intent)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && device != null) {
                        openDevice(device)
                    } else {
                        onStatus("הרשאת USB נדחתה")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    onStatus("חיישן חובר, מתחבר...")
                    findAndConnect()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    onStatus("החיישן נותק")
                    stopPolling()
                    serialPort?.close()
                    serialPort = null
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getDeviceExtra(intent: Intent): UsbDevice? =
        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

    fun register() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
        receiverRegistered = true
    }

    fun unregister() {
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(usbReceiver)
            } catch (_: IllegalArgumentException) {
                // already unregistered
            }
            receiverRegistered = false
        }
        stopPolling()
        serialPort?.close()
        serialPort = null
    }

    /** Looks for a connected USB-serial device, requesting permission if needed. */
    fun findAndConnect() {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            onStatus("לא נמצא חיישן USB מחובר")
            return
        }
        val device = availableDrivers[0].device
        if (usbManager.hasPermission(device)) {
            openDevice(device)
        } else {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val permissionIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION), flags
            )
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    private fun openDevice(device: UsbDevice) {
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver == null) {
            onStatus("שבב ה-USB של החיישן אינו נתמך")
            return
        }
        val connection = usbManager.openDevice(driver.device)
        if (connection == null) {
            onStatus("לא ניתן לפתוח חיבור לחיישן (נסה לנתק ולחבר שוב)")
            return
        }
        try {
            val port = driver.ports[0]
            port.open(connection)
            port.setParameters(
                baudRate,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            serialPort = port
            onStatus("מחובר לחיישן")
            startPolling()
        } catch (e: Exception) {
            onStatus("שגיאה בפתיחת החיבור: ${e.message}")
        }
    }

    private fun startPolling() {
        stopPolling()
        val runnable = object : Runnable {
            override fun run() {
                requestReading()
                handler.postDelayed(this, pollIntervalMs)
            }
        }
        pollRunnable = runnable
        handler.post(runnable)
    }

    private fun stopPolling() {
        pollRunnable?.let { handler.removeCallbacks(it) }
        pollRunnable = null
    }

    private fun requestReading() {
        val port = serialPort ?: return
        try {
            val request = ModbusRtu.buildReadHoldingRegistersRequest(slaveAddress, 0x0000, REGISTER_COUNT)
            port.write(request, 500)

            val response = ByteArray(64)
            val length = port.read(response, 500)
            if (length <= 0) {
                onStatus("אין תגובה מהחיישן")
                return
            }

            val registers = ModbusRtu.parseReadHoldingRegistersResponse(response.copyOf(length))
            if (registers == null) {
                onStatus("תגובה לא תקינה מהחיישן (ייתכן שכתובת/baud rate שונים מברירת המחדל)")
                return
            }

            SoilSensorReading.fromRegisters(registers)?.let { reading ->
                onStatus("מחובר לחיישן")
                onReadingReceived(reading)
            }
        } catch (e: Exception) {
            onStatus("שגיאת תקשורת: ${e.message}")
        }
    }
}
