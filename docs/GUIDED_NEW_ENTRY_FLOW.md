# Nuovo inserimento guidato — specifica implementata

Branch: `feature/guided-new-entry-flow`

Il branch nasce da `feature/day-card-allowance-spacing`, quindi contiene anche il recupero Netto/IRPEF e l'ultimo allineamento della scheda giorno. `main` non viene modificato.

## Flusso
### Giorno senza prestazioni
1. Primo pop-up: **Turno / Lavoro** oppure **Assenza**.
2. Lavoro -> secondo pop-up: **Turno** oppure **Giornaliero**.
3. Assenza -> secondo pop-up: **Ferie**, **Malattia**, **Congedo**, **IMA**.
4. L'editor finale mostra soltanto le opzioni pertinenti.

### Giorno con primo turno lavorato
Il primo pop-up viene saltato. Si apre direttamente **Seconda prestazione**:
- **Doppio completo**
- **Mezzo Doppio**
- **Mezzo Giornaliero**

### Giorno con assenza o con secondo turno già presente
Non viene proposta un'ulteriore prestazione.

## Editor compatto
Nel nuovo inserimento guidato:
- non viene mostrata la vecchia scelta generale Turno/Giornaliero/Doppio/Mezzo Doppio;
- non viene mostrato "Ripeti ultima configurazione";
- non viene mostrato il totale provvisorio dettagliato nella pagina (resta il totale nella barra di salvataggio);
- non vengono mostrati Data/Ora;
- non vengono mostrati Mansione/Operatore e Preset;
- Indennità e Suggerimenti sono righe compatte espandibili;
- Note è l'ultima sezione della pagina.

Modifica e Copia di prestazioni esistenti mantengono il vecchio editor completo per evitare regressioni.

## Regole Primo turno
### Turno
- M / P / S / S2 / N.
- TUMezzo resta disponibile.
- ONMezzo è nascosto.
- TUMezzo aggiunge automaticamente Mezza IMA, come da logica esistente.

### Giornaliero
- Base €90.
- Polivalenza automatica.
- ONMezzo è disponibile SOLO qui.
- ONMezzo porta la base a €45 e aggiunge Mezza IMA.
- TUMezzo è nascosto.

## Assenze
Ferie, Malattia e Congedo non mostrano le normali indennità lavorative.

### IMA
Mostra soltanto:
- Disdetta casa
- Disdetta casa festiva

Le due voci appartengono al gruppo esclusivo `IMA_DISDETTA`, quindi selezionandone una viene rimossa l'altra.

## Secondo turno
### Doppio completo
- Base Doppio intera (profilo predefinito €88,40).
- M / P / S / S2 / N.
- Indennità di turno intere.
- Area, Avviamento, Disagi e altre voci compatibili disponibili.
- Nessuna Polivalenza.
- Nessuna Mezza IMA.

Sono state aggiunte le regole Doppio mancanti per Mattina e Notte, comprese le varianti festive.

### Mezzo Doppio
- Base = metà della base Doppio (predefinito €44,20).
- M / P / S / S2 / N.
- Solo queste indennità sono al 50%: Pomeriggio, Sera, Sera2, Notte e relative varianti sabato/festive.
- Mattina, compresa Mattina festiva, resta intera.
- Area, Avviamento, Disagi e altre voci compatibili restano intere.
- Nessuna Polivalenza.
- Nessuna Mezza IMA.

### Mezzo Giornaliero
- Base €45.
- Nessuna Mezza IMA.
- Nessuna Polivalenza.
- Area, Avviamento, Disagi e altre voci compatibili disponibili.
- Non mostra la griglia M/P/S/S2/N.

## Verifiche prima del merge
1. `.\gradlew.bat testDebugUnitTest assembleDebug`
2. Giorno vuoto -> Lavoro -> Turno -> provare M/P/S/S2/N.
3. Primo Turno -> selezionare TUMezzo e verificare Mezza IMA automatica.
4. Giorno vuoto -> Lavoro -> Giornaliero -> verificare base €90 e Polivalenza.
5. Giornaliero -> ONMezzo -> verificare base €45 + Mezza IMA; ONMezzo non deve apparire nel Turno normale.
6. Giorno vuoto -> Assenza -> verificare Ferie/Malattia/Congedo senza opzioni lavorative.
7. IMA -> verificare soltanto Disdetta casa / festiva e mutua esclusione.
8. Con primo turno presente -> verificare che appaiano solo Doppio completo, Mezzo Doppio, Mezzo Giornaliero.
9. Doppio completo -> verificare base intera e indennità turno intere.
10. Mezzo Doppio -> verificare base metà; P/S/S2/N dimezzati; Mattina festiva NON dimezzata; Area/Avviamento/Disagi interi.
11. Mezzo Giornaliero -> €45, senza Mezza IMA/Polivalenza.
12. Dopo una seconda prestazione o un'assenza, "Aggiungi prestazione" deve mostrare il blocco di giornata completa/non disponibile.
13. Verificare modifica/copia delle prestazioni esistenti: devono continuare a usare l'editor precedente.
