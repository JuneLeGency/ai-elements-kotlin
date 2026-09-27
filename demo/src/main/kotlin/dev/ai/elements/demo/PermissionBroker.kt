package dev.ai.elements.demo

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import dev.ai.elements.harness.device.PermissionGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runtime permission requests for agent tools: the visible activity registers its launcher, and
 * a tool call waits for the user's answer. With no activity (app in the background) it denies.
 */
object PermissionBroker : PermissionGate {
    private var launcher: ActivityResultLauncher<Array<String>>? = null
    private var pending: CompletableDeferred<Boolean>? = null
    private val one = Mutex()

    /** Call from the activity's `onCreate`, before it is started. */
    fun attach(activity: ComponentActivity) {
        launcher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            pending?.complete(result.values.all { it })
        }
    }

    fun detach(activity: ComponentActivity) {
        if (activity.isFinishing) launcher = null
        pending?.complete(false)
    }

    override suspend fun request(permissions: List<String>): Boolean = one.withLock {
        val launcher = launcher ?: return@withLock false
        val answer = CompletableDeferred<Boolean>().also { pending = it }
        withContext(Dispatchers.Main) { launcher.launch(permissions.toTypedArray()) }
        answer.await()
    }
}
