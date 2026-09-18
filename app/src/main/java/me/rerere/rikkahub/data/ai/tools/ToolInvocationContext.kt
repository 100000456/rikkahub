package me.rerere.rikkahub.data.ai.tools

/** 调用上下文：谁在调工具、是不是后台无人值守触发。 */
data class ToolInvocationContext(
    val callerAssistantId: String? = null,
    val callerConversationId: String? = null,
    val isHeadless: Boolean = false,
) {
    companion object {
        val EMPTY = ToolInvocationContext()
    }
}
