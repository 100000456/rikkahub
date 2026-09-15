package me.rerere.rikkahub.data.service

import kotlinx.coroutines.flow.first
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Bot 通道调 AI 的桥。
 *
 * 跟主动消息走同一条路：拿助手 → 拿最近一条会话 → 发一句 → 等它回 → 把来回都写回会话。
 * 不碰 ChatService 的流式接口，避免两个内核版本 API 对不上。
 */
object BotAiBridge {

    suspend fun ask(
        settingsStore: SettingsStore,
        conversationRepository: ConversationRepository,
        providerManager: ProviderManager,
        chatService: ChatService?,
        text: String,
        assistantId: String = "",
    ): String {
        val settings = settingsStore.settingsFlow.first()
        val assistant = if (assistantId.isBlank()) {
            settings.getCurrentAssistant()
        } else {
            settings.assistants.find { it.id.toString() == assistantId }
                ?: settings.getCurrentAssistant()
        }

        val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId)
            ?: return "（没有配好可用的模型）"
        val providerSetting = model.findProvider(settings.providers)
            ?: return "（这个模型没挂在任何供应商下面）"
        val provider = providerManager.getProviderByType(providerSetting)

        var conversation = conversationRepository.getRecentConversations(assistant.id, limit = 1)
            .firstOrNull()
            ?.let { conversationRepository.getConversationById(it.id) }
        if (conversation == null) {
            val fresh = Conversation(
                id = Uuid.random(),
                assistantId = assistant.id,
                messageNodes = emptyList()
            )
            conversationRepository.insertConversation(fresh)
            conversation = fresh
        }
        val conversationId = conversation.id

        // 先把用户这句写进会话，再看历史，AI 才知道刚说了什么
        val withUser = conversation.copy(
            messageNodes = conversation.messageNodes +
                UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text(text)))
                    .toMessageNode(),
            updateAt = Instant.now()
        )
        write(chatService, conversationRepository, withUser)

        val messages = buildList {
            if (assistant.systemPrompt.isNotBlank()) {
                add(
                    UIMessage(
                        role = MessageRole.SYSTEM,
                        parts = listOf(UIMessagePart.Text(assistant.systemPrompt))
                    )
                )
            }
            addAll(withUser.currentMessages.takeLast(20))
        }

        val params = TextGenerationParams(
            model = model,
            reasoningLevel = assistant.reasoningLevel
        )

        val reply = runCatching {
            provider.generateText(providerSetting, messages, params)
                .message.parts
                .filterIsInstance<UIMessagePart.Text>()
                .joinToString("") { part -> part.text }
                .trim()
        }.getOrElse { e ->
            "（我这边生成失败了：${e.message ?: e::class.simpleName}）"
        }.ifBlank { "（没憋出话）" }

        val latest = conversationRepository.getConversationById(conversationId) ?: withUser
        write(
            chatService,
            conversationRepository,
            latest.copy(
                messageNodes = latest.messageNodes +
                    UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text(reply)))
                        .toMessageNode(),
                updateAt = Instant.now()
            )
        )
        return reply
    }

    private suspend fun write(
        chatService: ChatService?,
        conversationRepository: ConversationRepository,
        conversation: Conversation,
    ) {
        val service = chatService
        if (service != null) {
            val ok = runCatching {
                service.saveConversation(conversation.id, conversation)
            }.isSuccess
            if (ok) return
        }
        runCatching {
            if (conversationRepository.existsConversationById(conversation.id)) {
                conversationRepository.updateConversation(conversation)
            } else {
                conversationRepository.insertConversation(conversation)
            }
        }
    }
}
