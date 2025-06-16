package com.github.egorbaranov.cod3.completions

import com.github.egorbaranov.cod3.settings.PluginSettingsState
import com.intellij.util.net.ssl.CertificateManager
import ee.carlrobert.llm.client.anthropic.ClaudeClient
import ee.carlrobert.llm.client.google.GoogleClient
import ee.carlrobert.llm.client.llama.LlamaClient
import ee.carlrobert.llm.client.ollama.OllamaClient
import ee.carlrobert.llm.client.openai.OpenAIClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.net.ssl.X509TrustManager

class CompletionClientProvider(
    private val settings: PluginSettingsState = PluginSettingsState.getInstance()
) {

    fun getOpenAIClient(): OpenAIClient =
        OpenAIClient.Builder(settings.openAIApiKey)
            .setHost(settings.openAIApiUrl)
            .build(getDefaultClientBuilder())

    fun getGoogleClient(): GoogleClient =
        GoogleClient.Builder(settings.googleApiKey)
            .setHost(settings.googleApiUrl)
            .build(getDefaultClientBuilder())

    fun getClaudeClient(): ClaudeClient =
        ClaudeClient.Builder(settings.claudeApiKey, settings.claudeApiVersion)
            .setHost(settings.claudeApiUrl)
            .build(getDefaultClientBuilder())

    fun getOllamaClient(): OllamaClient =
        OllamaClient.Builder()
            .setHost(settings.ollamaApiUrl)
            .setPort(settings.ollamaPort)
            .setApiKey(settings.ollamaApiKey)
            .build(getDefaultClientBuilder())

    fun getLlamaClient(): LlamaClient =
        LlamaClient.Builder()
            .setHost(settings.llamaApiUrl)
            .setPort(settings.llamaPort)
            .setApiKey(settings.llamaApiKey)
            .build(getDefaultClientBuilder())

    private fun getDefaultClientBuilder(): OkHttpClient.Builder {
        val connectTimeout = 120L
        val readTimeout = 600L

        val builder = OkHttpClient.Builder()
        val certificateManager = CertificateManager.getInstance()
        val trustManager: X509TrustManager = certificateManager.trustManager
        builder.sslSocketFactory(certificateManager.sslContext.socketFactory, trustManager)
        return builder
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
    }
}