# Shift — piano per storico economico e correzioni retroattive

Stato: **specifica approvata, migrazione dello storico NON implementata**.
Non distribuire una release come se questa funzionalità fosse già attiva.

## Regole

- Una modifica manuale alla tariffa entra in vigore nell'istante del salvataggio; non modifica gli importi delle prestazioni già registrate, anche se la loro data lavorativa è successiva al salvataggio.
- Una correzione di bug economico aggiorna retroattivamente tutte e sole le prestazioni interessate dalla regola errata.
- Congedo = €30, Donazione sangue = €95, INAIL = €67,80, senza base ordinaria o altre voci. FuoriOrario = €7,75 fisso una volta per prestazione.
- ONMezzo primo Giornaliero = €45 con Mezza IMA automatica, secondo Giornaliero = €45 senza Mezza IMA. Mezzo Primo e Mezzo Doppio: tutte le rispettive indennità di turno al 50%; Area, Avviamento, Disagi invariati.
- Le indennità orarie esistenti diventano fisse usando come importo il valore unitario originario; percentuali non utilizzate.
- Disdetta casa festiva solo per data festiva secondo la classificazione del calendario Shift; IMA con 0 o 1 disdetta, mai due.
- Nel popup riepilogo, applicazioni = numero di prestazioni distinte in cui compare la voce.

## Strategia di persistenza da implementare separatamente

1. Aggiungere una migrazione Room versionata e una tabella per gli snapshot economici **per prestazione** con riferimento a shiftId, base, importo totale, minuti, righe di indennità con ruleId/nome/importo e data/ora di registrazione. Collegare a backup e restore, export e modifica della prestazione. Testare migrazione 7 -> successiva contro DB realistici e salvataggi esistenti.
2. Per ogni nuova prestazione, calcolare e salvare lo snapshot atomically insieme alla prestazione e alle selezioni. Per ogni prestazione già esistente senza snapshot, eseguire un backfill UNA SOLA VOLTA, dopo l'aggiornamento dei cataloghi/bug economici, prima di consentire modifiche ordinarie delle tariffe. I valori storici già sovrascritti da versioni precedenti non sono ricostruibili automaticamente.
3. Per la modifica della tariffa ordinaria, mantenere gli snapshot preesistenti e utilizzare la nuova tariffa solo per prestazioni registrate dopo il salvataggio. Se si modifica una prestazione precedente, definire una scelta esplicita fra preservare importi archiviati e ricalcolo manuale.
4. Per le correzioni retroattive, introdurre migrazioni economiche identificabili e idempotenti: individuare per codice regola i soli shift coinvolti, ricalcolare le sole componenti interessate con la nuova regola, mantenere tutte le altre tariffe storiche e aggiornare gli snapshot nella stessa transazione. Non usare un ricalcolo globale con le tariffe correnti.
5. Test automatici: rate change non altera i vecchi snapshot; bug correction interessa soltanto shift selezionati; salvataggio contemporaneo di due prestazioni; riavvio ripetuto non modifica i risultati; backup/restore mantiene tutti gli snapshot; migrazione preserva dati e chiavi.

## Stato del branch

Sul branch sono già codificati: importi fissi/assenze, conversione una tantum delle regole orarie, dimezzamento delle indennità di turno del Mezzo Doppio, ONMezzo come secondo mezzo Giornaliero, verifica festivo nell'editor e conteggio delle applicazioni nel popup. **Il repository continua però a ricalcolare lo storico usando le tariffe correnti**: prima di distribuire ai tester occorre completare e testare la migrazione dello storico descritta sopra, o rinviare esplicitamente questa funzionalità.
