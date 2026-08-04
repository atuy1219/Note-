package com.atuy.note

import android.app.PendingIntent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.atuy.note.sync.DRIVE_APPDATA_SCOPE
import com.atuy.note.ui.EnhancedNoteApp
import com.atuy.note.ui.NoteTheme
import com.atuy.note.ui.rememberUiPreferencesState
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()
    private val authorizationClient by lazy { Identity.getAuthorizationClient(this) }

    private val pdfPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importPdf)
    }

    private val imagePicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::importImage)
    }

    private val driveResolution = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        runCatching { authorizationClient.getAuthorizationResultFromIntent(data) }
            .onSuccess { auth ->
                auth.accessToken?.let(viewModel::syncWithDrive)
                    ?: viewModel.reportStatus("Google Drive did not return an access token")
            }
            .onFailure { viewModel.reportStatus(it.message ?: "Google Drive authorization failed") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiPreferences = rememberUiPreferencesState()
            NoteTheme(themeMode = uiPreferences.themeMode) {
                EnhancedNoteApp(
                    viewModel = viewModel,
                    uiPreferences = uiPreferences,
                    onImportPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                    onImportImage = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onSyncDrive = ::authorizeDrive,
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (viewModel.handleStylusKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun authorizeDrive() {
        val request = AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
        authorizationClient.authorize(request)
            .addOnSuccessListener { result ->
                when {
                    result.hasResolution() -> launchDriveResolution(result.pendingIntent)
                    result.accessToken != null -> viewModel.syncWithDrive(result.accessToken!!)
                    else -> viewModel.reportStatus("Google Drive authorization returned no token")
                }
            }
            .addOnFailureListener { error ->
                viewModel.reportStatus(error.message ?: "Google Drive authorization failed")
            }
    }

    private fun launchDriveResolution(pendingIntent: PendingIntent?) {
        if (pendingIntent == null) {
            viewModel.reportStatus("Google Drive authorization cannot be opened")
            return
        }
        driveResolution.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
    }
}
