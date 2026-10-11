package com.example.personaapp.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class ManualLlmTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val prompt: String,
    val deferredResult: CompletableDeferred<String>
)

object ManualLlmQueue {
    private val _pendingTasks = MutableStateFlow<List<ManualLlmTask>>(emptyList())
    val pendingTasks: StateFlow<List<ManualLlmTask>> = _pendingTasks.asStateFlow()

    suspend fun executeTask(title: String, prompt: String): String {
        val deferred = CompletableDeferred<String>()
        val task = ManualLlmTask(title = title, prompt = prompt, deferredResult = deferred)
        _pendingTasks.value = _pendingTasks.value + task

        deferred.invokeOnCompletion {
            _pendingTasks.value = _pendingTasks.value.filter { it.id != task.id }
        }

        return deferred.await()
    }

    fun submitResult(taskId: String, result: String) {
        val task = _pendingTasks.value.find { it.id == taskId }
        task?.deferredResult?.complete(result)
    }

    fun cancelTask(taskId: String) {
        val task = _pendingTasks.value.find { it.id == taskId }
        task?.deferredResult?.cancel()
    }
}
