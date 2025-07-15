package com.github.egorbaranov.cod3.toolWindow

import com.google.gson.*
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

// 1) Your envelope
data class SSEData(
    val content: JsonElement?,
    @SerializedName("tool_calls")
    val toolCalls: List<ToolCall>?
)

// 2) ToolCall with arguments as a Map<String, String>:
data class ToolCall(
    val name: String?,

    @JsonAdapter(ArgumentsDeserializer::class)
    val arguments: Map<String, String>?
)

// 3) Plan and Content unchanged
data class Plan(
    @SerializedName("type") val type: String,
    @SerializedName("task_title") val taskTitle: String,
    @SerializedName("task_description") val taskDescription: String,
    val steps: List<String>
)

sealed class Content {
    data class PlanContent(val plan: Plan) : Content()
    data class TextContent(val text: String) : Content()
    object None : Content()
}

// 4) Deserializer mapping JsonElement → Map<String, String>
class ArgumentsDeserializer : JsonDeserializer<Map<String, String>> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Map<String, String>? {
        if (json.isJsonNull) return null

        return when {
            json.isJsonObject -> {
                json.asJsonObject.entrySet().associate { (key, elem) ->
                    // for nested objects/arrays we just call toString()
                    key to when {
                        elem.isJsonPrimitive -> elem.asJsonPrimitive.asString
                        else                  -> elem.toString()
                    }
                }
            }

            json.isJsonPrimitive -> {
                // wrap a lone primitive as {"value": "<thatString>"}
                mapOf("value" to json.asString)
            }

            else -> {
                // arrays or whatever—just stringify
                mapOf("value" to json.toString())
            }
        }
    }
}

// 5) Parser unchanged
object SSEParser {
    private val gson: Gson = GsonBuilder().create()

    fun parse(raw: String): Pair<Content, List<ToolCall>?> {
        val envelope = gson.fromJson(raw, SSEData::class.java)

        val content = when {
            envelope.content == null || envelope.content.isJsonNull ->
                Content.None

            envelope.content.isJsonObject && envelope.content.asJsonObject.has("type") -> {
                val plan = gson.fromJson(envelope.content, Plan::class.java)
                Content.PlanContent(plan)
            }

            envelope.content.isJsonPrimitive ->
                Content.TextContent(envelope.content.asString)

            else ->
                Content.TextContent(envelope.content.toString())
        }

        return content to envelope.toolCalls
    }
}
