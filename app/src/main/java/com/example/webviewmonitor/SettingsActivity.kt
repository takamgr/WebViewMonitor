package com.example.webviewmonitor

import android.app.DatePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var rgInterval: RadioGroup
    private lateinit var rgNotifyMode: RadioGroup
    private lateinit var btnStartDate: Button
    private lateinit var btnEndDate: Button
    private lateinit var btnPickSound: Button
    private lateinit var etUrlRiku: EditText
    private lateinit var etUrlKei: EditText

    companion object {
        private const val REQUEST_RINGTONE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "設定"

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        rgInterval   = findViewById(R.id.rgInterval)
        rgNotifyMode = findViewById(R.id.rgNotifyMode)
        btnStartDate = findViewById(R.id.btnStartDate)
        btnEndDate   = findViewById(R.id.btnEndDate)
        btnPickSound = findViewById(R.id.btnPickSound)
        etUrlRiku    = findViewById(R.id.etUrlRiku)
        etUrlKei     = findViewById(R.id.etUrlKei)

        // 保存値を復元
        when (prefs.getLong("interval_ms", 5000L)) {
            5000L  -> rgInterval.check(R.id.rb5sec)
            30000L -> rgInterval.check(R.id.rb10sec)
            60000L -> rgInterval.check(R.id.rb15sec)
        }

        rgNotifyMode.check(
            if (prefs.getBoolean("is_repeat_mode", false)) R.id.rbNotifyRepeat
            else R.id.rbNotifyOnce
        )

        val startMs = prefs.getLong("start_date_ms", -1L)
        if (startMs != -1L) {
            val c = Calendar.getInstance().apply { timeInMillis = startMs }
            btnStartDate.text = "開始日: ${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.DAY_OF_MONTH)}"
        }

        val endMs = prefs.getLong("end_date_ms", -1L)
        if (endMs != -1L) {
            val c = Calendar.getInstance().apply { timeInMillis = endMs }
            btnEndDate.text = "終了日: ${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.DAY_OF_MONTH)}"
        }

        etUrlRiku.setText(prefs.getString("url_riku",
            "https://www.reserve.naltec.go.jp/web/ap-entry?slinky___page=forward:A1001_01"))
        etUrlKei.setText(prefs.getString("url_kei", "https://www.keikenkyo.or.jp/"))

        updateSoundButtonLabel()

        // イベントハンドラ
        rgInterval.setOnCheckedChangeListener { _, checkedId ->
            val ms = when (checkedId) {
                R.id.rb5sec  -> 5000L
                R.id.rb10sec -> 30000L
                R.id.rb15sec -> 60000L
                else         -> 5000L
            }
            prefs.edit().putLong("interval_ms", ms).apply()
        }

        rgNotifyMode.setOnCheckedChangeListener { _, checkedId ->
            prefs.edit().putBoolean("is_repeat_mode", checkedId == R.id.rbNotifyRepeat).apply()
        }

        btnStartDate.setOnClickListener { showDatePicker(isStart = true) }
        btnEndDate.setOnClickListener   { showDatePicker(isStart = false) }

        findViewById<Button>(R.id.btnApplyAndBack).setOnClickListener { saveAndFinish() }

        btnPickSound.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "通知音を選択")
                val saved = prefs.getString("ringtone_uri", null)
                if (saved != null) {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(saved))
                }
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_RINGTONE)
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        saveAndFinish()
    }

    override fun onSupportNavigateUp(): Boolean {
        saveAndFinish()
        return true
    }

    private fun saveAndFinish() {
        prefs.edit()
            .putString("url_riku", etUrlRiku.text.toString().trim())
            .putString("url_kei",  etUrlKei.text.toString().trim())
            .apply()
        finish()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_RINGTONE && resultCode == RESULT_OK) {
            val uri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            prefs.edit().putString("ringtone_uri", uri?.toString()).apply()
            updateSoundButtonLabel()
        }
    }

    private fun showDatePicker(isStart: Boolean) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            val selected = Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            prefs.edit().putLong(
                if (isStart) "start_date_ms" else "end_date_ms",
                selected.timeInMillis
            ).apply()
            if (isStart) {
                btnStartDate.text = "開始日: ${month + 1}/${day}"
            } else {
                btnEndDate.text = "終了日: ${month + 1}/${day}"
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateSoundButtonLabel() {
        val uriStr = prefs.getString("ringtone_uri", null)
        if (uriStr != null) {
            val ringtone = RingtoneManager.getRingtone(this, Uri.parse(uriStr))
            btnPickSound.text = "通知音: ${ringtone?.getTitle(this) ?: "選択済み"}"
        } else {
            btnPickSound.text = "通知音を選ぶ"
        }
    }
}
