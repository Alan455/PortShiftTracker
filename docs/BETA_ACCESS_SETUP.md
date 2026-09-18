# Beta access con Google + Firebase

Questa branch aggiunge un gate di accesso prima di `PortShiftApp`.

## Comportamento

- l'APK può essere distribuito direttamente, senza Google Play;
- l'utente deve accedere con un account Google;
- l'account deve avere un documento valido in `betaLicenses`;
- `enabled` deve essere `true`;
- `expiresAt` deve essere successivo all'ora del server Firebase;
- la verifica usa `Source.SERVER`, quindi all'avvio serve Internet;
- l'autorizzazione viene ricontrollata ogni 15 minuti mentre l'app resta aperta.

La data del telefono non viene usata per decidere la scadenza.

## 1. Crea il progetto Firebase

Nella console Firebase:

1. crea un progetto;
2. aggiungi una app Android con package:
   `it.alantamanti.portshifttracker`;
3. registra la SHA-1 del certificato con cui firmerai l'APK;
4. in Authentication abilita il provider Google;
5. crea un database Cloud Firestore.

## 2. Configura la build locale

Aggiungi queste righe al file locale `local.properties`, che è già escluso da Git:

```properties
PST_FIREBASE_API_KEY=...
PST_FIREBASE_APP_ID=...
PST_FIREBASE_PROJECT_ID=...
PST_GOOGLE_WEB_CLIENT_ID=...
```

I primi tre valori arrivano dalle impostazioni del progetto Firebase.
`PST_GOOGLE_WEB_CLIENT_ID` è il client OAuth di tipo Web usato da Sign in with Google.

La build accetta gli stessi nomi anche come variabili d'ambiente.

## 3. Pubblica le Security Rules

Usa il file `firestore.rules` presente nella root del repository.

Puoi copiarlo nella console Firestore > Rules oppure usare Firebase CLI:

```bash
firebase deploy --only firestore:rules
```

## 4. Autorizza il tester

In Firestore crea la collection:

`betaLicenses`

e come ID del documento usa ESATTAMENTE l'email Google del tester, ad esempio:

`mario.rossi@gmail.com`

Campi:

- `enabled`: Boolean = `true`
- `expiresAt`: Timestamp = data/ora di scadenza

Esempio logico:

```text
betaLicenses/
  mario.rossi@gmail.com
    enabled: true
    expiresAt: 31 ottobre 2026 23:59
```

Per revocare subito l'accesso puoi impostare `enabled = false` oppure eliminare il documento.
Per prorogarlo basta cambiare `expiresAt`.

Le Security Rules confrontano `expiresAt` con `request.time`, cioè l'ora del server Firebase, non con l'orologio Android.

## 5. Build

Esegui normalmente la build release firmata con il tuo keystore.

## Limite importante

Questo gate impedisce l'uso della build originale quando Firebase nega l'autorizzazione. Tuttavia PortShiftTracker conserva la propria logica principale localmente: un attaccante molto determinato che decompili, modifichi e rifirmi l'APK potrebbe rimuovere il gate e usare le sole funzioni locali.

Per rendere una licenza realmente non aggirabile tramite modifica dell'APK, almeno una funzione o una risorsa indispensabile deve restare su un backend controllato dal proprietario. Per un beta test con una persona, questo gate fornisce comunque login, revoca e scadenza centralizzati senza pubblicazione su Google Play.
