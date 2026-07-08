package com.warp.usque

import android.app.Activity
import android.content.ClipboardManager
import android.content.ClipData
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
    private lateinit var exportConfigBtn: MaterialButton
    private lateinit var importConfigBtn: MaterialButton
    private lateinit var languageRuButton: MaterialButton
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
    private val primary = Color.rgb(246, 196, 155) // soft orange / светло-оранжевый
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

    private fun tr(ru: String, en: String): String = if (useEnglish) en else ru
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
        val overviewTab = tabButton(tr("Главная", "Overview"))
        val configTab = tabButton(tr("Настройки", "Config"))
        val appsTab = tabButton(tr("Приложения", "Apps"))
        val tabsList = listOf(overviewTab, configTab, appsTab)
/*
        tabsList.forEachIndexed { index, b ->
            tabs.addView(b, LinearLayout.LayoutParams(0, dp(36), 1f).apply { if (index < 2) rightMargin = dp(8) })
        }
*/
        tabsList.forEachIndexed { index, b ->
            // ИСПРАВЛЕНО: Уменьшаем внутренние боковые отступы до 4dp, чтобы длинные русские слова влезли целиком
            b.setPadding(dp(4), b.paddingTop, dp(4), b.paddingBottom)
            
            tabs.addView(b, LinearLayout.LayoutParams(0, dp(36), 1f).apply { if (index < 2) rightMargin = dp(8) })
        }


        val homePage = buildHomePage()
        val configPage = buildConfigPage()
        val appsPage = buildAppsPage()
        val titles = listOf("Usque RU", tr("Настройки подключения", "Connection Config"), tr("Выбор приложений", "Select Apps"))
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
            refreshState(tr("Загружен endpoint по умолчанию", "Default endpoint loaded"))
        }
        saveNewProfileBtn.setOnClickListener { saveAsNewProfile() }
        overwriteProfileBtn.setOnClickListener { overwriteSelectedProfile() }
        deleteProfileBtn.setOnClickListener { deleteSelectedProfile() }
        exportConfigBtn.setOnClickListener { exportAllConfigToClipboard() }
        importConfigBtn.setOnClickListener { importAllConfigFromClipboard() }
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
            text = tr("Отключено", "Disconnected")
            textSize = 28f
            setTextColor(onPrimary)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(6), 0, 0)
        }
        statusText = TextView(this).apply {
            text = tr("Статус: Отключено", "Status: Disconnected")
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
            text = tr("Подключить VPN", "Connect VPN")
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

        content.addView(sectionTitle(tr("Режим проксирования", "Proxy Mode")))
        val modeCard = card()
        val modeBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10)) }
        modeValue = TextView(this).apply { text = tr("Глобальный режим", "Global Mode"); textSize = 18f; setTextColor(textColor); setTypeface(null, Typeface.BOLD) }
        modeHint = TextView(this).apply { text = tr("Все приложения идут через VPN. При переключении в раздельный режим через VPN будут идти только выбранные приложения.", "All apps use VPN. In split mode, only selected apps use VPN."); textSize = 14f; setTextColor(subText); setPadding(0, dp(4), 0, dp(6)) }
        splitModeSwitch = MaterialSwitch(this).apply {
            text = tr("Включить раздельное туннелирование", "Enable split tunneling")
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
            text = tr("Нажмите «Подключить». При первом запуске профиль зарегистрируется автоматически, и VPN запустится.", "Tap Connect. First launch will register automatically and start VPN.")
        }

        val languageCard = card()
        val languageBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
        languageRuButton = secondaryButton("Русский").apply { setOnClickListener { setLanguage(false) } }
        languageEnButton = secondaryButton("English").apply { setOnClickListener { setLanguage(true) } }
        languageBox.addView(languageRuButton, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(10) })
        languageBox.addView(languageEnButton, LinearLayout.LayoutParams(0, dp(50), 1f))
        languageCard.addView(languageBox)
        content.addView(languageCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })
        return content
    }

    private fun buildConfigPage(): View {
        val content = pageContent()
        content.addView(sectionHeader(tr("Настройки подключения", "Connection Config"), tr("Быстрое переключение между сохранёнными профилями.", "Switch between saved profiles quickly.")))

        val profileCard = card()
        val profileBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10)) }
        profileSpinner = Spinner(this).apply { background = round(surface2, dp(16), outline); setPadding(dp(10), 0, dp(10), 0) }
        profileNameInput = input(tr("Название профиля", "Profile Name"), tr("Например：Yandex 443 / Cloudflare 8443", "e.g. Yandex 443 / Cloudflare 8443"))
        saveNewProfileBtn = secondaryButton(tr("Сохранить как новый", "Save as New"))
        overwriteProfileBtn = secondaryButton(tr("Перезаписать текущий", "Overwrite Current"))
        deleteProfileBtn = secondaryButton(tr("Удалить выбранный профиль", "Delete Profile"))

