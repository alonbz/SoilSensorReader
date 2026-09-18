package com.agsense.soilsensor7in1

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Stores historical sensor readings, keyed by the sensor's stable device
 * key (VID:PID [+ serial]), using a plain SQLite table (no Room/annotation
 * processing, to keep the build simple and dependency-light).
 *
 * One row per saved sample. Rows accumulate indefinitely across every field
 * visit to every sensor - there is currently no automatic cleanup/rotation
 * of old data, so storage will grow over time; worth revisiting if the
 * database gets large after months of use.
 */
class SensorHistoryDbHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "sensor_history.db"
        private const val DB_VERSION = 1
        const val TABLE_READINGS = "readings"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_READINGS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sensor_key TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                temperature REAL NOT NULL,
                moisture REAL NOT NULL,
                ec INTEGER NOT NULL,
                salinity INTEGER NOT NULL,
                nitrogen INTEGER NOT NULL,
                phosphorus INTEGER NOT NULL,
                potassium INTEGER NOT NULL,
                ph REAL NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_sensor_time ON $TABLE_READINGS(sensor_key, timestamp)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_READINGS")
        onCreate(db)
    }

    fun insertReading(sensorKey: String, reading: SoilSensorReading) {
        val values = ContentValues().apply {
            put("sensor_key", sensorKey)
            put("timestamp", reading.timestampMillis)
            put("temperature", reading.temperatureC)
            put("moisture", reading.moisturePercent)
            put("ec", reading.ecUsCm)
            put("salinity", reading.salinityMgL)
            put("nitrogen", reading.nitrogenMgKg)
            put("phosphorus", reading.phosphorusMgKg)
            put("potassium", reading.potassiumMgKg)
            put("ph", reading.ph)
        }
        writableDatabase.insert(TABLE_READINGS, null, values)
    }

    /** Returns saved readings for one sensor within [fromMillis, toMillis], oldest first. */
    fun queryReadings(sensorKey: String, fromMillis: Long, toMillis: Long): List<HistoryRow> {
        val results = mutableListOf<HistoryRow>()
        val cursor = readableDatabase.query(
            TABLE_READINGS,
            null,
            "sensor_key = ? AND timestamp BETWEEN ? AND ?",
            arrayOf(sensorKey, fromMillis.toString(), toMillis.toString()),
            null, null,
            "timestamp ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                results.add(
                    HistoryRow(
                        timestampMillis = it.getLong(it.getColumnIndexOrThrow("timestamp")),
                        temperatureC = it.getDouble(it.getColumnIndexOrThrow("temperature")),
                        moisturePercent = it.getDouble(it.getColumnIndexOrThrow("moisture")),
                        ecUsCm = it.getInt(it.getColumnIndexOrThrow("ec")),
                        salinityMgL = it.getInt(it.getColumnIndexOrThrow("salinity")),
                        nitrogenMgKg = it.getInt(it.getColumnIndexOrThrow("nitrogen")),
                        phosphorusMgKg = it.getInt(it.getColumnIndexOrThrow("phosphorus")),
                        potassiumMgKg = it.getInt(it.getColumnIndexOrThrow("potassium")),
                        ph = it.getDouble(it.getColumnIndexOrThrow("ph"))
                    )
                )
            }
        }
        return results
    }
}

/** One historical row read back from storage (same shape as SoilSensorReading, plus its saved timestamp). */
data class HistoryRow(
    val timestampMillis: Long,
    val temperatureC: Double,
    val moisturePercent: Double,
    val ecUsCm: Int,
    val salinityMgL: Int,
    val nitrogenMgKg: Int,
    val phosphorusMgKg: Int,
    val potassiumMgKg: Int,
    val ph: Double
)
