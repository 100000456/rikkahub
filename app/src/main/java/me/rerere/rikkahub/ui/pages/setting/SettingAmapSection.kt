package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import me.rerere.rikkahub.data.datastore.AmapStore
import me.rerere.rikkahub.ui.context.LocalToaster

/**
 * 高德地图的 key。定位和搜附近都要它，没填就只能看到坐标。
 */
@Composable
fun AmapKeySection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    var draft by remember { mutableStateOf(AmapStore.readKey(context)) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "高德地图 key",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "填了它，定位才能把地址念出来，也能搜附近有什么。没填就只有一串坐标。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("key") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = {
                        AmapStore.writeKey(context, draft)
                        toaster.show(message = "存好了", type = ToastType.Success)
                    }
                ) {
                    Text("存下")
                }
            }
        }
    }
}
