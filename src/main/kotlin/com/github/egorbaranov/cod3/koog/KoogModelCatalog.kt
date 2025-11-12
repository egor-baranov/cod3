package com.github.egorbaranov.cod3.koog

import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel

/**
 * Centralized registry of Koog-compatible LLM models that we expose through the plugin UI.
 */
object KoogModelCatalog {

    data class Entry(
        val id: String,
        val label: String,
        val model: LLModel
    )

    val availableModels: List<Entry> = listOf(
        Entry(
            id = OpenAIModels.CostOptimized.GPT4oMini.id,
            label = "OpenAI GPT-4o Mini (cost-optimized)",
            model = OpenAIModels.CostOptimized.GPT4oMini
        ),
        Entry(
            id = OpenAIModels.Chat.GPT4o.id,
            label = "OpenAI GPT-4o",
            model = OpenAIModels.Chat.GPT4o
        ),
        Entry(
            id = OpenAIModels.Chat.GPT4_1.id,
            label = "OpenAI GPT-4.1",
            model = OpenAIModels.Chat.GPT4_1
        ),
        Entry(
            id = OpenAIModels.Chat.GPT5Mini.id,
            label = "OpenAI GPT-5 Mini",
            model = OpenAIModels.Chat.GPT5Mini
        )
    )

    val defaultModelId: String = OpenAIModels.CostOptimized.GPT4oMini.id

    fun resolve(id: String?): LLModel {
        return availableModels.firstOrNull { it.id == id }?.model ?: OpenAIModels.CostOptimized.GPT4oMini
    }
}
