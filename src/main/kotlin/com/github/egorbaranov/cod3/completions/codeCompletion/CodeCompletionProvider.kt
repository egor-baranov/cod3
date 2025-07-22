package com.github.egorbaranov.cod3.completions.codeCompletion

import com.intellij.codeInsight.inline.completion.*
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emptyFlow
import okhttp3.sse.EventSource
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import ee.carlrobert.llm.completion.CompletionEventListener
import kotlinx.coroutines.channels.awaitClose

class CodeCompletionProvider : DebouncedInlineCompletionProvider() {

    private val currentCallRef = AtomicReference<EventSource?>(null)

    override val id: InlineCompletionProviderID
        get() = InlineCompletionProviderID("Cod3")

    override fun isEnabled(event: InlineCompletionEvent): Boolean = true

    override suspend fun getSuggestionDebounced(request: InlineCompletionRequest): InlineCompletionSuggestion {
        val editor = request.editor
        val project = editor.project ?: return InlineCompletionSingleSuggestion.build(elements = emptyFlow())

        if (LookupManager.getActiveLookup(editor) != null) {
            return InlineCompletionSingleSuggestion.build(elements = emptyFlow())
        }

        // Read document text and caret offset once
        val (text, offset) = ReadAction.compute<Pair<String, Int>, Throwable> {
            editor.document.text to editor.caretModel.offset
        }
        val prefix = text.take(offset)
        val suffix = text.drop(offset)

        // Check cache before starting any remote call
        val rawCache = tryFindCache(project, prefix, suffix)
        val cacheValue = rawCache?.takeIf { it.isNotBlank() }
        if (cacheValue != null) {
            println("using cached value: $cacheValue")
            return InlineCompletionSingleSuggestion.build(
                elements = flowOf(InlineCompletionGrayTextElement(cacheValue))
            )
        }

        // No cache hit: stream from remote service and populate cache
        return InlineCompletionSingleSuggestion.build(elements = channelFlow {
            try {
                val eventListener = object : CompletionEventListener<String> {
                    override fun onMessage(message: String?, eventSource: EventSource?) {
                        if (message == null) return
                        println("got message fragment: $message")
                        // Append to existing cache
                        val current = tryFindCache(project, prefix, suffix).orEmpty()
                        val updated = current + message
                        println("update cache from $current to $updated")
                        project.service<CodeCompletionCacheService>().setCache(prefix, suffix, updated)
                        trySend(InlineCompletionGrayTextElement(message))
                    }
                }
                val call = project.service<CodeCompletionService>()
                    .getCodeCompletionAsync(prefix, suffix, eventListener)
                currentCallRef.set(call)
            } finally {
                awaitClose { currentCallRef.getAndSet(null)?.cancel() }
            }
        })
    }

    private fun tryFindCache(project: Project, prefix: String, suffix: String): String? {
        return project.service<CodeCompletionCacheService>().getCache(prefix, suffix)
    }

    override suspend fun getDebounceDelay(request: InlineCompletionRequest): Duration {
        return 300.toDuration(DurationUnit.MILLISECONDS)
    }
}
