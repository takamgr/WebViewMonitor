package com.example.webviewmonitor

import android.app.AlertDialog // 変更
import android.app.DatePickerDialog // 変更
import android.content.SharedPreferences // 変更
import java.util.Calendar // 変更
import android.media.Ringtone // 変更
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View // 変更
import android.view.WindowManager
import android.util.Log // 変更
import android.webkit.WebSettings // 変更
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView // 変更
import android.widget.ArrayAdapter // 変更
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner // 変更
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() { // 変更

    // 変更: 状態管理フラグ
    private var isMonitoring = false
    private var intervalMs: Long = 5000

    private lateinit var prefs: SharedPreferences // 変更

    // 変更: 保存済みURL管理
    private val savedNames = mutableListOf<String>()
    private val savedUrls = mutableListOf<String>()
    private lateinit var spinnerAdapter: ArrayAdapter<String>

    private val handler = Handler(Looper.getMainLooper()) // 変更
    private lateinit var webView: WebView // 変更
    private lateinit var etUrl: EditText // 変更
    private lateinit var tvStatus: TextView // 変更
    private lateinit var btnStart: Button // 変更
    private lateinit var btnStop: Button // 変更
    private lateinit var rgInterval: RadioGroup // 変更
    private lateinit var spinnerUrls: Spinner // 変更
    private lateinit var indicatorView: View // 変更
    private var loadCount = 0 // 変更
    private var monitoringUrl: String? = null // 変更: 監視開始時のURLを記録
    private var startDate: Calendar? = null // 変更: 日付範囲フィルター開始日
    private var endDate: Calendar? = null   // 変更: 日付範囲フィルター終了日
    private lateinit var btnStartDate: Button // 変更
    private lateinit var btnEndDate: Button   // 変更
    private var isRepeatMode = false          // 変更: 繰り返し通知モード
    private var lastAlarmTime = 0L            // 変更: 連続鳴動防止タイムスタンプ
    private var activeRingtone: Ringtone? = null // 変更: 停止用リングトーン参照
    private lateinit var rgNotifyMode: RadioGroup // 変更

    // 変更: 定期reloadのRunnable
    private val reloadRunnable = object : Runnable {
        override fun run() {
            if (isMonitoring) {
                // 変更: reload()→JS経由reload。POSTページでもonPageFinished確実発火
                webView.evaluateJavascript("window.location.reload();", null)
                flashIndicator()
                handler.postDelayed(this, intervalMs)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 変更: SharedPreferences初期化・URL復元
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        // 変更: View取得
        etUrl = findViewById(R.id.etUrl)
        tvStatus = findViewById(R.id.tvStatus)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        rgInterval = findViewById(R.id.rgInterval)
        webView = findViewById(R.id.webView)
        spinnerUrls = findViewById(R.id.spinnerUrls) // 変更
        indicatorView = findViewById(R.id.indicatorView) // 変更
        btnStartDate = findViewById(R.id.btnStartDate) // 変更
        btnEndDate = findViewById(R.id.btnEndDate)     // 変更
        rgNotifyMode = findViewById(R.id.rgNotifyMode) // 変更

        // 変更: 前回のURLを入力欄に復元
        etUrl.setText(prefs.getString("last_url", ""))

        // 変更: 保存済みURLをロードしてSpinnerを初期化
        loadSavedUrls()
        spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            mutableListOf<String>()
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerUrls.adapter = spinnerAdapter
        updateSpinner()

        // 変更: Spinner選択でURL入力欄に反映
        spinnerUrls.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                if (position > 0) {
                    etUrl.setText(savedUrls[position - 1])
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // 変更: WebView設定
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE // 変更: キャッシュ無効化でonPageFinished確実発火

        webView.webViewClient = object : WebViewClient() {

            // 変更: 全URLをWebView内で処理しリダイレクト後もWebViewClientを維持
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                Log.d("DEBUG", "shouldOverrideUrlLoading: ${request?.url}") // 変更
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("DEBUG", "onPageStarted: $url") // 変更
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("DEBUG", "onPageFinished fired: $url") // 変更
                if (!isMonitoring) return
                Log.d("DEBUG", "onPageFinished isMonitoring=$isMonitoring") // 変更

                // 変更: 読込完了カウント・ステータス更新
                loadCount++
                tvStatus.text = "監視中... 完了: ${loadCount}回"

                // 変更: outerHTMLではなくJSでdata-status属性を直接取得（JSONエンコード問題を回避）
                val currentUrl = url
                view?.evaluateJavascript("""
                    (function() {
                        var bodyText = document.body ? document.body.innerText.substring(0, 1000) : '';
                        var els = document.querySelectorAll('[data-status]');
                        var arr = ['BODY:' + bodyText.replace(/\n/g, ' ')];
                        for (var i = 0; i < els.length; i++) {
                            arr.push('STATUS:' + els[i].getAttribute('data-status'));
                        }
                        return arr.join('\n');
                    })()
                """.trimIndent()) { result ->
                    if (result == null) return@evaluateJavascript
                    // 変更: JSON外側クォートを除去しタブ・改行をアンエスケープ
                    val decoded = result
                        .removeSurrounding("\"")
                        .replace("\\n", "\n")
                        .replace("\\t", "\t")
                    val lines = decoded.split("\n")
                    val bodyText = lines.firstOrNull { it.startsWith("BODY:") }
                        ?.removePrefix("BODY:") ?: ""
                    val statuses = lines.filter { it.startsWith("STATUS:") }
                        .map { it.removePrefix("STATUS:") }
                    Log.d("DEBUG", "bodyText: ${bodyText.take(200)}") // 変更
                    Log.d("DEBUG", "statuses count=${statuses.size}") // 変更
                    statuses.forEach { Log.d("DEBUG", "STATUS: $it") } // 変更
                    checkHtml(bodyText, statuses, currentUrl)
                }
            }
        }

        // 変更: 通知モード選択
        rgNotifyMode.setOnCheckedChangeListener { _, checkedId ->
            isRepeatMode = checkedId == R.id.rbNotifyRepeat
        }

        // 変更: 日付フィルターボタン
        btnStartDate.setOnClickListener { showDatePicker(isStart = true) }
        btnEndDate.setOnClickListener { showDatePicker(isStart = false) }

        // 変更: 保存ボタン
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                showSaveDialog(url)
            }
        }

        // 変更: 読込ボタン
        findViewById<Button>(R.id.btnLoad).setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                prefs.edit().putString("last_url", url).apply() // 変更: URLを保存
                webView.loadUrl(url)
            }
        }

        // 変更: 監視開始ボタン
        btnStart.setOnClickListener {
            if (!isMonitoring) {
                startMonitoring()
            }
        }

        // 変更: 監視停止ボタン
        btnStop.setOnClickListener {
            if (isMonitoring) {
                stopMonitoring("監視停止")
            }
        }

        // 変更: 更新間隔選択
        rgInterval.setOnCheckedChangeListener { _, checkedId ->
            intervalMs = when (checkedId) {
                R.id.rb5sec -> 5000L
                R.id.rb10sec -> 30000L // 変更: 30秒
                R.id.rb15sec -> 60000L // 変更: 60秒
                else -> 5000L
            }
        }
    }

    // 変更: 監視開始処理
    private fun startMonitoring() {
        isMonitoring = true
        loadCount = 0 // 変更
        lastAlarmTime = 0L // 変更: 連続鳴動タイマーリセット
        monitoringUrl = webView.url // 変更: 監視開始時のURLを記録
        Log.d("DEBUG", "startMonitoring url=$monitoringUrl") // 変更
        tvStatus.text = "監視中... 完了: 0回"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handler.postDelayed(reloadRunnable, intervalMs)
    }

    // 変更: 監視停止処理
    private fun stopMonitoring(status: String) {
        isMonitoring = false
        tvStatus.text = status
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handler.removeCallbacks(reloadRunnable)
        activeRingtone?.stop() // 変更: 停止時にアラームも止める
        activeRingtone = null  // 変更
    }

    // 変更: 検査処理（bodyText=ログイン判定用, statuses=data-status値リスト）
    private fun checkHtml(bodyText: String, statuses: List<String>, currentUrl: String?) {
        // 変更: URLが変わっている場合のみログイン切れ検出
        val urlChanged = currentUrl != null && currentUrl != monitoringUrl
        if (urlChanged) {
            val loginKeywords = listOf("ログイン", "再ログイン", "セッション")
            if (loginKeywords.any { bodyText.contains(it) }) {
                Log.d("DEBUG", "login detected: url=$currentUrl") // 変更
                stopMonitoring("ログイン切れの可能性")
                return
            }
        }

        // 変更: data-status値をタブ分割して日付・空きスロットを確認
        for (statusValue in statuses) {
            val parts = statusValue.split("\t")
            if (parts.size < 2) continue

            val slots = parts.drop(1)
            val hasVacancy = slots.any { it.contains("○") || it.contains("△") }
            if (!hasVacancy) continue

            val datePart = parts[0].trim()
            val dateMatch = Regex("(\\d+)月\\s*(\\d+)日").find(datePart) ?: continue // 変更: 1桁日付前のスペース対応
            val month = dateMatch.groupValues[1].toIntOrNull() ?: continue
            val day = dateMatch.groupValues[2].toIntOrNull() ?: continue

            Log.d("DEBUG", "空きあり候補: ${month}月${day}日 slots=$slots") // 変更

            if (isDateInRange(month, day)) {
                Log.d("DEBUG", "空き検出: ${month}月${day}日") // 変更
                if (!isRepeatMode) {
                    // 変更: 1回通知で停止モード
                    stopMonitoring("空き検出！")
                    playAlarm()
                } else {
                    // 変更: 繰り返しモード（10秒以上経過していれば再発火）
                    val now = System.currentTimeMillis()
                    if (now - lastAlarmTime >= 10000L) {
                        lastAlarmTime = now
                        tvStatus.text = "空き検出中！ 監視継続..."
                        playAlarm()
                    }
                }
                return
            }
        }
    }

    // 変更: DatePickerDialogを表示して開始日/終了日を設定
    private fun showDatePicker(isStart: Boolean) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            val selected = Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (isStart) {
                startDate = selected
                btnStartDate.text = "開始日: ${month + 1}/${day}" // 変更: 月/日のみ表示
            } else {
                endDate = selected
                btnEndDate.text = "終了日: ${month + 1}/${day}" // 変更: 月/日のみ表示
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    // 変更: 月・日が日付範囲内か判定（年は当年/翌年を自動判定）
    private fun isDateInRange(month: Int, day: Int): Boolean {
        val now = Calendar.getInstance()
        val year = if (month < now.get(Calendar.MONTH) + 1) now.get(Calendar.YEAR) + 1
                   else now.get(Calendar.YEAR)
        val target = Calendar.getInstance().apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val afterStart = startDate?.let { !target.before(it) } ?: true
        val beforeEnd = endDate?.let { !target.after(it) } ?: true
        return afterStart && beforeEnd
    }

    // 変更: 保存済みURLをSharedPreferencesからロード
    private fun loadSavedUrls() {
        savedNames.clear()
        savedUrls.clear()
        val count = prefs.getInt("saved_count", 0)
        for (i in 0 until count) {
            val name = prefs.getString("saved_name_$i", null) ?: continue
            val url = prefs.getString("saved_url_$i", null) ?: continue
            savedNames.add(name)
            savedUrls.add(url)
        }
    }

    // 変更: 保存済みURLをSharedPreferencesに書き込み
    private fun persistSavedUrls() {
        val editor = prefs.edit()
        editor.putInt("saved_count", savedNames.size)
        for (i in savedNames.indices) {
            editor.putString("saved_name_$i", savedNames[i])
            editor.putString("saved_url_$i", savedUrls[i])
        }
        editor.apply()
    }

    // 変更: SpinnerのAdapterを更新
    private fun updateSpinner() {
        spinnerAdapter.clear()
        spinnerAdapter.add("保存済みURLを選択")
        spinnerAdapter.addAll(savedNames)
        spinnerAdapter.notifyDataSetChanged()
        spinnerUrls.setSelection(0)
    }

    // 変更: 名前入力ダイアログを表示して保存
    private fun showSaveDialog(url: String) {
        val input = EditText(this).apply { hint = "名前を入力" }
        AlertDialog.Builder(this)
            .setTitle("URLを保存")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (savedNames.size >= 10) {
                        savedNames.removeAt(0)
                        savedUrls.removeAt(0)
                    }
                    savedNames.add(name)
                    savedUrls.add(url)
                    persistSavedUrls()
                    updateSpinner()
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    // 変更: リロード命令時にインジケーターを300ms点灯
    private fun flashIndicator() {
        indicatorView.visibility = View.VISIBLE
        handler.postDelayed({ indicatorView.visibility = View.INVISIBLE }, 300)
    }

    // 変更: アラーム再生（参照を保持して停止可能にする）
    private fun playAlarm() {
        activeRingtone?.stop()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        activeRingtone = RingtoneManager.getRingtone(applicationContext, uri)
        activeRingtone?.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(reloadRunnable) // 変更
    }
}
