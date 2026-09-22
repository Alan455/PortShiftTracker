# Recupero Netto e IRPEF — dopo Shift Motion 1–3

Branch: `feature/restore-netto-irpef-after-motion`. Creato dall'attuale `main`; NON unire il vecchio `feature/netto-stimato-e-manuale` direttamente perché contiene versioni precedenti dell'interfaccia.

## Funzione recuperata
- Il campo **IRPEF %** in Impostazioni è una *percentuale forfettaria di trattenuta stimata* (0–100%), non un'aliquota fiscale italiana né un calcolo di busta paga.
- Nel Riepilogo, subito sotto il lordo mensile, riappare **Netto del mese** con trattenuta e netto stimati. La cifra si aggiorna con una dissolvenza breve coerente con la Fase 2.
- L'utente può inserire un **netto effettivo** per un mese: quando è compreso tra 0 e il lordo positivo, l'app ricava la percentuale forfettaria da `(lordo - netto) / lordo` (arrotondata a due decimali), la salva nel profilo e la applica alle stime degli altri mesi. Il netto manuale già salvato per altri mesi rimane invariato.
- Svuotando il campo e salvando, si elimina solo il netto manuale del mese; la percentuale globale resta invariata.
- Il netto manuale viene salvato in DataStore nel JSON dei confronti con busta paga, con campo facoltativo `manualNetCents`. Non sono richieste migrazioni e il campo resta compatibile con vecchi backup privi di quel valore.
- Salvataggio confronto e chiusura/riapertura mese conservano `manualNetCents`; aggiornamento del netto preserva le altre voci di confronto e il flag `locked`.
- Rimangono inalterati i calcoli lordi, le singole indennità, gli export e il controllo Firebase. I feedback di salvataggio della Fase 3 sono conservati.

## Verifiche prima di unire a main
1. Da Android Studio eseguire `.\gradlew.bat testDebugUnitTest assembleDebug`.
2. Nel Riepilogo verificare la nuova card e la dissolvenza dell'importo, anche cambiando mese.
3. Con lordo di prova € 2.400 e netto manuale € 1.680, la trattenuta forfettaria ricalcolata deve essere **30,00%**. Un mese diverso con lordo € 1.000 deve mostrare netto stimato € 700.
4. Cambiare l'IRPEF % dalle Impostazioni e controllare che il netto stimato cambi, mentre il lordo rimane invariato.
5. Inserire un netto maggiore del lordo, testo non numerico e un mese senza lordo: il campo manuale deve rifiutare il salvataggio.
6. Salvare un netto manuale, aprire **Confronta**, modificare e salvare i valori e chiudere il mese; tornare alla card Netto e verificare che il valore manuale sia rimasto.
7. Eliminare il netto manuale svuotando il campo, verificare che la percentuale IRPEF non cambi.
8. Testare ricerca Storico, cambio tab, animazioni calendario e schermata di blocco Firebase per confermare che Shift Motion 1–3 resti invariato.

Non è stato eseguito un build Gradle remoto dal connettore GitHub: test e compilazione vanno effettuati localmente prima del merge.
