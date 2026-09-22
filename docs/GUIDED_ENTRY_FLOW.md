# Shift — Inserimento guidato (nuovo flusso)

Branch: `feature/guided-entry-popups`, derivato da `feature/day-card-allowance-spacing` (include il recupero Netto/IRPEF e i rientri scheda giorno ancora non confluiti in `main`). **Non fare merge del vecchio branch separatamente.**

## Schermate

- Calendario → Aggiungi prestazione: controlla la data e mostra la prima domanda **Lavoro / Assenza** solo se non esiste un primo turno.
- Primo turno, Lavoro → secondo pop-up **Turno / Giornaliero**.
- Primo turno, Assenza → secondo pop-up **Ferie / Malattia / Congedo / IMA**; INAIL e Donazione sangue restano accessibili quando abilitate, per preservare i codici II/Ds.
- Primo turno già lavorato → pop-up diretto **Doppio turno / Mezzo Doppio / Mezzo Giornaliero**. Se esiste già un'assenza o un secondo turno, non propone nuove prestazioni; si può modificare il record esistente dalla scheda giorno.
- L'editor del nuovo inserimento mostra solo il ramo scelto. Nessun selettore generico di tipo, riepilogo dettagliato in alto, orari manuali, mansione o preset. Le indennità sono raggruppate in sezioni chiuse apribili; note facoltative in fondo e salvataggio fisso.
- Modifica e Copia continuano a usare il precedente editor completo per non alterare implicitamente record storici.

## Regole economiche preservate

- Primo turno Giornaliero: base €90, Polivalenza automatica; solo qui è visibile ONMezzo, con base €45 e Mezza IMA automatica.
- Primo turno normale: turno M/P/S/S2/N, TuMezzo selezionabile dove disponibile; Mezza IMA automatica per il mezzo primo turno secondo le regole del catalogo.
- Doppio completo: base Doppio configurata nel profilo (predefinita €88,40), indennità di turno **intera**, senza Mezza IMA.
- Mezzo Doppio: metà della base Doppio (predefinita €44,20), scelta fra M/P/S/S2/N. Soltanto le maggiorazioni P/S/S2/N, comprese quelle di sabato/festivo, sono dimezzate. Mattina e Mattina festiva, Area, Avviamento, Disagi e altre voci compatibili non vengono dimezzate da questa condizione. Nessuna Mezza IMA.
- Mezzo Giornaliero nel Doppio: base €45, no Polivalenza e no Mezza IMA; Area, Avviamento, Disagi e Altre voci compatibili rimangono disponibili.
- IMA: editor senza le indennità lavorative; solo Disdetta casa e Disdetta casa festiva, selezionabili in alternativa oppure nessuna delle due.
- I nuovi codici DOP_MAT/DOP_MATF/DOP_NOTTE/DOP_NOTTEF entrano nel catalogo con `insertIfMissing`, quindi non sovrascrivono le tariffe personalizzate già salvate.
- Il percorso guidato inserisce automaticamente gli intervalli nominali M 06:30–13, P 13–19:30, S 19:30–01 (+1), S2 19:30–02 (+1), N 01–06:30. Sono dati tecnici necessari per il calcolo di eventuali indennità orarie; non sono campi richiesti all'utente in questo percorso. Modifica/Copia mantiene il precedente editor.

## Verifica prima del merge

1. `.\gradlew.bat testDebugUnitTest assembleDebug`.
2. Una data vuota → Lavoro → Turno → selezionare M/P/S/S2/N; verificare il codice giorno feriale/sabato/festivo.
3. Una data vuota → Lavoro → Giornaliero; controllare €90, Polivalenza automatica e ONMezzo €45 con Mezza IMA.
4. Primo turno già presente → provare Doppio completo, Mezzo Doppio e Mezzo Giornaliero; controllare €88,40/€44,20 se il profilo conserva le basi predefinite, e €45 del Mezzo Giornaliero.
5. Controllare Mezzo Doppio Mattina festiva (indennità intera), Sera/Sera2/Notte festive (solo indennità di turno dimezzata), Area/Avviamento/Disagi non dimezzati, nessuna Mezza IMA.
6. Assenza → IMA: selezionare alternativamente ciascuna disdetta e verificare che nessuna indennità di lavoro sia visualizzata. Verificare Ferie/Malattia/Congedo su più giorni e la gestione dei duplicati.
7. Con primo turno + secondo, oppure assenza nella data, premere Aggiungi prestazione: non deve proporre un nuovo turno. Verificare che il primo turno preceda il doppio nella scheda giorno anche per orari che si sovrappongono.
8. Modifica e Copia su prestazioni esistenti, riepilogo lordo/netto, login Firebase e blocco al ritorno in primo piano devono continuare a funzionare.

Il workflow `guided-entry-ci.yml` esegue i test unitari e la build Debug sui commit del branch. Eseguire comunque la prova visiva e funzionale sul telefono prima del merge.
