package com.agsense.soilsensor7in1

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var usbHelper: UsbSerialHelper

    private lateinit var tvStatus: TextView
    private lateinit var tvMoisture: TextView
    private lateinit var tvTemperature: TextView
    private lateinit var tvEc: TextView
    private lateinit var tvPh: TextView
    private lateinit var tvN: TextView
    private lateinit var tvP: TextView
    private lateinit var tvK: TextView
    private lateinit var tvLastUpdate: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvMoisture = findViewById(R.id.tvMoisture)
        tvTemperature = findViewById(R.id.tvTemperature)
        tvEc = findViewById(R.id.tvEc)
        tvPh = findViewById(R.id.tvPh)
        tvN = findViewById(R.id.tvN)
        tvP = findViewById(R.id.tvP)
        tvK = findViewById(R.id.tvK)
        tvLastUpdate = findViewById(R.id.tvLastUpdate)

        usbHelper = UsbSerialHelper(
            context = this,
            slaveAddress = 1,
            baudRate = 9600,
            pollIntervalMs = 2000L,
            onReadingReceived = { reading -> runOnUiThread { updateUi(reading) } },
            onStatus = { message -> runOnUiThread { tvStatus.text = message } }
        )
        usbHelper.register()

        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            tvStatus.text = "מחפש חיישן..."
            usbHelper.findAndConnect()
        }

        // Try to auto-connect on launch, in case the sensor is already plugged in.
        usbHelper.findAndConnect()
    }

    private fun updateUi(reading: SoilSensorReading) {
        tvMoisture.text = "לחות: %.1f%%".format(reading.moisturePercent)
        tvTemperature.text = "טמפרטורה: %.1f°C".format(reading.temperatureC)
        tvEc.text = "מוליכות (EC): ${reading.ecUsCm} µS/cm"
        tvPh.text = "pH: %.1f".format(reading.ph)
        tvN.text = "חנקן (N): ${reading.nitrogenMgKg} mg/kg"
        tvP.text = "זרחן (P): ${reading.phosphorusMgKg} mg/kg"
        tvK.text = "אשלגן (K): ${reading.potassiumMgKg} mg/kg"

        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        tvLastUpdate.text = "עדכון אחרון: ${timeFormat.format(Date(reading.timestampMillis))}"
    }

    override fun onDestroy() {
        usbHelper.unregister()
        super.onDestroy()
    }
}
