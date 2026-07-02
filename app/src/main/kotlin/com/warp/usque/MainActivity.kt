package com.warp.usque

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.net.TrafficStats
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.util.Log
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.divider.MaterialDivider
import usqueandroid.Usqueandroid
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
//import java.nio.charset.Charsets
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object { private const val REQ_VPN = 1001 }
    data class AppEntry(val label: String, val packageName: String)

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("usque", MODE_PRIVATE) }
    private val configFile by lazy { File(filesDir, "config.json") }

    private lateinit var endpointInput: TextInputEditText
    private lateinit var portInput: TextInputEditText
    private lateinit var sniInput: TextInputEditText
    private lateinit var statusBanner: TextView
    private lateinit var statusText: TextView
    private lateinit var speedText: TextView
    private lateinit var configStateText: TextView
    private lateinit var logText: TextView
    private lateinit var ipv4Text: TextView
    private lateinit var ipv6Text: TextView
    private lateinit var modeValue: TextView
    private lateinit var modeHint: TextView
    private lateinit var splitModeSwitch: MaterialSwitch
    private lateinit var appSearchInput: TextInputEditText
    private lateinit var appSection: MaterialCardView
    private lateinit var appListContainer: LinearLayout
    private lateinit var appCountText: TextView
    private lateinit var connectButton: MaterialButton
    private lateinit var defaultBtn: MaterialButton
    private lateinit var homeProfileSpinner: Spinner
    private lateinit var currentProfileText: TextView
    private lateinit var profileSpinner: Spinner
    private lateinit var profileNameInput: TextInputEditText
    private lateinit var saveNewProfileBtn: MaterialButton
    private lateinit var overwriteProfileBtn: MaterialButton
    private lateinit var deleteProfileBtn: MaterialButton
    private lateinit var languageZhButton: MaterialButton
    private lateinit var languageEnButton: MaterialButton
    private lateinit var selectAllAppsBtn: MaterialButton
    private lateinit var clearAllAppsBtn: MaterialButton

    private var useEnglish = false
    private var vpnRunning = false
    private var vpnGranted = false
    private var configDirty = false
    private var appsLoaded = false
    private var suppressProfileSelection = false
    private var currentPageIndex = 0
    private var pageSwitcher: ((Int) -> Unit)? = null
    private var swipeDownX = 0f
    private var swipeDownY = 0f
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastSpeedTs = 0L
    private val speedTicker = object : Runnable {
        override fun run() {
            updateSpeedLine()
            handler.postDelayed(this, 1000)
        }
    }
    private val profiles = linkedMapOf<String, Triple<String, String, Int>>()
    private val allApps = mutableListOf<AppEntry>()
    private val selectedPackages = linkedSetOf<String>()

    private val bg = Color.rgb(255, 248, 242)
    private val surface = Color.rgb(255, 255, 255)
    private val surface2 = Color.rgb(255, 238, 224)
    private val primary = Color.rgb(246, 196, 155) // soft orange / 淡橘色
    private val darkAccent = Color.rgb(205, 126, 73)
    private val mango = Color.rgb(255, 224, 195)
    private val onPrimary = Color.rgb(82, 49, 28)
    private val textColor = Color.rgb(48, 39, 32)
    private val subText = Color.rgb(111, 91, 76)
    private val outline = Color.rgb(232, 211, 194)
    private val green = Color.rgb(49, 145, 104)
    private val danger = Color.rgb(188, 77, 77)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Usque RU"
        useEnglish = prefs.getBoolean("useEnglish", false)
        buildUi()
        loadSavedState()
        refreshAppList()
        refreshState()
        resetSpeedMeter()
        handler.post(speedTicker)
    }

    override fun onDestroy() {
        handler.removeCallbacks(speedTicker)
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeDownX = ev.rawX
                swipeDownY = ev.rawY
            }
            MotionEvent.ACTION_UP -> {
                val dx = ev.rawX - swipeDownX
                val dy = ev.rawY - swipeDownY
                val threshold = dp(72)
                if (kotlin.math.abs(dx) > threshold && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.4f) {
                    if (dx < 0 && currentPageIndex < 2) { pageSwitcher?.invoke(currentPageIndex + 1); return true }
                    if (dx > 0 && currentPageIndex > 0) { pageSwitcher?.invoke(currentPageIndex - 1); return true }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun tr(zh: String, en: String): String = if (useEnglish) en else zh
    private fun setLanguage(en: Boolean) {
        useEnglish = en
        prefs.edit().putBoolean("useEnglish", useEnglish).apply()
        buildUi()
        loadSavedState()
        refreshAppList()
        refreshState()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val toolbar = MaterialToolbar(this).apply {
            title = "Usque RU"
            subtitle = null
            setTitleTextColor(textColor)
            setSubtitleTextColor(subText)
            setBackgroundColor(Color.rgb(255, 252, 249))
            elevation = dp(2).toFloat()
            setPadding(dp(8), 0, dp(8), 0)
        }
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setBackgroundColor(Color.rgb(255, 252, 249))
        }
        val pageHost = FrameLayout(this).apply { setBackgroundColor(bg) }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(56)))
        root.addView(tabs, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(pageHost, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        fun pageScroll(child: View): ScrollView = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bg)
            (child.parent as? ViewGroup)?.removeView(child)
            addView(child)
        }
        val overviewTab = tabButton(tr("总览", "Overview"))
        val configTab = tabButton(tr("配置", "Config"))
        val appsTab = tabButton(tr("应用", "Apps"))
        val tabsList = listOf(overviewTab, configTab, appsTab)
        tabsList.forEachIndexed { index, b ->
            tabs.addView(b, LinearLayout.LayoutParams(0, dp(36), 1f).apply { if (index < 2) rightMargin = dp(8) })
        }

        val homePage = buildHomePage()
        val configPage = buildConfigPage()
        val appsPage = buildAppsPage()
        val titles = listOf("Usque RU", tr("连接配置", "Connection Config"), tr("选择应用", "Select Apps"))
        val pages = listOf(homePage, configPage, appsPage)
        fun showIndex(index: Int) {
            val safe = index.coerceIn(0, pages.lastIndex)
            currentPageIndex = safe
            toolbar.title = titles[safe]
            toolbar.subtitle = null
            tabsList.forEachIndexed { i, b -> setTabSelected(b, i == safe) }
            if (safe == 2) ensureAppsLoaded()
            pageHost.removeAllViews()
            pageHost.addView(pageScroll(pages[safe]), FrameLayout.LayoutParams(-1, -1))
        }

        overviewTab.setOnClickListener { showIndex(0) }
        configTab.setOnClickListener { showIndex(1) }
        appsTab.setOnClickListener { showIndex(2) }
        pageSwitcher = { showIndex(it) }
        showIndex(0)

        defaultBtn.setOnClickListener {
            val defaultEndpoint = Usqueandroid.getDefaultEndpoint(configFile.absolutePath)
            endpointInput.setText(parseEndpointHost(defaultEndpoint))
            portInput.setText(parseEndpointPort(defaultEndpoint, 443).toString())
            refreshState(tr("已读取默认 endpoint", "Default endpoint loaded"))
        }
        saveNewProfileBtn.setOnClickListener { saveAsNewProfile() }
        overwriteProfileBtn.setOnClickListener { overwriteSelectedProfile() }
        deleteProfileBtn.setOnClickListener { deleteSelectedProfile() }
        connectButton.setOnClickListener { if (vpnRunning) disconnectVpn() else connectVpn() }
        sniInput.addTextChangedListener(dirtyWatcher())
        endpointInput.addTextChangedListener(dirtyWatcher())
        portInput.addTextChangedListener(dirtyWatcher())
        appSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refreshAppList() }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun buildHomePage(): View {
        val content = pageContent()
        val hero = card().apply { setCardBackgroundColor(surface2) }
        val heroBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        val eyebrow = TextView(this).apply {
            text = "VPN STATUS"
            textSize = 12f
            letterSpacing = 0.16f
            setTextColor(onPrimary)
            setTypeface(null, Typeface.BOLD)
        }
        statusBanner = TextView(this).apply {
            text = tr("未连接", "Disconnected")
            textSize = 28f
            setTextColor(onPrimary)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(6), 0, 0)
        }
        statusText = TextView(this).apply {
            text = tr("状态: 未连接", "Status: Disconnected")
            textSize = 14f
            setTextColor(subText)
            setPadding(0, dp(2), 0, dp(2))
        }
        speedText = TextView(this).apply {
            text = "↓ 0 B/s   ↑ 0 B/s"
            textSize = 14f
            setTextColor(onPrimary)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        }
        homeProfileSpinner = Spinner(this).apply {
            background = round(surface, dp(16), outline)
            setPadding(dp(10), 0, dp(10), 0)
        }
        connectButton = MaterialButton(this).apply {
            text = tr("连接 VPN", "Connect VPN")
            textSize = 15f
            gravity = Gravity.CENTER
            setSingleLine(false)
            maxLines = 2
            setTextColor(Color.rgb(255, 255, 255))
            backgroundTintList = android.content.res.ColorStateList.valueOf(darkAccent)
            cornerRadius = dp(24)
            minHeight = dp(52)
            isAllCaps = false
        }
        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        actionRow.addView(connectButton, LinearLayout.LayoutParams(-1, dp(52)))
        heroBox.addView(eyebrow)
        heroBox.addView(statusBanner)
        heroBox.addView(statusText)
        heroBox.addView(speedText)
        heroBox.addView(homeProfileSpinner, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(10) })
        heroBox.addView(actionRow)
        hero.addView(heroBox)
        content.addView(hero, LinearLayout.LayoutParams(-1, -2))

        content.addView(sectionTitle(tr("代理模式", "Proxy Mode")))
        val modeCard = card()
        val modeBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10)) }
        modeValue = TextView(this).apply { text = tr("全局模式", "Global Mode"); textSize = 18f; setTextColor(textColor); setTypeface(null, Typeface.BOLD) }
        modeHint = TextView(this).apply { text = tr("所有应用走 VPN。切到分应用后，仅“应用”页勾选的应用走 VPN。", "All apps use VPN. In split mode, only selected apps use VPN."); textSize = 14f; setTextColor(subText); setPadding(0, dp(4), 0, dp(6)) }
        splitModeSwitch = MaterialSwitch(this).apply {
            text = tr("启用分应用代理", "Enable split tunneling")
            textSize = 16f
            setTextColor(textColor)
            setPadding(0, dp(2), 0, 0)
            setOnCheckedChangeListener { _, _ ->
                markDirty(); saveSelectedApps(); updateModeUi(); refreshAppList()
            }
        }
        modeBox.addView(modeValue)
        modeBox.addView(modeHint)
        modeBox.addView(splitModeSwitch)
        modeCard.addView(modeBox)
        content.addView(modeCard)

        ipv4Text = TextView(this)
        ipv6Text = TextView(this)
        configStateText = TextView(this)
        logText = TextView(this).apply {
            text = tr("点连接即可。首次连接会自动注册并启动 VPN。", "Tap Connect. First launch will register automatically and start VPN.")
        }

        val languageCard = card()
        val languageBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
        languageZhButton = secondaryButton("中文").apply { setOnClickListener { setLanguage(false) } }
        languageEnButton = secondaryButton("English").apply { setOnClickListener { setLanguage(true) } }
        languageBox.addView(languageZhButton, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(10) })
        languageBox.addView(languageEnButton, LinearLayout.LayoutParams(0, dp(50), 1f))
        languageCard.addView(languageBox)
        content.addView(languageCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })
        return content
    }

    private fun buildConfigPage(): View {
        val content = pageContent()
        content.addView(sectionHeader(tr("连接配置", "Connection Config"), tr("多组方案一键切换。", "Switch between saved profiles quickly.")))

        val profileCard = card()
        val profileBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10)) }
        profileSpinner = Spinner(this).apply { background = round(surface2, dp(16), outline); setPadding(dp(10), 0, dp(10), 0) }
        profileNameInput = input(tr("配置名称", "Profile Name"), tr("例如：Yandex 443 / Cloudflare 8443", "e.g. Yandex 443 / Cloudflare 8443"))
        saveNewProfileBtn = secondaryButton(tr("保存为新方案", "Save as New"))
        overwriteProfileBtn = secondaryButton(tr("覆盖当前方案", "Overwrite Current"))
        deleteProfileBtn = secondaryButton(tr("删除选中方案", "Delete Profile"))
        profileBox.addView(TextView(this).apply { text = tr("配置方案", "Profiles"); textSize = 18f; setTextColor(textColor); setTypeface(null, Typeface.BOLD) })
        profileBox.addView(TextView(this).apply { text = tr("保存当前 SNI / Endpoint / Port，后面可以直接切换。", "Save current SNI / Endpoint / Port for quick switching."); textSize = 12f; setTextColor(subText); setPadding(0, dp(2), 0, dp(6)) })
        profileBox.addView(profileSpinner, LinearLayout.LayoutParams(-1, dp(42)))
        profileBox.addView(inputWrap(tr("配置名称", "Profile Name"), profileNameInput), LinearLayout.LayoutParams(-1, dp(70)).apply { topMargin = dp(5) })
        val profileActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        profileActions.addView(overwriteProfileBtn, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(8) })
        profileActions.addView(saveNewProfileBtn, LinearLayout.LayoutParams(0, dp(42), 1f))
        profileBox.addView(profileActions, LinearLayout.LayoutParams(-1, dp(48)))
        profileBox.addView(deleteProfileBtn, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(5) })
        profileCard.addView(profileBox)
        content.addView(profileCard)

        content.addView(sectionTitle(tr("当前连接参数", "Current Connection")))
        val config = card()
        val configBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(8)) }
        sniInput = input("SNI", "yandex.ru")
        endpointInput = input("Endpoint IP", "162.159.198.2")
        portInput = input("Connect Port", "443")
        defaultBtn = secondaryButton(tr("读取默认 endpoint", "Load Default Endpoint"))
        configBox.addView(compactInputWrap("SNI", sniInput))
        configBox.addView(compactInputWrap("Endpoint IP", endpointInput))
        configBox.addView(compactInputWrap("Connect Port", portInput))
        configBox.addView(defaultBtn, LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(3) })
        config.addView(configBox)
        content.addView(config)
        return content
    }

    private fun buildAppsPage(): View {
        val content = pageContent()
        content.addView(sectionHeader(tr("选择应用", "Select Apps"), tr("这里只负责选择应用；是否启用分应用代理在“总览”页切换。", "Only choose apps here. Enable split tunneling on Overview.")))
        appSection = card()
        val appBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10)) }
        appSearchInput = input(tr("搜索应用", "Search Apps"), tr("应用名或包名", "App name or package"))
        appCountText = infoLine(tr("已选择 0 个应用", "0 apps selected"))
        appListContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        appBox.addView(inputWrap(tr("搜索", "Search"), appSearchInput))
        appBox.addView(appCountText)
        val appActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        selectAllAppsBtn = secondaryButton(tr("全选", "Select All")).apply { setOnClickListener { selectAllVisibleApps() } }
        clearAllAppsBtn = secondaryButton(tr("全不选", "Select None")).apply { setOnClickListener { clearAllVisibleApps() } }
        appActions.addView(selectAllAppsBtn, LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(8) })
        appActions.addView(clearAllAppsBtn, LinearLayout.LayoutParams(0, dp(40), 1f))
        appBox.addView(appActions, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(4) })
        appBox.addView(MaterialDivider(this).apply { dividerColor = outline }, LinearLayout.LayoutParams(-1, 1).apply { topMargin = dp(8); bottomMargin = dp(8) })
        appBox.addView(appListContainer)
        appSection.addView(appBox)
        content.addView(appSection)
        return content
    }

    private fun pageContent() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(14))
        setBackgroundColor(bg)
    }

    private fun tabButton(label: String) = MaterialButton(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        cornerRadius = dp(18)
        minHeight = dp(36)
        insetTop = 0
        insetBottom = 0
        setTextColor(subText)
        backgroundTintList = android.content.res.ColorStateList.valueOf(surface2)
    }

    private fun setTabSelected(button: MaterialButton, selected: Boolean) {
        button.setTextColor(if (selected) onPrimary else subText)
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(if (selected) primary else surface2)
    }

    private fun sectionHeader(title: String, desc: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(2), 0, dp(2), dp(8))
        addView(TextView(this@MainActivity).apply { text = title; textSize = 20f; setTextColor(textColor); setTypeface(null, Typeface.BOLD) })
        addView(TextView(this@MainActivity).apply { text = desc; textSize = 12f; setTextColor(subText); setPadding(0, dp(2), 0, 0) })
    }

    private fun card() = MaterialCardView(this).apply {
        radius = dp(24).toFloat()
        cardElevation = 0f
        strokeWidth = 1
        strokeColor = outline
        setCardBackgroundColor(surface)
        useCompatPadding = false
    }
    private fun secondaryButton(label: String) = MaterialButton(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        cornerRadius = dp(22)
        setTextColor(onPrimary)
        backgroundTintList = android.content.res.ColorStateList.valueOf(primary)
        strokeWidth = 0
        elevation = dp(1).toFloat()
    }
    private fun sectionTitle(s: String) = TextView(this).apply {
        text = s
        textSize = 14f
        setTextColor(subText)
        setTypeface(null, Typeface.BOLD)
        setPadding(dp(4), dp(10), dp(4), dp(6))
    }
    private fun input(label: String, hint: String) = TextInputEditText(this).apply {
        setHint(hint)
        setTextColor(textColor)
        setHintTextColor(Color.rgb(150, 123, 103))
        textSize = 14f
        isSingleLine = true
    }
    private fun inputWrap(label: String, edit: TextInputEditText) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, dp(70)).apply { bottomMargin = dp(5) }
        addView(TextView(this@MainActivity).apply {
            text = label
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(subText)
            setPadding(dp(2), 0, dp(2), dp(4))
        })
        addView(TextInputLayout(this@MainActivity).apply {
            isHintEnabled = false
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxBackgroundColor = surface
            setBoxCornerRadii(dp(18).toFloat(), dp(18).toFloat(), dp(18).toFloat(), dp(18).toFloat())
            setBoxStrokeColor(primary)
            addView(edit)
        }, LinearLayout.LayoutParams(-1, dp(50)))
    }
    private fun compactInputWrap(label: String, edit: TextInputEditText) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(2) }
        addView(TextView(this@MainActivity).apply {
            text = label
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(subText)
            setPadding(dp(2), 0, dp(2), dp(2))
        })
        addView(TextInputLayout(this@MainActivity).apply {
            isHintEnabled = false
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxBackgroundColor = surface
            setBoxCornerRadii(dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat())
            setBoxStrokeColor(primary)
            addView(edit)
        }, LinearLayout.LayoutParams(-1, dp(42)))
    }
    private fun pill(s: String) = TextView(this).apply {
        text = s
        setTextColor(primary)
        textSize = 12f
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        background = round(surface2, dp(18), outline)
        setPadding(dp(12), dp(7), dp(12), dp(7))
    }
    private fun infoLine(s: String) = TextView(this).apply {
        text = s
        setTextColor(subText)
        setPadding(0, dp(4), 0, dp(4))
        textSize = 13f
    }
    private fun round(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
        if (stroke != null) setStroke(1, stroke)
    }
    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun dirtyWatcher() = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { markDirty() }
        override fun afterTextChanged(s: Editable?) {}
    }
    private fun markDirty() { configDirty = true; updateConfigState() }
    private fun updateConfigState(extra: String = "") {
        val base = if (configDirty) tr("配置: 未保存，连接时会自动保存", "Config: unsaved, will auto-save on connect") else tr("配置: 已保存", "Config: saved")
        configStateText.text = if (extra.isBlank()) base else "$base · $extra"
    }
    private fun saveInputs() {
        prefs.edit()
            .putString("sni", sniInput.text?.toString().orEmpty())
            .putString("endpoint", normalizedEndpoint())
            .putInt("connectPort", normalizedPort())
            .putBoolean("splitMode", splitModeSwitch.isChecked)
            .putStringSet("selectedPackages", selectedPackages.toSet())
            .apply()
        configDirty = false
        updateConfigState(); updateModeUi()
    }
    private fun loadProfiles() {
        profiles.clear()
        val raw = prefs.getString("profilesJson", "") ?: ""
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                profiles[o.getString("name")] = Triple(o.optString("sni", "yandex.ru"), o.optString("endpoint", "162.159.198.2"), o.optInt("port", 443))
            }
        }
        if (profiles.isEmpty()) {
            profiles["默认 443"] = Triple("yandex.ru", "162.159.198.2", 443)
            profiles["备用 8443"] = Triple("yandex.ru", "162.159.198.2", 8443)
            persistProfiles()
        }
        refreshProfileSpinner()
    }
    private fun persistProfiles() {
        val arr = JSONArray()
        profiles.forEach { (name, v) ->
            arr.put(JSONObject().put("name", name).put("sni", v.first).put("endpoint", v.second).put("port", v.third))
        }
        prefs.edit().putString("profilesJson", arr.toString()).apply()
    }
    private fun refreshProfileSpinner() {
        val names = profiles.keys.toList()
        if (::profileSpinner.isInitialized) {
            profileSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
            val current = currentProfileName().takeIf { profiles.containsKey(it) } ?: names.firstOrNull().orEmpty()
            if (current.isNotBlank()) profileSpinner.setSelection(names.indexOf(current).coerceAtLeast(0), false)
            profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val name = names.getOrNull(position) ?: return
                    loadProfileForEditing(name)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        refreshHomeProfileSpinner()
    }
    private fun refreshHomeProfileSpinner() {
        if (!::homeProfileSpinner.isInitialized) return
        val names = profiles.keys.toList()
        suppressProfileSelection = true
        homeProfileSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        val current = currentProfileName().takeIf { profiles.containsKey(it) } ?: names.firstOrNull().orEmpty()
        if (current.isNotBlank()) homeProfileSpinner.setSelection(names.indexOf(current).coerceAtLeast(0), false)
        suppressProfileSelection = false
        homeProfileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressProfileSelection) return
                val name = names.getOrNull(position) ?: return
                switchHomeProfile(name)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        updateCurrentProfileUi()
    }
    private fun selectedProfileName(): String = profileSpinner.selectedItem?.toString().orEmpty()
    private fun currentProfileName(): String = prefs.getString("currentProfileName", "")?.orEmpty() ?: ""
    private fun setCurrentProfileName(name: String) { prefs.edit().putString("currentProfileName", name).apply() }
    private fun updateCurrentProfileUi() {
        if (!::currentProfileText.isInitialized) return
        val name = currentProfileName().takeIf { it.isNotBlank() } ?: profiles.keys.firstOrNull().orEmpty()
        val p = profiles[name]
        currentProfileText.text = if (p != null) {
            tr("当前配置：$name · SNI ${p.first} · ${p.second}:${p.third}", "Current: $name · SNI ${p.first} · ${p.second}:${p.third}")
        } else {
            tr("当前配置：未选择", "Current profile: none")
        }
    }
    private fun syncConfigProfileSpinner(name: String) {
        if (!::profileSpinner.isInitialized) return
        val names = profiles.keys.toList()
        val index = names.indexOf(name)
        if (index >= 0) profileSpinner.setSelection(index, false)
    }
    private fun switchHomeProfile(name: String) {
        if (!profiles.containsKey(name)) return
        if (vpnRunning) {
            refreshHomeProfileSpinner()
            toast(tr("请先断开 VPN，再切换配置", "Disconnect VPN before switching profile"))
            return
        }
        applyProfileToInputs(name, persist = true)
        setCurrentProfileName(name)
        syncConfigProfileSpinner(name)
        updateCurrentProfileUi()
        toast(tr("已切换配置：$name", "Profile switched: $name"))
    }
    private fun applyProfileToInputs(name: String, persist: Boolean) {
        val p = profiles[name] ?: return
        sniInput.setText(p.first)
        endpointInput.setText(p.second)
        portInput.setText(p.third.toString())
        profileNameInput.setText(name)
        if (persist) { markDirty(); saveInputs() }
    }
    private fun saveAsNewProfile() {
        val base = profileNameInput.text?.toString().orEmpty().trim().ifBlank { normalizedEndpoint() }
        val name = uniqueProfileName(base)
        profiles[name] = Triple(sniInput.text?.toString().orEmpty().ifBlank { "yandex.ru" }, normalizedEndpointHost(), normalizedPort())
        persistProfiles(); refreshProfileSpinner(); profileNameInput.setText(name); syncConfigProfileSpinner(name); toast(tr("已保存为新方案：$name", "Saved as new profile: $name"))
    }
    private fun overwriteSelectedProfile() {
        val selected = selectedProfileName()
        val name = selected.ifBlank { profileNameInput.text?.toString().orEmpty().trim() }
        if (name.isBlank()) return toast(tr("请先选择一个方案", "Select a profile first"))
        profiles[name] = Triple(sniInput.text?.toString().orEmpty().ifBlank { "yandex.ru" }, normalizedEndpointHost(), normalizedPort())
        persistProfiles(); refreshProfileSpinner(); profileNameInput.setText(name); syncConfigProfileSpinner(name)
        if (currentProfileName() == name) { setCurrentProfileName(name); refreshHomeProfileSpinner(); updateCurrentProfileUi() }
        toast(tr("已覆盖当前方案：$name", "Current profile overwritten: $name"))
    }
    private fun loadProfileForEditing(name: String) {
        if (!profiles.containsKey(name)) return
        applyProfileToInputs(name, persist = false)
    }
    private fun uniqueProfileName(base: String): String {
        if (!profiles.containsKey(base)) return base
        var index = 2
        while (profiles.containsKey("$base $index")) index++
        return "$base $index"
    }
    private fun deleteSelectedProfile() {
        val name = selectedProfileName()
        if (name.isBlank()) return
        profiles.remove(name)
        if (currentProfileName() == name) setCurrentProfileName(profiles.keys.firstOrNull().orEmpty())
        persistProfiles(); refreshProfileSpinner(); toast("已删除：$name")
    }

    private fun loadSavedState() {
        loadProfiles()
        val saved = prefs.getString("endpoint", "162.159.198.2:443") ?: "162.159.198.2:443"
        endpointInput.setText(parseEndpointHost(saved))
        portInput.setText(prefs.getInt("connectPort", parseEndpointPort(saved, 443)).toString())
        sniInput.setText(prefs.getString("sni", "yandex.ru") ?: "yandex.ru")
        selectedPackages.clear(); selectedPackages.addAll(prefs.getStringSet("selectedPackages", emptySet()) ?: emptySet())
        splitModeSwitch.isChecked = prefs.getBoolean("splitMode", false)
        if (currentProfileName().isBlank() && profiles.isNotEmpty()) setCurrentProfileName(profiles.keys.first())
        configDirty = false
        updateConfigState(); updateModeUi(); refreshHomeProfileSpinner(); updateCurrentProfileUi()
    }

    private fun ensureAppsLoaded() {
        if (appsLoaded) return
        appsLoaded = true
        appCountText.text = tr("正在读取应用列表…", "Loading app list…")
        loadInstalledApps()
    }
    private fun loadInstalledApps() {
        executor.execute {
            val pm = packageManager
            val flags = PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES
            val apps = pm.getInstalledPackages(flags)
                .asSequence()
                .mapNotNull { pkg -> pkg.applicationInfo }
                .filter { it.packageName != packageName }
                .map { info ->
                    val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(info.packageName)
                    AppEntry(label.ifBlank { info.packageName }, info.packageName)
                }
                .distinctBy { it.packageName }
                .sortedWith(compareBy<AppEntry> { it.label.lowercase() }.thenBy { it.packageName })
                .toList()
            handler.post { allApps.clear(); allApps.addAll(apps); refreshAppList() }
        }
    }
    private fun refreshAppList() {
        if (!::appListContainer.isInitialized) return
        if (!appsLoaded) { appCountText.text = tr("进入应用页后读取应用列表", "App list loads when opening Apps"); return }
        appListContainer.removeAllViews()
        val visible = visibleApps()
        visible.forEach { app -> appListContainer.addView(appRow(app), LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(5) }) }
        appCountText.text = if (splitModeSwitch.isChecked) tr("已选择 ${selectedPackages.size} 个 · 显示 ${visible.size}/${allApps.size}", "Selected ${selectedPackages.size} · Showing ${visible.size}/${allApps.size}") else tr("全局模式：选择暂不生效 · 显示 ${visible.size}/${allApps.size}", "Global mode: selections inactive · Showing ${visible.size}/${allApps.size}")
        updateModeUi()
    }
    private fun visibleApps(): List<AppEntry> {
        val query = appSearchInput.text?.toString().orEmpty().trim().lowercase()
        return allApps.asSequence()
            .filter { query.isBlank() || it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query) }
            .sortedWith(compareByDescending<AppEntry> { selectedPackages.contains(it.packageName) }.thenBy { it.label.lowercase() }.thenBy { it.packageName })
            .toList()
    }
    private fun selectAllVisibleApps() {
        if (!appsLoaded) return
        selectedPackages.addAll(visibleApps().map { it.packageName })
        markDirty(); saveSelectedApps(); refreshAppList()
        toast(tr("已全选当前列表", "Selected all visible apps"))
    }
    private fun clearAllVisibleApps() {
        if (!appsLoaded) return
        selectedPackages.removeAll(visibleApps().map { it.packageName }.toSet())
        markDirty(); saveSelectedApps(); refreshAppList()
        toast(tr("已取消选择当前列表", "Cleared visible apps"))
    }

    private fun appRow(app: AppEntry): View = MaterialCheckBox(this).apply {
        text = "${app.label}\n${app.packageName}"
        setTextColor(textColor)
        textSize = 13f
        background = round(if (selectedPackages.contains(app.packageName)) Color.rgb(255, 232, 212) else surface2, dp(18), outline)
        setPadding(dp(12), dp(6), dp(12), dp(6))
        isChecked = selectedPackages.contains(app.packageName)
        setOnCheckedChangeListener { _, checked ->
            if (checked) selectedPackages.add(app.packageName) else selectedPackages.remove(app.packageName)
            markDirty(); saveSelectedApps(); refreshAppList()
        }
    }
    private fun saveSelectedApps() { prefs.edit().putBoolean("splitMode", splitModeSwitch.isChecked).putStringSet("selectedPackages", selectedPackages.toSet()).apply() }
    private fun updateModeUi() {
        if (!::modeValue.isInitialized) return
        if (splitModeSwitch.isChecked) {
            modeValue.text = tr("分应用模式", "Split Mode")
            modeHint.text = tr("仅“应用”页勾选的应用走 VPN；已勾选应用会显示在列表最上面。", "Only selected apps use VPN; selected apps stay on top.")
            if (::appSection.isInitialized) { appSection.visibility = View.VISIBLE; appSection.alpha = 1.0f }
        } else {
            modeValue.text = tr("全局模式", "Global Mode")
            modeHint.text = tr("所有应用走 VPN；应用页的勾选会保留，但当前不生效。", "All apps use VPN; app selections are kept but inactive.")
            if (::appSection.isInitialized) { appSection.visibility = View.VISIBLE; appSection.alpha = 1.0f }
        }
    }
    private fun selectedPackagesForVpn(): ArrayList<String> = ArrayList(selectedPackages)

    private fun hasValidRegistration(): Boolean = configFile.exists() && runCatching { Usqueandroid.isRegistered(configFile.absolutePath) }.getOrDefault(false)
    private fun deleteInvalidConfigIfNeeded() {
        if (!configFile.exists()) return
        val txt = runCatching { configFile.readText() }.getOrDefault("")
        if (txt.contains("\"private_key\": \"\"") || txt.contains("\"access_token\": \"\"") || !hasValidRegistration()) runCatching { configFile.delete() }
    }
    private fun resetSpeedMeter() {
        lastRxBytes = TrafficStats.getTotalRxBytes().takeIf { it >= 0 } ?: 0L
        lastTxBytes = TrafficStats.getTotalTxBytes().takeIf { it >= 0 } ?: 0L
        lastSpeedTs = System.currentTimeMillis()
        if (::speedText.isInitialized) speedText.text = "↓ 0 B/s   ↑ 0 B/s"
    }
    private fun updateSpeedLine() {
        if (!::speedText.isInitialized) return
        if (!vpnRunning) { speedText.text = "↓ 0 B/s   ↑ 0 B/s"; return }
        val now = System.currentTimeMillis()
        val rx = TrafficStats.getTotalRxBytes().takeIf { it >= 0 } ?: return
        val tx = TrafficStats.getTotalTxBytes().takeIf { it >= 0 } ?: return
        if (lastSpeedTs <= 0L) { resetSpeedMeter(); return }
        val seconds = ((now - lastSpeedTs).coerceAtLeast(1)).toDouble() / 1000.0
        val down = ((rx - lastRxBytes).coerceAtLeast(0) / seconds).toLong()
        val up = ((tx - lastTxBytes).coerceAtLeast(0) / seconds).toLong()
        lastRxBytes = rx; lastTxBytes = tx; lastSpeedTs = now
        speedText.text = "↓ ${formatSpeed(down)}   ↑ ${formatSpeed(up)}"
    }
    private fun formatSpeed(bytesPerSecond: Long): String {
        val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
        var value = bytesPerSecond.toDouble()
        var idx = 0
        while (value >= 1024.0 && idx < units.lastIndex) { value /= 1024.0; idx++ }
        return if (idx == 0) "${value.toLong()} ${units[idx]}" else String.format(java.util.Locale.US, "%.1f %s", value, units[idx])
    }

    private fun refreshState(extra: String = "") {
        val state = if (vpnRunning) tr("Подключено", "Connected") else tr("Отключено", "Disconnected")
        statusBanner.text = state
        statusText.text = "${tr("Статус", "Status")}: $state${if (extra.isNotBlank()) " · $extra" else ""}"
        statusBanner.setTextColor(if (vpnRunning) green else onPrimary)
        connectButton.text = if (vpnRunning) tr("Отключить VPN", "Disconnect VPN") else tr("Подключить VPN", "Connect VPN")
        val dark = android.content.res.ColorStateList.valueOf(darkAccent)
        connectButton.backgroundTintList = dark
        connectButton.setTextColor(Color.WHITE)
        if (::languageZhButton.isInitialized) {
            languageZhButton.backgroundTintList = android.content.res.ColorStateList.valueOf(if (!useEnglish) darkAccent else primary)
            languageEnButton.backgroundTintList = android.content.res.ColorStateList.valueOf(if (useEnglish) darkAccent else primary)
            languageZhButton.setTextColor(if (!useEnglish) Color.WHITE else onPrimary)
            languageEnButton.setTextColor(if (useEnglish) Color.WHITE else onPrimary)
        }
        updateConfigState(extra)
    }




