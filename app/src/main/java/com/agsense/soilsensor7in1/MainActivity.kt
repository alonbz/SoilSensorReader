package com.agsense.soilsensor7in1

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var usbHelper: UsbSerialHelper
    private lateinit var prefs: SharedPreferences

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
    private lateinit var tvSensorId: TextView
    private lateinit var tvVersion: TextView

    /** Stable key (VID:PID + serial) of whichever sensor is currently connected, or null if none. */
    private var currentDeviceKey: String? = null
    private var currentDeviceDisplayInfo: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("sensor_names", MODE_PRIVATE)

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
        tvSensorId = findViewById(R.id.tvSensorId)
        tvVersion = findViewById(R.id.tvVersion)
        tvVersion.text = "גרסה: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        findViewById<View>(R.id.btnMenu).setOnClickListener { showAppMenu(it) }

        usbHelper = UsbSerialHelper(
            context = this,
            slaveAddress = 1,
            baudRate = 9600,
            pollIntervalMs = 2000L,
            onReadingReceived = { reading -> runOnUiThread { updateUi(reading) } },
            onStatus = { message -> runOnUiThread { tvStatus.text = message } },
            onDeviceInfo = { deviceKey, displayInfo ->
                runOnUiThread {
                    currentDeviceKey = deviceKey.ifEmpty { null }
                    currentDeviceDisplayInfo = displayInfo
                    refreshSensorIdDisplay()
                }
            }
        )
        usbHelper.register()

        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            tvStatus.text = "מחפש חיישן..."
            usbHelper.findAndConnect()
        }

        // Try to auto-connect on launch, in case the sensor is already plugged in.
        usbHelper.findAndConnect()
    }

    private fun showAppMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.inflate(R.menu.main_menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_rename_sensor -> showRenameSensorDialog()
                R.id.menu_version_info -> showVersionInfoDialog()
            }
            true
        }
        popup.show()
    }

    private fun showRenameSensorDialog() {
        val key = currentDeviceKey
        if (key == null) {
            AlertDialog.Builder(this)
                .setTitle("שם החיישן")
                .setMessage("אין חיישן מחובר כרגע - חבר חיישן לפני שנותנים לו שם.")
                .setPositiveButton("סגור", null)
                .show()
            return
        }

        val input = EditText(this).apply {
            setText(prefs.getString(key, ""))
            hint = "לדוגמה: חממה 1, חלקה צפונית..."
        }

        AlertDialog.Builder(this)
            .setTitle("שם החיישן")
            .setMessage("השם הזה יישמר לפי מזהה החיישן, ויוצג אוטומטית בכל פעם שהחיישן הזה יתחבר.")
            .setView(input)
            .setPositiveButton("שמור") { _, _ ->
                val name = input.text.toString().trim()
                prefs.edit().putString(key, name).apply()
                refreshSensorIdDisplay()
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun showVersionInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("גרסה ותאריך עדכון")
            .setMessage("גרסה: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nתאריך בנייה: ${BuildConfig.BUILD_DATE}")
            .setPositiveButton("סגור", null)
            .show()
    }

    /** Shows the saved name (if any) for the currently connected sensor alongside its technical ID. */
    private fun refreshSensorIdDisplay() {
        val key = currentDeviceKey
        if (key == null) {
            tvSensorId.text = ""
            return
        }
        val savedName = prefs.getString(key, null)
        tvSensorId.text = if (!savedName.isNullOrBlank()) {
            "שם החיישן: $savedName  ·  $currentDeviceDisplayInfo"
        } else {
            currentDeviceDisplayInfo
        }
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

        val timeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        tvLastUpdate.text = "עדכון אחרון: ${timeFormat.format(Date(reading.timestampMillis))}"
    }

    override fun onDestroy() {
        usbHelper.unregister()
        super.onDestroy()
    }
}
