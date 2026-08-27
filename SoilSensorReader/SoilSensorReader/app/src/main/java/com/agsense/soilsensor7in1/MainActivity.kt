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
    private lateinit var tvTemperature: TextView
    private lateinit var tvMoisture: TextView
    private lateinit var tvEc: TextView
    private lateinit var tvSalinity: TextView
    private lateinit var tvN: TextView
    private lateinit var tvP: TextView
    private lateinit var tvK: TextView
    private lateinit var tvPh: TextView
    private lateinit var tvLastUpdate: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvTemperature = findViewById(R.id.tvTemperature)
        tvMoisture = findViewById(R.id.tvMoisture)
        tvEc = findViewById(R.id.tvEc)
        tvSalinity = findViewById(R.id.tvSalinity)
        tvN = findViewById(R.id.tvN)
        tvP = findViewById(R.id.tvP)
        tvK = findViewById(R.id.tvK)
        tvPh = findViewById(R.id.tvPh)
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
        tvTemperature.text = "%.1f°C".format(reading.temperatureC)
        tvMoisture.text = "%.1f%%".format(reading.moisturePercent)
        tvEc.text = "${reading.ecUsCm} µS/cm"
        tvSalinity.text = "${reading.salinityMgL} mg/L"
        tvN.text = "${reading.nitrogenMgKg} mg/kg"
        tvP.text = "${reading.phosphorusMgKg} mg/kg"
        tvK.text = "${reading.potassiumMgKg} mg/kg"
        tvPh.text = "%.2f".format(reading.ph)

        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        tvLastUpdate.text = "עדכון אחרון: ${timeFormat.format(Date(reading.timestampMillis))}"
    }

    override fun onDestroy() {
        usbHelper.unregister()
        super.onDestroy()
    }
}
