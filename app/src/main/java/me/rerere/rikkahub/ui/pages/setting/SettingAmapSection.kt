package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import me.rerere.rikkahub.data.datastore.AmapStore
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalToaster

/**
 * 高德地图 key。要填的时候点一下，不用一直在页面上占一大块。
 */
@Composable
fun AmapKeySection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    var draft by remember { mutableStateOf(AmapStore.readKey(context)) }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("高德地图 API Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        AmapStore.writeKey(context, draft)
                        toaster.show(message = "存好了", type = ToastType.Success)
                        showDialog = false
                    }
                ) {
                    Text("存下")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("算了") }
            },
        )
    }

    CardGroup(
        modifier = modifier,
        title = { Text("高德地图 Key") },
    ) {
        item(
            headlineContent = { Text("高德地图 API Key") },
            supportingContent = {
                Text(
                    "位置服务与周边探索需要它：有了它才能把坐标转成地址、搜索附近的地点。" +
                        "不填只能拿到经纬度。"
                )
            },
            onClick = { showDialog = true },
        )
    }
}
