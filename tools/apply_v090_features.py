from pathlib import Path

ui_path = Path('app/src/main/java/it/alantamanti/portshifttracker/ui/PortShiftApp.kt')
text = ui_path.read_text(encoding='utf-8')


def replace_once(old: str, new: str, label: str):
    global text
    if old not in text:
        raise SystemExit(f'Missing patch anchor: {label}')
    text = text.replace(old, new, 1)

# Feature store import.
replace_once(
    'import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity\n',
    'import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity\nimport it.alantamanti.portshifttracker.data.local.AppFeatureStore\n',
    'feature store import'
)

# Compact shift codes in the monthly calendar.
text = text.replace('modifier = Modifier.weight(1f).height(38.dp)', 'modifier = Modifier.weight(1f).height(44.dp)')
replace_once(
'''        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            dayRows.take(3).forEach { row ->
                Box(
                    Modifier
                        .size(3.5.dp)
                        .clip(CircleShape)
                        .background(performanceColor(row.shift.performanceType))
                )
            }
        }
''',
'''        if (dayRows.isNotEmpty()) {
            Text(
                dayRows.take(2).joinToString(" ") { calendarShiftCode(it) },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
''',
    'calendar codes'
)

# Editor uses presets and editable calendar overrides.
replace_once(
    '    val uiPrefs = remember(context) { context.getSharedPreferences("portshift_ui", 0) }\n',
    '    val uiPrefs = remember(context) { context.getSharedPreferences("portshift_ui", 0) }\n    val featureStore = remember(context) { AppFeatureStore(context) }\n',
    'feature store editor'
)
replace_once(
    '    val editorDate = runCatching { LocalDateTime.parse(startText, editFormatter).toLocalDate() }.getOrDefault(initialDate)\n    val effectiveQuickMode = quickMode && performanceType == PerformanceType.TURNO\n',
    '    val editorDate = runCatching { LocalDateTime.parse(startText, editFormatter).toLocalDate() }.getOrDefault(initialDate)\n    val specialOverrideClass = featureStore.specialDay(editorDate)?.dayClass?.toPortDayClass()\n    val effectiveQuickMode = quickMode && performanceType == PerformanceType.TURNO\n',
    'special override editor'
)
replace_once(
'''    val suggestedCompanions = manualRules.filter { rule ->
        rule.id !in selectedIds && parseTags(rule.recommendedWithAnyTagCsv).any { it in selectedTags }
    }
''',
'''    val suggestedCompanions = manualRules.filter { rule ->
        rule.id !in selectedIds && parseTags(rule.recommendedWithAnyTagCsv).any { it in selectedTags }
    }
    val coherenceWarnings = consistencyWarnings(performanceType, rules.filter { it.id in selectedIds })
''',
    'coherence warnings'
)

text = text.replace(
    'selectedIds = applyQuickTurnSelection(selectedIds, rules, kind, editorDate)',
    'selectedIds = applyQuickTurnSelection(selectedIds, rules, kind, editorDate, specialOverrideClass)'
)
replace_once(
'''                            QuickShiftPanel(
                                date = editorDate,
                                rules = rules,
                                selectedKind = quickKind,
                                onKindSelected = { kind ->
                                    quickKind = kind
                                    selectedIds = applyQuickTurnSelection(selectedIds, rules, kind, editorDate, specialOverrideClass)
                                }
                            )
''',
'''                            QuickShiftPanel(
                                date = editorDate,
                                rules = rules,
                                selectedKind = quickKind,
                                overrideClass = specialOverrideClass,
                                onKindSelected = { kind ->
                                    quickKind = kind
                                    selectedIds = applyQuickTurnSelection(selectedIds, rules, kind, editorDate, specialOverrideClass)
                                }
                            )
''',
    'quick panel override'
)
replace_once(
'''                                                    selectedIds = applyQuickTurnSelection(
                                                        selectedIds,
                                                        rules,
                                                        kind,
                                                        newStart.toLocalDate()
                                                    )
''',
'''                                                    val newDate = newStart.toLocalDate()
                                                    val override = featureStore.specialDay(newDate)?.dayClass?.toPortDayClass()
                                                    selectedIds = applyQuickTurnSelection(
                                                        selectedIds,
                                                        rules,
                                                        kind,
                                                        newDate,
                                                        override
                                                    )
''',
    'date override update'
)

# Presets in editor, immediately before allowances.
replace_once(
'''                    item {
                        SectionHeader(
                            title = "3. Indennità",
''',
'''                    item {
                        PresetQuickBar(
                            rules = rules,
                            selectedIds = selectedIds,
                            role = role,
                            performanceType = performanceType,
                            onApply = { presetRole, ids ->
                                role = presetRole
                                selectedIds = ids
                            }
                        )
                    }

                    item {
                        SectionHeader(
                            title = "3. Indennità",
''',
    'preset quick bar'
)

replace_once(
'''                    relationWarnings.forEach { warning ->
                        item { WarningPanel(warning) }
                    }
''',
'''                    coherenceWarnings.forEach { warning ->
                        item { WarningPanel(warning) }
                    }
                    relationWarnings.forEach { warning ->
                        item { WarningPanel(warning) }
                    }
''',
    'warning display'
)

# Summary: rules are needed for category comparison; add payslip comparison and exports.
replace_once(
'''private fun SummaryScreen(repository: PortRepository) {
    val rows by repository.shiftRows.collectAsState(initial = emptyList())
    var month by remember { mutableStateOf(YearMonth.now()) }
''',
'''private fun SummaryScreen(repository: PortRepository) {
    val rows by repository.shiftRows.collectAsState(initial = emptyList())
    val rules by repository.rules.collectAsState(initial = emptyList())
    var month by remember { mutableStateOf(YearMonth.now()) }
''',
    'summary rules'
)
replace_once(
'''        item { SectionHeader("Giornate del mese") }
''',
'''        item { PayslipComparisonCard(month = month, rows = monthRows, rules = rules) }
        item { MonthlyExportCard(month = month, rows = monthRows) }

        item { SectionHeader("Giornate del mese") }
''',
    'summary features'
)

# Replace placeholder backup card with actual feature management.
replace_once(
'''        item {
            SettingsCard("Backup e dispositivi") {
                Text(
                    "Struttura predisposta per il futuro accesso Google, backup e autorizzazione dei dispositivi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Backup database") }
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Gestisci dispositivi") }
            }
        }
''',
'''        item { PresetSettingsCard() }
        item { SpecialCalendarSettingsCard() }
        item { BackupSettingsCard(repository) }
''',
    'settings feature cards'
)

ui_path.write_text(text, encoding='utf-8')

# Version bump.
build_path = Path('app/build.gradle.kts')
build = build_path.read_text(encoding='utf-8')
build = build.replace('versionCode = 12', 'versionCode = 13')
build = build.replace('versionName = "0.8.0"', 'versionName = "0.9.0"')
build_path.write_text(build, encoding='utf-8')

print('Applied PortShiftTracker v0.9.0 feature integration')
