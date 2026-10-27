package app.vohcarb.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.vohcarb.core.api.ApiException
import app.vohcarb.core.api.userMessage
import app.vohcarb.core.session.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignInState(
    val serverUrl: String,
    val email: String = "",
    val displayName: String = "",
    val busy: Boolean = false,
    val error: String? = null,
) {
    val canSubmit get() = !busy && serverUrl.isNotBlank() && email.contains('@')
}

class SignInViewModel(private val auth: AuthApi, private val sessions: SessionStore) : ViewModel() {

    private val _state = MutableStateFlow(SignInState(serverUrl = sessions.serverUrl))
    val state = _state.asStateFlow()

    fun edit(change: SignInState.() -> SignInState) = _state.update { it.change().copy(error = null) }

    fun signIn() {
        val form = _state.value
        if (!form.canSubmit) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                sessions.serverUrl = form.serverUrl
                auth.devLogin(form.email, form.displayName) // the app switches to the main screens on success
                _state.update { it.copy(busy = false) } // ready again for after a sign-out
            } catch (e: Exception) {
                val message = if ((e as? ApiException)?.isNotFound == true) {
                    "This server has no dev login. Is the backend running with the dev profile?"
                } else {
                    e.userMessage
                }
                _state.update { it.copy(busy = false, error = message) }
            }
        }
    }
}

@Composable
fun SignInScreen(viewModel: SignInViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Text("Vohcarb", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            "Learn a few words a day.",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = state.email,
            onValueChange = { v -> viewModel.edit { copy(email = v) } },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.displayName,
            onValueChange = { v -> viewModel.edit { copy(displayName = v) } },
            label = { Text("Name (optional)") },
            supportingText = { Text("What friends in your groups see") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.serverUrl,
            onValueChange = { v -> viewModel.edit { copy(serverUrl = v) } },
            label = { Text("Server") },
            supportingText = { Text("Emulator: http://10.0.2.2:8080 · Phone: http://<your PC's IP>:8080") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = viewModel::signIn, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Sign in")
        }
        Text(
            "Development sign-in: any email works. Google Sign-In comes later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
