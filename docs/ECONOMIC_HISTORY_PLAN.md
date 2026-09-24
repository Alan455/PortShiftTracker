# Shift — storico economico e tariffe personalizzabili

Stato: **implementazione sul branch feature/summary-category-breakdown-sheet; build e test sul dispositivo ancora da eseguire**. `main` non è stato modificato. Non distribuire ai tester prima dei test con backup e migrazione reale.

## Regole

- Modifica manuale tariffa: vale per le prestazioni registrate dopo il salvataggio, senza cambiare gli importi delle prestazioni già salvate.
- Correzione di un errore economico: aggiornare retroattivamente soltanto le prestazioni e le componenti interessate, non tutte le tariffe storiche.
- Tariffe personalizzabili di turno, Doppio, Giornaliero, mezzi primi, indennità e assenze. Mezzo Doppio è metà della base Doppio; DOP_G e ONMezzo sono metà della base Giornaliero, sincronizzate quando si modifica Giornaliero.
- Le regole già concordate per importi fissi delle assenze, FuoriOrario, Mezza IMA e dimezzamento delle indennità di turno restano in vigore.

## Implementato

1. Migrazione Room **7 → 8**, tabella `shift_pay_snapshots` con FK e cancellazione a cascata rispetto alla prestazione.
2. Ogni inserimento salva nello stesso commit DB la prestazione, le selezioni e il suo snapshot economico: base, minuti, righe con ruleId/nome/importo effettivo, e istante di salvataggio.
3. Riepilogo e storico leggono gli importi dagli snapshot. Le vecchie prestazioni che non ne hanno vengono congelate una sola volta, dopo l'applicazione delle correzioni delle regole note; non si riescono a ricostruire tariffe originarie già perse con i precedenti ricalcoli.
4. Modifica ordinaria delle tariffe e del profilo: prima congela eventuali prestazioni legacy e poi aggiorna la tariffa; non sovrascrive gli snapshot esistenti. Modifica soltanto delle note di una prestazione mantiene lo snapshot; la modifica dei suoi dati economici (indennità, orario, data, mansione) la ricalcola con i valori correnti.
5. Backup JSON **schemaVersion 2** include gli snapshot. Importa anche vecchi backup schema 1 senza snapshot, con backfill iniziale. Salvataggio/import e undo conservano gli importi nei casi previsti.
6. Funzioni esplicite `correctHistoricAllowanceLine` e `correctHistoricExclusiveAbsence` nel repository per applicare una correzione economica retroattiva selettiva, in transazione. Le correzioni già note al momento dell'upgrade sono applicate dal motore prima del backfill; **non** viene automaticamente eseguito un ricalcolo globale successivo.
7. Rimossa la riscrittura della tariffa Giornaliero a €90 e DOP_G a €45 all'avvio. I valori derivati DOP_G e ONMezzo sono legati alla tariffa Giornaliero attuale; nell'editor non sono modificabili separatamente.

## Cosa serve prima della distribuzione

- Eseguire `gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest` e correggere eventuali errori di compilazione.
- Su un emulatore o un dispositivo dedicato eseguire `connectedDebugAndroidTest` per gli snapshot e il backup JSON. La versione precedente con i dati reali del beta tester non deve essere disinstallata.
- Testare **su copia del database** una migrazione reale dalla versione 7 alla 8, comparando prestazioni, categorie, importi e totale mensile prima/dopo; verificare riapertura e idempotenza del backfill.
- Verificare il ripristino di un backup JSON v1 e v2, il funzionamento di eliminazione/annulla, e la modifica di una tariffa con turni storici.
- Le operazioni correttive retroattive future richiedono una formula specifica per la regola errata e un backup prima di ogni correzione; non c'è ancora un'interfaccia utente per attivarle né un audit trail persistente delle versioni pre-correzione.
- La descrizione e categoria storica delle regole vengono ancora correlate al catalogo corrente in alcune viste: il valore monetario della prestazione è congelato, ma un cambio del nome/categoria o l'eliminazione di una voce può modificare il raggruppamento visivo del suo dettaglio.
- NON aggiungere una disinstallazione come passo di migrazione: cancellerebbe i dati locali che si intende conservare.
