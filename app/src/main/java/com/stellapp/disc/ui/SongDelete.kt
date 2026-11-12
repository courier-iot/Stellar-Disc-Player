package com.stellapp.disc.ui

import android.app.Activity
import android.app.RecoverableSecurityException
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.stellapp.disc.PlayerViewModel
import com.stellapp.disc.data.Song

/**
 * "Delete from phone" for any screen. Call the returned function with a song:
 * it asks to confirm, then Android shows its own delete prompt (Android 11+).
 */
@Composable
fun rememberSongDeleter(vm: PlayerViewModel): (Song) -> Unit {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<Song?>(null) }
    var pending by remember { mutableStateOf<Song?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val s = pending
        pending = null
        if (result.resultCode == Activity.RESULT_OK && s != null) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                try { context.contentResolver.delete(s.uri, null, null) } catch (_: Exception) {}
            }
            vm.afterDelete(listOf(s.id))
        }
    }

    fun delete(s: Song) {
        pending = s
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(s.uri))
                launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } else {
                context.contentResolver.delete(s.uri, null, null)
                pending = null
                vm.afterDelete(listOf(s.id))
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                launcher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
            } else {
                pending = null
                vm.post("Android didn't allow deleting that file")
            }
        } catch (e: Exception) {
            pending = null
            vm.post("Couldn't delete that file")
        }
    }

    confirm?.let { s ->
        ConfirmDialog(
            title = "DELETE FILE",
            body = "Delete \"${s.title}\" from your phone? This can't be undone.",
            confirmLabel = "DELETE",
            onDismiss = { confirm = null },
        ) {
            confirm = null
            delete(s)
        }
    }

    return { s -> if (!s.isStream) confirm = s }
}