/*
    private fun connectVpn() {
        saveInputs()
        if (vpnRunning) { toast(tr("已经在运行", "Already running")); return }
        if (splitModeSwitch.isChecked && selectedPackages.isEmpty()) { toast(tr("请至少勾选一个应用", "Select at least one app")); log(tr("分应用模式下，请至少勾选一个应用再连接", "Select at least one app before connecting in split mode")); refreshState("未选择应用"); return }
        if (!hasValidRegistration()) {
            log(tr("未找到有效注册文件，正在自动注册…", "No valid registration found. Registering automatically…"))
            executor.execute {
                try {
                    deleteInvalidConfigIfNeeded()
                    val result = Usqueandroid.register(configFile.absolutePath, "Android")
                    handler.post {
                        if (result.isNullOrBlank() && hasValidRegistration()) { log(tr("注册成功，准备请求 VPN 权限…", "Registered. Requesting VPN permission…")); requestVpnAndStart() }
                        else { log("注册失败: ${result.ifNullOrBlank(tr("未知错误", "Unknown error"))}"); refreshState(tr("注册失败", "Registration failed")) }
                    }
                } catch (e: Exception) { handler.post { log("注册异常: ${e.message ?: e.javaClass.simpleName}"); refreshState(tr("注册异常", "Registration error")) } }
            }
            return
        }
        requestVpnAndStart()
    }
*/
    private fun connectVpn() {
        saveInputs()
        if (vpnRunning) { toast(tr("已经在运行", "Already running")); return }
        if (splitModeSwitch.isChecked && selectedPackages.isEmpty()) { 
            toast(tr("Выберите хотя бы одно приложение", "Select at least one app"))
            log(tr("В режиме раздельного туннелирования нужно выбрать приложения перед подключением", "Select at least one app before connecting in split mode"))
            refreshState("Приложения не выбраны")
            return 
        }
        
        if (!hasValidRegistration()) {
            log("Регистрация MASQUE профиля через Яндекс...")
            
            // Запускаем асинхронное получение MASQUE ключей через наш Воркер и Яндекс-прокси
            val selectedIp = normalizedEndpointHost()
            val selectedPort = normalizedPort().toString()
            
            fetchKeysFromWorkerProxy(this, selectedIp, selectedPort)
            return
        }
        requestVpnAndStart()
    }


    private fun requestVpnAndStart() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null && !vpnGranted) { startActivityForResult(prepareIntent, REQ_VPN); return }
        vpnGranted = true; startTunnelNow()
    }
    private fun startTunnelNow() {
        val sni = sniInput.text?.toString().orEmpty().ifBlank { "yandex.ru" }
        val endpoint = "${normalizedEndpointHost()}:${normalizedPort()}"
        val splitMode = splitModeSwitch.isChecked
        val allowedApps = if (splitMode) selectedPackagesForVpn() else arrayListOf()
        log(if (splitMode) tr("Запуск раздельного VPN：${allowedApps.size} прил. · $endpoint", "Starting split VPN: ${allowedApps.size} apps · $endpoint") else tr("Запуск глобального VPN：$endpoint", "Starting global VPN: $endpoint"))
        resetSpeedMeter()
        vpnRunning = true; refreshState(tr("请求中", "Starting"))
        val intent = Intent(this, UsqueVpnService::class.java)
            .putExtra("configPath", configFile.absolutePath)
            .putExtra("sni", sni)
            .putExtra("endpoint", endpoint)
            .putExtra("splitMode", splitMode)
            .putStringArrayListExtra("allowedApps", allowedApps)
        startService(intent)
        log(tr("Служба VPN успешно запущена", "VPN service started"))
        refreshState(if (splitMode) tr("分应用模式运行中", "Split mode running") else tr("全局模式运行中", "Global mode running"))
    }
    private fun disconnectVpn() {
        log(tr("Остановка службы VPN…", "Stopping VPN service…"))
        vpnRunning = false; refreshState(tr("正在停止", "Stopping")); resetSpeedMeter()
        UsqueVpnService.stopActiveTunnel()
        runCatching { startService(Intent(this, UsqueVpnService::class.java).setAction(UsqueVpnService.ACTION_STOP)) }
        handler.postDelayed({ runCatching { stopService(Intent(this, UsqueVpnService::class.java)) }; onTunnelStopped(tr("已停止", "Stopped")) }, 500)
    }
    private fun onTunnelStopped(msg: String) { vpnRunning = false; refreshState(msg); log(msg) }
    private fun normalizedEndpointHost(): String = parseEndpointHost(endpointInput.text?.toString().orEmpty().trim().ifBlank { "162.159.198.2" }).ifBlank { "162.159.198.2" }
    private fun normalizedPort(): Int = (portInput.text?.toString().orEmpty().trim().toIntOrNull() ?: parseEndpointPort(endpointInput.text?.toString().orEmpty(), 443)).coerceIn(1, 65535)
    private fun normalizedEndpoint(): String = "${normalizedEndpointHost()}:${normalizedPort()}"
    private fun parseEndpointHost(value: String): String { val v = value.trim(); if (v.isBlank()) return "162.159.198.2"; if (v.startsWith("[") && v.contains("]")) return v.substringAfter("[").substringBefore("]"); return if (v.count { it == ':' } == 1) v.substringBefore(':') else v }
    private fun parseEndpointPort(value: String, fallback: Int): Int { val v = value.trim(); val p = when { v.startsWith("[") && v.contains("]:") -> v.substringAfter("]:"); v.count { it == ':' } == 1 -> v.substringAfter(':'); else -> "" }; return p.toIntOrNull()?.takeIf { it in 1..65535 } ?: fallback }
    private fun log(msg: String) { logText.text = msg }
    private fun String?.ifNullOrBlank(fallback: String): String = if (this.isNullOrBlank()) fallback else this
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN && resultCode == RESULT_OK) { vpnGranted = true; if (hasValidRegistration()) startTunnelNow() else connectVpn() }
        else if (requestCode == REQ_VPN) toast(tr("Доступ к VPN не разрешен в системе", "VPN permission denied"))
    }




    fun fetchKeysFromWorkerProxy(context: Context, userIp: String, userPort: String) {
        Thread {
            try {
                val yandexBytes = intArrayOf(104, 116, 116, 112, 115, 58, 47, 47, 116, 114, 97, 110, 115, 108, 97, 116, 101, 46, 121, 97, 110, 100, 101, 120, 46, 114, 117, 47, 116, 114, 97, 110, 115, 108, 97, 116, 101, 63, 117, 114, 108, 61)
                val yandexPart = yandexBytes.map { it.toChar() }.joinToString("")

                val workerBytes = intArrayOf(104, 116, 116, 112, 115, 58, 47, 47, 109, 97, 115, 113, 117, 101, 45, 114, 101, 103, 46, 101, 108, 101, 117, 116, 104, 101, 114, 105, 102, 101, 114, 46, 119, 111, 114, 107, 101, 114, 115, 46, 100, 101, 118, 47)
                val workerPart = workerBytes.map { it.toChar() }.joinToString("")

                val langBytes = intArrayOf(38, 108, 97, 110, 103, 61, 100, 101, 45, 114, 117)
                val langPart = langBytes.map { it.toChar() }.joinToString("")

                val proxyUrl = yandexPart + workerPart + langPart
                
                val url = URL(proxyUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 7000
                connection.readTimeout = 7000
                
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

//                val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val responseText = connection.inputStream.bufferedReader(java.nio.charset.StandardCharsets.UTF_8).use { it.readText() }

                if (responseText.contains("{")) {
                    val jsonStart = responseText.indexOf("{")
                    val jsonEnd = responseText.lastIndexOf("}") + 1
                    val rawJson = responseText.substring(jsonStart, jsonEnd)
                    
                    saveFinalConfig(rawJson, userIp, userPort, "yandex.ru")

                    // Безопасно переключаемся на главный поток для уведомлений и старта
                    (context as Activity).runOnUiThread {
                        Toast.makeText(context, "Регистрация успешна! Запуск...", Toast.LENGTH_SHORT).show()
                        requestVpnAndStart()
                    }
                } else {
                    (context as Activity).runOnUiThread {
                        Toast.makeText(context, "Ошибка: Неверный ответ прокси", Toast.LENGTH_SHORT).show()
                        refreshState("Ошибка данных")
                    }
                }
            } catch (e: Exception) {
                (context as Activity).runOnUiThread {
                    android.util.Log.e("USQUE_REG", "Ошибка автоматической регистрации: ${e.message}")
                    Toast.makeText(context, "Ошибка сети при регистрации", Toast.LENGTH_SHORT).show()
                    refreshState("Ошибка сети")
                }
            }
        }.start()
    }

    fun saveFinalConfig(serverResponseJson: String, selectedIp: String, selectedPort: String, selectedSni: String) {
        try {
            val cloudflareData = JSONObject(serverResponseJson)
            val finalConfig = JSONObject().apply {
                put("private_key", cloudflareData.getString("privKey"))
                put("peer_public_key", cloudflareData.getString("cloudflare_pub"))
                put("interface_v4", cloudflareData.getString("client_ipv4"))
                put("interface_v6", cloudflareData.getString("client_ipv6"))
                put("endpoint", selectedIp)
                put("port", selectedPort.toIntOrNull() ?: 443)
                put("sni", selectedSni.replace(Regex("^(https?://)?(www\\.)?"), "").substringBefore("/"))
            }
            configFile.writeText(finalConfig.toString(2))
            android.util.Log.d("USQUE_BUILD", "config.json успешно сформирован!")
        } catch (e: Exception) {
            android.util.Log.e("USQUE_BUILD", "Ошибка сборки конфига: ${e.message}")
        }
    }





}
