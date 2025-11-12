package com.github.egorbaranov.cod3.toolWindow.chat

import com.agentclientprotocol.model.ToolCallStatus
import com.agentclientprotocol.model.ToolKind
import com.github.egorbaranov.cod3.acp.ToolCallSnapshot
import com.github.egorbaranov.cod3.koog.KoogToolCallSnapshot

data class ToolCallViewModel(
    val id: String,
    val title: String?,
    val name: String?,
    val statusText: String?,
    val kindText: String?,
    val arguments: Map<String, String>,
    val output: List<String>
)

fun ToolCallSnapshot.toViewModel(): ToolCallViewModel = ToolCallViewModel(
    id = id,
    title = title,
    name = name,
    statusText = status?.readable(),
    kindText = kind?.readable(),
    arguments = arguments,
    output = content
)

fun KoogToolCallSnapshot.toViewModel(): ToolCallViewModel = ToolCallViewModel(
    id = id,
    title = title,
    name = name,
    statusText = status,
    kindText = null,
    arguments = arguments,
    output = output
)

private fun ToolCallStatus.readable(): String = name.lowercase().replace('_', ' ')

private fun ToolKind.readable(): String = name.lowercase().replace('_', ' ')
