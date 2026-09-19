package it.alantamanti.portshifttracker.auth

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import it.alantamanti.portshifttracker.BuildConfig
import it.alantamanti.portshifttracker.data.repository.PortRepository
import it.alantamanti.portshifttracker.ui.PortShiftApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private const val FIREBASE_APP_NAME = "portshift-beta-access"
private const val LICENSE_COLLECTION = "betaLicenses"
private const val ACCESS_RECHECK_MILLIS = 15 * 60 * 1000L

internal sealed interface BetaAccessState {
    data object Checking : BetaAccessState
    data object SignedOut : BetaAccessState
    data class Granted(val email: String) : BetaAccessState
    data class Denied(val email: String?, val message: String) : BetaAccessState
    data class ConfigurationMissing(val missingKeys: List<String>) : BetaAccessState
    data class Error(val message: String) : BetaAccessState
}

internal class BetaAccessManager(private val appContext: Context) {
    var state by mutableStateOf<BetaAccessState>(BetaAccessState.Checking)
        private set

    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    // Un risultato di una verifica iniziata prima della pausa non può riaprire l'app.
    private var verificationVersion = 0L
    private var isForeground = false

    fun onForeground() {
        isForeground = true
        verificationVersion++
        if (state is BetaAccessState.Granted) state = BetaAccessState.Checking
    }

    fun onBackground() {
        isForeground = false
        verificationVersion++
        if (state is BetaAccessState.Granted) state = BetaAccessState.Checking
    }

    init {
        configureFirebase()
    }

    private fun configureFirebase() {
        val missing = buildList {
            if (BuildConfig.FIREBASE_API_KEY.isBlank()) add("PST_FIREBASE_API_KEY")
            if (BuildConfig.FIREBASE_APP_ID.isBlank()) add("PST_FIREBASE_APP_ID")
            if (BuildConfig.FIREBASE_PROJECT_ID.isBlank()) add("PST_FIREBASE_PROJECT_ID")
            if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) add("PST_GOOGLE_WEB_CLIENT_ID")
        }

        if (missing.isNotEmpty()) {
            state = BetaAccessState.ConfigurationMissing(missing)
            return
        }

        runCatching {
            val firebaseApp = FirebaseApp.getApps(appContext)
                .firstOrNull { it.name == FIREBASE_APP_NAME }
                ?: FirebaseApp.initializeApp(
                    appContext,
                    FirebaseOptions.Builder()
                        .setApiKey(BuildConfig.FIREBASE_API_KEY)
                        .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                        .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                        .build(),
                    FIREBASE_APP_NAME
                )

            auth = FirebaseAuth.getInstance(firebaseApp)
            firestore = FirebaseFirestore.getInstance(firebaseApp)
        }.onFailure {
            state = BetaAccessState.Error(
                "Impossibile inizializzare Firebase. Controlla la configurazione della beta."
            )
        }
    }

    suspend fun refreshAccess() {
        if (state is BetaAccessState.ConfigurationMissing || !isForeground) return
        val requestVersion = ++verificationVersion
        val currentAuth = auth ?: return
        val currentFirestore = firestore ?: return

        val user = currentAuth.currentUser
        if (user == null) {
            state = BetaAccessState.SignedOut
            return
        }

        val email = user.email
        if (email.isNullOrBlank() || !user.isEmailVerified) {
            state = BetaAccessState.Denied(
                email = email,
                message = "L'account Google non dispone di un indirizzo email verificato."
            )
            return
        }

        state = BetaAccessState.Checking

        try {
            val snapshot = currentFirestore
                .collection(LICENSE_COLLECTION)
                .document(email)
                .get(Source.SERVER)
                .awaitResult()

            if (requestVersion != verificationVersion || !isForeground) return
            state = if (snapshot.exists()) {
                BetaAccessState.Granted(email)
            } else {
                BetaAccessState.Denied(
                    email = email,
                    message = "Account non autorizzato."
                )
            }
        } catch (error: FirebaseFirestoreException) {
            if (requestVersion != verificationVersion || !isForeground) return
            state = when (error.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    BetaAccessState.Denied(
                        email = email,
                        message = "Account non autorizzato, disabilitato o periodo di prova scaduto."
                    )

                FirebaseFirestoreException.Code.UNAVAILABLE ->
                    BetaAccessState.Error(
                        "Serve una connessione Internet per verificare l'autorizzazione."
                    )

                else ->
                    BetaAccessState.Error(
                        "Verifica dell'autorizzazione non riuscita. Riprova."
                    )
            }
        } catch (_: Exception) {
            if (requestVersion != verificationVersion || !isForeground) return
            state = BetaAccessState.Error(
                "Verifica dell'autorizzazione non riuscita. Riprova."
            )
        }
    }

    suspend fun signIn(context: Context) {
        val currentAuth = auth
        if (currentAuth == null || state is BetaAccessState.ConfigurationMissing) return

        state = BetaAccessState.Checking

        try {
            val option = GetSignInWithGoogleOption.Builder(
                serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
            ).build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()

            val credential = CredentialManager.create(context)
                .getCredential(context = context, request = request)
                .credential

            if (
                credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                state = BetaAccessState.Error("Credenziale Google non valida.")
                return
            }

            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(
                googleCredential.idToken,
                null
            )

            currentAuth.signInWithCredential(firebaseCredential).awaitResult()
            refreshAccess()
        } catch (_: GetCredentialException) {
            state = BetaAccessState.SignedOut
        } catch (_: Exception) {
            state = BetaAccessState.Error(
                "Accesso con Google non riuscito. Riprova."
            )
        }
    }

    suspend fun signOut(context: Context) {
        verificationVersion++
        auth?.signOut()
        runCatching {
            CredentialManager.create(context)
                .clearCredentialState(ClearCredentialStateRequest())
        }
        state = BetaAccessState.SignedOut
    }
}

