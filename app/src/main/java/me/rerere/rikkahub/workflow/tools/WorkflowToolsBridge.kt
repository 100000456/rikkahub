package me.rerere.rikkahub.workflow.tools

import me.rerere.ai.core.Tool
import me.rerere.rikkahub.workflow.execution.WorkflowEngine
import me.rerere.rikkahub.workflow.repository.WorkflowRepository

/** 把七个 workflow_* 工具打包成一组，交给本地工具开关挂上去。 */
fun buildWorkflowToolsForLocal(
    repository: WorkflowRepository,
    engine: WorkflowEngine,
    knownToolNamesProvider: () -> List<String>,
): List<Tool> = listOf(
    workflowCreateTool(repository, knownToolNamesProvider),
    workflowListTool(repository),
    workflowGetTool(repository),
    workflowUpdateTool(repository, knownToolNamesProvider),
    workflowDeleteTool(repository),
    workflowSetEnabledTool(repository),
    workflowRunTool(engine, repository),
)
