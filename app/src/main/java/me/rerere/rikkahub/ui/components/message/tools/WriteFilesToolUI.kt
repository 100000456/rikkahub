/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.components.message.tools

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 打包文件 (write_files): AI 把生成的文件打成 ZIP 交给用户
 *
 * 摘要显示文件数与下载按钮, 详情为完整文件清单。
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
        val zipName = remember(context) { zipNameOf(context) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${files.size} 个文件",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            DownloadZipButton(files = files, zipName = zipName)
        }
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val files = filesOf(context)
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
                DownloadZipButton(files = files, zipName = zipName)
            }
            files.forEach { (name, text) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatSize(text.length),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/** 下载按钮: 弹系统保存对话框, 把内存里的文件打成 ZIP 写进去 */
@Composable
private fun DownloadZipButton(files: Map<String, String>, zipName: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val result = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        ZipOutputStream(stream).use { zipOut ->
                            files.forEach { (name, text) ->
                                zipOut.putNextEntry(ZipEntry(name))
                                zipOut.write(text.toByteArray(Charsets.UTF_8))
                                zipOut.closeEntry()
                            }
                            zipOut.finish()
                        }
                    } ?: error("打不开保存位置")
                }
                withContext(Dispatchers.Main) {
                    val message = if (result.isSuccess) {
                        "已保存 $zipName"
                    } else {
                        "保存失败: ${result.exceptionOrNull()?.message ?: "未知原因"}"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    FilledTonalButton(
        onClick = { saver.launch(zipName) },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = "下载 ZIP",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** 粗估文件大小 */
private fun formatSize(charCount: Int): String = when {
    charCount >= 1024 * 1024 -> "%.1f MB".format(charCount / 1024f / 1024f)
    charCount >= 1024 -> "%.1f KB".format(charCount / 1024f)
    else -> "$charCount B"
}