/*
        // 🛠️ ИСПРАВЛЕНИЕ: Настраиваем отображение текста для ВСЕХ трех кнопок управления профилями
        val profileButtonsList = listOf(saveNewProfileBtn, overwriteProfileBtn, deleteProfileBtn)
        profileButtonsList.forEach { btn ->
            btn.isSingleLine = false         // Разрешаем перенос на новую строку
            btn.maxLines = 2                 // Ограничиваем максимум двумя строками
            btn.isAllCaps = false            // Отключаем принудительный Caps Lock
            btn.ellipsize = null
            
            // Уменьшаем отступы со всех сторон, чтобы двухстрочный текст сидел плотно
            btn.setPadding(dp(4), dp(2), dp(4), dp(2))
            
            // Отключаем встроенную минимальную высоту MaterialButton (чтобы кнопки не раздувались)
  
//            btn.minHeight = 0
//            btn.minimumHeight = 0
        }

        exportConfigBtn = secondaryButton(tr("Экспорт всего конфига", "Export entire config"))
        importConfigBtn = secondaryButton(tr("Импорт из буфера", "Import from buffer"))
        
        profileBox.addView(TextView(this).apply { text = tr("Профили настроек", "Profiles"); textSize = 18f; setTextColor(textColor); setTypeface(null, Typeface.BOLD) })
        profileBox.addView(TextView(this).apply { text = tr("Сохранить текущие SNI / Endpoint / Port для быстрого переключения.", "Save current SNI / Endpoint / Port for quick switching."); textSize = 12f; setTextColor(subText); setPadding(0, dp(2), 0, dp(6)) })
        profileBox.addView(profileSpinner, LinearLayout.LayoutParams(-1, dp(42)))
        profileBox.addView(inputWrap(tr("Название профиля", "Profile Name"), profileNameInput), LinearLayout.LayoutParams(-1, dp(70)).apply { topMargin = dp(5) })

        // Создаем горизонтальный контейнер для первых двух кнопок с автоматической высотой
        val profileActions = LinearLayout(this).apply { 
            orientation = LinearLayout.HORIZONTAL 
        }
        
        // Добавляем кнопки «Перезаписать» и «Сохранить как новый» с высотой WRAP_CONTENT
        profileActions.addView(overwriteProfileBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { 
            rightMargin = dp(8) 
        })
        profileActions.addView(saveNewProfileBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        
        // Добавляем горизонтальный контейнер в основной блок
        profileBox.addView(profileActions, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        
        // Возвращаем кнопку «Удалить выбранный профиль» на экран с автоматической высотой
        profileBox.addView(deleteProfileBtn, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { 
            topMargin = dp(8) 
        })
*/
        // 🛠️ ИСПРАВЛЕНИЕ: Разделяем настройку парных кнопок и кнопки удаления
        
        // Настройка для верхних горизонтальных кнопок (сжимаем их, чтобы поместились в ряд)
        val horizontalButtons = listOf(overwriteProfileBtn, saveNewProfileBtn)
        horizontalButtons.forEach { btn ->
            btn.isSingleLine = false
            btn.maxLines = 2
            btn.isAllCaps = false
            btn.ellipsize = null
            btn.setPadding(dp(4), dp(2), dp(4), dp(2))
//            btn.minHeight = 0
//            btn.minimumHeight = 0
        }

        // Настройка для нижней одиночной кнопки удаления (возвращаем стандартную комфортную высоту)
        deleteProfileBtn.apply {
            isSingleLine = false
            maxLines = 2
            isAllCaps = false
            ellipsize = null
            // Даем полноценные вертикальные отступы по 10dp, чтобы кнопка стала стандартной высоты
            setPadding(dp(12), dp(10), dp(12), dp(10))
            // Задаем комфортную минимальную высоту для нажатия пальцем
            minHeight = dp(40)
            minimumHeight = dp(40)
        }

        exportConfigBtn = secondaryButton(tr("Экспорт всего конфига", "Export entire config"))
        importConfigBtn = secondaryButton(tr("Импорт из буфера", "Import from buffer"))
        
        profileBox.addView(TextView(this).apply { text = tr("Профили настроек", "Profiles"); textSize = 18f; setTextColor(textColor); setTypeface(null, Typeface.BOLD) })
        profileBox.addView(TextView(this).apply { text = tr("Сохранить текущие SNI / Endpoint / Port для быстрого переключения.", "Save current SNI / Endpoint / Port for quick switching."); textSize = 12f; setTextColor(subText); setPadding(0, dp(2), 0, dp(6)) })
        profileBox.addView(profileSpinner, LinearLayout.LayoutParams(-1, dp(42)))
        profileBox.addView(inputWrap(tr("Название профиля", "Profile Name"), profileNameInput), LinearLayout.LayoutParams(-1, dp(70)).apply { topMargin = dp(5) })

        // Создаем горизонтальный контейнер для первых двух кнопок с автоматической высотой
        val profileActions = LinearLayout(this).apply { 
            orientation = LinearLayout.HORIZONTAL 
        }
        
        // Добавляем кнопки «Перезаписать» и «Сохранить как новый» с высотой WRAP_CONTENT
        profileActions.addView(overwriteProfileBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { 
            rightMargin = dp(8) 
        })
        profileActions.addView(saveNewProfileBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        
        // Добавляем горизонтальный контейнер в основной блок
        profileBox.addView(profileActions, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        
        // Добавляем кнопку «Удалить выбранный профиль» с автоматической высотой
        profileBox.addView(deleteProfileBtn, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { 
            topMargin = dp(10) // Увеличили отступ сверху для визуального разделения блоков
        })




        // 🟢 НАШ НОВЫЙ БЛОК: Создаем горизонтальный ряд для Экспорта и Импорта
        val backupActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        // Инициализируем и добавляем кнопку Экспорта (левая)
//        exportConfigBtn = MaterialButton(this).apply {
//            text = tr("Экспорт всего конфига", "Export entire config")
//            setOnClickListener { exportAllConfigToClipboard() }
//        }
        backupActions.addView(exportConfigBtn, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(8) })

        // Инициализируем и добавляем кнопку Импорта (правая)
//        importConfigBtn = MaterialButton(this).apply {
//            text = "Импорт"
//            setOnClickListener { importAllConfigFromClipboard() }
//        }
        backupActions.addView(importConfigBtn, LinearLayout.LayoutParams(0, dp(42), 1f))

        // Укладываем готовый ряд кнопок бэкапа в основной вертикальный контейнер профилей
        profileBox.addView(backupActions, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })


        profileCard.addView(profileBox)
        content.addView(profileCard)

        content.addView(sectionTitle(tr("Текущие параметры подключения", "Current Connection")))
        val config = card()
        val configBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(8)) }
        sniInput = input("SNI", "apteka.ru")
        endpointInput = input("Endpoint IP", "162.159.198.2")
        portInput = input("Connect Port", "443")
        defaultBtn = secondaryButton(tr("Загрузить endpoint по умолчанию", "Load Default Endpoint"))
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
        content.addView(sectionHeader(tr("Выбор приложений", "Select Apps"), tr("Здесь выбираются только приложения. Включение раздельного туннелирования выполняется на «Главной» странице.", "Only choose apps here. Enable split tunneling on Overview.")))
        appSection = card()
        val appBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10)) }
        appSearchInput = input(tr("Поиск приложений", "Search Apps"), tr("Название или имя пакета", "App name or package"))
        appCountText = infoLine(tr("Выбрано приложений: 0", "0 apps selected"))
        appListContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        appBox.addView(inputWrap(tr("Поиск", "Search"), appSearchInput))
        appBox.addView(appCountText)
        val appActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        selectAllAppsBtn = secondaryButton(tr("Выбрать все", "Select All")).apply { setOnClickListener { selectAllVisibleApps() } }
        clearAllAppsBtn = secondaryButton(tr("Снять выделение", "Select None")).apply { setOnClickListener { clearAllVisibleApps() } }
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

        // 🛠️ ИСПРАВЛЕНИЕ: Разрешаем многострочность
        isSingleLine = false
        maxLines = 2
        ellipsize = null

        // 🛠️ ИСПРАВЛЕНИЕ: Уменьшаем внутренние боковые отступы до 4dp (по аналогии с вкладками)
        setPadding(dp(4), paddingTop, dp(4), paddingBottom)
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
        val base = if (configDirty) tr("Конфиг: не сохранен, при подключении сохранится автоматически", "Config: unsaved, will auto-save on connect") else tr("Конфиг: сохранен", "Config: saved")
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
                profiles[o.getString("name")] = Triple(o.optString("sni", "apteka.ru"), o.optString("endpoint", "162.159.198.2"), o.optInt("port", 443))
            }
        }
        if (profiles.isEmpty()) {
            profiles["По умолчанию 443"] = Triple("apteka.ru", "162.159.198.2", 443)
            profiles["Альтернативный 8443"] = Triple("apteka.ru", "162.159.198.2", 8443)
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
            tr("Текущий профиль：$name · SNI ${p.first} · ${p.second}:${p.third}", "Current: $name · SNI ${p.first} · ${p.second}:${p.third}")
        } else {
            tr("Текущий профиль: не выбран", "Current profile: none")
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
            toast(tr("Отключите VPN перед переключением профиля", "Disconnect VPN before switching profile"))
            return
        }
        applyProfileToInputs(name, persist = true)
        setCurrentProfileName(name)
        syncConfigProfileSpinner(name)
        updateCurrentProfileUi()
        toast(tr("Профиль переключен：$name", "Profile switched: $name"))
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
        profiles[name] = Triple(sniInput.text?.toString().orEmpty().ifBlank { "apteka.ru" }, normalizedEndpointHost(), normalizedPort())
        persistProfiles(); refreshProfileSpinner(); profileNameInput.setText(name); syncConfigProfileSpinner(name); toast(tr("Сохранено как новый профиль: $name", "Saved as new profile: $name"))
    }
    private fun overwriteSelectedProfile() {
        val selected = selectedProfileName()
        val name = selected.ifBlank { profileNameInput.text?.toString().orEmpty().trim() }
        if (name.isBlank()) return toast(tr("Сначала выберите профиль", "Select a profile first"))
        profiles[name] = Triple(sniInput.text?.toString().orEmpty().ifBlank { "apteka.ru" }, normalizedEndpointHost(), normalizedPort())
        persistProfiles(); refreshProfileSpinner(); profileNameInput.setText(name); syncConfigProfileSpinner(name)
        if (currentProfileName() == name) { setCurrentProfileName(name); refreshHomeProfileSpinner(); updateCurrentProfileUi() }
        toast(tr("Текущий профиль перезаписан：$name", "Current profile overwritten: $name"))
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
        persistProfiles(); refreshProfileSpinner(); toast("Удалено：$name")
    }

    private fun loadSavedState() {
        loadProfiles()
        val saved = prefs.getString("endpoint", "162.159.198.2:443") ?: "162.159.198.2:443"
        endpointInput.setText(parseEndpointHost(saved))
        portInput.setText(prefs.getInt("connectPort", parseEndpointPort(saved, 443)).toString())
        sniInput.setText(prefs.getString("sni", "apteka.ru") ?: "apteka.ru")
        selectedPackages.clear(); selectedPackages.addAll(prefs.getStringSet("selectedPackages", emptySet()) ?: emptySet())
        splitModeSwitch.isChecked = prefs.getBoolean("splitMode", false)
        if (currentProfileName().isBlank() && profiles.isNotEmpty()) setCurrentProfileName(profiles.keys.first())
        configDirty = false
        updateConfigState(); updateModeUi(); refreshHomeProfileSpinner(); updateCurrentProfileUi()
    }

    private fun ensureAppsLoaded() {
        if (appsLoaded) return
        appsLoaded = true
        appCountText.text = tr("Загрузка списка приложений…", "Loading app list…")
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
        if (!appsLoaded) { appCountText.text = tr("Список приложений загрузится при открытии вкладки «Приложения»", "App list loads when opening Apps"); return }
        appListContainer.removeAllViews()
        val visible = visibleApps()
        visible.forEach { app -> appListContainer.addView(appRow(app), LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(5) }) }
        appCountText.text = if (splitModeSwitch.isChecked) tr("Выбрано ${selectedPackages.size} прил. · Показать ${visible.size}/${allApps.size}", "Selected ${selectedPackages.size} · Showing ${visible.size}/${allApps.size}") else tr("Глобальный режим: выбор не активен · Отображается ${visible.size}/${allApps.size}", "Global mode: selections inactive · Showing ${visible.size}/${allApps.size}")
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
        toast(tr("Выбраны все приложения в списке", "Selected all visible apps"))
    }
    private fun clearAllVisibleApps() {
        if (!appsLoaded) return
        selectedPackages.removeAll(visibleApps().map { it.packageName }.toSet())
        markDirty(); saveSelectedApps(); refreshAppList()
        toast(tr("Выбор со всех приложений в списке снят", "Cleared visible apps"))
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
            modeValue.text = tr("Раздельный режим", "Split Mode")
            modeHint.text = tr("Через VPN идут только выбранные приложения. Выбранные приложения всегда отображаются в самом верху списка.", "Only selected apps use VPN; selected apps stay on top.")
            if (::appSection.isInitialized) { appSection.visibility = View.VISIBLE; appSection.alpha = 1.0f }
        } else {
            modeValue.text = tr("Глобальный режим", "Global Mode")
            modeHint.text = tr("Все приложения идут через VPN. Выбор на вкладке приложений сохраняется, но сейчас не активен.", "All apps use VPN; app selections are kept but inactive.")
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
        if (::languageRuButton.isInitialized) {
            languageRuButton.backgroundTintList = android.content.res.ColorStateList.valueOf(if (!useEnglish) darkAccent else primary)
            languageEnButton.backgroundTintList = android.content.res.ColorStateList.valueOf(if (useEnglish) darkAccent else primary)
            languageRuButton.setTextColor(if (!useEnglish) Color.WHITE else onPrimary)
            languageEnButton.setTextColor(if (useEnglish) Color.WHITE else onPrimary)
        }
        updateConfigState(extra)
    }





    private fun connectVpn() {
        saveInputs()
        if (vpnRunning) { toast(tr("Приложение уже работает", "Already running")); return }
        if (splitModeSwitch.isChecked && selectedPackages.isEmpty()) { toast(tr("Выберите хотя бы одно приложение", "Select at least one app")); log(tr("Выберите хотя бы одно приложение перед подлючением в раздельном режиме", "Select at least one app before connecting in split mode")); refreshState("Приложение не выбрано"); return }
        if (!hasValidRegistration()) {
            log(tr("Не найдена действительная регистрация. Автоматическая регистрация…", "No valid registration found. Registering automatically…"))
            executor.execute {
                try {
                    deleteInvalidConfigIfNeeded()
                    val result = Usqueandroid.register(configFile.absolutePath, "Android")
                    handler.post {
                        if (result.isNullOrBlank() && hasValidRegistration()) { 
                            log(tr("Зарегистрировано. Запрашивается разрешение на использование VPN…", "Registered. Requesting VPN permission…")); 
                            requestVpnAndStart() 
                        } else { 
                            log("Регистрация не удалась: ${result.ifNullOrBlank(tr("Неизвестная ошибка", "Unknown error"))}"); 
                            refreshState(tr("Регистрация не удалась", "Registration failed")) 
                        }
                    }
                } catch (e: Exception) { handler.post { log("Ошибка регистрации: ${e.message ?: e.javaClass.simpleName}"); refreshState(tr("Ошибка регистрации", "Registration error")) } }
            }
            return
        }
        requestVpnAndStart()
    }

