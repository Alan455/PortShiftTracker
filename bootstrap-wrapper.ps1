$ErrorActionPreference = "Stop"
$Version = "9.6.0"
$Tmp = Join-Path $env:TEMP "portshift-gradle-$Version"
$Zip = "$Tmp.zip"
$Url = "https://services.gradle.org/distributions/gradle-$Version-bin.zip"

Write-Host "Scarico Gradle $Version..."
Invoke-WebRequest -Uri $Url -OutFile $Zip

if (Test-Path $Tmp) {
    try { Remove-Item $Tmp -Recurse -Force -ErrorAction Stop }
    catch { Write-Warning "Cartella temporanea in uso: continuo comunque." }
}

Expand-Archive -Path $Zip -DestinationPath $Tmp -Force
$GradleBat = "$Tmp\gradle-$Version\bin\gradle.bat"
& $GradleBat wrapper --gradle-version $Version
if ($LASTEXITCODE -ne 0) { throw "Creazione Gradle Wrapper fallita (codice $LASTEXITCODE)." }

# Ferma il daemon prima di tentare la pulizia: evita il lock del gradle-instrumentation-agent.
& $GradleBat --stop | Out-Null
Start-Sleep -Milliseconds 800

try { Remove-Item $Zip -Force -ErrorAction SilentlyContinue } catch {}
try { Remove-Item $Tmp -Recurse -Force -ErrorAction Stop }
catch { Write-Warning "Wrapper creato correttamente. Non sono riuscito a cancellare la cartella temporanea perché un file è ancora in uso; puoi ignorare questo avviso." }

Write-Host "Gradle Wrapper creato. Ora puoi usare .\gradlew.bat"
