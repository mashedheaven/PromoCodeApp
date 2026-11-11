package com.promocodeapp.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.promocodeapp.data.remote.Session
import com.promocodeapp.data.remote.userMessage
import com.promocodeapp.data.repository.AuthRepository
import com.promocodeapp.data.repository.AuthResult
import com.promocodeapp.data.settings.Settings
import com.promocodeapp.data.settings.SettingsStore
import com.promocodeapp.location.GeofenceRegistrar
import com.promocodeapp.location.Permissions
import com.promocodeapp.sync.SyncEngine
import com.promocodeapp.sync.SyncOutcome
import com.promocodeapp.ui.components.DropdownField
import com.promocodeapp.ui.theme.LocalStatusColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the system currently lets the app do; re-read whenever the screen resumes. */
data class AlertPermissions(val notifications: Boolean, val location: Boolean, val background: Boolean) {
    val ready: Boolean get() = notifications && location && background

    companion object {
        fun read(context: Context) = AlertPermissions(
            notifications = Permissions.canNotify(context),
            location = Permissions.hasFineLocation(context),
            background = Permissions.hasBackgroundLocation(context),
        )
    }
}

data class AccountUi(val busy: Boolean = false, val message: String? = null, val isError: Boolean = false)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val registrar: GeofenceRegistrar,
    private val auth: AuthRepository,
    private val syncEngine: SyncEngine,
) : ViewModel() {
    val settings: StateFlow<Settings> = store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())
    val session: StateFlow<Session?> = auth.session
    val lastSyncAt: StateFlow<Long?> = store.lastSyncAt.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val syncAvailable: Boolean get() = auth.isAvailable

    private val _account = MutableStateFlow(AccountUi())
    val account: StateFlow<AccountUi> = _account.asStateFlow()

    fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch { store.update(transform) }
    }

    /** Registers geofences as soon as the user grants what they need. */
    fun onPermissionsChanged() {
        viewModelScope.launch { registrar.sync(force = true) }
    }

    fun signIn(email: String, password: String) = authenticate { auth.signIn(email, password) }
    fun signUp(email: String, password: String) = authenticate { auth.signUp(email, password) }

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            _account.value = AccountUi(message = "Enter your e-mail address first.", isError = true)
            return
        }
        _account.value = AccountUi(busy = true)
        viewModelScope.launch {
            _account.value = when (val r = auth.sendPasswordReset(email)) {
                is AuthResult.Failed -> AccountUi(message = r.message, isError = true)
                else -> AccountUi(message = "If that address has an account, a reset link is on its way.")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            _account.value = AccountUi(message = "Signed out. Your data is still on this device.")
        }
    }

    fun syncNow() {
        _account.value = AccountUi(busy = true)
        viewModelScope.launch {
            _account.value = try {
                when (syncEngine.sync()) {
                    SyncOutcome.SYNCED -> AccountUi(message = "Everything is up to date.")
                    SyncOutcome.SKIPPED -> AccountUi(message = "Sign in to sync.", isError = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AccountUi(message = e.userMessage(), isError = true)
            }
        }
    }

    private fun authenticate(call: suspend () -> AuthResult) {
        _account.value = AccountUi(busy = true)
        viewModelScope.launch {
            _account.value = when (val r = call()) {
                AuthResult.SignedIn -> AccountUi()
                AuthResult.ConfirmationRequired -> AccountUi(message = "Check your inbox to confirm your address, then sign in.")
                is AuthResult.Failed -> AccountUi(message = r.message, isError = true)
            }
        }
    }

    fun clearMessage() = _account.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenAccount: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            AlertSetupCard(enabled = settings.nearbyAlerts, onChanged = viewModel::onPermissionsChanged)

            SectionTitle("Notifications")
            SwitchRow("Nearby alerts", "When you arrive somewhere you have a coupon or membership", settings.nearbyAlerts) { on ->
                viewModel.update { it.copy(nearbyAlerts = on) }
            }
            SwitchRow("Expiry reminders", "Before a coupon runs out", settings.expiryReminders) { on ->
                viewModel.update { it.copy(expiryReminders = on) }
            }
            if (settings.expiryReminders) {
                DropdownField(
                    label = "Remind me",
                    value = settings.expiryLeadDays,
                    options = listOf(0, 1, 2, 3, 7),
                    optionLabel = { if (it == 0) "On the last day" else if (it == 1) "1 day before" else "$it days before" },
                    onSelect = { d -> viewModel.update { it.copy(expiryLeadDays = d) } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            SwitchRow("Membership reminders", "Before a membership renews", settings.membershipReminders) { on ->
                viewModel.update { it.copy(membershipReminders = on) }
            }

            SectionTitle("Coupons")
            SwitchRow("Archive expired coupons", "A week after they expire", settings.autoArchiveExpired) { on ->
                viewModel.update { it.copy(autoArchiveExpired = on) }
            }
            DropdownField(
                label = "Default alert radius",
                value = settings.defaultRadiusMeters,
                options = listOf(100, 150, 200, 300, 500, 1000),
                optionLabel = { "$it m" },
                onSelect = { r -> viewModel.update { it.copy(defaultRadiusMeters = r) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )

            SectionTitle("Backup & sync")
            if (viewModel.syncAvailable) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenAccount).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(session?.email ?: "Sign in", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (session != null) "Syncing across your devices" else "Back up your coupons and use them on other devices",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    "Your data is stored on this device and included in Android's device backup. Cloud sync isn't set up in this build.",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

/** Walks the user through the three grants nearby alerts depend on. */
@Composable
private fun AlertSetupCard(enabled: Boolean, onChanged: () -> Unit) {
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(AlertPermissions.read(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    fun reread() {
        val now = AlertPermissions.read(context)
        if (now != permissions) {
            permissions = now
            onChanged()
        }
        refresh++
    }
    // Returning from system settings may have changed things.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reread() }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) openAppSettings(context, notifications = true)
        reread()
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        // "Approximate only" is not enough for geofences; send the user to fix it.
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] != true) openAppSettings(context)
        reread()
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) openAppSettings(context)
        reread()
    }

    val status = LocalStatusColors.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ok = permissions.ready && enabled
                Icon(
                    if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null,
                    tint = if (ok) status.ok else status.warning,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    when {
                        !enabled -> "Nearby alerts are switched off"
                        ok -> "Nearby alerts are on"
                        else -> "Finish setting up nearby alerts"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            when {
                !enabled || permissions.ready -> Text(
                    "Your location is checked on this device only and is never uploaded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                !permissions.notifications -> Step("Allow notifications so alerts can reach you.", "Allow notifications") {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else openAppSettings(context, notifications = true)
                }
                !permissions.location -> Step("Allow precise location so the app knows when you reach a store.", "Allow location") {
                    locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                else -> Step(
                    "Last step: choose \"Allow all the time\" so alerts work while the app is closed. " +
                        "Android handles this efficiently and your location never leaves your phone.",
                    "Allow all the time",
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }
            }
        }
    }
}

@Composable
private fun Step(text: String, action: String, onClick: () -> Unit) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FilledTonalButton(onClick = onClick) { Text(action) }
}

private fun openAppSettings(context: Context, notifications: Boolean = false) {
    val intent = if (notifications) {
        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun SectionTitle(text: String) {
    HorizontalDivider()
    Text(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val ui by viewModel.account.collectAsStateWithLifecycle()
    val lastSync by viewModel.lastSyncAt.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & sync") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val current = session
            if (current != null) {
                Text("Signed in as", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(current.email, style = MaterialTheme.typography.titleMedium)
                Text(
                    lastSync?.let { "Last synced ${DateUtils.getRelativeTimeSpanString(it)}" } ?: "Not synced yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = viewModel::syncNow, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (ui.busy) "Syncing…" else "Sync now")
                }
                OutlinedButton(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
            } else {
                Text(
                    "Sign in to back up your coupons, places and memberships and keep them in step across devices. " +
                        "Signing in to a different account than last time replaces the data on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; viewModel.clearMessage() },
                    label = { Text("E-mail") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; viewModel.clearMessage() },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                val canSubmit = !ui.busy && email.contains('@') && password.length >= 6
                Button(onClick = { viewModel.signIn(email, password) }, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
                    Text(if (ui.busy) "Please wait…" else "Sign in")
                }
                OutlinedButton(onClick = { viewModel.signUp(email, password) }, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
                    Text("Create account")
                }
                TextButton(onClick = { viewModel.resetPassword(email) }, enabled = !ui.busy) { Text("Forgot password?") }
            }
            ui.message?.let {
                Text(it, color = if (ui.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
