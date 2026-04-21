package com.example.webviewmonitor

import android.Manifest
import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.content.SharedPreferences
import java.util.Calendar
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        const val ACTION_STOP_FROM_NOTIFICATION = "com.example.webviewmonitor.ACTION_STOP_FROM_NOTIFICATION"
        private const val REQUEST_SETTINGS = 2001
    }

    private var isMonitoring = false
    private var intervalMs: Long = 5000

    private lateinit var prefs: SharedPreferences

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var webView: WebView
    private lateinit var tvStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var indicatorView: View
    private var loadCount = 0
    private var monitoringUrl: String? = null
    private var startDate: Calendar? = null
    private var endDate: Calendar? = null
    private var isRepeatMode = false
    private var lastAlarmTime = 0L
    private var zeroDetectCount = 0
    private var selectedFilter: Map<String, List<Int>> = emptyMap()
    private var isCalendarLoaded = false
    private var calendarDates: ArrayList<String> = arrayListOf()
    private var activeRingtone: Ringtone? = null
    private var selectedRingtoneUri: Uri? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenReceiver: ScreenReceiver? = null
    private lateinit var radarView: RadarView // 変更

    private val reloadRunnable = object : Runnable {
        override fun run() {
            if (isMonitoring) {
                webView.evaluateJavascript("window.location.reload();", null)
                flashIndicator()
                // 変更
                if (radarView.visibility == View.VISIBLE) {
                    radarView.updateStatus("監視中...", "${loadCount}回")
                    radarView.triggerReloadDot()
                }
                handler.postDelayed(this, intervalMs)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        tvStatus      = findViewById(R.id.tvStatus)
        btnStart      = findViewById(R.id.btnStart)
        btnStop       = findViewById(R.id.btnStop)
        webView       = findViewById(R.id.webView)
        indicatorView = findViewById(R.id.indicatorView)
        radarView = findViewById(R.id.radarView) // 変更

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                Log.d("DEBUG", "shouldOverrideUrlLoading: ${request?.url}")
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("DEBUG", "onPageStarted: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("DEBUG", "onPageFinished fired: $url")
                if (!isMonitoring) return
                Log.d("DEBUG", "onPageFinished isMonitoring=$isMonitoring")

                loadCount++
                tvStatus.text = "監視中... 完了: ${loadCount}回"

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
                    val decoded = result
                        .removeSurrounding("\"")
                        .replace("\\n", "\n")
                        .replace("\\t", "\t")
                    val lines = decoded.split("\n")
                    val bodyText = lines.firstOrNull { it.startsWith("BODY:") }
                        ?.removePrefix("BODY:") ?: ""
                    val statuses = lines.filter { it.startsWith("STATUS:") }
                        .map { it.removePrefix("STATUS:") }
                    Log.d("DEBUG", "bodyText: ${bodyText.take(200)}")
                    Log.d("DEBUG", "statuses count=${statuses.size}")
                    statuses.forEach { Log.d("DEBUG", "STATUS: $it") }
                    // 変更
                    val isKei = url?.contains("kei-reserve.jp") == true
                    if (isKei && statuses.isEmpty()) {
                        view?.evaluateJavascript("""
                            (function() {
                                var arr = [];
                                var rows = document.querySelectorAll('tr');
                                for (var r = 0; r < rows.length; r++) {
                                    var cells = rows[r].querySelectorAll('td');
                                    if (cells.length < 5) continue;
                                    var dateText = cells[0].innerText.trim();
                                    if (!dateText.match(/\d+月/)) continue;
                                    var slots = [];
                                    for (var c = 1; c <= 4; c++) {
                                        slots.push(cells[c].innerText.trim());
                                    }
                                    arr.push('KEI:' + dateText + '\t' + slots.join('\t'));
                                }
                                return arr.join('\n');
                            })()
                        """.trimIndent()) { keiResult ->
                            if (keiResult == null) return@evaluateJavascript
                            val keiDecoded = keiResult
                                .removeSurrounding("\"")
                                .replace("\\n", "\n")
                                .replace("\\t", "\t") // 変更
                            Log.d("DEBUG", "keiRaw: $keiDecoded") // 変更
                            val keiLines = keiDecoded.split("\n")
                                .filter { it.startsWith("KEI:") }
                            Log.d("DEBUG", "keiLines count=${keiLines.size}") // 変更
                            if (keiLines.isNotEmpty()) {
                                zeroDetectCount = 0
                                checkKeiHtml(keiLines, url)
                            } else {
                                zeroDetectCount++
                                val threshold = if (intervalMs == 5000L) 3 else 1
                                if (zeroDetectCount >= threshold) {
                                    zeroDetectCount = 0
                                    if (radarView.visibility == View.VISIBLE)
                                        radarView.setState(RadarView.RadarState.SESSION_EXPIRED)
                                    notifySessionExpired()
                                }
                            }
                        }
                        return@evaluateJavascript
                    }
                    if (statuses.isEmpty()) {
                        zeroDetectCount++
                        val threshold = if (intervalMs == 5000L) 3 else 1
                        if (zeroDetectCount >= threshold) {
                            zeroDetectCount = 0
                            if (radarView.visibility == View.VISIBLE)
                                radarView.setState(RadarView.RadarState.SESSION_EXPIRED)
                            notifySessionExpired()
                        }
                    } else {
                        zeroDetectCount = 0
                    }
                    checkHtml(bodyText, statuses, currentUrl)
                }
            }
        }

        // サイト選択ボタン
        findViewById<Button>(R.id.btnRiku).setOnClickListener {
            val url = prefs.getString("url_riku",
                "https://www.reserve.naltec.go.jp/web/ap-entry?slinky___page=forward:A1001_01")!!
            webView.loadUrl(url)
        }

        findViewById<Button>(R.id.btnKei).setOnClickListener {
            val url = prefs.getString("url_kei", "https://www.kei-reserve.jp/kei_reserve/pc/wb01_login/wb01-login-input")!! // 変更
            webView.loadUrl(url)
        }

        findViewById<android.widget.ImageButton>(R.id.btnSettings).setOnClickListener {
            if (isFreeUser(this)) {
                val intent = Intent(this, SettingsActivity::class.java).apply {
                    putExtra("is_calendar_loaded", false)
                }
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQUEST_SETTINGS)
            } else {
                val isKei = webView.url?.contains("kei-reserve.jp") == true
                val js = if (isKei) """
                    (function() {
                        var arr = [];
                        var rows = document.querySelectorAll('tr');
                        for (var r = 0; r < rows.length; r++) {
                            var cells = rows[r].querySelectorAll('td');
                            if (cells.length < 5) continue;
                            var dateText = cells[0].innerText.trim();
                            if (dateText.match(/\d+月/)) arr.push(dateText);
                        }
                        return arr.join('\n');
                    })()
                """.trimIndent() else """
                    (function() {
                        var els = document.querySelectorAll('[data-status]');
                        var arr = [];
                        for (var i = 0; i < els.length; i++) {
                            var parts = els[i].getAttribute('data-status').split('\t');
                            var dateText = parts[0].trim();
                            if (dateText.match(/\d+月/)) arr.push(dateText);
                        }
                        return arr.join('\n');
                    })()
                """.trimIndent()
                webView.evaluateJavascript(js) { result ->
                    val dates = (result ?: "")
                        .removeSurrounding("\"")
                        .replace("\\n", "\n")
                        .split("\n")
                        .filter { it.isNotBlank() && it.contains("月") }
                    val loaded = dates.isNotEmpty()
                    if (loaded) {
                        isCalendarLoaded = true
                        calendarDates = ArrayList(dates)
                    }
                    val intent = Intent(this, SettingsActivity::class.java).apply {
                        putExtra("is_calendar_loaded", loaded)
                        putStringArrayListExtra("calendar_dates", ArrayList(dates))
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQUEST_SETTINGS)
                }
            }
        }

        btnStart.setOnClickListener {
            if (!isMonitoring) {
                if (isFreeUser(this)) checkCalendarAndStart()
                else if (selectedFilter.isNotEmpty()) startMonitoring()
                else checkCalendarAndStart()
            }
        }
        btnStop.setOnClickListener  { if (isMonitoring)  stopMonitoring("監視停止") }
        updateStartButton()
        // 変更
        requestNotificationPermission()
    }

    private fun checkCalendarAndStart() {
        webView.evaluateJavascript("""
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
            val decoded = result
                .removeSurrounding("\"")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
            val lines = decoded.split("\n")
            val bodyText = lines.firstOrNull { it.startsWith("BODY:") }
                ?.removePrefix("BODY:") ?: ""
            val statuses = lines.filter { it.startsWith("STATUS:") }
                .map { it.removePrefix("STATUS:") }

            val isKei = webView.url?.contains("kei-reserve.jp") == true
            if (isKei) {
                webView.evaluateJavascript("""
                    (function() {
                        var arr = [];
                        var rows = document.querySelectorAll('tr');
                        for (var r = 0; r < rows.length; r++) {
                            var cells = rows[r].querySelectorAll('td');
                            if (cells.length < 5) continue;
                            var dateText = cells[0].innerText.trim();
                            if (!dateText.match(/\d+月/)) continue;
                            var slots = [];
                            for (var c = 1; c <= 4; c++) { slots.push(cells[c].innerText.trim()); }
                            arr.push('KEI:' + dateText + '\t' + slots.join('\t'));
                        }
                        return arr.join('\n');
                    })()
                """.trimIndent()) { keiResult ->
                    if (keiResult == null) return@evaluateJavascript
                    val keiDecoded = keiResult.removeSurrounding("\"").replace("\\n", "\n").replace("\\t", "\t")
                    val keiLines = keiDecoded.split("\n").filter { it.startsWith("KEI:") }
                    if (keiLines.isEmpty()) {
                        AlertDialog.Builder(this)
                            .setMessage("予約カレンダーが見つかりません。正しいページで監視を開始してください。")
                            .setPositiveButton("OK", null)
                            .show()
                        return@evaluateJavascript
                    }
                    val dates = keiLines.map { it.removePrefix("KEI:").split("\t")[0].trim() }
                    isCalendarLoaded = true
                    calendarDates = ArrayList(dates)
                    if (!isFreeUser(this)) {
                        VacancyFilterDialog.show(this, dates) { selFilter ->
                            selectedFilter = selFilter
                            startMonitoring()
                        }
                    } else {
                        startMonitoring()
                    }
                }
            } else if (statuses.isEmpty()) {
                AlertDialog.Builder(this)
                    .setMessage("予約カレンダーが見つかりません。正しいページで監視を開始してください。")
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                val dates = statuses.map { it.split("\t")[0].trim() }.filter { it.contains("月") }
                isCalendarLoaded = true
                calendarDates = ArrayList(dates)
                if (!isFreeUser(this)) {
                    VacancyFilterDialog.show(this, dates) { selFilter ->
                        selectedFilter = selFilter
                        startMonitoring()
                        checkHtml(bodyText, statuses, webView.url)
                    }
                } else {
                    startMonitoring()
                    checkHtml(bodyText, statuses, webView.url)
                }
            }
        }
    }

    private fun startMonitoring() {
        intervalMs   = prefs.getLong("interval_ms", 5000L)
        isRepeatMode = prefs.getBoolean("is_repeat_mode", false)
        val startMs  = prefs.getLong("start_date_ms", -1L)
        startDate    = if (startMs != -1L) Calendar.getInstance().apply { timeInMillis = startMs } else null
        val endMs    = prefs.getLong("end_date_ms", -1L)
        endDate      = if (endMs != -1L) Calendar.getInstance().apply { timeInMillis = endMs } else null
        val uriStr   = prefs.getString("ringtone_uri", null)
        selectedRingtoneUri = if (uriStr != null) Uri.parse(uriStr) else null

        isMonitoring  = true
        loadCount     = 0
        lastAlarmTime = 0L
        monitoringUrl = webView.url
        Log.d("DEBUG", "startMonitoring url=$monitoringUrl")
        tvStatus.text = "監視中... 完了: 0回"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handler.postDelayed(reloadRunnable, intervalMs)
        // 変更
        startService(Intent(this, MonitoringService::class.java))
        registerScreenReceiver()
    }

    private fun stopMonitoring(status: String) {
        isMonitoring = false
        zeroDetectCount = 0
        tvStatus.text = status
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handler.removeCallbacks(reloadRunnable)
        activeRingtone?.stop()
        activeRingtone = null
        // 変更
        stopService(Intent(this, MonitoringService::class.java))
        unregisterScreenReceiver()
        releaseWakeLock()
    }

    // 変更
    private fun checkKeiHtml(keiLines: List<String>, currentUrl: String?) {
        for (line in keiLines) {
            val parts = line.removePrefix("KEI:").split("\t")
            Log.d("DEBUG", "kei parts=$parts size=${parts.size}")
            if (parts.size < 2) continue

            val dateStr = parts[0].trim()
            val allowedRounds = selectedFilter[dateStr]
            if (selectedFilter.isNotEmpty() && allowedRounds == null) continue

            val slots = if (allowedRounds == null || allowedRounds.isEmpty()) parts.drop(1)
                        else allowedRounds.mapNotNull { r -> parts.getOrNull(r) }
            val hasVacancy = slots.any { it.contains("○") || it.contains("△") }
            if (!hasVacancy) continue

            val datePart = parts[0].trim()
            val monthMatch = Regex("(\\d+)月\\s*(\\d+)日").find(datePart) ?: continue
            val month = monthMatch.groupValues[1].toIntOrNull() ?: continue
            val day   = monthMatch.groupValues[2].toIntOrNull() ?: continue

            Log.d("DEBUG", "軽自動車空きあり候補: ${month}月${day}日 slots=$slots")
            if (isDateInRange(month, day)) {
                if (!isRepeatMode) {
                    stopMonitoring("空き検出！（軽自動車）")
                    playAlarm()
                    if (radarView.visibility == View.VISIBLE)
                        radarView.setState(RadarView.RadarState.VACANCY)
                    notifyVacancy()
                    handler.postDelayed({
                        activeRingtone?.stop()
                        activeRingtone = null
                    }, 5000)
                } else {
                    val now2 = System.currentTimeMillis()
                    if (now2 - lastAlarmTime >= 10000L) {
                        lastAlarmTime = now2
                        tvStatus.text = "空き検出中！（軽自動車） 監視継続..."
                        playAlarm()
                        if (radarView.visibility == View.VISIBLE)
                            radarView.setState(RadarView.RadarState.VACANCY)
                        notifyVacancy()
                    }
                }
                return
            }
        }
    }

    private fun checkHtml(bodyText: String, statuses: List<String>, currentUrl: String?) {
        val urlChanged = currentUrl != null && currentUrl != monitoringUrl
        if (urlChanged) {
            val loginKeywords = listOf("ログイン", "再ログイン", "セッション")
            if (loginKeywords.any { bodyText.contains(it) }) {
                Log.d("DEBUG", "login detected: url=$currentUrl")
                stopMonitoring("ログイン切れの可能性")
                return
            }
        }

        for (statusValue in statuses) {
            val parts = statusValue.split("\t")
            if (parts.size < 2) continue

            val dateStr = parts[0].trim()
            val allowedRounds = selectedFilter[dateStr]
            if (selectedFilter.isNotEmpty() && allowedRounds == null) continue

            val slots = if (allowedRounds == null || allowedRounds.isEmpty()) parts.drop(1)
                        else allowedRounds.mapNotNull { r -> parts.getOrNull(r) }
            val hasVacancy = slots.any { it.contains("○") || it.contains("△") }
            if (!hasVacancy) continue

            val datePart  = parts[0].trim()
            val dateMatch = Regex("(\\d+)月\\s*(\\d+)日").find(datePart) ?: continue
            val month     = dateMatch.groupValues[1].toIntOrNull() ?: continue
            val day       = dateMatch.groupValues[2].toIntOrNull() ?: continue

            Log.d("DEBUG", "空きあり候補: ${month}月${day}日 slots=$slots")

            if (isDateInRange(month, day)) {
                Log.d("DEBUG", "空き検出: ${month}月${day}日")
                if (!isRepeatMode) {
                    stopMonitoring("空き検出！")
                    playAlarm()
                    if (radarView.visibility == View.VISIBLE)
                        radarView.setState(RadarView.RadarState.VACANCY)
                    // 変更
                    notifyVacancy()
                    // 1回通知モード：5秒後に自動停止
                    handler.postDelayed({
                        activeRingtone?.stop()
                        activeRingtone = null
                    }, 5000)
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastAlarmTime >= 10000L) {
                        lastAlarmTime = now
                        tvStatus.text = "空き検出中！ 監視継続..."
                        playAlarm()
                        if (radarView.visibility == View.VISIBLE)
                            radarView.setState(RadarView.RadarState.VACANCY)
                        // 変更
                        notifyVacancy()
                    }
                }
                return
            }
        }
    }

    private fun isDateInRange(month: Int, day: Int): Boolean {
        val now  = Calendar.getInstance()
        val year = if (month < now.get(Calendar.MONTH) + 1) now.get(Calendar.YEAR) + 1
                   else now.get(Calendar.YEAR)
        val target = Calendar.getInstance().apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val afterStart = startDate?.let { !target.before(it) } ?: true
        val beforeEnd  = endDate?.let   { !target.after(it)  } ?: true
        return afterStart && beforeEnd
    }

    private fun flashIndicator() {
        indicatorView.visibility = View.VISIBLE
        handler.postDelayed({ indicatorView.visibility = View.INVISIBLE }, 300)
    }

    private fun playAlarm() {
        activeRingtone?.stop()
        val uri = selectedRingtoneUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        activeRingtone = RingtoneManager.getRingtone(applicationContext, uri)
        activeRingtone?.play()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isMonitoring) enterPipMode()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (isMonitoring) {
            enterPipMode()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean, newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            // 変更：PiP時はRadarViewを表示、他を非表示
            val root = webView.parent as? ViewGroup ?: return
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (child !== radarView) child.visibility = View.GONE
            }
            radarView.visibility = View.VISIBLE
            // 変更
            if (isMonitoring) {
                val isKei = webView.url?.contains("kei-reserve.jp") == true
                val bitmapRes = if (isKei) R.drawable.radar_kei else R.drawable.radar_riku // 変更
                val bitmap = BitmapFactory.decodeResource(resources, bitmapRes) // 変更
                radarView.startRadar(bitmap, RadarView.RadarState.MONITORING) // 変更
            }
        } else {
            // 変更：PiP終了時はWebViewを表示、RadarViewを非表示
            radarView.stopRadar()
            radarView.visibility = View.GONE
            val root = webView.parent as? ViewGroup ?: return
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (child !== radarView) child.visibility = View.VISIBLE
            }
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(1, 1))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == ACTION_STOP_FROM_NOTIFICATION) {
            stopMonitoring("監視停止")
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SETTINGS && resultCode == RESULT_OK && data != null) {
            val jsonStr = data.getStringExtra("selected_filter_json")
            if (jsonStr != null) {
                val json = org.json.JSONObject(jsonStr)
                val map = mutableMapOf<String, List<Int>>()
                for (key in json.keys()) {
                    val arr = json.getJSONArray(key)
                    map[key] = (0 until arr.length()).map { arr.getInt(it) }
                }
                selectedFilter = map
            }
        }
    }

    override fun onResume() {
        super.onResume()
        intervalMs   = prefs.getLong("interval_ms", 5000L)
        isRepeatMode = prefs.getBoolean("is_repeat_mode", false)
        val startMs  = prefs.getLong("start_date_ms", -1L)
        startDate    = if (startMs != -1L) Calendar.getInstance().apply { timeInMillis = startMs } else null
        val endMs    = prefs.getLong("end_date_ms", -1L)
        endDate      = if (endMs != -1L) Calendar.getInstance().apply { timeInMillis = endMs } else null
        val uriStr   = prefs.getString("ringtone_uri", null)
        selectedRingtoneUri = if (uriStr != null) Uri.parse(uriStr) else null
        updateStartButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(reloadRunnable)
    }

    private fun notifyVacancy() {
        val intent = Intent(this, MonitoringService::class.java).apply {
            action = MonitoringService.ACTION_NOTIFY
        }
        startService(intent)
    }

    private fun notifySessionExpired() {
        val intent = Intent(this, MonitoringService::class.java).apply {
            action = MonitoringService.ACTION_NOTIFY
            putExtra("message", "セッション切れの可能性があります")
        }
        startService(intent)
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        screenReceiver = ScreenReceiver {
            acquireWakeLock()
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenReceiver, filter)
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            unregisterReceiver(it)
            screenReceiver = null
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WebViewMonitor::MonitorWakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L) // 最大10分
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

    private fun updateStartButton() {
        if (isFreeUser(this)) {
            val startMs = prefs.getLong("start_date_ms", -1L)
            val endMs   = prefs.getLong("end_date_ms",   -1L)
            val enabled = startMs != -1L && endMs != -1L
            btnStart.isEnabled = enabled
            btnStart.alpha = if (enabled) 1f else 0.4f
        } else {
            btnStart.isEnabled = true
            btnStart.alpha = 1f
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }
}

fun isFreeUser(context: android.content.Context): Boolean {
    return if (BuildConfig.FLAVOR == "dev") {
        context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            .getBoolean("dev_is_free", false)
    } else {
        BuildConfig.IS_FREE
    }
}
