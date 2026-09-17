param(
    [string]$ProjectPath = $PSScriptRoot
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path $ProjectPath).Path
$BuildFile = Join-Path $Root "app\build.gradle.kts"
$UiFile = Join-Path $Root "app\src\main\java\it\alantamanti\portshifttracker\ui\PortShiftApp.kt"
$DbFile = Join-Path $Root "app\src\main\java\it\alantamanti\portshifttracker\data\local\AppDatabase.kt"

if (!(Test-Path $BuildFile) -or !(Test-Path $UiFile) -or !(Test-Path $DbFile)) {
    throw "Cartella progetto non valida: $Root"
}

$build = Get-Content $BuildFile -Raw
$build = $build.Replace('androidx.compose:compose-bom:2026.08.00', 'androidx.compose:compose-bom:2026.04.01')
$build = $build.Replace('versionCode = 6', 'versionCode = 7')
$build = $build.Replace('versionName = "0.6.0"', 'versionName = "0.6.1"')
Set-Content -Path $BuildFile -Value $build -Encoding UTF8

$ui = Get-Content $UiFile -Raw
$ui = $ui.Replace("import androidx.compose.foundation.layout.weight`r`n", "")
$ui = $ui.Replace("import androidx.compose.foundation.layout.weight`n", "")
Set-Content -Path $UiFile -Value $ui -Encoding UTF8

$db = Get-Content $DbFile -Raw
$db = $db.Replace('exportSchema = true', 'exportSchema = false')
Set-Content -Path $DbFile -Value $db -Encoding UTF8

Write-Host "Patch v0.6.1 applicata a: $Root"
$Gradlew = Join-Path $Root "gradlew.bat"
if (Test-Path $Gradlew) {
    & $Gradlew --stop | Out-Null
    Push-Location $Root
    try { & .\gradlew.bat clean assembleDebug }
    finally { Pop-Location }
} else {
    Write-Host "Gradle Wrapper non trovato. Esegui prima bootstrap-wrapper.ps1 nella cartella del progetto."
}