@Composable
internal fun BetaAccessGate(repository: PortRepository) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember {
        BetaAccessManager(context.applicationContext)
    }
    val scope = rememberCoroutineScope()
    val state = manager.state

    // Anche se Android sospende il controllo periodico in background, ogni
    // ritorno in primo piano blocca immediatamente la UI e richiede Firebase.
    DisposableEffect(manager, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    manager.onForeground()
                    scope.launch { manager.refreshAccess() }
                }
                Lifecycle.Event.ON_PAUSE -> manager.onBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            manager.onBackground()
        }
    }

    LaunchedEffect(manager, lifecycleOwner) {
        while (true) {
            delay(ACCESS_RECHECK_MILLIS)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                manager.refreshAccess()
            }
        }
    }

    if (state is BetaAccessState.Granted) {
        PortShiftApp(repository)
        return
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "PortShiftTracker Beta",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                when (state) {
                    BetaAccessState.Checking -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Verifica autorizzazione in corso…")
                    }

                    BetaAccessState.SignedOut -> {
                        Text(
                            "Questa versione è riservata agli account Google autorizzati.",
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                scope.launch { manager.signIn(context) }
                            }
                        ) {
                            Text("Accedi con Google")
                        }
                    }

                    is BetaAccessState.Denied -> {
                        state.email?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            state.message,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                scope.launch {
                                    manager.signOut(context)
                                    manager.signIn(context)
                                }
                            }
                        ) {
                            Text("Usa un altro account Google")
                        }
                    }

                    is BetaAccessState.ConfigurationMissing -> {
                        Text(
                            "Firebase non è ancora configurato per questa build.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Mancano: " + state.missingKeys.joinToString(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    is BetaAccessState.Error -> {
                        Text(
                            state.message,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                scope.launch { manager.refreshAccess() }
                            }
                        ) {
                            Text("Riprova")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                scope.launch { manager.signOut(context) }
                            }
                        ) {
                            Text("Cambia account")
                        }
                    }

                    is BetaAccessState.Granted -> Unit
                }
            }
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(
                    task.exception ?: IllegalStateException("Operazione Firebase non riuscita.")
                )
            }
        }
    }
