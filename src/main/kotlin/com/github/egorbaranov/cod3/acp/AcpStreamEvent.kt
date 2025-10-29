package com.github.egorbaranov.cod3.acp

import com.agentclientprotocol.model.PromptResponse
import com.agentclientprotocol.model.ToolCallStatus

sealed class AcpStreamEvent {
    data class AgentContentText(val text: String) : AcpStreamEvent()
    data class ToolCallUpdate(val toolCall: ToolCallSnapshot, val final: Boolean) : AcpStreamEvent()
    data class Completed(val response: PromptResponse) : AcpStreamEvent()
    data class Error(val throwable: Throwable) : AcpStreamEvent()
}

data class ToolCallSnapshot(
    val id: String,
    val name: String?,
    val arguments: Map<String, String>,
    val status: ToolCallStatus?
)
