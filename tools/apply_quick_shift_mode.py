from pathlib import Path

ui_path = Path("app/src/main/java/it/alantamanti/portshifttracker/ui/PortShiftApp.kt")
build_path = Path("app/build.gradle.kts")
text = ui_path.read_text(encoding="utf-8")

old = ''') {
    val initialZone = ZoneId.of(initialShift?.zoneId ?: "Europe/Rome")
'''
new = ''') {
    val context = LocalContext.current
    val initialZone = ZoneId.of(initialShift?.zoneId ?: "Europe/Rome")
'''
if old not in text:
    raise SystemExit("editor start marker not found")
text = text.replace(old, new, 1)

old = '''    var showNotes by remember(initialShift?.id) { mutableStateOf(initialShift?.notes?.isNotBlank() == true) }
    var showBreakdown by remember(initialShift?.id) { mutableStateOf(false) }
    val calculator = remember { AllowanceCalculator() }
'''
new = '''    var showNotes by remember(initialShift?.id) { mutableStateOf(initialShift?.notes?.isNotBlank() == true) }
    var showBreakdown by remember(initialShift?.id) { mutableStateOf(false) }
    val uiPrefs = remember(context) { context.getSharedPreferences("portshift_ui", 0) }
    var quickMode by remember(initialShift?.id) { mutableStateOf(uiPrefs.getBoolean("quick_shift_mode", true)) }
    var quickKind by remember(initialShift?.id, initialSelectedIds, rules) {
        mutableStateOf(inferQuickShiftKind(initialSelectedIds, rules))
    }
    val editorDate = runCatching { LocalDateTime.parse(startText, editFormatter).toLocalDate() }.getOrDefault(initialDate)
    val effectiveQuickMode = quickMode && performanceType == PerformanceType.TURNO
    val calculator = remember { AllowanceCalculator() }
'''
if old not in text:
    raise SystemExit("editor state marker not found")
text = text.replace(old, new, 1)

text = text.replace(
    'italianTitle(initialDate.format(shortDayFormatter))',
    'italianTitle(editorDate.format(shortDayFormatter))',
    1
)

old = '''                    preview?.let { pay ->
                        item {
'''
new = '''                    item {
                        EntryModeSelector(
                            quickMode = effectiveQuickMode,
                            quickEnabled = performanceType == PerformanceType.TURNO,
                            onQuick = {
                                quickMode = true
                                uiPrefs.edit().putBoolean("quick_shift_mode", true).apply()
                                val inferred = inferQuickShiftKind(selectedIds, rules)
                                if (inferred != null) quickKind = inferred
                                quickKind?.let { kind ->
                                    selectedIds = applyQuickTurnSelection(selectedIds, rules, kind, editorDate)
                                }
                            },
                            onDetailed = {
                                quickMode = false
                                uiPrefs.edit().putBoolean("quick_shift_mode", false).apply()
                            }
                        )
                    }

                    if (effectiveQuickMode) {
                        item {
                            QuickShiftPanel(
                                date = editorDate,
                                rules = rules,
                                selectedKind = quickKind,
                                onKindSelected = { kind ->
                                    quickKind = kind
                                    selectedIds = applyQuickTurnSelection(selectedIds, rules, kind, editorDate)
                                }
                            )
                        }
                    }

                    preview?.let { pay ->
                        item {
'''
if old not in text:
    raise SystemExit("preview marker not found")
text = text.replace(old, new, 1)

# The editor already has LocalContext at function scope now.
text = text.replace('''                    item {
                        val context = LocalContext.current
                        val currentStart = runCatching { LocalDateTime.parse(startText, editFormatter) }.getOrDefault(initialStart)
''', '''                    item {
                        val currentStart = runCatching { LocalDateTime.parse(startText, editFormatter) }.getOrDefault(initialStart)
''', 1)

old = '''                                            startText = newStart.format(editFormatter)
                                            endText = newStart.plus(duration).format(editFormatter)
                                        },
'''
new = '''                                            startText = newStart.format(editFormatter)
                                            endText = newStart.plus(duration).format(editFormatter)
                                            if (quickMode && performanceType == PerformanceType.TURNO) {
                                                quickKind?.let { kind ->
                                                    selectedIds = applyQuickTurnSelection(
                                                        selectedIds,
                                                        rules,
                                                        kind,
                                                        newStart.toLocalDate()
                                                    )
                                                }
                                            }
                                        },
'''
if old not in text:
    raise SystemExit("date picker callback marker not found")
text = text.replace(old, new, 1)

old = '''                    categoriesForPerformance(performanceType).forEach { category ->
                        val categoryRules = manualRules.filter { it.category == category }
'''
new = '''                    categoriesForPerformance(performanceType)
                        .filterNot { effectiveQuickMode && it == AllowanceCategory.TURNO }
                        .forEach { category ->
                        val categoryRules = manualRules.filter { it.category == category }
'''
if old not in text:
    raise SystemExit("categories marker not found")
text = text.replace(old, new, 1)

# Version bump.
build = build_path.read_text(encoding="utf-8")
build = build.replace('versionCode = 11', 'versionCode = 12')
build = build.replace('versionName = "0.7.3"', 'versionName = "0.8.0"')
build_path.write_text(build, encoding="utf-8")

ui_path.write_text(text, encoding="utf-8")
print("Applied switchable five-shift quick mode v0.8.0")
