# Shift Motion — Fase 3 (stile bilanciato)

Branch: `feature/shift-motion-phase3`. Il branch è creato da `main` dopo il merge della Fase 2; non unire prima del collaudo.

## Interventi
- **Navigazione globale:** la barra Material 3 rimane presente; il titolo e il contenuto delle cinque sezioni (Home, Riepilogo, Storico, Indennità, Impostazioni) cambiano con dissolvenza breve (120–180 ms). L'icona selezionata si ingrandisce leggermente (1,10×) e il cambio tab produce un feedback tattile discreto, rispettando le impostazioni Android del dispositivo.
- **Sicurezza:** le animazioni restano *all'interno* di PortShiftApp, che viene rimosso subito dal gate Firebase quando l'autorizzazione non è valida. Non introdurre transizioni ritardate tra stato autorizzato e stato bloccato.
- **Feedback unificato:** uno SnackbarHost condiviso dello Scaffold mostra i messaggi sopra la barra di navigazione anche quando si passa di sezione; non c'è più un secondo host sovrapposto nella Home.
- **Home:** mantenuta l'azione Annulla dopo l'eliminazione di una prestazione, mostrando la conferma solo dopo la cancellazione riuscita e un messaggio in caso di errore. Errore di ripristino segnalato.
- **Impostazioni:** il pulsante viene disabilitato durante il salvataggio; compare la conferma solo dopo il risultato del repository. Valori non validi ed errori ricevono feedback, senza un falso messaggio persistente di successo.
- **Indennità:** abilitazione/disabilitazione e salvataggio mostrano la conferma globale solo dopo successo. Se il salvataggio nell'editor fallisce, il dialogo mantiene tutti i dati e mostra l'errore al suo interno.

Nessuna modifica a tariffe, calcoli, database, formati di export o controlli di licenza.

## Verifiche prima del merge
1. Eseguire `.\gradlew.bat testDebugUnitTest assembleDebug` in Android Studio e installare la build Debug.
2. Passare velocemente fra tutte le cinque schede, verificando che non rimangano sovrapposti vecchi contenuti dopo la transizione e che il titolo sia quello corretto.
3. Ridurre o disattivare le animazioni nelle impostazioni di accessibilità Android; verificare leggibilità, selezione e feedback.
4. In Home eliminare una prestazione e usare Annulla. Verificare che l'elemento venga ripristinato e non siano creati duplicati; verificare anche le operazioni normali di inserimento/modifica.
5. Salvare le Impostazioni con valori validi e non validi; il messaggio di successo deve arrivare soltanto dopo il salvataggio effettivo.
6. Abilitare/disabilitare un'indennità e salvarne una modifica; controllare che conferme ed eventuali errori non chiudano prematuramente il dialogo.
7. Tornare in primo piano senza Internet: le schermate operative devono scomparire immediatamente, senza fade-out di dati durante la verifica Firebase.
8. Verificare che gli Snackbar non coprano la barra di navigazione e che non compaiano due snackbar sovrapposti nella Home.

L'esecuzione della build e dei test sul dispositivo resta necessaria: i commit GitHub da soli non attestano la compilazione.
