package com.agsense.soilsensor7in1

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CrashActivity : AppCompatActivity() {
    companion object { const val EXTRA_TRACE = "trace" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra(EXTRA_TRACE) ?: "(no details)"
        val pad = (12 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        root.addView(TextView(this).apply { text = "האפליקציה קרסה - פרטי השגיאה:"; textSize = 18f })
        root.addView(Button(this).apply {
            text = "העתק / שתף שגיאה"
            setOnClickListener {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("crash", trace))
                Toast.makeText(this@CrashActivity, "הועתק", Toast.LENGTH_SHORT).show()
                startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, trace), "שתף"))
            }
        })
        root.addView(ScrollView(this).apply {
            addView(TextView(this@CrashActivity).apply { text = trace; textSize = 11f; setTextIsSelectable(true) })
        })
        setContentView(root)
    }
}
