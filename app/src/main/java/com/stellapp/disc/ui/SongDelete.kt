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
 * "Delete from phone" for any screen. Call the returned function with one or more songs:
 * it asks to confirm, then Android shows its own prompt. On Android 11+ files go to the
 * phone's trash (restorable for about 30 days from a Files app) instead of being erased.
 */
@Composable
fun rememberSongsDeleter(vm: PlayerViewModel): (List<Song>) -> Unit {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<List<Song>?>(null) }
    var pending by remember { mutableStateOf<List<Song>?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val list = pending
        pending = null
        if (result.resultCode == Activity.RESULT_OK && list != null) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                list.forEach { s -> try { context.contentResolver.delete(s.uri, null, null) } catch (_: Exception) {} }
            }
            vm.afterDelete(list.map { it.id })
        }
    }

    fun delete(list: List<Song>) {
        pending = list
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pi = MediaStore.createTrashRequest(context.contentResolver, list.map { it.uri }, true)
                launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } else {
                list.forEach { s -> context.contentResolver.delete(s.uri, null, null) }
                pending = null
                vm.afterDelete(list.map { it.id })
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && e is RecoverableSecurityException && list.size == 1) {
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

    confirm?.let { list ->
        val what = if (list.size == 1) "\"${list[0].title}\"" else "${list.size} files"
        val where = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "They go to your phone's trash, where a Files app can restore them for about 30 days."
        } else {
            "This can't be undone."
        }
        ConfirmDialog(
            title = if (list.size == 1) "DELETE FILE" else "DELETE ${list.size} FILES",
            body = "Delete $what from your phone? $where",
            confirmLabel = "DELETE",
            onDismiss = { confirm = null },
        ) {
            confirm = null
            delete(list)
        }
    }

    return { list -> list.filter { !it.isStream }.takeIf { it.isNotEmpty() }?.let { confirm = it } }
}

/** Single-song version of [rememberSongsDeleter]. */
@Composable
fun rememberSongDeleter(vm: PlayerViewModel): (Song) -> Unit {
    val many = rememberSongsDeleter(vm)
    return { s -> many(listOf(s)) }
}
