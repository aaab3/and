package com.runtime.android

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.runtime.android.db.AppDatabase
import com.runtime.android.db.RoomConversationRepository
import com.runtime.android.db.RoomMessageRepository
import com.runtime.android.security.AndroidSecretProvider
import com.runtime.binding.DefaultBindingResolver
import com.runtime.conversation.DefaultConversationRuntime
import com.runtime.conversation.DefaultConversationStore
import com.runtime.conversation.SendMessageRequest
import com.runtime.core.AppResult
import com.runtime.model.AnthropicLikeModelProvider
import com.runtime.model.DefaultProviderRegistry
import com.runtime.model.GeminiLikeModelProvider
import com.runtime.model.openai.OpenAiCompatibleModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class MainActivity : AppCompatActivity() {

    private lateinit var editBaseUrl: TextInputEditText
    private lateinit var editApiKey: TextInputEditText
    private lateinit var editMessage: TextInputEditText
    private lateinit var textLog: TextView

    private lateinit var db: AppDatabase
    private lateinit var secrets: AndroidSecretProvider
    private val providerRepo = InMemoryProviderDefinitionRepository()
    private val credRepo = InMemoryCredentialRefRepository()
    private val profileRepo = InMemoryModelProfileRepository()
    private val bindingRepo = InMemoryModelBindingRepository()

    private lateinit var runtime: DefaultConversationRuntime
    private lateinit var conversationId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editBaseUrl = findViewById(R.id.editBaseUrl)
        editApiKey = findViewById(R.id.editApiKey)
        editMessage = findViewById(R.id.editMessage)
        textLog = findViewById(R.id.textLog)
        val buttonSend = findViewById<Button>(R.id.buttonSend)

        db = AppDatabase.get(this)
        secrets = AndroidSecretProvider(this)
        val convRepo = RoomConversationRepository(db)
        val msgRepo = RoomMessageRepository(db)
        val store = DefaultConversationStore(convRepo, msgRepo)
        val resolver = DefaultBindingResolver(
            bindingRepo,
            providerRepo,
            profileRepo,
            credRepo,
            secrets
        )
        val registry = DefaultProviderRegistry(
            listOf(
                OpenAiCompatibleModelProvider(OkHttpClient()),
                AnthropicLikeModelProvider(),
                GeminiLikeModelProvider()
            )
        )
        runtime = DefaultConversationRuntime(store, resolver, registry)

        lifecycleScope.launch {
            val base = editBaseUrl.text?.toString().orEmpty().ifBlank { "https://api.openai.com/v1" }
            val key = editApiKey.text?.toString().orEmpty()
            seedBindingWorkspace(secrets, providerRepo, credRepo, profileRepo, bindingRepo, key, base)

            when (val savedKey = withContext(Dispatchers.IO) { secrets.getSecret("alias-key") }) {
                is AppResult.Success -> withContext(Dispatchers.Main) {
                    if (editApiKey.text.isNullOrBlank()) {
                        editApiKey.setText(savedKey.value)
                    }
                }
                is AppResult.Failure -> Unit
            }

            conversationId = withContext(Dispatchers.IO) {
                val latest = db.conversationDao().latestForWorkspace("ws-1")
                if (latest != null) {
                    withContext(Dispatchers.Main) {
                        appendLog("Resuming conversation ${latest.id}\n")
                    }
                    latest.id
                } else {
                    when (val c = store.createConversation("ws-1", "MVP")) {
                        is AppResult.Failure -> {
                            withContext(Dispatchers.Main) {
                                appendLog("createConversation: ${c.error.message}\n")
                            }
                            ""
                        }
                        is AppResult.Success -> {
                            withContext(Dispatchers.Main) {
                                appendLog("Ready. conversationId=${c.value.id}\n")
                            }
                            c.value.id
                        }
                    }
                }
            }
        }

        buttonSend.setOnClickListener {
            lifecycleScope.launch {
                if (!::conversationId.isInitialized || conversationId.isBlank()) {
                    appendLog("Conversation not ready.\n")
                    return@launch
                }
                val base = editBaseUrl.text?.toString().orEmpty().ifBlank { "https://api.openai.com/v1" }
                val key = editApiKey.text?.toString().orEmpty()
                val msg = editMessage.text?.toString().orEmpty()
                if (msg.isBlank()) {
                    appendLog("Message is empty.\n")
                    return@launch
                }
                seedBindingWorkspace(secrets, providerRepo, credRepo, profileRepo, bindingRepo, key, base)
                val result = withContext(Dispatchers.IO) {
                    runtime.sendMessage(
                        SendMessageRequest(
                            workspaceId = "ws-1",
                            conversationId = conversationId,
                            userMessage = msg
                        )
                    )
                }
                when (result) {
                    is AppResult.Success -> appendLog("assistant: ${result.value.assistantMessage.content}\n")
                    is AppResult.Failure -> appendLog("error [${result.error.code}]: ${result.error.message}\n")
                }
            }
        }
    }

    private fun appendLog(line: String) {
        textLog.append(line)
    }
}