/*
    private fun connectVpn() {
        saveInputs()
        if (vpnRunning) { toast("Приложение уже работает"); return }
        if (splitModeSwitch.isChecked && selectedPackages.isEmpty()) { 
            toast("Выберите хотя бы одно приложение")
            log("В режиме раздельного туннелирования нужно выбрать приложения перед подключением")
            refreshState("Приложения не выбраны")
            return 
        }
        
        if (!hasValidRegistration()) {
            log("Регистрация нового MASQUE профиля через Яндекс...")
            val selectedIp = normalizedEndpointHost()
            val selectedPort = normalizedPort().toString()
            
            fetchKeysFromWorkerProxy(this, selectedIp, selectedPort)
            return
        }
        requestVpnAndStart()
    }
*/
/*
    private fun connectVpn() {
        saveInputs()
        if (vpnRunning) { toast(tr("Приложение уже работает", "Already running")); return }
        if (splitModeSwitch.isChecked && selectedPackages.isEmpty()) { 
            toast(tr("Выберите хотя бы одно приложение", "Select at least one app"))
            log(tr("Выберите хотя бы одно приложение перед подлючением в раздельном режиме", "Select at least one app before connecting in split mode"))
            refreshState("Приложение не выбрано")
            return 
        }
        
        // Возвращаем официальную встроенную регистрацию через Go-библиотеку
        if (!hasValidRegistration()) {
            log(tr("Не найдена действительная регистрация. Автоматическая регистрация…", "No valid registration found. Registering automatically…"))
            executor.execute {
                try {
                    deleteInvalidConfigIfNeeded()
                    
                    // Вызов нативного метода Go (из usque_android.go, строка 66)
                    val result = Usqueandroid.register(configFile.absolutePath, "Android")
                    
                    handler.post {
                        if (result.isNullOrBlank() && hasValidRegistration()) { 
                            log(tr("Зарегистрировано. Запрашивается разрешение на использование VPN…", "Registered. Requesting VPN permission…"))
                            requestVpnAndStart() 
                        } else { 
                            log("Регистрация не удалась: ${result.ifNullOrBlank(tr("Неизвестная ошибка", "Unknown error"))}")
                            refreshState(tr("Registration failed", "Регистрация не удалась")) 
                        }
                    }
                } catch (e: Exception) { 
                    handler.post { 
                        log("Ошибка регистрации: ${e.message ?: e.javaClass.simpleName}")
                        refreshState(tr("Ошибка регистрации", "Registration error")) 


                        log("Регистрация нового MASQUE профиля через Cloudflare Worker...")
                        val selectedIp = normalizedEndpointHost()
                        val selectedPort = normalizedPort().toString()
            
                        // 🟢 ВОТ ЗДЕСЬ ПРОИСХОДИТ ОБРАЩЕНИЕ К ВОРКЕРУ:
                        fetchKeysFromWorkerProxy(this, selectedIp, selectedPort)


                    } 
                }
            }
            return
        }
        requestVpnAndStart()
    }
*/
/*
    private fun connectVpn() {
        saveInputs()
        if (vpnRunning) { toast("Приложение уже работает"); return }
        if (splitModeSwitch.isChecked && selectedPackages.isEmpty()) { 
            toast("Выберите хотя бы одно приложение")
            log("В режиме раздельного туннелирования нужно выбрать приложения перед подключением")
            refreshState("Приложения не выбраны")
            return 
        }
        
        // Если регистрации нет — отправляем запрос на ваш Cloudflare Worker
        if (!hasValidRegistration()) {
            log("Регистрация нового MASQUE профиля через Cloudflare Worker...")
            val selectedIp = normalizedEndpointHost()
            val selectedPort = normalizedPort().toString()
            
            // 🟢 ВОТ ЗДЕСЬ ПРОИСХОДИТ ОБРАЩЕНИЕ К ВОРКЕРУ:
            fetchKeysFromWorkerProxy(this, selectedIp, selectedPort)
            return
        }
        requestVpnAndStart()
    }
*/

