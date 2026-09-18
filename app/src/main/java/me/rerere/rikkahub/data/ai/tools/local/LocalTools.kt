package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.tts.provider.TTSManager

class LocalTools(
    private val context: Context,
    private val eventBus: AppEventBus,
    private val ttsManager: TTSManager,
    private val settingsStore: SettingsStore,
) {
    val javascriptTool by lazy { buildJavascriptTool() }

    val timeTool by lazy { buildTimeInfoTool() }

    val clipboardTool by lazy { buildClipboardTool(context) }

    val ttsTool by lazy { buildTextToSpeechTool(eventBus, ttsManager, settingsStore) }

    val askUserTool by lazy { buildAskUserTool() }

    val screenTimeTool by lazy { buildScreenTimeTool(context, eventBus) }

    val calendarQueryTool by lazy { buildCalendarQueryTool(context) }

    val calendarCreateTool by lazy { buildCalendarCreateTool(context) }

    val alarmTool by lazy { buildAlarmTool(context) }

    val timerTool by lazy { buildTimerTool(context) }

    val torchTool by lazy { buildTorchTool(context) }

    val getVolumeTool by lazy { buildGetVolumeTool(context) }

    val setVolumeTool by lazy { buildSetVolumeTool(context) }

    val getBrightnessTool by lazy { buildGetBrightnessTool(context) }

    val setBrightnessTool by lazy { buildSetBrightnessTool(context) }

    val vibrateTool by lazy { buildVibrateTool(context) }

    val batteryTool by lazy { buildBatteryTool(context) }

    val storageInfoTool by lazy { buildStorageInfoTool(context) }

    val toastTool by lazy { buildToastTool(context) }

    val wakeScreenTool by lazy { buildWakeScreenTool(context) }

    val smsTool by lazy { buildSmsTool(context) }

    val notificationPostTool by lazy { buildNotificationPostTool(context) }

    val mediaScannerTool by lazy { buildMediaScannerTool(context) }

    val shareTool by lazy { buildShareTool(context) }

    val notificationsTool by lazy { buildNotificationsTool(context) }

    val musicTool by lazy { buildMusicTool(context) }

    val cameraTool by lazy { buildCameraTool(context) }

    val appSwitchTool by lazy { buildAppSwitchTool(context) }

    val appUsageTool by lazy { buildAppUsageTool(context) }

    val wifiInfoTool by lazy { buildWifiInfoTool(context) }

    val telephonyInfoTool by lazy { buildTelephonyInfoTool(context) }

    val gadgetbridgeTool by lazy { buildGadgetbridgeTool(context) }

    val locationTool by lazy { buildLocationTool(context) }

    val exploreNearbyTool by lazy { buildExploreNearbyTool(context) }

    val avatarTool by lazy { buildAvatarTool(context, settingsStore) }

    fun getTools(options: List<LocalToolOption>): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
        }
        if (options.contains(LocalToolOption.TimeInfo)) {
            tools.add(timeTool)
        }
        if (options.contains(LocalToolOption.Clipboard)) {
            tools.add(clipboardTool)
        }
        if (options.contains(LocalToolOption.Tts)) {
            tools.add(ttsTool)
        }
        if (options.contains(LocalToolOption.AskUser)) {
            tools.add(askUserTool)
        }
        if (options.contains(LocalToolOption.ScreenTime)) {
            tools.add(screenTimeTool)
        }
        if (options.contains(LocalToolOption.Calendar)) {
            tools.add(calendarQueryTool)
            tools.add(calendarCreateTool)
        }
        if (options.contains(LocalToolOption.Alarm)) {
            tools.add(alarmTool)
            tools.add(timerTool)
        }
        if (options.contains(LocalToolOption.SystemTools)) {
            tools.add(torchTool)
            tools.add(getVolumeTool)
            tools.add(setVolumeTool)
            tools.add(getBrightnessTool)
            tools.add(setBrightnessTool)
            tools.add(vibrateTool)
            tools.add(batteryTool)
            tools.add(storageInfoTool)
            tools.add(toastTool)
            tools.add(wakeScreenTool)
        }
        if (options.contains(LocalToolOption.Sms)) {
            tools.add(smsTool)
        }
        if (options.contains(LocalToolOption.Notification)) {
            tools.add(notificationPostTool)
        }
        if (options.contains(LocalToolOption.MediaScanner)) {
            tools.add(mediaScannerTool)
        }
        if (options.contains(LocalToolOption.Share)) {
            tools.add(shareTool)
        }
        if (options.contains(LocalToolOption.NotificationsReader)) {
            tools.add(notificationsTool)
        }
        if (options.contains(LocalToolOption.Music)) {
            tools.add(musicTool)
        }
        if (options.contains(LocalToolOption.Camera)) {
            tools.add(cameraTool)
        }
        if (options.contains(LocalToolOption.AppControl)) {
            tools.add(appSwitchTool)
        }
        if (options.contains(LocalToolOption.DeviceInfo)) {
            tools.add(wifiInfoTool)
            tools.add(telephonyInfoTool)
            tools.add(appUsageTool)
        }
        if (options.contains(LocalToolOption.Gadgetbridge)) {
            tools.add(gadgetbridgeTool)
        }
        if (options.contains(LocalToolOption.Location)) {
            tools.add(locationTool)
        }
        if (options.contains(LocalToolOption.ExploreNearby)) {
            tools.add(exploreNearbyTool)
        }
        if (options.contains(LocalToolOption.Workflows)) {
            val known = tools.map { it.name }
            tools.addAll(
                me.rerere.rikkahub.workflow.tools.buildWorkflowToolsForLocal(
                    repository = org.koin.java.KoinJavaComponent.getKoin()
                        .get<me.rerere.rikkahub.workflow.repository.WorkflowRepository>(),
                    engine = org.koin.java.KoinJavaComponent.getKoin()
                        .get<me.rerere.rikkahub.workflow.execution.WorkflowEngine>(),
                    knownToolNamesProvider = { known },
                )
            )
        }
        if (options.contains(LocalToolOption.ScreenAutomation)) {
            tools.add(tapTool())
            tools.add(longPressTool())
            tools.add(swipeTool())
            tools.add(readWindowTreeTool())
            tools.add(findNodeTool())
            tools.add(clickNodeTool())
            tools.add(setTextTool())
            tools.add(scrollTool())
            tools.add(globalActionTool())
            tools.add(takeScreenshotTool(context))
        }

        // 换头像：常驻，不用开关
        tools.add(avatarTool)
        return tools
    }
}
