#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
自用正式包：占开发版那个包名 + 开发版那把签名。

这样正式包能直接盖在现在这个开发版上，助手、聊天记录、设置、开关全都留着，
不用备份再恢复。构建本身还是 release（会压缩优化），所以调试标记和多余开销都没了。
"""

import io
import sys

P = 'app/build.gradle.kts'
MARK = '自用正式包：占开发版那个包名'

s = io.open(P, encoding='utf-8').read()

if MARK in s:
    print('PATCH_OK: already patched')
    sys.exit(0)

old = '        release {\n            signingConfig = signingConfigs.getByName("release")\n'
new = (
    '        release {\n'
    '            // ' + MARK + '，并用开发版那把签名，直接盖上去，数据不用搬\n'
    '            signingConfig = signingConfigs.getByName("debug")\n'
    '            applicationIdSuffix = ".debug"\n'
)

if old not in s:
    print('PATCH_FAILED: build.gradle.kts release anchor not found')
    sys.exit(1)

io.open(P, 'w', encoding='utf-8').write(s.replace(old, new, 1))
print('PATCH_OK: build.gradle.kts release build type patched')
