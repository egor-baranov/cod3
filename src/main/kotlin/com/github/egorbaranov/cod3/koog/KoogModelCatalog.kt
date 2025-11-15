package com.github.egorbaranov.cod3.koog

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel
import com.github.egorbaranov.cod3.ui.Icons
import javax.swing.Icon

/**
 * Centralized registry of Koog-compatible LLM models that we expose through the plugin UI.
 */
object KoogModelCatalog {

    data class Entry(
        val id: String,
        val label: String,
        val model: LLModel,
        val provider: Provider
    )

    enum class Provider(val displayName: String, val icon: Icon) {
        OPENAI("OpenAI", Icons.OpenAI),
        ANTHROPIC("Anthropic", Icons.Anthropic),
        GOOGLE("Google Gemini", Icons.Google)
    }

    val availableModels: List<Entry> = listOf(
        Entry(
            id = OpenAIModels.CostOptimized.GPT4oMini.id,
            label = "GPT-4o Mini (cost-optimized)",
            model = OpenAIModels.CostOptimized.GPT4oMini,
            provider = Provider.OPENAI
        ),
        Entry(
            id = OpenAIModels.Chat.GPT4o.id,
            label = "GPT-4o",
            model = OpenAIModels.Chat.GPT4o,
            provider = Provider.OPENAI
        ),
        Entry(
            id = OpenAIModels.Chat.GPT4_1.id,
            label = "GPT-4.1",
            model = OpenAIModels.Chat.GPT4_1,
            provider = Provider.OPENAI
        ),
        Entry(
            id = OpenAIModels.Chat.GPT5Mini.id,
            label = "GPT-5 Mini",
            model = OpenAIModels.Chat.GPT5Mini,
            provider = Provider.OPENAI
        ),
        Entry(
            id = AnthropicModels.Sonnet_3_5.id,
            label = "Claude 3.5 Sonnet",
            model = AnthropicModels.Sonnet_3_5,
            provider = Provider.ANTHROPIC
        ),
        Entry(
            id = AnthropicModels.Haiku_3_5.id,
            label = "Claude 3.5 Haiku",
            model = AnthropicModels.Haiku_3_5,
            provider = Provider.ANTHROPIC
        ),
        Entry(
            id = AnthropicModels.Sonnet_4_5.id,
            label = "Claude 4.5 Sonnet",
            model = AnthropicModels.Sonnet_4_5,
            provider = Provider.ANTHROPIC
        ),
        Entry(
            id = AnthropicModels.Opus_4.id,
            label = "Claude 4 Opus",
            model = AnthropicModels.Opus_4,
            provider = Provider.ANTHROPIC
        ),
        Entry(
            id = GoogleModels.Gemini2_5Pro.id,
            label = "Gemini 2.5 Pro",
            model = GoogleModels.Gemini2_5Pro,
            provider = Provider.GOOGLE
        ),
        Entry(
            id = GoogleModels.Gemini2_5Flash.id,
            label = "Gemini 2.5 Flash",
            model = GoogleModels.Gemini2_5Flash,
            provider = Provider.GOOGLE
        ),
        Entry(
            id = GoogleModels.Gemini2_5FlashLite.id,
            label = "Gemini 2.5 Flash Lite",
            model = GoogleModels.Gemini2_5FlashLite,
            provider = Provider.GOOGLE
        ),
        Entry(
            id = GoogleModels.Gemini2_0Flash.id,
            label = "Gemini 2.0 Flash",
            model = GoogleModels.Gemini2_0Flash,
            provider = Provider.GOOGLE
        ),
        Entry(
            id = GoogleModels.Gemini2_0FlashLite.id,
            label = "Gemini 2.0 Flash Lite",
            model = GoogleModels.Gemini2_0FlashLite,
            provider = Provider.GOOGLE
        )
    )

    val defaultModelId: String = availableModels.first { it.provider == Provider.OPENAI }.id

    fun resolveEntry(id: String?): Entry =
        availableModels.firstOrNull { it.id == id } ?: availableModels.first { it.id == defaultModelId }

    fun resolveModel(id: String?): LLModel = resolveEntry(id).model
}
