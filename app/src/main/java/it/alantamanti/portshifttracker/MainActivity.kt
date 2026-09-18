package it.alantamanti.portshifttracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import it.alantamanti.portshifttracker.auth.BetaAccessGate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as PortShiftApplication).repository
        setContent { BetaAccessGate(repository) }
    }
}
