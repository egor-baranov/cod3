package com.github.egorbaranov.cod3.state

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "ChatState",
    storages = [Storage("chat.xml")]
)
class ChatState : PersistentStateComponent<ChatState.State> {

    data class State(
        var chats: MutableList<Chat> = mutableListOf()
    )

    private var state = State()

    var chats: MutableList<Chat>
        get() = state.chats
        set(value) {
            state.chats = value
        }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    data class Chat(
        var id: Int = 0,
        var title: String = "",
        var messages: MutableList<Message> = mutableListOf()
    ) {
        data class Message(
            var role: String = "USER",
            var text: String = "",
            var attachments: List<Attachment> = emptyList()
        )

        data class Attachment(
            var title: String = "",
            var content: String = "",
            var navigationPath: String? = null
        )
    }

    companion object {
        fun getInstance(): ChatState =
            ApplicationManager.getApplication().getService(ChatState::class.java)
    }
}
