# PortShiftTracker v0.11.0

App Android nativa per registrare le prestazioni portuali e calcolare base, indennità e totale giornaliero/mensile.

## Inserimento rapido
- Turno ordinario: Mattina, Pomeriggio, Sera, Sera2, Notte e Giornaliero.
- Doppio: Pom, Sera e Sera2, con base separata da €88,40.
- Le varianti di sabato, festivo e semifestivo vengono determinate automaticamente dalla data selezionata nel calendario.
- Il calendario speciale può forzare manualmente Feriale / Sabato / Festivo / Semifestivo.
- TUMezzo e ONmezzo selezionano obbligatoriamente Mezza IMA anche a livello del motore di calcolo.
- Ferie e Malattia possono essere inserite su un intervallo di date.

## Sicurezza dei dati e coerenza
- Il salvataggio è bloccato finché manca il tipo di turno richiesto, salvo Ferie/Malattia/IMA.
- Una stessa prestazione non può essere inserita due volte nello stesso giorno per lo stesso lavoratore.
- Turno + Doppio nello stesso giorno restano consentiti.
- I periodi multi-giorno vengono salvati in una sola transazione Room: in caso di conflitto viene effettuato rollback completo.
- Le selezioni vengono normalizzate anche nel repository e nel motore, non soltanto nell'interfaccia.
- Le vecchie voci Doppio Giornaliero restano leggibili nello storico come voci legacy.

## Interfaccia e apprendimento
- Preset mansione / Area / Disagi / Avviamento.
- Area, Disagi e Avviamento in liste orizzontali scorrevoli.
- Le indennità usate di recente, nella stessa prestazione e con la stessa mansione, salgono automaticamente nell'ordine.
- Riepilogo compatto della selezione, senza contare il tipo turno come una normale indennità.
- Formula di calcolo visibile durante l'inserimento.
- Calendario mensile con codici turno e supporto a più prestazioni nella stessa giornata.

## Riepilogo, confronto e export
- Riepilogo mensile per base, indennità e tipo di prestazione.
- Confronto con i valori della busta paga.
- Export Excel (.xlsx) e PDF.
- Backup/ripristino completo.
- Preset, calendario speciale e confronti busta sono salvati con Jetpack DataStore; i vecchi dati SharedPreferences vengono migrati automaticamente.

## Test
Sono presenti:
- test unitari del motore economico;
- test delle regole TUMezzo/ONmezzo + Mezza IMA;
- test della scelta automatica feriale/sabato/festivo;
- test dell'ordinamento adattivo delle indennità;
- test strumentali Room per duplicati e salvataggi atomici;
- test Compose dell'inserimento rapido del Doppio e delle voci storiche.

Il workflow Android CI resta manuale e, quando avviato, esegue unit test, build, test strumentali/Compose e screenshot.

## Build locale
Il progetto usa compileSdk 36, Gradle Wrapper 9.6 e Compose BOM 2026.04.01.

Da PowerShell nella cartella del progetto:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Per installare direttamente sul dispositivo collegato:

```powershell
.\gradlew.bat installDebug
```

APK debug:
`app\build\outputs\apk\debug\app-debug.apk`
