package com.agsense.soilsensor

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val KEY_SAVE_INTERVAL_SUFFIX = ":interval_minutes"
        private const val DEFAULT_SAVE_INTERVAL_MINUTES = 5
    }

    private lateinit var usbHelper: UsbSerialHelper
    private lateinit var prefs: SharedPreferences
    private lateinit var dbHelper: SensorHistoryDbHelper

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

    /** Stable key (VID:PID + serial) of whichever sensor is currently connected, or null if none. */
    private var currentDeviceKey: String? = null
    private var currentDeviceDisplayInfo: String = ""

    /** Per-sensor throttling: last time (millis) we actually persisted a sample for that sensor. */
    private val lastSavedAt = mutableMapOf<String, Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("sensor_names", MODE_PRIVATE)
        dbHelper = SensorHistoryDbHelper(this)

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

        findViewById<View>(R.id.btnMenu).setOnClickListener { showAppMenu(it) }

        usbHelper = UsbSerialHelper(
            context = this,
            slaveAddress = 1,
            baudRate = 9600,
            pollIntervalMs = 2000L,
            onReadingReceived = { reading ->
                runOnUiThread {
                    updateUi(reading)
                    maybeSaveReading(reading)
                }
            },
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

    /** Persists a reading for the currently connected sensor, throttled to that sensor's own configured save interval. */
    private fun maybeSaveReading(reading: SoilSensorReading) {
        val key = currentDeviceKey ?: return
        val intervalMs = getSaveIntervalMinutes(key) * 60_000L
        val last = lastSavedAt[key] ?: 0L
        if (reading.timestampMillis - last >= intervalMs) {
            dbHelper.insertReading(key, reading)
            lastSavedAt[key] = reading.timestampMillis
        }
    }

    private fun getSaveIntervalMinutes(key: String): Int =
        prefs.getInt(key + KEY_SAVE_INTERVAL_SUFFIX, DEFAULT_SAVE_INTERVAL_MINUTES)

    private fun showAppMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.inflate(R.menu.main_menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_sensor_settings -> showSensorSettingsDialog()
                R.id.menu_history -> openHistory()
                R.id.menu_version_info -> showVersionInfoDialog()
            }
            true
        }
        popup.show()
    }

    /** Per-sensor dialog: name, plus this specific sensor's own save interval. */
    private fun showSensorSettingsDialog() {
        val key = currentDeviceKey
        if (key == null) {
            AlertDialog.Builder(this)
                .setTitle("הגדרות חיישן")
                .setMessage("אין חיישן מחובר כרגע - חבר חיישן לפני שמגדירים שם או מרווח שמירה (שניהם פרטניים לכל חיישן).")
                .setPositiveButton("חזרה למסך הראשי", null)
                .show()
            return
        }

        val nameInput = EditText(this).apply {
            hint = "לדוגמה: חממה 1, חלקה צפונית..."
            setText(prefs.getString(key, ""))
        }
        val intervalInput = EditText(this).apply {
            hint = "מרווח שמירה (דקות)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(getSaveIntervalMinutes(key).toString())
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }

        // Full technical details are shown here (where the name is set), not on the main screen once a name exists.
        container.addView(TextView(this).apply {
            text = "פרטי החיישן: $currentDeviceDisplayInfo"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#888888"))
            setPadding(0, 0, 0, dp(12))
        })

        container.addView(TextView(this).apply { text = "שם החיישן:" })
        container.addView(nameInput)

        container.addView(TextView(this).apply {
            text = "מרווח שמירת היסטוריה לחיישן הזה (בדקות):"
            setPadding(0, dp(12), 0, 0)
        })
        container.addView(intervalInput)

        AlertDialog.Builder(this)
            .setTitle("הגדרות חיישן")
            .setView(container)
            .setPositiveButton("שמור") { _, _ ->
                prefs.edit().putString(key, nameInput.text.toString().trim()).apply()
                refreshSensorIdDisplay()

                val minutes = intervalInput.text.toString().toIntOrNull()
                if (minutes != null && minutes > 0) {
                    prefs.edit().putInt(key + KEY_SAVE_INTERVAL_SUFFIX, minutes).apply()
                } else {
                    Toast.makeText(this, "מרווח השמירה לא תקין - נשאר ללא שינוי", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("חזרה למסך הראשי", null)
            .show()
    }

    private fun openHistory() {
        val key = currentDeviceKey
        if (key == null) {
            Toast.makeText(this, "חבר חיישן לפני שפותחים היסטוריה", Toast.LENGTH_SHORT).show()
            return
        }
        val savedName = prefs.getString(key, null)
        val label = if (!savedName.isNullOrBlank()) {
            "$savedName  ·  $currentDeviceDisplayInfo"
        } else {
            currentDeviceDisplayInfo
        }
        startActivity(
            Intent(this, HistoryActivity::class.java)
                .putExtra(HistoryActivity.EXTRA_SENSOR_KEY, key)
                .putExtra(HistoryActivity.EXTRA_SENSOR_LABEL, label)
                .putExtra(HistoryActivity.EXTRA_SENSOR_NAME, savedName ?: "")
        )
    }

    private fun showVersionInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("גרסה ותאריך עדכון")
            .setMessage("גרסה: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nתאריך בנייה: ${BuildConfig.BUILD_DATE}")
            .setPositiveButton("חזרה למסך הראשי", null)
            .show()
    }

    /**
     * Once a name is saved, the main screen shows only the name; the technical details
     * (VID/PID/ID) are shown only if no name was saved yet, and always in the sensor settings dialog.
     */
    private fun refreshSensorIdDisplay() {
        val key = currentDeviceKey
        if (key == null) {
            tvSensorId.text = ""
            return
        }
        val savedName = prefs.getString(key, null)
        tvSensorId.text = if (!savedName.isNullOrBlank()) {
            "שם החיישן: $savedName"
        } else {
            currentDeviceDisplayInfo
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
