package me.rerere.rikkahub.ui.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.service.AppLockGuardService
import me.rerere.rikkahub.data.service.AppLockStore
import me.rerere.rikkahub.ui.theme.RikkahubTheme

/**
 * 被锁住的 App 被推开时弹的这一页。输对 PIN 才放行。
 */
class AppLockUnlockActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE = "locked_package"
    }

    private var targetPackage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent?.getStringExtra(EXTRA_PACKAGE).orEmpty()
        setContent {
            RikkahubTheme {
                AppLockScreen(
                    appName = targetPackage,
                    requirePin = AppLockStore.getRequirePin(this, targetPackage),
                    pinLength = AppLockStore.getPinLength(this),
                    message = AppLockStore.getLockMessage(this, targetPackage),
                    onDismiss = { finish() },
                    onSubmit = { pin ->
                        if (AppLockStore.verifyPin(this, pin)) {
                            AppLockGuardService.markUnlocked(targetPackage)
                            startActivity(
                                packageManager
                                    .getLaunchIntentForPackage(targetPackage)
                                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            finish()
                        } else {
                            Toast.makeText(this, "不对，再想一下", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
    }
}

@Composable
private fun AppLockScreen(
    appName: String,
    requirePin: Boolean,
    pinLength: Int,
    message: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message ?: "这个先别开",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "锁着的是 $appName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!requirePin) {
                Button(onClick = onDismiss) { Text("回桌面") }
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(pinLength) { index ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                if (index < input.length) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape,
                            )
                    )
                }
            }

            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "删"),
            )
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { label ->
                        if (label.isBlank()) {
                            Box(modifier = Modifier.size(72.dp))
                        } else {
                            Button(
                                modifier = Modifier.size(72.dp),
                                onClick = {
                                    when (label) {
                                        "删" -> if (input.isNotEmpty()) {
                                            input = input.dropLast(1)
                                        }

                                        else -> {
                                            if (input.length < pinLength) {
                                                input += label
                                            }
                                            if (input.length == pinLength) {
                                                val attempt = input
                                                input = ""
                                                onSubmit(attempt)
                                            }
                                        }
                                    }
                                },
                            ) { Text(label) }
                        }
                    }
                }
            }

            TextButton(onClick = onDismiss) { Text("先出去") }
        }
    }
}
