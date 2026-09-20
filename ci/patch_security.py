#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
安全设置那三个开关落地：
  1) Settings 里加三个字段
  2) 审批判定接上「强制确认 / 自动批准」
  3) 后台工作流拦截敏感工具
  4) 路由 + 设置页入口

每一步都先查锚点，找不到就整脚本退出（脚本幂等，重复跑不会叠）。
"""

import io
import sys

ROOT = ''
SETTINGS = ROOT + 'app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt'
LOOP = ROOT + 'app/src/main/java/me/rerere/rikkahub/data/ai/GenerationLoop.kt'
ENGINE = ROOT + 'app/src/main/java/me/rerere/rikkahub/workflow/execution/WorkflowEngine.kt'
ROUTE = ROOT + 'app/src/main/java/me/rerere/rikkahub/RouteActivity.kt'
PAGE = ROOT + 'app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPage.kt'

DONE = []


def read(path):
    return io.open(path, encoding='utf-8').read()


def write(path, text):
    io.open(path, 'w', encoding='utf-8').write(text)


def die(msg):
    print('PATCH_FAILED: ' + msg)
    sys.exit(1)


# ---------------------------------------------------------------- 1. Settings
s = read(SETTINGS)
if 'forceConfirmToolCalls' in s:
    print('1. settings already patched')
else:
    anchor = '    val sponsorAlertDismissedAt: Int = 0,\n'
    if anchor not in s:
        die('PreferencesStore anchor not found')
    s = s.replace(anchor, anchor + (
        '\n'
        '    // 安全设置：工具调用确认方式那三个开关\n'
        '    val forceConfirmToolCalls: Boolean = false,\n'
        '    val autoApproveAllTools: Boolean = true,\n'
        '    val workflowHeadlessBlockSensitive: Boolean = false,\n'
    ), 1)
    write(SETTINGS, s)
    print('1. settings patched')
DONE.append('settings')

# ------------------------------------------------------------- 2. 审批判定
HELPER = '''
/**
 * 安全设置的总开关压过单个工具自己的声明：
 * 开着「自动批准所有工具调用」就一律放行，开着「强制确认工具调用」就一律弹窗，
 * 两个都不开，才按工具自己声明的来。
 */
private fun approvalOverride(local: Boolean): Boolean {
    val settings = runCatching {
        org.koin.java.KoinJavaComponent.getKoin()
            .get<me.rerere.rikkahub.data.datastore.SettingsStore>()
            .settingsFlow.value
    }.getOrNull() ?: return local
    if (settings.autoApproveAllTools) return false
    if (settings.forceConfirmToolCalls) return true
    return local
}
'''

s = read(LOOP)
old = 'toolDef?.needsApproval(tool.inputAsJson()) == true &&'
new = 'approvalOverride(toolDef?.needsApproval(tool.inputAsJson()) == true) &&'
changed = False
if new not in s:
    if old not in s:
        die('GenerationLoop anchor not found')
    s = s.replace(old, new, 1)
    changed = True
if 'private fun approvalOverride(' not in s:
    s = s.rstrip('\n') + '\n' + HELPER
    changed = True
if changed:
    write(LOOP, s)
    print('2. generation loop patched')
else:
    print('2. generation loop already patched')
DONE.append('loop')

# --------------------------------------------------- 3. 后台工作流拦截敏感工具
s = read(ENGINE)
if 'blockSensitiveTools' in s:
    print('3. workflow engine already patched')
else:
    old = '''        val tools = buildList {
            addAll(localTools.getTools(authoringAssistant.localTools))
            addAll(pluginToolProvider.getTools())
        }

        // 敏感工具拦截交给动作层的硬线守卫兜底（兔子侧暂无全局开关）'''
    if old not in s:
        die('WorkflowEngine anchor not found')
    new = '''        val allTools = buildList {
            addAll(localTools.getTools(authoringAssistant.localTools))
            addAll(pluginToolProvider.getTools())
        }

        // 安全设置里的「后台工作流拦截敏感工具」：开着的话，把声明了「要用户确认」的工具
        // 从这一轮的工具面里摘掉。摘掉之后动作找不到工具，会记一条失败，历史里看得见。
        // 「自动批准所有工具调用」开着时这一条自动失效（跟原版一个逻辑）。
        val blockSensitiveTools =
            settings.workflowHeadlessBlockSensitive && !settings.autoApproveAllTools
        val tools = if (blockSensitiveTools) {
            val kept = allTools.filterNot { tool ->
                runCatching {
                    tool.needsApproval(kotlinx.serialization.json.JsonObject(emptyMap()))
                }.getOrDefault(false)
            }
            Log.i(
                TAG,
                "headless sensitive-tool block: kept ${kept.size}/${allTools.size} tool(s)"
            )
            kept
        } else {
            allTools
        }'''
    s = s.replace(old, new, 1)
    write(ENGINE, s)
    print('3. workflow engine patched')
DONE.append('engine')

# ------------------------------------------------------- 4. 路由 + 设置页入口
print('4. route already in tree, skip')
DONE.append('route')

s = read(PAGE)
if 'Screen.SettingSecurity' in s:
    print('5. settings page already patched')
else:
    lines = s.split('\n')
    idx = None
    for i, line in enumerate(lines):
        if 'navController.navigate(Screen.SettingQqBot)' in line:
            idx = i - 1
            break
    if idx is None or idx < 0:
        die('SettingPage: QqBot item not found')
    if lines[idx].strip() != 'item(':
        die('SettingPage: unexpected item head: ' + lines[idx])
    end = None
    for j in range(idx + 1, min(idx + 12, len(lines))):
        if lines[j].strip() == ')':
            end = j
            break
    if end is None:
        die('SettingPage: item block end not found')
    block = lines[idx:end + 1]
    if len(block) < 5:
        die('SettingPage: item block too short: %d lines' % len(block))
    indent = block[3][:len(block[3]) - len(block[3].lstrip())]
    mine = list(block)
    mine = [b.replace('Screen.SettingQqBot', 'Screen.SettingSecurity') for b in mine]
    if 'leadingContent' in mine[2]:
        mine[2] = indent + 'leadingContent = { Text("\U0001F6E1") },'
    mine[3] = indent + 'headlineContent = { Text("安全设置") },'
    mine[4] = indent + 'supportingContent = { Text("工具调用的确认方式") },'
    lines[idx:idx] = mine
    write(PAGE, '\n'.join(lines))
    print('5. settings page patched')
DONE.append('page')

print('PATCH_OK: ' + ', '.join(DONE))
