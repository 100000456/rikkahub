package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class LocalToolOption {
    @Serializable
    @SerialName("javascript_engine")
    data object JavascriptEngine : LocalToolOption()

    @Serializable
    @SerialName("time_info")
    data object TimeInfo : LocalToolOption()

    @Serializable
    @SerialName("clipboard")
    data object Clipboard : LocalToolOption()

    @Serializable
    @SerialName("tts")
    data object Tts : LocalToolOption()

    @Serializable
    @SerialName("ask_user")
    data object AskUser : LocalToolOption()

    @Serializable
    @SerialName("screen_time")
    data object ScreenTime : LocalToolOption()

    @Serializable
    @SerialName("calendar")
    data object Calendar : LocalToolOption()

    @Serializable
    @SerialName("alarm")
    data object Alarm : LocalToolOption()

    @Serializable
    @SerialName("system_tools")
    data object SystemTools : LocalToolOption()

    @Serializable
    @SerialName("sms")
    data object Sms : LocalToolOption()

    @Serializable
    @SerialName("notification")
    data object Notification : LocalToolOption()

    @Serializable
    @SerialName("media_scanner")
    data object MediaScanner : LocalToolOption()

    @Serializable
    @SerialName("share")
    data object Share : LocalToolOption()

    @Serializable
    @SerialName("notifications_reader")
    data object NotificationsReader : LocalToolOption()

    @Serializable
    @SerialName("music")
    data object Music : LocalToolOption()

    @Serializable
    @SerialName("camera")
    data object Camera : LocalToolOption()

    @Serializable
    @SerialName("app_control")
    data object AppControl : LocalToolOption()

    @Serializable
    @SerialName("device_info")
    data object DeviceInfo : LocalToolOption()

    @Serializable
    @SerialName("gadgetbridge")
    data object Gadgetbridge : LocalToolOption()

    @Serializable
    @SerialName("location")
    data object Location : LocalToolOption()

    @Serializable
    @SerialName("explore_nearby")
    data object ExploreNearby : LocalToolOption()
}
