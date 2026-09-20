/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.components.message.tools

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.FileAdd
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 打包文件 (write_files): AI 把生成的文件交给用户
 *
 * 单个文件时直接下载原文件 (不再套一层 ZIP), 多个文件时可打包;
 * 每个文件还能另存为 Word 文档或纯文本, 由用户自己挑。
 * 文件内容直接取自工具输出里的 files_content, 不会再猜数据源。
 */
object WriteFilesToolUI : ToolUIRenderer {
    override val toolName: String = "write_files"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.FileAdd

    @Composable
    override fun title(context: ToolUIContext): String {
        val zipName = context.arguments.getStringContent("zip_name")
        return if (zipName.isNullOrBlank()) "打包文件" else "打包 $zipName"
    }

    private fun zipNameOf(context: ToolUIContext): String =
        context.content.getStringContent("zip_name")
            ?: context.arguments.getStringContent("zip_name")
            ?: "files.zip"

    /** 从工具输出读取文件内容, 输出里没有时回退到入参 */
    private fun filesOf(context: ToolUIContext): Map<String, String> {
        val filesContent = context.content?.jsonObjectOrNull?.get("files_content")?.jsonObjectOrNull
        if (filesContent != null) {
            return filesContent.entries.mapNotNull { entry ->
                val text = (entry.value as? JsonPrimitive)?.contentOrNull
                if (text == null) null else entry.key to text
            }.toMap()
        }
        val argumentFiles = (context.arguments.jsonObjectOrNull?.get("files") as? JsonArray)
            ?: return emptyMap()
        return argumentFiles.mapNotNull { element ->
            val obj = element.jsonObjectOrNull ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val text = obj["content"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            name to text
        }.toMap()
    }

    override fun hasSummary(context: ToolUIContext): Boolean =
        context.tool.isExecuted && filesOf(context).isNotEmpty()

    @Composable
    override fun Summary(context: ToolUIContext) {
        val files = remember(context) { filesOf(context) }
        if (files.isEmpty()) return
        val saver = rememberFileSaver()
        val zipName = remember(context) { zipNameOf(context) }
        val single = files.entries.singleOrNull()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (single != null) fileLeafName(single.key) else "${files.size} 个文件",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (single != null) {
                FilledTonalButton(
                    onClick = {
                        saver.save(
                            format = OutFormat.Raw,
                            fileName = fileLeafName(single.key),
                            bytes = single.value.toByteArray(Charsets.UTF_8),
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(text = "下载", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                FilledTonalButton(
                    onClick = {
                        saver.save(
                            format = OutFormat.Zip,
                            fileName = zipName,
                            bytes = zipBytes(files),
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(text = "下载 ZIP", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val files = filesOf(context)
        val saver = rememberFileSaver()
        if (files.isEmpty()) {
            DefaultToolPreview(context = context)
            return
        }
        val zipName = zipNameOf(context)
        Column(
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = zipName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = {
                        saver.save(
                            format = OutFormat.Zip,
                            fileName = zipName,
                            bytes = zipBytes(files),
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(text = "打包 ZIP", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                text = "每个文件都能单独存, 点右边挑格式",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            files.forEach { (name, text) ->
                FileRow(name = name, text = text, saver = saver)
            }
        }
    }
}

/** 单个文件卡片: 名字 + 大小 + 挑格式下载 */
@Composable
private fun FileRow(name: String, text: String, saver: FileSaver) {
    val leaf = fileLeafName(name)
    val menu = remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatSize(text.length),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        Box {
            FilledTonalButton(
                onClick = { menu.value = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(text = "另存", style = MaterialTheme.typography.labelSmall)
            }
            DropdownMenu(
                expanded = menu.value,
                onDismissRequest = { menu.value = false },
            ) {
                DropdownMenuItem(
                    text = { Text(text = "原样下载 ($leaf)") },
                    onClick = {
                        menu.value = false
                        saver.save(OutFormat.Raw, leaf, text.toByteArray(Charsets.UTF_8))
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = "存成 Word 文档 (.docx)") },
                    onClick = {
                        menu.value = false
                        saver.save(OutFormat.Docx, withExtension(leaf, "docx"), docxBytes(text))
                    },
                )
                if (!leaf.endsWith(".txt", ignoreCase = true)) {
                    DropdownMenuItem(
                        text = { Text(text = "存成纯文本 (.txt)") },
                        onClick = {
                            menu.value = false
                            saver.save(OutFormat.Txt, withExtension(leaf, "txt"), text.toByteArray(Charsets.UTF_8))
                        },
                    )
                }
            }
        }
    }
}

/** 下载格式 */
private enum class OutFormat(val mime: String) {
    Zip("application/zip"),
    Docx("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    Txt("text/plain"),
    Raw("application/octet-stream"),
}

private class SavePayload(val fileName: String, val bytes: ByteArray)

private fun interface FileSaver {
    fun save(format: OutFormat, fileName: String, bytes: ByteArray)
}

/**
 * 准备四个系统保存对话框, 分别对应 ZIP / Word / 纯文本 / 原样
 *
 * 每次点下载先把要写的字节放进 payload, 再拉起对应格式的保存框。
 */
@Composable
private fun rememberFileSaver(): FileSaver {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val payload = remember { mutableStateOf<SavePayload?>(null) }
    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(OutFormat.Zip.mime),
    ) { uri -> handleSaveResult(uri, payload, context, scope) }
    val docxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(OutFormat.Docx.mime),
    ) { uri -> handleSaveResult(uri, payload, context, scope) }
    val txtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(OutFormat.Txt.mime),
    ) { uri -> handleSaveResult(uri, payload, context, scope) }
    val rawLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(OutFormat.Raw.mime),
    ) { uri -> handleSaveResult(uri, payload, context, scope) }
    return remember(zipLauncher, docxLauncher, txtLauncher, rawLauncher) {
        FileSaver { format, fileName, bytes ->
            payload.value = SavePayload(fileName, bytes)
            when (format) {
                OutFormat.Zip -> zipLauncher
                OutFormat.Docx -> docxLauncher
                OutFormat.Txt -> txtLauncher
                OutFormat.Raw -> rawLauncher
            }.launch(fileName)
        }
    }
}

/** 保存对话框回调: 把 payload 里的字节写进用户选的位置 */
private fun handleSaveResult(
    uri: Uri?,
    payload: MutableState<SavePayload?>,
    context: Context,
    scope: CoroutineScope,
) {
    val pending = payload.value
    payload.value = null
    if (uri == null || pending == null) return
    scope.launch(Dispatchers.IO) {
        val result = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(pending.bytes)
            } ?: error("打不开保存位置")
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                if (result.isSuccess) {
                    "已保存 ${pending.fileName}"
                } else {
                    "保存失败: ${result.exceptionOrNull()?.message ?: "未知原因"}"
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

/** 打包成 ZIP */
private fun zipBytes(files: Map<String, String>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        files.forEach { (name, text) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(text.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        zip.finish()
    }
    return out.toByteArray()
}

// ---- 最小 Word 文档 (.docx) 生成: 3 个 XML 塞进一个 ZIP ----

private const val DOCX_CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""

private const val DOCX_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""

private val HEADING_REGEX = Regex("^(#{1,6})\\s+(.+)$")
private val BULLET_REGEX = Regex("^\\s*[-*+]\\s+(.+)$")

/** 把 Markdown 正文转成 docx 字节 */
private fun docxBytes(markdown: String): ByteArray {
    val documentXml = buildDocumentXml(markdown)
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        zip.write(DOCX_CONTENT_TYPES.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.putNextEntry(ZipEntry("_rels/.rels"))
        zip.write(DOCX_RELS.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.putNextEntry(ZipEntry("word/document.xml"))
        zip.write(documentXml.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.finish()
    }
    return out.toByteArray()
}

private fun buildDocumentXml(text: String): String {
    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
    sb.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>")
    text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { line ->
        sb.append(paragraphXml(line))
    }
    sb.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr>")
    sb.append("</w:body></w:document>")
    return sb.toString()
}

private fun paragraphXml(rawLine: String): String {
    val line = rawLine.trimEnd()
    val heading = HEADING_REGEX.find(line)
    if (heading != null) {
        val size = when (heading.groupValues[1].length) {
            1 -> 36
            2 -> 32
            3 -> 28
            else -> 26
        }
        return "<w:p><w:pPr><w:spacing w:before=\"240\" w:after=\"120\"/></w:pPr>" +
            runXml(heading.groupValues[2].replace("**", ""), bold = true, size = size) +
            "</w:p>"
    }
    val bullet = BULLET_REGEX.find(line)
    if (bullet != null) {
        return "<w:p><w:pPr><w:ind w:left=\"420\" w:hanging=\"210\"/></w:pPr>" +
            runXml("· " + bullet.groupValues[1].replace("**", ""), bold = false, size = 22) +
            "</w:p>"
    }
    if (line.isBlank()) return "<w:p/>"
    val runs = runsXml(line)
    return if (runs.isEmpty()) "<w:p/>" else "<w:p>$runs</w:p>"
}

/** 一行正文拆成若干 run, 处理 **加粗** 和 `代码` */
private fun runsXml(line: String): String {
    val sb = StringBuilder()
    val plain = StringBuilder()
    var bold = false
    var index = 0
    fun flush() {
        if (plain.isNotEmpty()) {
            sb.append(runXml(plain.toString(), bold = bold, size = 22))
            plain.setLength(0)
        }
    }
    while (index < line.length) {
        when {
            line.startsWith("**", index) -> {
                flush()
                bold = !bold
                index += 2
            }

            line[index] == '`' -> {
                flush()
                val end = line.indexOf('`', index + 1)
                if (end > index + 1) {
                    sb.append(runXml(line.substring(index + 1, end), bold = false, size = 20, mono = true))
                    index = end + 1
                } else {
                    plain.append(line[index])
                    index += 1
                }
            }

            else -> {
                plain.append(line[index])
                index += 1
            }
        }
    }
    flush()
    return sb.toString()
}

private fun runXml(text: String, bold: Boolean, size: Int? = null, mono: Boolean = false): String {
    if (text.isEmpty()) return ""
    val props = StringBuilder()
    if (bold) props.append("<w:b/>")
    if (mono) props.append("<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/>")
    if (size != null) {
        props.append("<w:sz w:val=\"").append(size).append("\"/><w:szCs w:val=\"").append(size).append("\"/>")
    }
    val propsXml = if (props.isEmpty()) "" else "<w:rPr>$props</w:rPr>"
    return "<w:r>$propsXml<w:t xml:space=\"preserve\">${xmlEscape(text)}</w:t></w:r>"
}

private fun xmlEscape(text: String): String = buildString(text.length) {
    text.forEach { ch ->
        when {
            ch == '&' -> append("&amp;")
            ch == '<' -> append("&lt;")
            ch == '>' -> append("&gt;")
            ch == '"' -> append("&quot;")
            ch == '\'' -> append("&apos;")
            ch == '\t' -> append(' ')
            ch.code < 0x20 -> Unit
            else -> append(ch)
        }
    }
}

/** 去掉目录, 只留文件名 (系统保存框不接受路径分隔符) */
private fun fileLeafName(name: String): String = name.substringAfterLast('/')

/** 换掉后缀 */
private fun withExtension(fileName: String, ext: String): String {
    val base = fileName.substringBeforeLast('.', fileName)
    return "$base.$ext"
}

/** 粗估文件大小 */
private fun formatSize(charCount: Int): String = when {
    charCount >= 1024 * 1024 -> "%.1f MB".format(charCount / 1024f / 1024f)
    charCount >= 1024 -> "%.1f KB".format(charCount / 1024f)
    else -> "$charCount B"
}
