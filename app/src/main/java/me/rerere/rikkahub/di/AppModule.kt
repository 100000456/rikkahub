package me.rerere.rikkahub.di

import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.ai.tools.ChatToolFactory
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.service.MemoryBankService
import me.rerere.rikkahub.plugin.di.pluginModule
import me.rerere.rikkahub.service.ChatNotificationManager
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceTerminalSessionManager
import me.rerere.rikkahub.utils.EmojiData
import me.rerere.rikkahub.utils.EmojiUtils
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.SoundEffectPlayer
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.web.WebServerManager
import me.rerere.tts.provider.TTSManager
import org.koin.dsl.module

val appModule = module {
    // 插件沙箱：扫描器、加载器、沙箱、管理器、工具出口、管理页 ViewModel
    includes(pluginModule)

    single<Json> { JsonInstant }

    single {
        AppEventBus()
    }

    single {
        LocalTools(get(), get(), get(), get())
    }

    // 记忆库那两件：DAO 和记忆库服务。
    // 插件沙箱的 Loader 会伸手要 MemoryBankService，没登记的话整条链断掉，
    // 表现就是聊天页一开就崩。
    single {
        get<AppDatabase>().memoryBankDao()
    }
    single {
        MemoryBankService(
            memoryBankDAO = get(),
            okHttpClient = get(),
            context = get(),
        )
    }

    // 工作流：台账 / 仓库 / 条件 / 执行器 / 引擎 / 触发器注册表
    single { me.rerere.rikkahub.data.agentrun.AgentRunRepository() }
    single {
        me.rerere.rikkahub.workflow.repository.WorkflowRepository(
            workflowDao = get<me.rerere.rikkahub.data.db.AppDatabase>().workflowDao(),
            workflowRunDao = get<me.rerere.rikkahub.data.db.AppDatabase>().workflowRunDao(),
        )
    }
    single { me.rerere.rikkahub.workflow.condition.ContextProvider(get(), get(), get()) }
    single { me.rerere.rikkahub.workflow.execution.WorkflowActionRunner() }
    single {
        me.rerere.rikkahub.workflow.execution.WorkflowEngine(
            repository = get(),
            settingsStore = get(),
            contextProvider = get(),
            actionRunner = get(),
        ).also { engine ->
            get<me.rerere.rikkahub.workflow.repository.WorkflowRepository>().bindEngine(engine)
        }
    }
    single {
        me.rerere.rikkahub.workflow.trigger.TriggerRegistry(
            context = get(),
            appScope = get(),
            workflowRepository = get(),
        )
    }

    single {
        UpdateChecker(
            client = get(),
            appScope = get(),
        )
    }

    single {
        AppScope()
    }

    single<EmojiData> {
        EmojiUtils.loadEmoji(get())
    }

    single {
        TTSManager(get())
    }

    single {
        Firebase.crashlytics
    }

    // 打点用的那个。配置是占位的，个别机器上可能压根初始化不起来，
    // 不能让它把聊天页一起拖死：起不来就换一个什么都不做的替身顶上。
    single {
        runCatching { Firebase.analytics }.getOrElse { noOpFirebaseAnalytics() }
    }

    single {
        SoundEffectPlayer(get())
    }

    single {
        WorkspaceTerminalSessionManager(get(), get())
    }

    // 生成通知与业务解耦：ChatService 只发事件，通知由这里消费；
    // createdAtStart 保证进程启动即订阅，否则后台生成的事件会因无订阅者而丢失
    single(createdAtStart = true) {
        ChatNotificationManager(
            context = get(),
            appScope = get(),
            eventBus = get(),
            settingsStore = get(),
        )
    }

    single {
        ChatToolFactory(
            json = get(),
            memoryRepository = get(),
            conversationRepository = get(),
            localTools = get(),
            mcpManager = get(),
            skillManager = get(),
            pluginToolProvider = get(),
            workspaceRepository = get(),
        )
    }

    single {
        ChatService(
            context = get(),
            appScope = get(),
            appEventBus = get(),
            settingsStore = get(),
            conversationRepo = get(),
            memoryRepository = get(),
            generationLoop = get(),
            translationHandler = get(),
            templateTransformer = get(),
            providerManager = get(),
            chatToolFactory = get(),
            mcpManager = get(),
            filesManager = get(),
            workspaceRepository = get(),
            folderRepository = get()
        )
    }

    single {
        WebServerManager(
            context = get(),
            appScope = get(),
            chatService = get(),
            conversationRepo = get(),
            folderRepo = get(),
            settingsStore = get(),
            filesManager = get()
        )
    }
}

/**
 * 打点件的替身：所有调用都当没发生，不抛异常。
 */
private fun noOpFirebaseAnalytics(): FirebaseAnalytics =
    java.lang.reflect.Proxy.newProxyInstance(
        FirebaseAnalytics::class.java.classLoader,
        arrayOf(FirebaseAnalytics::class.java),
    ) { _, _, _ -> null } as FirebaseAnalytics