/*
    private fun connectVpn() {
        saveInputs()
        if (vpnRunning) { toast(tr("Приложение уже работает", "Already running")); return }
        if (splitModeSwitch.isChecked && selectedPackages.isEmpty()) { 
            toast(tr("Выберите хотя бы одно приложение", "Select at least one app"))
            log(tr("Выберите хотя бы одно приложение перед подлючением в раздельном режиме", "Select at least one app before connecting in split mode"))
            refreshState("Приложение не выбрано")
            return 
        }
        
        if (!hasValidRegistration()) {
            log(tr("Не найдена действительная регистрация. Автоматическая регистрация…", "No valid registration found. Registering automatically…"))
            executor.execute {
                try {
                    deleteInvalidConfigIfNeeded()
                    
                    // 1. Попытка официальной регистрации через нативный Go
                    val result = Usqueandroid.register(configFile.absolutePath, "Android")
                    
                    handler.post {
                        if (result.isNullOrBlank() && hasValidRegistration()) { 
                            log(tr("Зарегистрировано. Запрашивается разрешение на использование VPN…", "Registered. Requesting VPN permission…"))
                            
//                            // ИСПРАВЛЕНИЕ: Перезаписываем IP-адрес в созданном конфиге на тот, что выбран пользователем в UI
//                            val selectedIp = normalizedEndpointHost()
//                            val selectedPort = normalizedPort().toString()
//                            saveFinalConfig(configFile.readText(), selectedIp, selectedPort)
                            
                            requestVpnAndStart() 
                        } else { 
                            log("Регистрация не удалась: ${result.ifNullOrBlank(tr("Неизвестная ошибка", "Unknown error"))}"); 
                            refreshState(tr("Регистрация не удалась", "Registration failed")) 

//                            // Если метод вернул ошибку, но исключения не было — тоже пробуем воркер
//                            log("Нативная регистрация вернула ошибку: $result. Пробуем Cloudflare Worker...")
//                            val selectedIp = normalizedEndpointHost()
//                            val selectedPort = normalizedPort().toString()
//                            fetchKeysFromWorkerProxy(this@MainActivity, selectedIp, selectedPort)
                        }
                    }
                } catch (e: Exception) { 
                    handler.post { 
                        log("Ошибка встроенной регистрации: ${e.message ?: e.javaClass.simpleName}"); 
                        refreshState(tr("Ошибка встроенной регистрации", "Registration error")) 

//                        log("Ошибка встроенной регистрации: ${e.message ?: e.javaClass.simpleName}. Переключаемся на Cloudflare Worker...")
//                        val selectedIp = normalizedEndpointHost()
//                        val selectedPort = normalizedPort().toString()
//            
//                        // 🟢 ИСПРАВЛЕНИЕ: Используем явный указатель на класс `this@MainActivity` вместо `this`
//                        fetchKeysFromWorkerProxy(this@MainActivity, selectedIp, selectedPort)
                    } 
                }
            }
            return
        }
        
//        // Если регистрация уже была создана ранее, перед стартом обновляем в ней IP/Порт из полей ввода
//        val selectedIp = normalizedEndpointHost()
//        val selectedPort = normalizedPort().toString()
//        if (configFile.exists()) {
//            saveFinalConfig(configFile.readText(), selectedIp, selectedPort)
//        }
        
        requestVpnAndStart()
    }
*/



    private fun requestVpnAndStart() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null && !vpnGranted) { startActivityForResult(prepareIntent, REQ_VPN); return }
        vpnGranted = true
        startTunnelNow()
    }

    private fun startTunnelNow() {
        val sni = sniInput.text?.toString().orEmpty().ifBlank { "apteka.ru" }
        val endpoint = "${normalizedEndpointHost()}:${normalizedPort()}"
        val splitMode = splitModeSwitch.isChecked
        val allowedApps = if (splitMode) selectedPackagesForVpn() else arrayListOf()
        log(if (splitMode) "Запуск раздельного VPN: ${allowedApps.size} прил. · $endpoint" else "Запуск глобального VPN: $endpoint")
        resetSpeedMeter()
        vpnRunning = true
        refreshState("Запуск")
        val intent = Intent(this, UsqueVpnService::class.java)
            .putExtra("configPath", configFile.absolutePath)
            .putExtra("sni", sni)
            .putExtra("endpoint", endpoint)
            .putExtra("splitMode", splitMode)
            .putStringArrayListExtra("allowedApps", allowedApps)
        startService(intent)
        log("Служба VPN успешно запущена")
        refreshState(if (splitMode) "Раздельный режим" else "Глобальный режим")
    }

    private fun disconnectVpn() {
        log("Остановка службы VPN...")
        vpnRunning = false
        refreshState("Остановка")
        resetSpeedMeter()
        UsqueVpnService.stopActiveTunnel()
        runCatching { startService(Intent(this, UsqueVpnService::class.java).setAction(UsqueVpnService.ACTION_STOP)) }
        handler.postDelayed({ 
            runCatching { stopService(Intent(this, UsqueVpnService::class.java)) }
            onTunnelStopped("Остановлено") 
        }, 500)
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
        else if (requestCode == REQ_VPN) toast("Доступ к VPN не разрешен в системе")
    }

