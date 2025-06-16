package com.github.egorbaranov.cod3.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import ee.carlrobert.llm.client.google.models.GoogleModel

@State(
    name = "MyPluginSettings",
    storages = [Storage("my-plugin-settings.xml")]
)
class PluginSettingsState : PersistentStateComponent<PluginSettingsState> {

    var openAIApiKey: String = ""
    var openAIApiUrl: String = DEFAULT_OPENAI_API_URL

    var googleApiKey: String = ""
    var googleApiUrl: String = ""
    var googleModel: GoogleModel = GoogleModel.GEMINI_2_5_PRO_EXP

    var claudeApiKey: String = ""
    var claudeApiVersion: String = ""
    var claudeApiUrl: String = ""

    var ollamaApiKey: String = ""
    var ollamaApiUrl: String = ""
    var ollamaPort: Int = 8080

    var llamaApiKey: String = ""
    var llamaApiUrl: String = ""
    var llamaPort: Int = 8081

    var retryQuantity: Int = 1
    var indexingSteps: Int = 1

    override fun getState(): PluginSettingsState = this

    override fun loadState(state: PluginSettingsState) {
        state.copyStateTo(this)
    }

    fun copyStateTo(target: PluginSettingsState) {
        target.openAIApiKey = this.openAIApiKey
        target.openAIApiUrl = this.openAIApiUrl
    }

    companion object {
        const val DEFAULT_OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"

        fun getInstance(): PluginSettingsState =
            ApplicationManager.getApplication().getService(PluginSettingsState::class.java)
    }
}