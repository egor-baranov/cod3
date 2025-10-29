package com.github.egorbaranov.cod3.acp

import com.agentclientprotocol.model.PlanEntryPriority
import com.agentclientprotocol.model.PlanEntryStatus
import com.agentclientprotocol.model.PromptResponse
import com.agentclientprotocol.model.ToolCallStatus
import com.agentclientprotocol.model.ToolKind

sealed class AcpStreamEvent {
    data class AgentContentText(val text: String) : AcpStreamEvent()
    data class PlanUpdate(val entries: List<PlanEntryView>) : AcpStreamEvent()
    data class ToolCallUpdate(val toolCall: ToolCallSnapshot, val final: Boolean) : AcpStreamEvent()
    data class Completed(val response: PromptResponse) : AcpStreamEvent()
    data class Error(val throwable: Throwable) : AcpStreamEvent()
}

data class PlanEntryView(
    val order: Int,
    val content: String,
    val status: PlanEntryStatus,
    val priority: PlanEntryPriority
)

data class ToolCallSnapshot(
    val id: String,
    val name: String?,
    val title: String?,
    val kind: ToolKind?,
    val arguments: Map<String, String>,
    val status: ToolCallStatus?,
    val content: List<String>
)