/*
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

                val responseText = connection.inputStream.bufferedReader(java.nio.charset.StandardCharsets.UTF_8).use { it.readText() }

                if (responseText.contains("{")) {
                    val jsonStart = responseText.indexOf("{")
                    val jsonEnd = responseText.lastIndexOf("}") + 1
                    val rawJson = responseText.substring(jsonStart, jsonEnd)
                    
                    saveFinalConfig(rawJson, userIp, userPort)

                    (context as Activity).runOnUiThread {
                        Toast.makeText(context, "Регистрация успешна! Запуск...", Toast.LENGTH_SHORT).show()
                        requestVpnAndStart()
                    }
                } else {
                    (context as Activity).runOnUiThread {
                        Toast.makeText(context, "Ошибка: Неверный ответ прокси", Toast.LENGTH_SHORT).show()
                        this@MainActivity.refreshState("Ошибка данных")
                    }
                }
            } catch (e: Exception) {
                (context as Activity).runOnUiThread {
                    android.util.Log.e("USQUE_REG", "Ошибка автоматической регистрации: ${e.message}")
                    Toast.makeText(context, "Ошибка сети при регистрации", Toast.LENGTH_SHORT).show()
                    this@MainActivity.refreshState("Ошибка сети")
                }
            }
        }.start()
    }
*/

