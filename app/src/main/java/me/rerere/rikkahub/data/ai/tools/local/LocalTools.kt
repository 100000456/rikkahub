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
        return tools
    }
}
