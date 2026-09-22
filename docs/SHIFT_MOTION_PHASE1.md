# Shift Motion — Fase 1 (stile bilanciato)

Branch: `feature/shift-motion-phase1`. Non unire in `main` prima di eseguire i controlli sul dispositivo.

## Piano operativo e modifiche
1. **Calendario:** transizione in dissolvenza di 160–220 ms al cambio mese; bordo e sfondo della giornata selezionata aggiornati in 180 ms. Celle, sigle e dati sottostanti invariati.
2. **Scheda giorno:** dissolvenza di 140–220 ms quando cambia la data selezionata. Ogni azione continua a riferirsi alla propria prestazione.
3. **Inserimento:** scelta del tipo di prestazione e dei turni rapidi con colori animati in 180 ms; comparsa e scomparsa graduale della spiegazione della selezione.
4. **Indennità:** colori, spunta, descrizione e contatore delle selezioni con transizioni brevi. Le regole automatiche e la normalizzazione degli ID non vengono modificate.
5. **Editor:** campo Note e dettaglio calcolo espandibili; totale economico visualizzato con un'interpolazione di 300 ms, senza cambiare il calcolo effettivo in centesimi.
6. **Salvataggio:** dopo il risultato positivo del repository compare "Prestazione salvata" per 420 ms, poi l'editor si chiude; dopo un errore le selezioni restano disponibili.

## Verifiche da fare prima del merge
- `./gradlew testDebugUnitTest assembleDebug` (su Windows: `.\gradlew.bat testDebugUnitTest assembleDebug`).
- Selezionare date vuote, date con un turno e date con doppio (es. M | S2); navigare avanti e indietro fra mesi di 4, 5 e 6 settimane.
- Cambiare rapidamente le prestazioni Turno, Giornaliero, Doppio e Mezzo Doppio; controllare che le indennità già incompatibili vengano rimosse come prima.
- Provare Giornaliero da 90 €, ONMezzo da 45 € con Mezza IMA e il mezzo Giornaliero nel Doppio da 45 € senza Mezza IMA; controllare il totale finale oltre all'animazione.
- Selezionare e rimuovere indennità, note e dettaglio calcolo, verificando che i dati siano conservati.
- Salvare un turno: messaggio di conferma solo su successo; tentare un duplicato e verificare che l'editor rimanga aperto con l'errore.
- Nelle impostazioni di accessibilità Android, ridurre/disattivare le animazioni e verificare che l'app resti utilizzabile.
- Mettere l'app in background e ritornare senza Internet: il controllo di accesso Firebase deve continuare a bloccare le schermate operative.

Le durate usano le animazioni native Compose, che seguono la scala delle animazioni del sistema. Nessuna migrazione del database o modifica delle tariffe.
