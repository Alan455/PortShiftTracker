# Changelog

## 0.12.0
- Vincolo database per impedire duplicati dello stesso tipo di prestazione nello stesso giorno.
- Room schema export attivo e test della migrazione 6 → 7.
- Regole esplicite per Android Auto Backup e trasferimento dispositivo.
- Eliminazione con Annulla.
- Copia prestazione su un altro giorno con ricalcolo automatico della variante del turno.
- Mansioni recenti e comando "Ripeti ultima configurazione".
- Chiusura del mese dopo il controllo busta paga con conferma prima delle modifiche.
- Nuova schermata Storico con ricerca, filtri per tipo e intervallo di date.
- Query per intervallo/mese per evitare di ricalcolare tutto lo storico ad ogni modifica.
- Statistiche mensili e andamento degli ultimi sei mesi.
- Pipeline release manuale predisposta per APK/AAB firmati tramite GitHub Secrets.
- Backup manuale aggiornato con il giorno di servizio.

## 0.11.0
- DataStore per preset, calendario speciale e confronto busta.
- TUMezzo/ONmezzo con Mezza IMA obbligatoria nel motore.
- Controllo duplicati nel repository e salvataggi multi-giorno atomici.
- Ordinamento adattivo delle indennità e formula di calcolo visibile.
- Test unitari, Room e Compose ampliati.

## 0.10.2
- Doppio rapido con Pom, Sera e Sera2.
- Variante feriale/sabato/festiva determinata automaticamente dalla data.
