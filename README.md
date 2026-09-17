# PortShiftTracker v0.7.0

Versione con interfaccia grafica completamente rivista in Material 3, mantenendo il motore economico delle versioni precedenti.

## Nuova UI
- Home con calendario mensile e indicatori colorati per Turno / Doppio / Mezzo Doppio.
- Giornata selezionata con card compatte, totale giornaliero e modifica/dettaglio prestazioni.
- Inserimento e modifica prestazione a schermo intero, con sezioni per tipo turno, Avviamento, Disagi, Area e altre voci.
- Calcolo economico in tempo reale durante l'inserimento.
- Riepilogo mensile con totale, base, indennità, giorni lavorati e totale per tipo di prestazione.
- Gestione indennità con filtri Turni / Doppi / Generali e ricerca.
- Impostazioni riorganizzate in card: base turno, base Doppio e base Mezzo Doppio calcolata automaticamente.

## Logiche mantenute
- Turno base configurabile (preset €67,80).
- Doppio base configurabile (preset €88,40), senza Polivalenza.
- Mezzo Doppio = 50% della base Doppio; indennità Doppio al 50%, Area e Disagi interi.
- Polivalenza automatica solo sul turno normale con indennità di turno.
- Ferie/Malattia/IMA sostituiscono la base secondo le regole già definite.
- Mezza IMA compatibile con il mezzo turno, non con Doppio/Mezzo Doppio.

## Build
Il progetto usa compileSdk 36 e Compose BOM 2026.04.01.
Se il Gradle Wrapper non è ancora presente, eseguire da PowerShell nella cartella del progetto:

```powershell
powershell -ExecutionPolicy Bypass -File .\bootstrap-wrapper.ps1
```

Poi:

```powershell
.\gradlew.bat clean assembleDebug
```

APK debug:
`app\build\outputs\apk\debug\app-debug.apk`
