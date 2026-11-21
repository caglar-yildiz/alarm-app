package com.caglaryildiz.alarm

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

internal val midnight = 0xFF0B1021.toInt()
internal val mutedColor = 0xFFC0C4E1.toInt()
internal val accentColor = 0xFFC5BAFF.toInt()
internal val goldColor = 0xFFFFD98C.toInt()

abstract class AlarmScreen : Activity() {
    protected lateinit var panel: LinearLayout
    private var scroll: ScrollView? = null

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    protected fun layout(
        title: String? = null,
        footer: View? = null,
        resetScroll: Boolean = false,
        animate: Boolean = false
    ) {
        val previousScroll = if (resetScroll) 0 else scroll?.scrollY ?: 0
        panel = column()
        val container = ScrollView(this).apply {
            background = Starfield()
            isFillViewport = true
            clipToPadding = false
            addView(panel)
            setOnApplyWindowInsetsListener { _, insets ->
                applyScreenInsets(insets, footer != null)
                insets
            }
        }
        scroll = container
        if (footer == null) {
            setContentView(container)
        } else {
            val root = column().apply {
                setBackgroundColor(midnight)
                addView(container, LinearLayout.LayoutParams(-1, 0, 1f))
                addView(footer)
            }
            root.setOnApplyWindowInsetsListener { _, insets ->
                if (Build.VERSION.SDK_INT >= 35) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    footer.setPadding(bars.left, 0, bars.right, bars.bottom)
                }
                insets
            }
            setContentView(root)
        }
        container.requestApplyInsets()
        container.post { container.scrollTo(0, previousScroll) }
        if (animate) {
            panel.alpha = 0f
            panel.translationY = dp(14).toFloat()
            panel.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
        if (title == null) return
        label(title, 32f, bold = true).apply {
            accessibilityHeading()
            if (title == getString(R.string.app_name)) {
                setTextColor(accentColor)
                letterSpacing = .12f
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applyScreenInsets(insets: WindowInsets, hasFooter: Boolean) {
        if (Build.VERSION.SDK_INT >= 35) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            panel.setPadding(dp(20) + bars.left, dp(20) + bars.top, dp(20) + bars.right, dp(24) + if (hasFooter) 0 else bars.bottom)
        } else {
            panel.setPadding(dp(20), dp(20), dp(20), dp(24))
        }
    }

    protected fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    protected fun View.accessibilityHeading() {
        if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
    }

    protected fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    protected fun card() = column().apply {
        background = glass(0xDD242F58.toInt(), 0xD137204F.toInt(), 20)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        panel.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }

    protected fun glass(from: Int, to: Int, radius: Int) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(from, to)).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), 0x748C8CD8)
        }

    protected fun timeLabel(text: String, size: Float, parent: LinearLayout = panel, color: Int = Color.WHITE) =
        label(text, size, parent, color = color).apply {
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = .055f
        }

    protected fun styleInput(input: EditText) {
        input.background = glass(0xDA11192F.toInt(), 0xD6231C37.toInt(), 12)
        input.setPadding(dp(12), dp(9), dp(12), dp(9))
        input.minHeight = dp(48)
    }

    protected fun Switch.stylePlanetToggle() {
        val checkedStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        trackTintList = ColorStateList(checkedStates, intArrayOf(0xFF6969BD.toInt(), 0xFF494264.toInt()))
        val lit = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFFF4FFFF.toInt(), 0xFF91D7FF.toInt(), 0xFF7566EA.toInt())).apply {
            shape = GradientDrawable.OVAL
            setSize(dp(22), dp(22))
            setStroke(dp(1), 0xFFC5ECFF.toInt())
        }
        val dim = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFFD3CCE9.toInt(), 0xFF74658F.toInt())).apply {
            shape = GradientDrawable.OVAL
            setSize(dp(22), dp(22))
        }
        thumbDrawable = StateListDrawable().apply {
            addState(checkedStates[0], lit)
            addState(checkedStates[1], dim)
        }
    }

    protected fun label(
        text: String,
        size: Float = 16f,
        parent: LinearLayout = panel,
        bold: Boolean = false,
        color: Int = Color.WHITE
    ) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(0, dp(5), 0, dp(5))
        parent.addView(this)
    }

    protected fun button(
        text: String,
        parent: LinearLayout = panel,
        primary: Boolean = true,
        destructive: Boolean = false,
        action: () -> Unit
    ) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 16f
        val textColor = when {
            destructive -> 0xFFFFC4D1.toInt()
            primary -> midnight
            else -> accentColor
        }
        setTextColor(ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(mutedColor, textColor)
        ))
        background = RippleDrawable(
            ColorStateList.valueOf(0x35FFFFFF),
            when {
                destructive -> glass(0xE65E213E.toInt(), 0xE6461B36.toInt(), 18)
                primary -> glass(0xFF9BDFFF.toInt(), 0xFFC3A0FC.toInt(), 18)
                else -> glass(0xA6253152.toInt(), 0xA42F2149.toInt(), 18)
            },
            null
        )
        minimumHeight = dp(48)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setOnClickListener { action() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    /** A round, softly lit badge that holds a line icon. */
    protected fun iconBadge(
        icon: Int,
        parent: LinearLayout,
        size: Int = 52,
        from: Int = 0xFF5B4BC4.toInt(),
        to: Int = 0xFF2B3C86.toInt(),
        tint: Int = Color.WHITE
    ) = android.widget.ImageView(this).apply {
        setImageResource(icon)
        setColorFilter(tint)
        val inset = dp(size) / 4
        setPadding(inset, inset, inset, inset)
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(from, to)).apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(1), 0x66FFFFFF)
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        parent.addView(this, LinearLayout.LayoutParams(dp(size), dp(size)))
    }

    /** A small rounded tag, e.g. a status or a Premium marker. */
    protected fun pill(
        text: String,
        parent: LinearLayout,
        color: Int = 0xFFE7DFFF.toInt(),
        fill: Int = 0x604E407B,
        stroke: Int = 0x668C8CD8
    ) = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(color)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setSingleLine()
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(10), dp(4), dp(10), dp(4))
        background = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), stroke)
        }
        parent.addView(this, LinearLayout.LayoutParams(-2, -2))
    }

    protected fun premiumPill(parent: LinearLayout) =
        pill(getString(R.string.premium_badge), parent, goldColor, 0x40FFD98C, 0x99FFD98C.toInt())

    /** A digits-only box for one part of a typed time or date. */
    private fun digitField(hint: String, length: Int, value: String) = EditText(this).apply {
        this.hint = hint
        setText(value)
        inputType = InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(android.text.InputFilter.LengthFilter(length))
        gravity = Gravity.CENTER
        textSize = 26f
        setTextColor(Color.WHITE)
        setHintTextColor(0x80C0C4E1.toInt())
        setSelectAllOnFocus(true)
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
        styleInput(this)
    }

    /** Moves the cursor on to [next] once [field] holds all its digits, so typing "0730" fills both boxes. */
    private fun advance(field: EditText, length: Int, next: EditText) {
        field.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                if (field.hasFocus() && (s?.length ?: 0) >= length) next.requestFocus()
            }
        })
    }

    /** A dialog holding [fields]; OK runs [submit], which returns an error message or null when done. */
    private fun typedDialog(title: String, fields: List<View>, focus: EditText, last: EditText, submit: () -> String?) {
        val content = column().apply { setPadding(dp(24), dp(16), dp(24), 0) }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        content.addView(row, LinearLayout.LayoutParams(-1, -2))
        fields.forEach { row.addView(it) }
        val error = label("", 14f, content, color = 0xFFFFB4C4.toInt()).apply {
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val dialog = android.app.AlertDialog.Builder(this, R.style.AstralDialogTheme)
            .setTitle(title)
            .setView(content)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        fun trySubmit() {
            val message = submit()
            if (message == null) dialog.dismiss() else {
                error.text = message
                error.visibility = View.VISIBLE
            }
        }
        last.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        last.setOnEditorActionListener { _, _, _ -> trySubmit(); true }
        dialog.setOnShowListener {
            // Kept open on a mistake: the default OK button would close the dialog.
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener { trySubmit() }
            focus.requestFocus()
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
    }

    private fun separator(text: String) = TextView(this).apply {
        this.text = text
        textSize = 26f
        setTextColor(Color.WHITE)
        setPadding(dp(8), 0, dp(8), 0)
    }

    /** Type a time as hours and minutes instead of tapping it on the clock. */
    protected fun typeTime(initial: java.time.LocalTime, picked: (java.time.LocalTime) -> Unit) {
        val hour = digitField(getString(R.string.type_hint_hour), 2, "%02d".format(java.util.Locale.ROOT, initial.hour))
        val minute = digitField(getString(R.string.type_hint_minute), 2, "%02d".format(java.util.Locale.ROOT, initial.minute))
        advance(hour, 2, minute)
        val box = LinearLayout.LayoutParams(dp(88), -2)
        hour.layoutParams = box
        minute.layoutParams = LinearLayout.LayoutParams(dp(88), -2)
        hour.contentDescription = getString(R.string.type_hint_hour)
        minute.contentDescription = getString(R.string.type_hint_minute)
        typedDialog(getString(R.string.type_time_title), listOf(hour, separator(":"), minute), hour, minute) {
            val time = TypedInput.time(hour.text.toString(), minute.text.toString())
                ?: return@typedDialog getString(R.string.type_invalid_time)
            picked(time)
            null
        }
    }

    /** Type a date as day, month and year, in the order the app language writes them. */
    protected fun typeDate(initial: java.time.LocalDate, earliest: java.time.LocalDate, picked: (java.time.LocalDate) -> Unit) {
        val day = digitField(getString(R.string.type_hint_day), 2, "%02d".format(java.util.Locale.ROOT, initial.dayOfMonth))
        val month = digitField(getString(R.string.type_hint_month), 2, "%02d".format(java.util.Locale.ROOT, initial.monthValue))
        val year = digitField(getString(R.string.type_hint_year), 4, initial.year.toString())
        val order = TypedInput.dateOrder(appLocale()).map {
            when (it) {
                TypedInput.Field.DAY -> day
                TypedInput.Field.MONTH -> month
                TypedInput.Field.YEAR -> year
            }
        }
        day.contentDescription = getString(R.string.type_hint_day)
        month.contentDescription = getString(R.string.type_hint_month)
        year.contentDescription = getString(R.string.type_hint_year)
        order.forEachIndexed { index, field ->
            field.layoutParams = LinearLayout.LayoutParams(0, -2, if (field === year) 1.5f else 1f)
            order.getOrNull(index + 1)?.let { advance(field, if (field === year) 4 else 2, it) }
        }
        val pattern = java.time.format.DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            java.time.format.FormatStyle.SHORT, null, java.time.chrono.IsoChronology.INSTANCE, appLocale())
        val mark = pattern.firstOrNull { it in "./-" }?.toString() ?: "/"
        val views = listOf(order[0], separator(mark), order[1], separator(mark), order[2])
        typedDialog(getString(R.string.type_date_title), views, order[0], order[2]) {
            when (val result = TypedInput.date(day.text.toString(), month.text.toString(), year.text.toString(), earliest)) {
                is TypedInput.DateResult.Ok -> { picked(result.date); null }
                TypedInput.DateResult.TooEarly -> getString(R.string.type_date_past)
                TypedInput.DateResult.Invalid -> getString(R.string.type_invalid_date)
            }
        }
    }

    /** Icon, title and description in one row, used for feature lists. */
    protected fun featureRow(
        icon: Int,
        title: String,
        description: String,
        parent: LinearLayout,
        included: Boolean = false
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2))
        iconBadge(icon, row, 44, 0xFF4A3C9E.toInt(), 0xFF243567.toInt(), 0xFFE9E2FF.toInt())
        val text = column()
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14) })
        label(title, 16f, text, bold = true).setPadding(0, 0, 0, dp(2))
        label(description, 13.5f, text, color = mutedColor).setPadding(0, 0, 0, 0)
        if (included) {
            row.addView(android.widget.ImageView(this).apply {
                setImageResource(R.drawable.ic_check)
                setColorFilter(0xFF9BE8C0.toInt())
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginStart = dp(8) })
        }
        return row
    }
}

