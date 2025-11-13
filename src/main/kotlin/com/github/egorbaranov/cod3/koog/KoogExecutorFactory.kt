package com.github.egorbaranov.cod3.koog

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import com.github.egorbaranov.cod3.settings.PluginSettingsState

internal object KoogExecutorFactory {

    fun create(settings: PluginSettingsState, provider: KoogModelCatalog.Provider): SingleLLMPromptExecutor {
        return when (provider) {
            KoogModelCatalog.Provider.OPENAI -> createOpenAIExecutor(settings)
            KoogModelCatalog.Provider.ANTHROPIC -> createAnthropicExecutor(settings)
            KoogModelCatalog.Provider.GOOGLE -> createGoogleExecutor(settings)
        }
    }

    private fun createOpenAIExecutor(settings: PluginSettingsState): SingleLLMPromptExecutor {
        val apiKey = settings.openAIApiKey.trim()
        require(apiKey.isNotEmpty()) { "OpenAI API key is not configured in settings." }
        val baseUrl = settings.openAIApiUrl.trim()
        return if (baseUrl.isNotEmpty() && baseUrl != PluginSettingsState.DEFAULT_OPENAI_API_URL) {
            SingleLLMPromptExecutor(OpenAILLMClient(apiKey, OpenAIClientSettings(baseUrl = baseUrl)))
        } else {
            simpleOpenAIExecutor(apiKey)
        }
    }

    private fun createAnthropicExecutor(settings: PluginSettingsState): SingleLLMPromptExecutor {
        val apiKey = settings.claudeApiKey.trim()
        require(apiKey.isNotEmpty()) { "Anthropic API key is not configured in settings." }
        val baseUrl = settings.claudeApiUrl.trim().ifEmpty { PluginSettingsState.DEFAULT_ANTHROPIC_API_URL }
        val apiVersion = settings.claudeApiVersion.trim().ifEmpty { PluginSettingsState.DEFAULT_ANTHROPIC_API_VERSION }
        val client = AnthropicLLMClient(
            apiKey = apiKey,
            settings = AnthropicClientSettings(baseUrl = baseUrl, apiVersion = apiVersion)
        )
        return SingleLLMPromptExecutor(client)
    }

    private fun createGoogleExecutor(settings: PluginSettingsState): SingleLLMPromptExecutor {
        val apiKey = settings.googleApiKey.trim()
        require(apiKey.isNotEmpty()) { "Google API key is not configured in settings." }
        val baseUrl = settings.googleApiUrl.trim()
        return if (baseUrl.isNotEmpty() && baseUrl != PluginSettingsState.DEFAULT_GOOGLE_API_URL) {
            SingleLLMPromptExecutor(GoogleLLMClient(apiKey, GoogleClientSettings(baseUrl = baseUrl)))
        } else {
            simpleGoogleAIExecutor(apiKey)
        }
    }
}