/*
    fun saveFinalConfig(serverResponseJson: String, selectedIp: String, selectedPort: String, selectedSni: String) {
        try {
            val cloudflareData = JSONObject(serverResponseJson)
            
            val rawIpv4 = cloudflareData.getString("client_ipv4")
            val cleanIpv4 = if (rawIpv4.contains("/")) rawIpv4.substringBefore("/") else rawIpv4

            val rawIpv6 = cloudflareData.getString("client_ipv6")
            val cleanIpv6 = if (rawIpv6.contains("/")) rawIpv6.substringBefore("/") else rawIpv6

            val ipv4Array = JSONArray().apply { put(cleanIpv4.trim()) }
            val ipv6Array = JSONArray().apply { put(cleanIpv6.trim()) }

            val finalConfig = JSONObject().apply {
                put("private_key", cloudflareData.getString("privKey"))
                put("public_key", cloudflareData.getString("cloudflare_pub"))
                put("ipv4", ipv4Array)
                put("ipv6", ipv6Array)
                
                put("endpoint", selectedIp.trim().ifBlank { "162.159.198.2" })
                put("port", selectedPort.toIntOrNull()?.takeIf { it > 0 } ?: 443)
                put("sni", selectedSni.replace(Regex("^(https?://)?(www\\.)?"), "").substringBefore("/").ifBlank { "apteka.ru" })
            }
            
            configFile.writeText(finalConfig.toString(2))
            android.util.Log.d("USQUE_BUILD", "config.json для MASQUE успешно записан!")
        } catch (e: Exception) {
            android.util.Log.e("USQUE_BUILD", "Ошибка сборки конфига: ${e.message}")
        }
    }
*/
/*
    fun saveFinalConfig(serverResponseJson: String, selectedIp: String, selectedPort: String) {
        try {
            val cloudflareData = JSONObject(serverResponseJson)
            
            // Очищаем IP от масок подсетей (убираем /32 или /128, если они есть)
            val rawIpv4 = cloudflareData.optString("client_ipv4", "")
            val cleanIpv4 = if (rawIpv4.contains("/")) rawIpv4.substringBefore("/") else rawIpv4

            val rawIpv6 = cloudflareData.optString("client_ipv6", "")
            val cleanIpv6 = if (rawIpv6.contains("/")) rawIpv6.substringBefore("/") else rawIpv6

            // Формируем эндпоинты с портами (Go-код ожидает их в таком формате внутри endpoint_v4/v6)
            val port = selectedPort.toIntOrNull()?.takeIf { it > 0 } ?: 443
            val ipV4 = selectedIp.trim().ifBlank { "162.159.198.2" }
            val endpointV4WithPort = "$ipV4:$port"
            
            // Для IPv6 оборачиваем адрес в квадратные скобки, если это необходимо
            val ipV6 = "2606:4700:103::2" // Значение по умолчанию или из UI
            val endpointV6WithPort = if (ipV6.contains(":")) "[$ipV6]:$port" else "$ipV6:$port"

            val finalConfig = JSONObject().apply {
                // Соответствие маппингу json-тегов из Go-структуры:
                put("private_key", cloudflareData.optString("privKey", ""))
                put("endpoint_v4", endpointV4WithPort)
                put("endpoint_v6", endpointV6WithPort)
                put("endpoint_h2_v4", endpointV4WithPort) // Дублируем для HTTP/2 режима, если нужно
                put("endpoint_h2_v6", endpointV6WithPort)
                put("endpoint_pub_key", cloudflareData.optString("cloudflare_pub", ""))
                put("id", cloudflareData.optString("id", ""))
                put("access_token", cloudflareData.optString("access_token", ""))
                put("ipv4", cleanIpv4.trim()) // Передаем как СТРОКУ, а не JSONArray
                put("ipv6", cleanIpv6.trim()) // Передаем как СТРОКУ, а не JSONArray
            }
            
            configFile.writeText(finalConfig.toString(2))
            android.util.Log.d("USQUE_BUILD", "config.json для usque-go успешно записан!")
        } catch (e: Exception) {
            android.util.Log.e("USQUE_BUILD", "Ошибка сборки конфига: ${e.message}")
        }
    }
*/

