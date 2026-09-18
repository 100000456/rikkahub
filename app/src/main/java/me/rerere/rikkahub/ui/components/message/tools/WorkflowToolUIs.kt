package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import me.rerere.rikkahub.workflow.tools.WorkflowApprovalRenderer

/**
 * 工作流的工具调用在聊天里怎么说人话。
 *
 * 橘瓣那边把「翻译」写好了（WorkflowApprovalRenderer），但界面上没人接，
 * 弹出来还是一串参数。搬过来我手动挂上：审批卡上看到的是
 * 「Create workflow xxx / When: 每天 9:00 / Do: 1. 发通知 ...」这种。
 */
internal abstract class WorkflowToolUIBase : ToolUIRenderer {

    private fun plain(context: ToolUIContext): String =
        WorkflowApprovalRenderer.renderPlain(
            context.tool.toolName,
            context.arguments.toString(),
        )

    private fun prettyName(name: String): String = when (name) {
        "workflow_create" -> "建一个工作流"
        "workflow_update" -> "改工作流"
        "workflow_delete" -> "删工作流"
        "workflow_set_enabled" -> "开关工作流"
        "workflow_run" -> "跑一次工作流"
        "workflow_list" -> "看看有哪些工作流"
        "workflow_get" -> "看工作流的内容"
        else -> name
    }

    @Composable
    override fun title(context: ToolUIContext): String {
        val text = plain(context).trim()
        if (text.startsWith("{")) return prettyName(context.tool.toolName)
        val first = text.lineSequence().firstOrNull()?.trim().orEmpty()
        if (first.isBlank()) return prettyName(context.tool.toolName)
        return first
    }

    override fun hasSummary(context: ToolUIContext): Boolean =
        !plain(context).trim().startsWith("{")

    @Composable
    override fun Summary(context: ToolUIContext) {
        Text(
            text = plain(context),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal object WorkflowCreateToolUI : WorkflowToolUIBase() {
    override val toolName: String = "workflow_create"
}

internal object WorkflowUpdateToolUI : WorkflowToolUIBase() {
    override val toolName: String = "workflow_update"
}

internal object WorkflowDeleteToolUI : WorkflowToolUIBase() {
    override val toolName: String = "workflow_delete"
}

internal object WorkflowSetEnabledToolUI : WorkflowToolUIBase() {
    override val toolName: String = "workflow_set_enabled"
}

internal object WorkflowRunToolUI : WorkflowToolUIBase() {
    override val toolName: String = "workflow_run"
}

internal object WorkflowListToolUI : WorkflowToolUIBase() {
    override val toolName: String = "workflow_list"
}

internal object WorkflowGetToolUI : WorkflowToolUIBase() {
    override val toolName: String = "workflow_get"
}
