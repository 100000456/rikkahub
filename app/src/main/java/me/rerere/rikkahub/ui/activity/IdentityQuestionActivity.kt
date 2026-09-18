package me.rerere.rikkahub.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.ai.tools.local.BiometricResult
import me.rerere.rikkahub.data.ai.tools.local.IdentityQuestionStore
import me.rerere.rikkahub.ui.theme.RikkahubTheme

/**
 * 备选验证那一页：出一道题，她答对了才放行。
 *
 * 答完了把结果回填进 [BiometricPromptActivity.buffer]，后台等着的工具就被唤醒。
 * 她要是直接退出去，就当成放弃，别让工具在那儿干等。
 */
class IdentityQuestionActivity : ComponentActivity() {

    private var requestId: String = ""
    private var questionId: String = ""
    private var completed: Boolean = false

    private fun finishWith(result: BiometricResult) {
        if (completed) return
        completed = true
        BiometricPromptActivity.buffer.complete(requestId, result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestId = intent?.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        questionId = intent?.getStringExtra(EXTRA_QUESTION_ID).orEmpty()
        val question = intent?.getStringExtra(EXTRA_QUESTION).orEmpty()
        val hint = intent?.getStringExtra(EXTRA_HINT)

        if (requestId.isBlank() || questionId.isBlank()) {
            finish()
            return
        }

        val store = IdentityQuestionStore(this)

        setContent {
            RikkahubTheme {
                var input by remember { mutableStateOf("") }
                var wrong by remember { mutableStateOf(false) }

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
                            text = "先答一题",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = question,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        if (!hint.isNullOrBlank()) {
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        OutlinedTextField(
                            value = input,
                            onValueChange = {
                                input = it
                                wrong = false
                            },
                            singleLine = true,
                            label = { Text("答案") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (wrong) {
                            Text(
                                text = "不对，再想一下",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Button(
                            enabled = input.isNotBlank(),
                            onClick = {
                                if (store.verify(questionId, input)) {
                                    finishWith(BiometricResult.Success("identity_question"))
                                    finish()
                                } else {
                                    wrong = true
                                    input = ""
                                }
                            },
                        ) {
                            Text("交卷")
                        }
                        TextButton(
                            onClick = { finish() },
                        ) {
                            Text("先出去")
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        finishWith(BiometricResult.Error("user_cancelled"))
    }

    companion object {
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_QUESTION_ID = "question_id"
        const val EXTRA_QUESTION = "question"
        const val EXTRA_HINT = "hint"
    }
}
