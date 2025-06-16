package com.github.egorbaranov.cod3.state

import com.github.egorbaranov.cod3.settings.PluginSettingsState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "ChatState",
    storages = [Storage("chat.xml")]
)
class ChatState: PersistentStateComponent<ChatState> {

    var chats: List<Chat> = mutableListOf()

    override fun getState(): ChatState = this
    override fun loadState(state: ChatState) {
        state.copyStateTo(this)
    }

    fun copyStateTo(target: ChatState) {
        target.chats = this.chats
    }


    data class Chat(
        val messages: MutableList<Message>
    ) {
        data class Message(
            val text: String,
            val attachments: List<Attachment>
        )

        data class Attachment(
            val text: String
        )
    }

    companion object {
        fun getInstance(): ChatState =
            ApplicationManager.getApplication().getService(ChatState::class.java)
    }
}