class MainActivity : WeatherScreen() {
    private enum class Tab(val titleRes: Int, val icon: Int) {
        ALARMS(R.string.tab_alarms, R.drawable.ic_beng_launcher),
        WEATHER(R.string.tab_weather, R.drawable.ic_weather),
        TOOLS(R.string.tab_tools, R.drawable.ic_time_tools),
        PREMIUM(R.string.tab_premium, R.drawable.ic_crown)
    }

    private enum class Tool(val titleRes: Int, val icon: Int) {
        STOPWATCH(R.string.tool_stopwatch, R.drawable.ic_stopwatch),
        COUNTDOWNS(R.string.tool_countdowns, R.drawable.ic_hourglass),
        WAKE(R.string.wake_check, R.drawable.ic_wake_check),
        BRIEFING(R.string.briefing_title, R.drawable.ic_briefing),
        STATS(R.string.tool_stats, R.drawable.ic_stats),
        BEDTIME(R.string.bedtime_title, R.drawable.ic_bedtime),
        LANGUAGE(R.string.tool_language, R.drawable.ic_language)
    }

    private var tab = Tab.ALARMS
    private var tool: Tool? = null
    private val timeTools by lazy { TimeTools(this) }
    private val toolHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshToolReadout = Runnable(::updateToolReadout)
    private var toolReadouts = emptyList<Pair<Countdown, TextView>>()
    private var stopwatchReadout: TextView? = null
    private var stopwatchTile: TextView? = null
    private var toolsVisible = false
    private val wake by lazy { WakeCheck(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tab = Tab.entries.firstOrNull { it.name == savedInstanceState?.getString("tab") } ?: Tab.ALARMS
        tool = Tool.entries.firstOrNull { it.name == savedInstanceState?.getString("tool") }
        if (savedInstanceState == null) applyNavigationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (applyNavigationIntent(intent)) render(animateTabChange = true)
    }

    private fun applyNavigationIntent(intent: Intent): Boolean {
        return when {
            intent.getBooleanExtra("wakeCheck", false) || intent.getBooleanExtra("wakeTab", false) -> {
                tab = Tab.TOOLS
                tool = Tool.WAKE
                true
            }
            intent.getBooleanExtra("timersTab", false) -> {
                tab = Tab.TOOLS
                tool = Tool.COUNTDOWNS
                true
            }
            else -> false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("tab", tab.name)
        outState.putString("tool", tool?.name)
        super.onSaveInstanceState(outState)
    }

    override fun onPremiumChanged() {
        if (tab == Tab.PREMIUM || tab == Tab.TOOLS) render()
    }

    override fun onWeatherChanged() {
        if (tab == Tab.WEATHER) render()
    }

    private fun navigation(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = false
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(0xFF161B34.toInt())
        for (item in Tab.entries) {
            val selected = item == tab
            val title = getString(item.titleRes)
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dp(72)
                setPadding(dp(4), dp(8), dp(4), dp(8))
                isSelected = selected
                isFocusable = true
                contentDescription = title
                if (selected) setBackgroundColor(0xFF29264C.toInt())
                addView(android.widget.ImageView(this@MainActivity).apply {
                    setImageResource(item.icon)
                    if (item != Tab.ALARMS) setColorFilter(when {
                        item == Tab.PREMIUM && selected -> goldColor
                        selected -> accentColor
                        else -> mutedColor
                    })
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(26), dp(26)))
                label(title, 12f, this, bold = selected, color = if (selected) accentColor else mutedColor).apply {
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setPadding(0, dp(6), 0, 0)
                    layoutParams = LinearLayout.LayoutParams(-1, -2)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                setOnClickListener {
                    if (tab != item || (item == Tab.TOOLS && tool != null)) {
                        tab = item
                        if (item == Tab.TOOLS) tool = null
                        render(animateTabChange = true)
                        if (tab == Tab.PREMIUM) refreshPremium()
                    }
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun openTool(entry: Tool) {
        if (entry == Tool.LANGUAGE) {
            pickLanguage()
            return
        }
        tab = Tab.TOOLS
        tool = entry
        render(animateTabChange = true)
    }

    private fun showTools() {
        when (tool) {
            null, Tool.LANGUAGE -> showToolGrid()
            Tool.STOPWATCH -> showStopwatch()
            Tool.COUNTDOWNS -> showCountdowns()
            Tool.WAKE -> showWakeCheck()
            Tool.STATS -> showStats()
            Tool.BRIEFING -> showBriefingSettings()
            Tool.BEDTIME -> showBedtime()
        }
    }

    /** Tools as a two-column grid: icon on top, name and live status below. */
    private fun showToolGrid() {
        label(getString(R.string.tools_subtitle), 15f, color = mutedColor)
        for (pair in Tool.entries.chunked(2)) {
            val row = LinearLayout(this).apply { isBaselineAligned = false }
            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            pair.forEachIndexed { index, entry -> toolTile(entry, row, first = index == 0) }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        }
    }

    private fun toolTile(entry: Tool, row: LinearLayout, first: Boolean) {
        val title = getString(entry.titleRes)
        val status = toolStatus(entry)
        val frame = android.widget.FrameLayout(this).apply {
            background = RippleDrawable(ColorStateList.valueOf(0x35FFFFFF),
                glass(0xDD242F58.toInt(), 0xD137204F.toInt(), 22), null)
            isClickable = true
            isFocusable = true
            minimumHeight = dp(168)
            contentDescription = "$title, $status"
            setOnClickListener { openTool(entry) }
        }
        row.addView(frame, LinearLayout.LayoutParams(0, -1, 1f).apply {
            if (first) marginEnd = dp(6) else marginStart = dp(6)
        })
        val content = column().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(22), dp(12), dp(16))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        frame.addView(content, android.widget.FrameLayout.LayoutParams(-1, -2))
        val (from, to) = when (entry) {
            Tool.STOPWATCH -> 0xFF3E7BD8.toInt() to 0xFF3B3594.toInt()
            Tool.COUNTDOWNS -> 0xFF7A55E0.toInt() to 0xFF3A2C86.toInt()
            Tool.WAKE -> 0xFFE0A14F.toInt() to 0xFF7A3F8F.toInt()
            Tool.STATS -> 0xFF4FB28A.toInt() to 0xFF2B4C86.toInt()
            Tool.BRIEFING -> 0xFFE8B54A.toInt() to 0xFFB0577A.toInt()
            Tool.BEDTIME -> 0xFF5B4BC4.toInt() to 0xFF1E2A5E.toInt()
            Tool.LANGUAGE -> 0xFF3AA6A0.toInt() to 0xFF2B4C86.toInt()
        }
        iconBadge(entry.icon, content, 60, from, to)
        label(title, 17f, content, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(2))
        }
        val subtitle = label(status, 13f, content, color = mutedColor).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, 0)
        }
        if (entry == Tool.STOPWATCH && timeTools.stopwatchRunning) stopwatchTile = subtitle
        if (entry == Tool.WAKE && !premiumAccess.unlocked) {
            val badgeHolder = LinearLayout(this).apply { gravity = Gravity.CENTER }
            content.addView(badgeHolder, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            premiumPill(badgeHolder)
        }
    }

    private fun toolStatus(entry: Tool): String = when (entry) {
        Tool.STOPWATCH -> {
            val elapsed = timeTools.stopwatchElapsed(android.os.SystemClock.elapsedRealtime())
            when {
                timeTools.stopwatchRunning -> formatTime(elapsed)
                elapsed > 0 -> getString(R.string.tool_status_paused, formatTime(elapsed))
                else -> getString(R.string.tool_stopwatch_desc)
            }
        }
        Tool.COUNTDOWNS -> {
            val timers = timeTools.timers()
            val running = timers.count { it.deadline != 0L }
            when {
                timers.isEmpty() -> getString(R.string.tool_countdowns_desc)
                running > 0 -> resources.getQuantityString(R.plurals.countdowns_running, running, running)
                else -> resources.getQuantityString(R.plurals.countdowns_saved, timers.size, timers.size)
            }
        }
        Tool.WAKE -> when {
            !premiumAccess.unlocked -> getString(R.string.wake_tile_locked)
            wake.enabled -> getString(R.string.wake_status_on, wake.delay)
            else -> getString(R.string.wake_status_off)
        }
        Tool.STATS -> WakeStats(this).summary().let {
            if (it.total == 0) getString(R.string.tool_stats_desc)
            else resources.getQuantityString(R.plurals.stats_streak_status, it.streak, it.streak)
        }
        Tool.BRIEFING -> getString(if (MorningBriefing(this).enabled) R.string.briefing_status_on else R.string.briefing_desc)
        Tool.BEDTIME -> Bedtime(this).let { bedtime ->
            if (!bedtime.enabled) getString(R.string.bedtime_desc)
            else getString(R.string.bedtime_status_on, sleepDuration(this, bedtime.goal))
        }
        Tool.LANGUAGE -> AppLanguage.displayName(this, AppLanguage.current(this))
    }

    private fun showBriefingSettings() {
        val briefing = MorningBriefing(this)
        val hero = card()
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        hero.addView(top, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        iconBadge(R.drawable.ic_briefing, top, 60, 0xFFE8B54A.toInt(), 0xFFB0577A.toInt())
        label(getString(R.string.briefing_headline), 19f, top, bold = true).layoutParams =
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14) }
        label(getString(R.string.briefing_description), 15f, hero, color = mutedColor).setPadding(0, dp(12), 0, dp(4))

        val settings = card()
        fun toggle(title: String, checked: Boolean, locked: Boolean, change: (Boolean) -> Unit) {
            settings.addView(Switch(this).apply {
                text = if (locked) "$title  ✦" else title
                textSize = 17f
                setTextColor(Color.WHITE)
                if (locked) contentDescription = getString(R.string.premium_feature_description, title)
                stylePlanetToggle()
                minHeight = dp(52)
                isChecked = checked && !locked
                setOnCheckedChangeListener { _, enabled ->
                    if (locked) {
                        tab = Tab.PREMIUM
                        render(animateTabChange = true)
                        refreshPremium()
                    } else {
                        change(enabled)
                        render()
                    }
                }
            })
        }
        toggle(getString(R.string.briefing_show), briefing.enabled, locked = false) { briefing.enabled = it }
        toggle(getString(R.string.briefing_speak), briefing.speak, locked = !premiumAccess.unlocked) { briefing.speak = it }
        label(getString(R.string.briefing_speak_hint), 13f, settings, color = mutedColor)
        button(getString(R.string.briefing_preview), settings, primary = false) {
            startActivity(Intent(this, BriefingActivity::class.java))
        }
    }

    private fun showBedtime() {
        val bedtime = Bedtime(this)
        val hero = card()
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        hero.addView(top, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        iconBadge(R.drawable.ic_bedtime, top, 60, 0xFF5B4BC4.toInt(), 0xFF1E2A5E.toInt())
        label(getString(R.string.bedtime_headline), 19f, top, bold = true).layoutParams =
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14) }
        label(getString(R.string.bedtime_description), 15f, hero, color = mutedColor).setPadding(0, dp(12), 0, dp(4))
        sleepNowLabel(hero)

        val settings = card()
        settings.addView(Switch(this).apply {
            text = getString(R.string.bedtime_reminder)
            textSize = 17f
            setTextColor(Color.WHITE)
            stylePlanetToggle()
            minHeight = dp(52)
            isChecked = bedtime.enabled
            setOnCheckedChangeListener { _, enabled ->
                bedtime.enabled = enabled
                if (enabled && !getSystemService(NotificationManager::class.java).areNotificationsEnabled()) requestNotifications()
                render()
            }
        })
        fun choices(title: String, values: List<Int>, selected: Int, text: (Int) -> String, pick: (Int) -> Unit) {
            label(title, 15f, settings, color = mutedColor).setPadding(0, dp(10), 0, 0)
            values.chunked(3).forEach { chunk ->
                val row = LinearLayout(this).apply { isBaselineAligned = false }
                settings.addView(row)
                chunk.forEachIndexed { index, value ->
                    button(text(value), row, primary = value == selected) {
                        pick(value)
                        render()
                    }.layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                        topMargin = dp(8)
                        if (index > 0) marginStart = dp(6)
                    }
                }
                repeat(3 - chunk.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = dp(6) }) }
            }
        }
        choices(getString(R.string.bedtime_goal), Bedtime.GOALS, bedtime.goal, { sleepDuration(this, it) }) { bedtime.goal = it }
        choices(getString(R.string.bedtime_lead), Bedtime.LEADS, bedtime.lead,
            { getString(R.string.minutes_short, it) }) { bedtime.lead = it }
        if (bedtime.enabled) {
            val next = bedtime.nextReminder
            label(if (next > 0) getString(R.string.bedtime_next,
                Instant.ofEpochMilli(next).atZone(java.time.ZoneId.systemDefault()).format(nextAlarmFormat()))
                else getString(R.string.bedtime_none), 14f, settings, color = mutedColor).setPadding(0, dp(12), 0, 0)
            if (!getSystemService(NotificationManager::class.java).areNotificationsEnabled()) showMissingPermissions()
        }
    }

    /** "Sleep now and you get 7 h 40 min" when the next real alarm is within a night's reach. */
    private fun sleepNowLabel(parent: LinearLayout) {
        val next = Bedtime(this).nextAlarm() ?: return
        val minutes = ((next - System.currentTimeMillis()) / 60_000L).toInt()
        if (minutes !in 60..16 * 60) return
        label(getString(R.string.bedtime_sleep_now, sleepDuration(this, minutes)), 14f, parent, color = 0xFFC9C3F0.toInt())
    }

    private fun showStats() {
        val stats = WakeStats(this).summary()
        val hero = card()
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        hero.addView(top, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        iconBadge(R.drawable.ic_stats, top, 60, 0xFF4FB28A.toInt(), 0xFF2B4C86.toInt())
        val titles = column()
        top.addView(titles, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14) })
        label(getString(R.string.stats_streak_title), 15f, titles, color = mutedColor)
        timeLabel(resources.getQuantityString(R.plurals.stats_days, stats.streak, stats.streak), 34f, titles)
        if (stats.bestStreak > 0) {
            label(getString(R.string.stats_best,
                resources.getQuantityString(R.plurals.stats_days, stats.bestStreak, stats.bestStreak)), 14f, titles, color = mutedColor)
        }
        if (stats.total == 0) {
            label(getString(R.string.stats_empty), 15f, hero, color = mutedColor)
            label(getString(R.string.stats_how), 13f, hero, color = mutedColor)
            return
        }

        val week = card()
        label(getString(R.string.stats_week), 17f, week, bold = true).accessibilityHeading()
        val row = LinearLayout(this).apply { isBaselineAligned = false }
        week.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        val locale = resources.configuration.locales[0]
        for ((date, result) in stats.week) {
            val (mark, fill, meaning) = when (result) {
                DayResult.ON_TIME -> Triple("✓", 0xFF3A8A64.toInt(), R.string.stats_on_time)
                DayResult.LATE -> Triple("✕", 0xFF8A3A55.toInt(), R.string.stats_late)
                DayResult.NONE -> Triple("–", 0x40FFFFFF, R.string.stats_no_alarm)
            }
            val cell = column().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                isFocusable = true
                contentDescription = "${date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)}: ${getString(meaning)}"
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
            row.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            label(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale), 12f, cell, color = mutedColor).apply {
                gravity = Gravity.CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            cell.addView(TextView(this).apply {
                text = mark
                gravity = Gravity.CENTER
                textSize = 15f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(fill) }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(32), dp(32)))
        }
        label("✓ ${getString(R.string.stats_on_time)}  ·  ✕ ${getString(R.string.stats_late)}  ·  – ${getString(R.string.stats_no_alarm)}",
            12f, week, color = mutedColor).setPadding(0, dp(10), 0, 0)

        val month = card()
        label(getString(R.string.stats_month), 17f, month, bold = true).accessibilityHeading()
        label(getString(R.string.stats_on_time_rate, stats.onTimeDays, stats.alarmDays), 15f, month)
        label(resources.getQuantityString(R.plurals.stats_snoozes, stats.snoozes, stats.snoozes), 15f, month)
        stats.averageSeconds?.let { seconds ->
            label(getString(R.string.stats_average, "%d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)), 15f, month)
        }
        label(getString(R.string.stats_how), 13f, month, color = mutedColor)
        label(getString(R.string.stats_private), 13f, month, color = mutedColor)
    }

    private fun pickLanguage() {
        val tags = AppLanguage.supported
        val current = tags.indexOf(AppLanguage.current(this)).coerceAtLeast(0)
        android.app.AlertDialog.Builder(this, R.style.AstralDialogTheme)
            .setTitle(getString(R.string.tool_language))
            .setSingleChoiceItems(tags.map { AppLanguage.displayName(this, it) }.toTypedArray(), current) { dialog, index ->
                dialog.dismiss()
                if (index != current) AppLanguage.set(this, tags[index])
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showStopwatch() {
        val controls = card()
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER }
        controls.addView(header, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        iconBadge(R.drawable.ic_stopwatch, header, 56, 0xFF3E7BD8.toInt(), 0xFF3B3594.toInt())
        stopwatchReadout = timeLabel("", 44f, controls).apply { gravity = Gravity.CENTER }
        button(getString(if (timeTools.stopwatchRunning) R.string.pause else R.string.start), controls) {
            timeTools.toggleStopwatch()
            render()
        }
        button(getString(R.string.reset), controls, primary = false) {
            timeTools.resetStopwatch()
            render()
        }
    }

    private fun showCountdowns() {
        timeTools.timers().filter { it.deadline != 0L }.forEach { timeTools.finishIfDue(it.id) }
        button(getString(R.string.countdown_add_button), action = ::addCountdown)
        val timers = timeTools.timers()
        if (timers.isEmpty()) label(getString(R.string.countdown_empty), parent = card(), color = mutedColor)
        toolReadouts = timers.map { timer ->
            val item = card()
            val status = getString(when {
                timer.deadline != 0L -> R.string.countdown_running
                timer.remaining == 0L -> R.string.countdown_done
                timer.remaining < timer.duration -> R.string.countdown_paused
                else -> R.string.countdown_ready
            })
            label("${formatTime(timer.duration)} · $status", 16f, item, bold = true)
            val readout = timeLabel("", 36f, item).apply { gravity = Gravity.CENTER }
            val action = getString(when {
                timer.deadline != 0L -> R.string.pause
                timer.remaining == 0L -> R.string.restart
                timer.remaining < timer.duration -> R.string.resume
                else -> R.string.start
            })
            button(action, item) {
                timeTools.toggle(timer.id)
                render()
            }
            val actions = LinearLayout(this).apply { isBaselineAligned = false }
            item.addView(actions)
            val reset = button(getString(R.string.reset), actions, primary = false) {
                timeTools.reset(timer.id)
                render()
            }
            val delete = button(getString(R.string.delete), actions, primary = false, destructive = true) {
                timeTools.remove(timer.id)
                render()
            }
            reset.layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                topMargin = dp(8)
                marginEnd = dp(8)
            }
            delete.layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(8) }
            timer to readout
        }
        if (timeTools.notificationsBlocked()) {
            val settings = card()
            label(getString(R.string.countdown_notifications_hint), 14f, settings, color = mutedColor)
            button(getString(R.string.notification_settings), settings, primary = false) {
                startActivity(timeTools.notificationSettings())
            }
        }
    }

    private fun addCountdown() {
        val fields = column().apply { setPadding(dp(20), dp(8), dp(20), 0) }
        fun timeField(name: String) = EditText(this).apply {
            hint = name
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
            styleInput(this)
            fields.addView(this)
        }
        val hours = timeField(getString(R.string.countdown_hours))
        val minutes = timeField(getString(R.string.countdown_minutes))
        val seconds = timeField(getString(R.string.countdown_seconds))
        val dialog = android.app.AlertDialog.Builder(this, R.style.AstralDialogTheme)
            .setTitle(getString(R.string.countdown_add))
            .setView(fields)
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.start), null)
            .create()
        dialog.show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            fun read(field: EditText, max: Int): Int? {
                val value = field.text.toString().let { if (it.isBlank()) 0 else it.toIntOrNull() }
                if (value == null || value !in 0..max) {
                    field.error = getString(R.string.countdown_range_error, max)
                    return null
                }
                return value
            }
            val h = read(hours, 99) ?: return@setOnClickListener
            val m = read(minutes, 59) ?: return@setOnClickListener
            val sec = read(seconds, 59) ?: return@setOnClickListener
            val duration = (h * 3600L + m * 60 + sec) * 1000
            if (duration == 0L) {
                seconds.error = getString(R.string.countdown_min_error)
                return@setOnClickListener
            }
            timeTools.add(duration)
            dialog.dismiss()
            render()
        }
    }

    private fun updateToolReadout() {
        val now = android.os.SystemClock.elapsedRealtime()
        stopwatchReadout?.text = formatTime(timeTools.stopwatchElapsed(now), tenths = true)
        stopwatchTile?.text = formatTime(timeTools.stopwatchElapsed(now))
        var running = (stopwatchReadout != null || stopwatchTile != null) && timeTools.stopwatchRunning
        for ((timer, readout) in toolReadouts) {
            val remaining = timer.remainingAt(now)
            if (timer.deadline != 0L && remaining == 0L) {
                timeTools.finishIfDue(timer.id)
                render()
                return
            }
            readout.text = formatTime((remaining + 999) / 1000 * 1000)
            running = running || timer.deadline != 0L
        }
        if (toolsVisible && running) {
            val delay = if (stopwatchReadout != null) 100L else 250L
            toolHandler.postDelayed(refreshToolReadout, delay)
        }
    }

    /** Pending "are you awake?" confirmations; shown wherever the user lands first. */
    private fun showWakeConfirmations(): Boolean {
        if (!premiumAccess.unlocked) return false
        val pending = wake.activeIds().mapNotNull { id -> alarms.all().firstOrNull { it.id == id } }
        if (pending.isEmpty()) return false
        val prompt = card().apply {
            background = glass(0xEE6B4A2A.toInt(), 0xEE4A2A5E.toInt(), 20)
        }
        label(getString(R.string.wake_prompt_title), 20f, prompt, bold = true).accessibilityHeading()
        label(getString(R.string.wake_prompt_text), 14f, prompt, color = 0xFFF1E6D6.toInt())
        for (alarm in pending) {
            button(getString(R.string.wake_confirm_for, alarm.label.ifBlank { alarm.time.format(clockFormat) }), prompt) {
                wake.confirm(alarm.id)
                render()
            }
        }
        return true
    }

    private fun showWakeCheck() {
        val hero = card()
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        hero.addView(top, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        iconBadge(R.drawable.ic_wake_check, top, 60, 0xFFE0A14F.toInt(), 0xFF7A3F8F.toInt())
        val titles = column()
        top.addView(titles, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14) })
        label(getString(R.string.wake_headline), 19f, titles, bold = true)
        val badges = LinearLayout(this)
        titles.addView(badges)
        if (!premiumAccess.unlocked) premiumPill(badges)
        else if (wake.enabled) pill(getString(R.string.wake_status_on, wake.delay), badges, 0xFF9BE8C0.toInt(), 0x403A8A64, 0x889BE8C0.toInt())
        else pill(getString(R.string.wake_status_off), badges)
        label(getString(R.string.wake_description), 15f, hero, color = mutedColor).setPadding(0, dp(12), 0, dp(4))

        showWakeConfirmations()

        val how = card()
        label(getString(R.string.wake_how_title), 17f, how, bold = true).accessibilityHeading()
        val steps = listOf(
            getString(R.string.wake_step_1),
            getString(R.string.wake_step_2, wake.delay),
            getString(R.string.wake_step_3)
        )
        steps.forEachIndexed { index, text ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            how.addView(row)
            row.addView(TextView(this).apply {
                this.text = (index + 1).toString()
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(midnight)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF9BDFFF.toInt(), 0xFFC3A0FC.toInt())).apply { shape = GradientDrawable.OVAL }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(28), dp(28)))
            label(text, 15f, row).layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) }
        }

        if (!premiumAccess.unlocked) {
            button(getString(R.string.premium_explore)) {
                tab = Tab.PREMIUM
                render(animateTabChange = true)
                refreshPremium()
            }
            return
        }
        val settings = card()
        settings.addView(Switch(this).apply {
            text = getString(R.string.wake_check)
            textSize = 17f
            setTextColor(Color.WHITE)
            contentDescription = getString(R.string.premium_feature_description, getString(R.string.wake_check))
            stylePlanetToggle()
            minHeight = dp(52)
            isChecked = wake.enabled
            setOnCheckedChangeListener { _, enabled ->
                wake.enabled = enabled
                render()
            }
        })
        label(getString(R.string.wake_delay), 15f, settings, color = mutedColor).setPadding(0, dp(10), 0, 0)
        val delays = LinearLayout(this).apply { isBaselineAligned = false }
        settings.addView(delays)
        listOf(1, 3, 5, 10).forEachIndexed { index, minutes ->
            button(getString(R.string.minutes_short, minutes), delays, primary = minutes == wake.delay) {
                wake.delay = minutes
                render()
            }.apply {
                contentDescription = resources.getQuantityString(R.plurals.minutes, minutes, minutes)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                    topMargin = dp(8)
                    if (index > 0) marginStart = dp(6)
                }
            }
        }
        if (wake.enabled && !wake.canPrompt()) {
            label(getString(R.string.wake_permissions_needed), 14f, settings, color = mutedColor)
            if (wake.isChannelBlocked()) {
                button(getString(R.string.wake_enable_notifications), settings, primary = false) {
                    startActivity(wake.channelSettingsIntent())
                }
            }
            showMissingPermissions()
        }
    }
    private val alarms by lazy { Alarms(this) }
    private val onRingChanged: () -> Unit = { render() }

    override fun onStart() {
        super.onStart()
        RingService.listeners.add(onRingChanged)
        toolsVisible = true
    }

    override fun onStop() {
        RingService.listeners.remove(onRingChanged)
        toolsVisible = false
        toolHandler.removeCallbacks(refreshToolReadout)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        alarms.syncAll()
        render()
        refreshPremium()
        // Only from the home screen, never on top of a ringing alarm.
        if (RingService.active == null) ReviewPrompt.maybeAsk(this)
    }

    private fun render(animateTabChange: Boolean = false) {
        toolHandler.removeCallbacks(refreshToolReadout)
        stopwatchReadout = null
        stopwatchTile = null
        toolReadouts = emptyList()
        layout(
            when (tab) {
                Tab.ALARMS -> getString(R.string.app_name)
                Tab.WEATHER -> getString(R.string.weather_title)
                Tab.TOOLS -> getString(tool?.takeIf { it != Tool.LANGUAGE }?.titleRes ?: tab.titleRes)
                else -> getString(tab.titleRes)
            },
            navigation(),
            resetScroll = animateTabChange,
            animate = animateTabChange
        )
        RingService.active?.let { ring ->
            val ringing = card()
            label(getString(R.string.alarm_ringing), 20f, ringing, bold = true).accessibilityHeading()
            if (ring.label.isNotBlank()) label(ring.label, parent = ringing)
            button(getString(R.string.open_ringing_alarm), ringing) {
                startActivity(Intent(this, RingActivity::class.java))
            }
        }
        when (tab) {
            Tab.PREMIUM -> { showPremium(); return }
            Tab.WEATHER -> { showWeather(); return }
            Tab.TOOLS -> {
                if (tool != null && tool != Tool.LANGUAGE) button(getString(R.string.back_to_tools), primary = false) {
                    tool = null
                    render(animateTabChange = true)
                }
                showTools()
                updateToolReadout()
                return
            }
            Tab.ALARMS -> Unit
        }
        showWakeConfirmations()
        val all = alarms.all().sortedBy { it.time }
        showVacation()
        showNextAlarm(all)
        button(getString(R.string.add_alarm)) { openEditor(null) }
        showMissingPermissions()
        if (all.isNotEmpty()) showReliability()
        label(getString(R.string.my_alarms), 22f, bold = true).apply {
            setPadding(0, dp(24), 0, dp(2))
            accessibilityHeading()
        }
        if (all.isEmpty()) label(getString(R.string.no_alarms), parent = card(), color = mutedColor)
        all.forEach(::showAlarm)
        if (alarms.pausedUntil == null && all.any { it.enabled && it.days.isNotEmpty() }) {
            button(getString(R.string.vacation_button), primary = false, action = ::pickVacationEnd)
        }
    }

    /** Vacation mode banner: repeating alarms are paused until a date. */
    private fun showVacation() {
        val until = alarms.pausedUntil ?: return
        val box = card().apply { background = glass(0xEE2A5A6B.toInt(), 0xEE2A3A5E.toInt(), 20) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        box.addView(top)
        iconBadge(R.drawable.ic_vacation, top, 40, 0xFF3AA6A0.toInt(), 0xFF2B4C86.toInt())
        label(getString(R.string.vacation_title), 18f, top, bold = true).apply {
            accessibilityHeading()
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) }
        }
        label(getString(R.string.vacation_active, until.format(dateFormat())), 14f, box, color = 0xFFE0F2F1.toInt())
        button(getString(R.string.vacation_end), box, primary = false) {
            alarms.pausedUntil = null
            render()
        }
    }

    private fun pickVacationEnd() {
        val today = java.time.LocalDate.now()
        val suggested = today.plusDays(7)
        android.app.DatePickerDialog(this, R.style.AstralDialogTheme, { _, year, month, day ->
            alarms.pausedUntil = java.time.LocalDate.of(year, month + 1, day)
            render()
        }, suggested.year, suggested.monthValue - 1, suggested.dayOfMonth).apply {
            setTitle(getString(R.string.vacation_pick))
            datePicker.minDate = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            setButton(android.app.AlertDialog.BUTTON_NEUTRAL, getString(R.string.type_date)) { _, _ ->
                typeDate(suggested, today) {
                    alarms.pausedUntil = it
                    render()
                }
            }
        }.show()
    }

    private fun showNextAlarm(all: List<Alarm>) {
        val now = ZonedDateTime.now()
        val next = alarms.nextRing(now)?.second
        val hero = card()
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        hero.addView(row)
        val details = column()
        row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        label(getString(R.string.next_alarm), 15f, details, color = mutedColor)
        val summary = when {
            next == null -> getString(R.string.no_alarm_planned)
            !alarms.canSchedule() -> getString(R.string.alarm_permission_needed)
            else -> Instant.ofEpochMilli(next).atZone(now.zone).format(nextAlarmFormat())
        }
        label(summary, 22f, details, bold = true)
        if (alarms.canSchedule()) sleepNowLabel(details)
        row.addView(AstralPlanet(this), LinearLayout.LayoutParams(dp(112), dp(96)).apply { marginStart = dp(4) })

        // Wake check stays visible from the home screen: status at a glance, one tap to its settings.
        val wakeOn = premiumAccess.unlocked && wake.enabled
        val chipRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            minimumHeight = dp(40)
            setOnClickListener { openTool(Tool.WAKE) }
        }
        hero.addView(chipRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        val chip = if (wakeOn) {
            pill(getString(R.string.wake_chip_on, wake.delay), chipRow, 0xFF9BE8C0.toInt(), 0x403A8A64, 0x889BE8C0.toInt())
        } else {
            pill(getString(R.string.wake_chip_off), chipRow, goldColor, 0x30FFD98C, 0x88FFD98C.toInt())
        }
        chip.setCompoundDrawablesRelativeWithIntrinsicBounds(
            getDrawable(R.drawable.ic_wake_check)?.mutate()?.apply {
                setTint(chip.currentTextColor)
                setBounds(0, 0, dp(16), dp(16))
            }, null, null, null)
        chip.compoundDrawablePadding = dp(6)
        chipRow.contentDescription = chip.text
    }

    private fun showMissingPermissions() {
        val notifications = getSystemService(NotificationManager::class.java)
        val exactAlarmMissing = !alarms.canSchedule()
        val notificationsMissing = !notifications.areNotificationsEnabled()
        val fullScreenMissing = Build.VERSION.SDK_INT >= 34 && !notifications.canUseFullScreenIntent()
        if (!exactAlarmMissing && !notificationsMissing && !fullScreenMissing) return

        val permissions = card()
        label(getString(R.string.missing_permissions), 18f, permissions, bold = true).apply { accessibilityHeading() }
        if (exactAlarmMissing) {
            label(getString(R.string.permission_exact_alarm), 14f, permissions, color = mutedColor)
            button(getString(R.string.permission_exact_alarm_button), permissions, primary = false) {
                openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            }
        }
        if (notificationsMissing) {
            label(getString(R.string.permission_notifications), 14f, permissions, color = mutedColor)
            button(getString(R.string.permission_notifications_button), permissions, primary = false) { requestNotifications() }
        }
        if (fullScreenMissing) {
            label(getString(R.string.permission_full_screen), 14f, permissions, color = mutedColor)
            button(getString(R.string.permission_full_screen_button), permissions, primary = false) {
                openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            }
        }
    }

    /** Battery and phone-maker settings that can silently stop alarms; hidden once the user has dealt with them. */
    private fun showReliability() {
        val reliability = Reliability(this)
        if (!reliability.needsAttention) return
        val box = card()
        label(getString(R.string.reliability_title), 18f, box, bold = true).accessibilityHeading()
        if (reliability.batteryRestricted) {
            label(getString(R.string.reliability_battery), 14f, box, color = mutedColor)
            button(getString(R.string.reliability_battery_button), box, primary = false) { reliability.openBatterySettings() }
        }
        reliability.brand?.let { brand ->
            label(getString(R.string.reliability_brand, brand.displayName), 14f, box, color = mutedColor)
            button(getString(R.string.reliability_brand_button, brand.displayName), box, primary = false) {
                reliability.openBrandSettings()
            }
        }
        button(getString(R.string.reliability_done), box) {
            reliability.dismissed = true
            render()
        }
    }

    private fun requestNotifications() {
        val preferences = getPreferences(MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            (!preferences.getBoolean("notificationRequested", false) ||
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            preferences.edit().putBoolean("notificationRequested", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) render()
    }

    private fun showAlarm(alarm: Alarm) {
        val time = alarm.time.format(clockFormat)
        val repeat = when {
            alarm.days.isEmpty() -> alarm.date?.format(dateFormat()) ?: getString(R.string.repeat_once)
            alarm.days.size == 7 -> getString(R.string.repeat_every_day)
            alarm.days == setOf(6, 7) -> getString(R.string.repeat_weekend)
            alarm.days == (1..5).toSet() -> getString(R.string.repeat_weekdays)
            else -> resources.getQuantityString(R.plurals.day_count, alarm.days.size, alarm.days.size)
        }
        val alarmCard = card().apply {
            setPadding(dp(14), dp(6), dp(14), dp(9))
            isFocusable = true
            setOnClickListener { openEditor(alarm) }
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        alarmCard.addView(row)
        timeLabel(time, 35f, row, color = if (alarm.enabled) Color.WHITE else mutedColor).apply {
            setPadding(0, 0, 0, 0)
            setSingleLine()
            if (alarm.label.isBlank()) layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        if (alarm.label.isNotBlank()) {
            label(alarm.label, 16f, row, bold = true, color = if (alarm.enabled) Color.WHITE else mutedColor).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginStart = dp(12)
                    marginEnd = dp(6)
                }
            }
        }
        val dateExpired = alarm.date != null && alarm.nextAfter(ZonedDateTime.now()) == null
        row.addView(Switch(this).apply {
            contentDescription = getString(R.string.alarm_switch_description, time)
            stylePlanetToggle()
            minWidth = dp(48)
            minHeight = dp(48)
            isChecked = alarm.enabled && !dateExpired
            setOnCheckedChangeListener { _, enabled ->
                if (enabled && dateExpired) {
                    render()
                    openEditor(alarm, enableOnSave = true)
                } else {
                    alarms.save(alarm.copy(enabled = enabled, snoozeAt = 0))
                    render()
                }
            }
        })
        val tags = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        alarmCard.addView(tags)
        addAlarmTag(tags, repeat, description = alarm.days.takeIf { it.isNotEmpty() }
            ?.sorted()?.joinToString(", ") { weekdayName(it) })
        if (alarm.task != AlarmTask.NONE) addAlarmTag(tags, getString(alarm.task.labelRes))
        showSkip(alarm, alarmCard)
        if (dateExpired && alarm.enabled) label(getString(R.string.alarm_date_passed), 13f, alarmCard, color = mutedColor)
        if (alarm.snoozeAt > 0) {
            button(getString(R.string.cancel_snooze), alarmCard, primary = false) {
                alarms.save(alarm.copy(snoozeAt = 0))
                render()
            }
        }
    }

    /** "Skip next" for repeating alarms, or the skipped date with an undo. */
    private fun showSkip(alarm: Alarm, parent: LinearLayout) {
        if (!alarm.enabled || alarm.days.isEmpty()) return
        val today = java.time.LocalDate.now()
        val skipped = alarm.skipDate?.takeIf { !it.isBefore(today) }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        if (skipped != null) {
            addAlarmTag(row, getString(R.string.skip_until, skipped.format(dateFormat())))
            actionTag(row, getString(R.string.skip_undo)) {
                alarms.save(alarm.copy(skipDate = null))
                render()
            }
        } else {
            val next = alarms.next(alarm) ?: return
            val date = Instant.ofEpochMilli(next).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            actionTag(row, getString(R.string.skip_next)) {
                alarms.save(alarm.copy(skipDate = date))
                render()
            }.contentDescription = getString(R.string.skip_next_description, date.format(dateFormat()))
        }
    }

    /** A tappable tag, styled like the alarm tags but in the accent color. */
    private fun actionTag(row: LinearLayout, text: String, action: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(accentColor)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER_VERTICAL
        minHeight = dp(40)
        setPadding(dp(10), 0, dp(10), 0)
        isClickable = true
        isFocusable = true
        background = RippleDrawable(ColorStateList.valueOf(0x35FFFFFF), GradientDrawable().apply {
            setColor(0x30A0D8FF)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), 0x669BDFFF)
        }, null)
        setOnClickListener { action() }
        row.addView(this, LinearLayout.LayoutParams(-2, -2).apply { if (row.childCount > 0) marginStart = dp(6) })
    }

    private fun addAlarmTag(
        row: LinearLayout,
        text: String,
        description: String? = null
    ) {
        val tag = TextView(this).apply {
            this.text = text
            if (description != null) contentDescription = description
            textSize = 12f
            setTextColor(0xFFE7DFFF.toInt())
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                setColor(0x604E407B)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), 0x668C8CD8)
            }
        }
        tag.maxWidth = (resources.displayMetrics.widthPixels - dp(78)).coerceAtLeast(0) / 2
        row.addView(tag, LinearLayout.LayoutParams(-2, -2).apply {
            if (row.childCount > 0) marginStart = dp(5)
        })
    }

    private fun openSettings(action: String) {
        startActivity(Intent(action, Uri.fromParts("package", packageName, null)))
    }

    private fun openEditor(alarm: Alarm?, enableOnSave: Boolean = false) {
        startActivity(Intent(this, AlarmEditorActivity::class.java)
            .putExtra("id", alarm?.id)
            .putExtra("enableOnSave", enableOnSave))
    }
}
