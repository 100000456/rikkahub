<div align="center">
  <img src="docs/icon.png" alt="App Icon" width="100" />
  <h1>兔子</h1>

一个安卓上的 AI 聊天客户端，自己改着用的。

[![License](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)

</div>

## 这是什么

基于 RikkaHub，再把另一个二改版「橘瓣」里的功能一点点搬回来，自己用着顺手就行。

个人自用版本，非官方，和两个上游项目的作者都没有关系。

## 上游项目

| 项目 | 说明 |
| --- | --- |
| [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) | 本体，界面和整体架构都来自这里 |
| [sue1231513/orangechat](https://github.com/sue1231513/orangechat) | 橘瓣，本地工具、插件等功能的来源 |

两个都是开源项目，本仓库同样开源。

## 相比原版多了什么

搬进来一批本地工具：闹钟、倒计时、手电筒、音量、亮度、震动、电量、存储、提示条、亮屏、短信、通知、相册刷新、分享、相机、音乐。

还搭了一条云编译流水线，推一次就自动出包。

后面打算继续搬：插件、工作流、记忆库、云同步、手表健康、定位。

## 免责声明

- 这是个人自用的修改版，不是官方版本，和 RikkaHub、橘瓣的作者没有任何关系。
- 没有经过完整测试，可能存在缺陷、崩溃、数据丢失或权限异常等风险。
- 使用本版本产生的一切后果由使用者自行承担，与上游作者无关。
- 需要稳定版本请走官方渠道，下载并使用原版。
- 请不要把本版本对外分发后，声称是原作者的作品。

## 出包

在 Actions 里跑 `Build Debug APK`，产物挂在 Releases。

本地自己编需要一个 `app/google-services.json` 占位文件，不然 Gradle 那步过不去。

## 许可

[GNU Affero General Public License v3.0](LICENSE)。

原项目版权归原作者所有。这里只做个人自用改动，照旧以 AGPL-3.0 开源。
