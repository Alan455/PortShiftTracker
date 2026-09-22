# Shift Motion — Fase 2 (stile bilanciato)

Branch: `feature/shift-motion-phase2`. Questo branch nasce da `main`, già aggiornato con la Fase 1; `main` resta invariato fino alla verifica.

## Piano operativo e interventi
1. **Riepilogo / periodo:** il titolo del mese cambia con una dissolvenza breve (circa 140–200 ms); gli intervalli, le query e i filtri restano immutati.
2. **Riepilogo / KPI e totale:** ogni cifra mostra il nuovo valore *esatto* con dissolvenza (circa 130–250 ms). Nessuna cifra interpolata entra nei calcoli, negli export o nei dati persistenti.
3. **Riepilogo / dettaglio per categoria:** barre Base, Turno, Avviamento, Disagi, Area e Doppio aggiornate in circa 300 ms; le singole Altre voci mantengono importi e percentuali indipendenti e la loro identità per regola.
4. **Riepilogo / andamento:** barre settimanali animate in circa 300 ms; importi, settimana migliore e prestazione più usata continuano a derivare dai dati originali.
5. **Riepilogo / azioni:** il colore dei pulsanti cambia dolcemente; confronto busta ed export si espandono/chiudono in circa 120–240 ms. Le giornate dettagliate restano una lista lazy con animazione sugli elementi.
6. **Storico / ricerca e filtri:** contatore risultati e somma filtrata cambiano con dissolvenza; le cinque categorie rimangono esclusive e le righe visibili si animano al cambiare del filtro o dell'ordine.
7. **Storico / dettaglio prestazione:** `Vedi dettaglio` apre una sezione animata Base + indennità effettive + Totale, con `Nascondi dettaglio` per richiuderla.

Le animazioni usano API native di Compose e rispettano la scala animazioni del dispositivo. Non è stata modificata la logica economica, il database, l'autorizzazione Firebase o il formato degli export.

## Collaudo richiesto prima del merge
- `.\gradlew.bat testDebugUnitTest assembleDebug`.
- Riepilogo: cambia mese rapidamente avanti e indietro; confronta i totali visualizzati con il mese originale, anche per mesi vuoti.
- Riepilogo: verifica che Base e ogni singola Altra voce mantengano i loro importi e le rispettive percentuali dopo la transizione.
- Riepilogo: apri/chiudi più volte Confronta, Esporta e Dettaglio; controlla che Excel/PDF e il blocco/riapertura del mese funzionino come prima.
- Storico: alterna Tutti, Turni, Assenze, Mezzi primi turni, Doppi e Mezzi doppi, anche con ricerca attiva o lista vuota. Ogni prestazione deve apparire una sola volta.
- Storico: espandi e richiudi varie righe; i dettagli devono mostrare il valore di Base, tutte le indennità pertinenti e lo stesso Totale visualizzato sulla card.
- Prova lo scorrimento di uno Storico lungo e le animazioni disattivate nelle impostazioni accessibilità.
- Testa di nuovo il controllo di accesso Firebase al ritorno in primo piano senza rete: la UI non deve rimanere visibile durante la verifica.

Non fare il merge finché la compilazione, i test e la prova pratica su telefono non sono completati.