/*
    fun saveFinalConfig(serverResponseJson: String, selectedIp: String, selectedPort: String) {
        try {
            val cloudflareData = JSONObject(serverResponseJson)
            
            // 1. Извлекаем чистые IP-адреса интерфейса
            val rawIpv4 = cloudflareData.optString("client_ipv4", "172.16.0.2")
            val cleanIpv4 = if (rawIpv4.contains("/")) rawIpv4.substringBefore("/") else rawIpv4

            val rawIpv6 = cloudflareData.optString("client_ipv6", "")
            val cleanIpv6 = if (rawIpv6.contains("/")) rawIpv6.substringBefore("/") else rawIpv6

            // 2. Сборка эндпоинтов с выбранными пользователем в UI IP и Портом
            val port = selectedPort.toIntOrNull()?.takeIf { it > 0 } ?: 443
            val ipV4 = selectedIp.trim().ifBlank { "162.159.198.2" }
            val endpointV4WithPort = "$ipV4:$port"
            
            val ipV6 = "2606:4700:103::2"
            val endpointV6WithPort = if (ipV6.contains(":")) "[$ipV6]:$port" else "$ipV6:$port"

            // 3. Формируем итоговый config.json строго по структуре вашего Go-движка
            val finalConfig = JSONObject().apply {
                put("private_key", cloudflareData.optString("privKey", ""))
                put("endpoint_v4", endpointV4WithPort)
                put("endpoint_v6", endpointV6WithPort)
                put("endpoint_h2_v4", endpointV4WithPort)
                put("endpoint_h2_v6", endpointV6WithPort)
                
                // Передаем публичный ключ сервера (PEM-блок из cloudflare_pub)
                put("endpoint_pub_key", cloudflareData.optString("cloudflare_pub", ""))
                
                // Читаем id и access_token напрямую из нового ответа Воркера
                put("id", cloudflareData.optString("id", ""))
                put("access_token", cloudflareData.optString("access_token", ""))
                
                put("ipv4", cleanIpv4.trim())
                put("ipv6", cleanIpv6.trim())
            }
            
            configFile.writeText(finalConfig.toString(2))
            android.util.Log.d("USQUE_BUILD", "config.json успешно собран и синхронизирован с Воркером!")
        } catch (e: Exception) {
            android.util.Log.e("USQUE_BUILD", "Ошибка сборки конфига: ${e.message}")
        }
    }
*/

