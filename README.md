# PortShiftTracker v0.12.0

App Android nativa per registrare le prestazioni portuali, applicare automaticamente le indennità corrette e confrontare i risultati con la busta paga.

## Inserimento rapido
- Turno ordinario: Mattina, Pomeriggio, Sera, Sera2, Notte e Giornaliero.
- Doppio: Pom, Sera e Sera2 con base separata da €88,40.
- Varianti di sabato, festivo e semifestivo determinate automaticamente dalla data.
- Calendario speciale modificabile.
- TUMezzo / ONmezzo con Mezza IMA obbligatoria anche nel motore di calcolo.
- Ferie e Malattia inseribili su intervallo.
- Mansioni recenti richiamabili con un tocco.
- "Ripeti ultima configurazione" per replicare velocemente mansione, turno e indennità.
- Copia prestazione su un'altra data con ricalcolo automatico della variante turno.

## Coerenza e sicurezza dati
- Salvataggio bloccato se manca il tipo turno richiesto.
- Duplicati dello stesso tipo di prestazione nello stesso giorno bloccati sia nel repository sia dal database.
- Turno + Doppio nello stesso giorno restano consentiti.
- Periodi multi-giorno salvati in una singola transazione Room.
- Eliminazione con possibilità di Annulla.
- Mesi chiudibili dopo il controllo busta paga; eventuali modifiche richiedono conferma.
- Room schema export attivo, database v7 e migrazione 6 → 7 testata.
- Backup Android esplicitamente limitato a database Room e DataStore.
- Backup manuale completo disponibile dall'app.

## Riepilogo, storico e statistiche
- Riepilogo mensile con base, indennità, totale, giorni e prestazioni.
- Statistiche mensili: Turni, Doppi, Mezzi Doppi, Avviamenti, Ferie, Malattia e voci più frequenti.
- Andamento economico degli ultimi sei mesi.
- Schermata Storico con ricerca per mansione, note, indennità e codice.
- Filtri per tipo prestazione e intervallo di date.
- Query per intervallo/mese per evitare di ricalcolare tutto lo storico ad ogni modifica.
- Confronto con busta paga.
- Export Excel e PDF.

## Backup e preferenze
Preset, calendario speciale, confronti busta e chiusura mesi sono salvati con Jetpack DataStore.
I vecchi dati SharedPreferences vengono migrati automaticamente.

## Build locale
Il progetto usa compileSdk 36, Gradle Wrapper 9.6 e Java 17.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

APK debug:
`app\build\outputs\apk\debug\app-debug.apk`

## Release firmata
È presente il workflow manuale **Android signed release**. Per usarlo servono questi GitHub Secrets:

- `PST_KEYSTORE_BASE64`
- `PST_KEYSTORE_PASSWORD`
- `PST_KEY_ALIAS`
- `PST_KEY_PASSWORD`

Il workflow genera e verifica:
- APK release firmato;
- AAB release firmato;
- opzionalmente una GitHub Release v0.12.0 usando `CHANGELOG.md`.

Il workflow resta manuale: nessuna build release viene avviata automaticamente ad ogni push.

## Test
La suite comprende:
- test unitari del motore economico;
- test TUMezzo/ONmezzo + Mezza IMA;
- test calendario feriale/sabato/festivo;
- test copy/repeat/mansioni recenti;
- test repository per duplicati, rollback atomico e query per intervallo;
- test migrazione Room 6 → 7;
- test Compose del Doppio rapido e delle voci storiche;
- build debug e release.
