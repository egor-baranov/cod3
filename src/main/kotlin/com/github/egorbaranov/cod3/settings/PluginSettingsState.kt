package com.github.egorbaranov.cod3.settings

import com.github.egorbaranov.cod3.koog.KoogModelCatalog
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "MyPluginSettings",
    storages = [Storage("my-plugin-settings.xml")]
)
class PluginSettingsState : PersistentStateComponent<PluginSettingsState> {

    var openAIApiKey: String = ""
    var openAIApiUrl: String = DEFAULT_OPENAI_API_URL

    var googleApiKey: String = ""
    var googleApiUrl: String = ""

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

    var useKoogAgents: Boolean = false
    var koogSystemPrompt: String = DEFAULT_KOOG_SYSTEM_PROMPT
    var koogModelId: String = DEFAULT_KOOG_MODEL_ID
    var koogMcpCommand: String = ""
    var koogMcpWorkingDirectory: String = ""
    var koogMcpClientName: String = "cod3-koog-mcp"

    var useAgentClientProtocol: Boolean = false
    var acpAgentCommand: String = ""
    var acpAgentWorkingDirectory: String = ""
    var acpSessionRoot: String = ""

    override fun getState(): PluginSettingsState = this

    override fun loadState(state: PluginSettingsState) {
        state.copyStateTo(this)
    }

    fun copyStateTo(target: PluginSettingsState) {
        target.openAIApiKey = this.openAIApiKey
        target.openAIApiUrl = this.openAIApiUrl
        target.googleApiKey = this.googleApiKey
        target.googleApiUrl = this.googleApiUrl
        target.claudeApiKey = this.claudeApiKey
        target.claudeApiVersion = this.claudeApiVersion
        target.claudeApiUrl = this.claudeApiUrl
        target.ollamaApiKey = this.ollamaApiKey
        target.ollamaApiUrl = this.ollamaApiUrl
        target.ollamaPort = this.ollamaPort
        target.llamaApiKey = this.llamaApiKey
        target.llamaApiUrl = this.llamaApiUrl
        target.llamaPort = this.llamaPort
        target.retryQuantity = this.retryQuantity
        target.indexingSteps = this.indexingSteps
        target.useKoogAgents = this.useKoogAgents
        target.koogSystemPrompt = this.koogSystemPrompt
        target.koogModelId = this.koogModelId
        target.koogMcpCommand = this.koogMcpCommand
        target.koogMcpWorkingDirectory = this.koogMcpWorkingDirectory
        target.koogMcpClientName = this.koogMcpClientName
        target.useAgentClientProtocol = this.useAgentClientProtocol
        target.acpAgentCommand = this.acpAgentCommand
        target.acpAgentWorkingDirectory = this.acpAgentWorkingDirectory
        target.acpSessionRoot = this.acpSessionRoot
    }

    companion object {
        const val DEFAULT_OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
        const val DEFAULT_ANTHROPIC_API_URL = "https://api.anthropic.com"
        const val DEFAULT_ANTHROPIC_API_VERSION = "2023-06-01"
        const val DEFAULT_GOOGLE_API_URL = "https://generativelanguage.googleapis.com"
        private const val DEFAULT_KOOG_SYSTEM_PROMPT =
            "You are Cod3, an IDE-native coding assistant focused on practical, minimal changes."
        private val DEFAULT_KOOG_MODEL_ID = KoogModelCatalog.defaultModelId

        fun getInstance(): PluginSettingsState =
            ApplicationManager.getApplication().getService(PluginSettingsState::class.java)
    }
}