//    // 🟢 ЭКСПОРТ: Собирает все файлы настроек в одну строку и копирует в буфер
    // 🟢 ЭКСПОРТ: Собирает все файлы настроек в чистый JSON и копирует в буфер
    fun exportAllConfigToClipboard() {
        try {
            val exportData = JSONObject()

            // 1. Читаем основной config.json (ключи MASQUE, IP, Порт, SNI)
            if (configFile.exists()) {
                exportData.put("config", JSONObject(configFile.readText()))
            }

            // 2. Читаем сохраненные профили (схемы переключения)
            // Имя файла "profiles.json" может отличаться, проверьте как оно названо в вашем коде
            val profilesFile = File(filesDir, "profiles.json") 
            if (profilesFile.exists()) {
                exportData.put("profiles", JSONObject(profilesFile.readText()))
            }

//            // 3. Переводим в Base64, чтобы ТСПУ или мессенджеры не ломали структуру кавычек
//            val rawBytes = exportData.toString().toByteArray(java.nio.charset.StandardCharsets.UTF_8)
//            val base64String = android.util.Base64.encodeToString(rawBytes, android.util.Base64.NO_WRAP)

            // 3. Переводим в обычную JSON-строку с красивыми отступами (2 пробела)
            // Если нужен компактный вид в одну строку, используйте просто exportData.toString()
            val jsonString = exportData.toString(2)

            // 4. Копируем в буфер обмена Android
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
//            val clip = ClipData.newPlainText("Usque Config", base64String)
            val clip = ClipData.newPlainText("Usque Config", jsonString)
            clipboard.setPrimaryClip(clip)

            toast("Конфигурация скопирована в буфер обмена!")
        } catch (e: Exception) {
            toast("Ошибка экспорта: ${e.message}")
        }
    }

//    // 🟢 ИМПОРТ: Читает строку из буфера, распаковывает и восстанавливает файлы
    // 🟢 ИМПОРТ: Читает чистый JSON из буфера и восстанавливает файлы настройки
    fun importAllConfigFromClipboard() {
        try {
            if (vpnRunning) {
                toast("Сначала отключите VPN!")
                return
            }

            // 1. Получаем текст из буфера обмена
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData == null || clipData.itemCount == 0) {
                toast("Буфер обмена пуст!")
                return
            }

//            val base64String = clipData.getItemAt(0).text.toString().trim()
            val rawJsonString = clipData.getItemAt(0).text.toString().trim()
            
/*
            // 2. Декодируем Base64 обратно в JSON
            val decodedBytes = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
            val decodedString = String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8)
            val importData = JSONObject(decodedString)
*/
            // 2. Сразу парсим строку как JSON (без декодирования Base64)
            val importData = JSONObject(rawJsonString)

            // 3. Восстанавливаем основной config.json
            if (importData.has("config")) {
                configFile.writeText(importData.getJSONObject("config").toString(2))
            }

            // 4. Восстанавливаем профили переключения
            if (importData.has("profiles")) {
                val profilesFile = File(filesDir, "profiles.json")
                profilesFile.writeText(importData.getJSONObject("profiles").toString(2))
            }

            // 5. Обновляем интерфейс приложения, чтобы новые данные отобразились на экране
            runOnUiThread {
                toast("Конфигурация успешно импортирована!")
                // Перерисовываем интерфейс (подгружаем новые SNI/IP в поля ввода)
                refreshState("Конфиг обновлен")
                // Вызываем встроенную китайскую функцию обновления полей, если она есть
                runCatching { saveInputs() } 
            }
        } catch (e: Exception) {
            toast("Ошибка импорта: Неверный формат данных")
        }
    }


}
