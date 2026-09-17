from pathlib import Path

ui_path = Path("app/src/main/java/it/alantamanti/portshifttracker/ui/PortShiftApp.kt")
build_path = Path("app/build.gradle.kts")
text = ui_path.read_text(encoding="utf-8")

# Imports for native Android date/time pickers and Compose context.
if "import android.app.DatePickerDialog" not in text:
    text = text.replace(
        "package it.alantamanti.portshifttracker.ui\n\n",
        "package it.alantamanti.portshifttracker.ui\n\nimport android.app.DatePickerDialog\nimport android.app.TimePickerDialog\n"
    )
if "import androidx.compose.ui.platform.LocalContext" not in text:
    text = text.replace(
        "import androidx.compose.ui.graphics.Color\n",
        "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.platform.LocalContext\n"
    )

old = '''                    item {
                        EditorSectionCard(title = "2. Data, orario e mansione") {
                            Text(
                                "Formato data e ora: AAAA-MM-GG HH:MM",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = startText,
                                onValueChange = { startText = it },
                                label = { Text("Inizio") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = endText,
                                onValueChange = { endText = it },
                                label = { Text("Fine") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Durata rapida", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(4L, 6L, 8L).forEach { hours ->
                                    OutlinedButton(
                                        onClick = {
                                            runCatching { LocalDateTime.parse(startText, editFormatter) }
                                                .getOrNull()
                                                ?.let { endText = it.plusHours(hours).format(editFormatter) }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("+$hours h")
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = role,
                                onValueChange = { role = it },
                                label = { Text("Mansione") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(4.dp))
                            if (!showNotes) {
                                TextButton(onClick = { showNotes = true }) { Text("＋ Aggiungi note") }
                            } else {
                                OutlinedTextField(
                                    value = notes,
                                    onValueChange = { notes = it },
                                    label = { Text("Note") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2
                                )
                                TextButton(
                                    onClick = {
                                        notes = ""
                                        showNotes = false
                                    }
                                ) { Text("Rimuovi note") }
                            }
                        }
                    }
'''

new = '''                    item {
                        val context = LocalContext.current
                        val currentStart = runCatching { LocalDateTime.parse(startText, editFormatter) }.getOrDefault(initialStart)
                        val currentEnd = runCatching { LocalDateTime.parse(endText, editFormatter) }.getOrDefault(initialEnd)

                        EditorSectionCard(title = "2. Data e orario") {
                            PickerField(
                                label = "Data",
                                value = italianTitle(currentStart.toLocalDate().format(shortDayFormatter)),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    val duration = java.time.Duration.between(currentStart, currentEnd)
                                        .takeIf { !it.isNegative && !it.isZero }
                                        ?: java.time.Duration.ofHours(6)
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            val newStart = LocalDateTime.of(
                                                LocalDate.of(year, month + 1, day),
                                                currentStart.toLocalTime()
                                            )
                                            startText = newStart.format(editFormatter)
                                            endText = newStart.plus(duration).format(editFormatter)
                                        },
                                        currentStart.year,
                                        currentStart.monthValue - 1,
                                        currentStart.dayOfMonth
                                    ).show()
                                }
                            )

                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PickerField(
                                    label = "Ora inizio",
                                    value = currentStart.format(timeFormatter),
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        val duration = java.time.Duration.between(currentStart, currentEnd)
                                            .takeIf { !it.isNegative && !it.isZero }
                                            ?: java.time.Duration.ofHours(6)
                                        TimePickerDialog(
                                            context,
                                            { _, hour, minute ->
                                                val newStart = LocalDateTime.of(
                                                    currentStart.toLocalDate(),
                                                    LocalTime.of(hour, minute)
                                                )
                                                startText = newStart.format(editFormatter)
                                                endText = newStart.plus(duration).format(editFormatter)
                                            },
                                            currentStart.hour,
                                            currentStart.minute,
                                            true
                                        ).show()
                                    }
                                )
                                PickerField(
                                    label = "Ora fine",
                                    value = currentEnd.format(timeFormatter) + if (currentEnd.toLocalDate().isAfter(currentStart.toLocalDate())) " +1g" else "",
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        TimePickerDialog(
                                            context,
                                            { _, hour, minute ->
                                                var candidate = LocalDateTime.of(
                                                    currentStart.toLocalDate(),
                                                    LocalTime.of(hour, minute)
                                                )
                                                if (!candidate.isAfter(currentStart)) candidate = candidate.plusDays(1)
                                                endText = candidate.format(editFormatter)
                                            },
                                            currentEnd.hour,
                                            currentEnd.minute,
                                            true
                                        ).show()
                                    }
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Durata rapida", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    java.time.Duration.between(currentStart, currentEnd)
                                        .takeIf { !it.isNegative }
                                        ?.let { d -> "${d.toHours()}h ${d.toMinutesPart()}m" }
                                        ?: "—",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(4L, 6L, 8L).forEach { hours ->
                                    OutlinedButton(
                                        onClick = { endText = currentStart.plusHours(hours).format(editFormatter) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("$hours h")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        EditorSectionCard(title = "Mansione e note") {
                            OutlinedTextField(
                                value = role,
                                onValueChange = { role = it },
                                label = { Text("Mansione") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(4.dp))
                            if (!showNotes) {
                                TextButton(onClick = { showNotes = true }) { Text("＋ Aggiungi note") }
                            } else {
                                OutlinedTextField(
                                    value = notes,
                                    onValueChange = { notes = it },
                                    label = { Text("Note") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2
                                )
                                TextButton(
                                    onClick = {
                                        notes = ""
                                        showNotes = false
                                    }
                                ) { Text("Rimuovi note") }
                            }
                        }
                    }
'''

if old not in text:
    raise SystemExit("Old date/time editor block not found")
text = text.replace(old, new, 1)

helper_marker = '''@Composable
private fun EditorSectionCard(title: String, content: @Composable () -> Unit) {'''
helper = '''@Composable
private fun PickerField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier
            .heightIn(min = 62.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EditorSectionCard(title: String, content: @Composable () -> Unit) {'''
if "private fun PickerField(" not in text:
    if helper_marker not in text:
        raise SystemExit("EditorSectionCard marker not found")
    text = text.replace(helper_marker, helper, 1)

ui_path.write_text(text, encoding="utf-8")

build = build_path.read_text(encoding="utf-8")
build = build.replace('versionCode = 10', 'versionCode = 11')
build = build.replace('versionName = "0.7.2"', 'versionName = "0.7.3"')
build_path.write_text(build, encoding="utf-8")
print("Applied v0.7.3 native date/time picker revision")
