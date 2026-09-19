package com.agsense.soilsensor

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.Gravity
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SENSOR_KEY = "sensor_key"
        const val EXTRA_SENSOR_LABEL = "sensor_label"
        const val EXTRA_SENSOR_NAME = "sensor_name"
    }

    private lateinit var dbHelper: SensorHistoryDbHelper
    private lateinit var sensorKey: String
    private var sensorName: String = ""
    private var sensorLabelText: String = ""
    private var lastRows: List<HistoryRow> = emptyList()

    private lateinit var tvHistorySensorLabel: TextView
    private lateinit var btnFromDate: Button
    private lateinit var btnToDate: Button
    private lateinit var tvHistoryStatus: TextView
    private lateinit var tableHistory: TableLayout
    private lateinit var chartHistory: LineChart

    private var fromCalendar: Calendar = Calendar.getInstance()
    private var toCalendar: Calendar = Calendar.getInstance()

    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        applySystemBarInsets()

        dbHelper = SensorHistoryDbHelper(this)
        sensorKey = intent.getStringExtra(EXTRA_SENSOR_KEY) ?: ""
        val sensorLabel = intent.getStringExtra(EXTRA_SENSOR_LABEL) ?: ""
        sensorName = intent.getStringExtra(EXTRA_SENSOR_NAME) ?: ""
        sensorLabelText = sensorLabel

        tvHistorySensorLabel = findViewById(R.id.tvHistorySensorLabel)
        tvHistorySensorLabel.text = sensorLabel

        btnFromDate = findViewById(R.id.btnFromDate)
        btnToDate = findViewById(R.id.btnToDate)
        tvHistoryStatus = findViewById(R.id.tvHistoryStatus)
        tableHistory = findViewById(R.id.tableHistory)
        chartHistory = findViewById(R.id.chartHistory)

        // Default range: last 7 days, up to now
        toCalendar = Calendar.getInstance()
        fromCalendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
        updateDateButtons()

        btnFromDate.setOnClickListener { pickDateTime(fromCalendar) { updateDateButtons() } }
        btnToDate.setOnClickListener { pickDateTime(toCalendar) { updateDateButtons() } }

        findViewById<Button>(R.id.btnRefreshHistory).setOnClickListener { loadHistory() }
        findViewById<Button>(R.id.btnBackHome).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnExportExcel).setOnClickListener { exportExcel(share = false) }
        findViewById<Button>(R.id.btnShareExcel).setOnClickListener { exportExcel(share = true) }

        setupChartAppearance()
        loadHistory()
    }

    private fun updateDateButtons() {
        btnFromDate.text = dateTimeFormat.format(fromCalendar.time)
        btnToDate.text = dateTimeFormat.format(toCalendar.time)
    }

    private fun pickDateTime(calendar: Calendar, onDone: () -> Unit) {
        DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)

                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        onDone()
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadHistory() {
        if (sensorKey.isEmpty()) {
            tvHistoryStatus.text = "לא ידוע לאיזה חיישן שייכת ההיסטוריה הזו"
            return
        }
        val fromMillis = fromCalendar.timeInMillis
        val toMillis = toCalendar.timeInMillis
        if (fromMillis > toMillis) {
            Toast.makeText(this, "תאריך ה\"מ\" מאוחר מתאריך ה\"עד\"", Toast.LENGTH_SHORT).show()
            return
        }

        val rows = dbHelper.queryReadings(sensorKey, fromMillis, toMillis)
        lastRows = rows
        tvHistoryStatus.text = if (rows.isEmpty()) {
            "אין נתונים שמורים בטווח הנבחר"
        } else {
            "${rows.size} רשומות בטווח הנבחר"
        }

        populateTable(rows)
        populateChart(rows, fromMillis)
    }

    /** Exports the currently selected history range as .xlsx: saved to Downloads, or shared via the share sheet. */
    private fun exportExcel(share: Boolean) {
        if (lastRows.isEmpty()) {
            Toast.makeText(this, "אין נתונים לייצוא בטווח הנבחר", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(java.util.Date())
            val safeName = sensorName.replace(Regex("[\\/:*?\"<>|\\s]+"), "_").trim('_')
            val fileName = "AGSense_" + (if (safeName.isNotEmpty()) "${safeName}_" else "") + "history_$stamp.xlsx"
            val bytes = XlsxExporter.build(lastRows, if (sensorName.isNotEmpty()) sensorName else sensorLabelText)

            if (share) {
                val dir = java.io.File(cacheDir, "exports").apply { mkdirs() }
                val file = java.io.File(dir, fileName).apply { writeBytes(bytes) }
                val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(android.content.Intent.createChooser(send, "ייצוא היסטוריה"))
            } else {
                val where = saveToDownloads(fileName, bytes)
                Toast.makeText(this, "הקובץ נשמר: $where", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "שגיאה בייצוא: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveToDownloads(fileName: String, bytes: ByteArray): String {
        val mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw java.io.IOException("לא ניתן ליצור קובץ בהורדות")
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            return "הורדות/$fileName"
        }
        // Android 8-9: no storage permission requested, so use the app's own external Downloads folder.
        val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        val file = java.io.File(dir, fileName).apply { writeBytes(bytes) }
        return file.absolutePath
    }

    private fun populateTable(rows: List<HistoryRow>) {
        // Remove all rows except the header (index 0)
        while (tableHistory.childCount > 1) {
            tableHistory.removeViewAt(1)
        }

        val rowFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        for ((index, row) in rows.withIndex()) {
            val tableRow = TableRow(this).apply {
                if (index % 2 == 1) setBackgroundColor(Color.parseColor("#F5F5F5"))
            }

            fun cell(text: String, minWidthDp: Int): TextView = TextView(this).apply {
                this.text = text
                textSize = 12f
                setPadding(dp(6), dp(6), dp(6), dp(6))
                minWidth = dp(minWidthDp)
                gravity = Gravity.CENTER
            }

            tableRow.addView(cell(rowFormat.format(row.timestampMillis), 130))
            tableRow.addView(cell("%.1f".format(row.temperatureC), 60))
            tableRow.addView(cell("%.1f".format(row.moisturePercent), 60))
            tableRow.addView(cell(row.ecUsCm.toString(), 60))
            tableRow.addView(cell(row.salinityMgL.toString(), 60))
            tableRow.addView(cell(row.nitrogenMgKg.toString(), 50))
            tableRow.addView(cell(row.phosphorusMgKg.toString(), 50))
            tableRow.addView(cell(row.potassiumMgKg.toString(), 50))
            tableRow.addView(cell("%.2f".format(row.ph), 60))

            tableHistory.addView(tableRow)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun setupChartAppearance() {
        chartHistory.description.isEnabled = false
        chartHistory.legend.apply {
            isEnabled = true
            verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
            orientation = Legend.LegendOrientation.HORIZONTAL
            textSize = 9f
        }
        chartHistory.axisRight.isEnabled = false
        chartHistory.xAxis.granularity = 1f
    }

    private fun populateChart(rows: List<HistoryRow>, fromMillis: Long) {
        if (rows.isEmpty()) {
            chartHistory.clear()
            chartHistory.invalidate()
            return
        }

        fun series(label: String, colorHex: String, extractor: (HistoryRow) -> Float): LineDataSet {
            val entries = rows.map { row ->
                val minutesSinceStart = (row.timestampMillis - fromMillis) / 60000f
                com.github.mikephil.charting.data.Entry(minutesSinceStart, extractor(row))
            }
            return LineDataSet(entries, label).apply {
                color = Color.parseColor(colorHex)
                setCircleColor(Color.parseColor(colorHex))
                circleRadius = 1.5f
                lineWidth = 1.5f
                setDrawValues(false)
                setDrawCircles(false)
            }
        }

        val dataSets = listOf(
            series("Temp", "#F5A328") { it.temperatureC.toFloat() },
            series("Moisture", "#50A0F0") { it.moisturePercent.toFloat() },
            series("EC", "#AA8CE1") { it.ecUsCm.toFloat() },
            series("Salinity", "#78909C") { it.salinityMgL.toFloat() },
            series("N", "#8BC34A") { it.nitrogenMgKg.toFloat() },
            series("P", "#29BED2") { it.phosphorusMgKg.toFloat() },
            series("K", "#5B7CE1") { it.potassiumMgKg.toFloat() },
            series("pH", "#FDC618") { it.ph.toFloat() }
        )

        chartHistory.xAxis.valueFormatter = object : ValueFormatter() {
            private val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            override fun getFormattedValue(value: Float): String {
                val millis = fromMillis + (value * 60000L).toLong()
                return fmt.format(millis)
            }
        }

        chartHistory.data = LineData(dataSets)
        chartHistory.invalidate()
    }
    /** targetSdk 35+ forces edge-to-edge: pad the content so it doesn't sit under the status/navigation bars. */
    private fun applySystemBarInsets() {
        val content = findViewById<View>(android.R.id.content)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }
}
