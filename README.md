<div align="center">
  <img src="docs/icon.png" alt="App Icon" width="100" />
  <h1>兔子 / Tuzi</h1>
  <p>一个安卓上的 AI 聊天客户端 / An Android AI chat client</p>

  [![License](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)
</div>

A personal fork of RikkaHub. 中文在前，English below.

---

## 中文

### 简介

兔子是一个安卓上的 AI 聊天客户端。它基于 RikkaHub 二次开发，并把另一个非官方二改版本「橘瓣」中的部分功能移植回来，主要用于个人日常使用。

本仓库为个人自用版本，非官方发行版，与 RikkaHub、橘瓣的作者均无隶属或合作关系。

### 上游项目

| 项目 | 说明 |
| --- | --- |
| [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) | 项目本体，应用架构与主要界面来自该项目 |
| [sue1231513/orangechat](https://github.com/sue1231513/orangechat) | 橘瓣，本地工具、通知保活等功能的来源 |

两个上游项目均为开源项目，本仓库同样以开源方式发布。

### 相对上游多出的功能

本地工具（可在「系统工具」页统一开关）：

- 闹钟与倒计时
- 手电筒、音量、亮度、震动、电量、存储、提示条、亮屏
- 短信读取，系统通知的发送与读取
- 相册刷新、系统分享、相机、音乐控制
- 应用控制（打开应用与网页、回到聊天）
- 设备信息（网络状态、SIM 卡信号、应用使用时长）
- 手表健康（读取 Gadgetbridge 导出的数据库）
- 位置与探索周边（需要定位权限，可选配置高德地图 key）

其他：

- 后台保活：常驻通知 + 开机自启 + 被清理后自动恢复；通知文案可自定义或自动轮换
- 主动消息：按固定时间点或随机间隔主动发消息
- 应用锁：基于无障碍服务，对指定应用加解锁页
- 云编译：推送后由 GitHub Actions 自动构建并发布安装包

计划继续移植：插件系统、工作流、记忆库、云同步。

### 构建

仓库内置两条 GitHub Actions 工作流：`Build Debug APK` 与 `Build Release APK`，可手动触发，产物发布在 Releases 页面。

本地构建需要自行提供 `app/google-services.json` 占位文件，否则 Gradle 配置阶段会失败。

### 免责声明

- 本项目为个人自用修改版，不是官方版本，与 RikkaHub、橘瓣的作者没有任何关系。
- 本项目未经完整测试，可能存在缺陷、崩溃、数据丢失或权限异常等风险。
- 使用本项目所产生的一切后果由使用者自行承担，与上游作者无关。
- 需要稳定版本请通过官方渠道获取并使用原版应用。
- 请勿在对外分发本版本时声称其为原作者的成果。

### 许可

本项目以 [GNU Affero General Public License v3.0](LICENSE) 许可发布。原项目版权归原作者所有，本仓库仅包含个人自用范围内的修改，并继续以 AGPL-3.0 开源。

---

## English

### Overview

Tuzi is an Android AI chat client. It is a fork of RikkaHub, with parts of another community fork called OrangeChat (橘瓣) ported back in. It is maintained for personal daily use.

This repository is a personal fork. It is not an official release, and it is not affiliated with the authors of RikkaHub or OrangeChat.

### Upstream projects

| Project | Description |
| --- | --- |
| [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) | The base project. App architecture and most of the UI come from here |
| [sue1231513/orangechat](https://github.com/sue1231513/orangechat) | OrangeChat. Source of the local tools, notification keep-alive, and related features |

Both upstream projects are open source, and this repository is released the same way.

### Features added on top of upstream

Local tools (can be toggled together on the "System Tools" settings page):

- Alarm and countdown timer
- Torch, volume, brightness, vibration, battery, storage, toast, keep screen awake
- Read SMS, post and read system notifications
- Media scan, system share, camera, music control
- App control (open apps and URLs, bring the chat to front)
- Device info (network state, SIM signal, per-app usage time)
- Wearable health (reads the database exported by Gadgetbridge)
- Location and explore nearby (requires location permission; optional Amap API key)

Others:

- Keep-alive: a persistent notification, auto start on boot, and recovery after being killed; the notification text can be customized or rotated automatically
- Proactive messages: send a message on fixed times or at random intervals
- App lock: blocks selected apps with an unlock screen, built on the accessibility service
- Cloud build: GitHub Actions builds and publishes the APK on push

Planned ports: plugin system, workflows, memory bank, cloud sync.

### Build

The repository ships two GitHub Actions workflows: `Build Debug APK` and `Build Release APK`. They can be triggered manually, and the artifacts are published on the Releases page.

For a local build, you need to provide your own `app/google-services.json` placeholder file, otherwise the Gradle configuration step will fail.

### Disclaimer

- This is a personal modified build, not an official release, and it has no relationship with the authors of RikkaHub or OrangeChat.
- It has not been fully tested, and it may contain defects, crashes, data loss, or permission issues.
- Any consequence of using this build is borne by the user alone and is unrelated to the upstream authors.
- If you need a stable version, please obtain and use the original application through its official channel.
- Do not redistribute this build while claiming it to be the work of the original authors.

### License

This project is released under the [GNU Affero General Public License v3.0](LICENSE). Copyright of the original project belongs to its original authors. This repository contains personal-use modifications only, and remains open source under AGPL-3.0.
