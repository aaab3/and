package com.runtime.android.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.runtime.android.ui.chat.*
import com.runtime.android.ui.chatlist.ChatListScreen
import com.runtime.android.ui.chatlist.ConversationItem
import com.runtime.android.ui.settings.ProviderUiItem
import com.runtime.android.ui.settings.SettingsScreen
import com.runtime.android.ui.skills.CreateSkillScreen
import com.runtime.android.ui.skills.SkillUiItem
import com.runtime.android.ui.skills.SkillsScreen
import com.runtime.android.ui.tasks.ScheduledTaskUiItem
import com.runtime.android.ui.tasks.ScheduledTasksScreen
import com.runtime.android.ui.theme.RuntimeTheme
import kotlinx.coroutines.launch

enum class Screen { CHAT, SETTINGS, SKILLS, CREATE_SKILL, TASKS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuntimeApp(
    chatViewModel: ChatViewModel,
    conversations: List<ConversationItem>,
    providers: List<ProviderUiItem>,
    skills: List<SkillUiItem>,
    promptTemplates: List<PromptTemplate>,
    scheduledTasks: List<ScheduledTaskUiItem>,
    availableTools: List<String>,
    onSelectConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onDeleteConversation: (String) -> Unit,
    onAddProvider: (name: String, baseUrl: String, apiKey: String, modelId: String, systemPrompt: String) -> Unit,
    onEditProvider: (ProviderUiItem) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onSetDefaultProvider: (String) -> Unit,
    onImportSkillFile: () -> Unit,
    onImportSkillFolder: () -> Unit,
    onDeleteSkill: (String) -> Unit,
    onCreateSkill: (name: String, description: String, body: String, tools: List<String>) -> Unit,
    onAddTask: (name: String, skillName: String?, prompt: String, schedule: String) -> Unit,
    onToggleTask: (id: String, enabled: Boolean) -> Unit,
    onDeleteTask: (String) -> Unit
) {
    RuntimeTheme {
        var currentScreen by remember { mutableStateOf(Screen.CHAT) }
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        when (currentScreen) {
            Screen.CHAT -> {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            ChatListScreen(
                                conversations = conversations,
                                onSelect = { id ->
                                    onSelectConversation(id)
                                    scope.launch { drawerState.close() }
                                },
                                onNewChat = {
                                    onNewChat()
                                    scope.launch { drawerState.close() }
                                },
                                onDelete = onDeleteConversation,
                                onOpenSettings = {
                                    currentScreen = Screen.SETTINGS
                                    scope.launch { drawerState.close() }
                                },
                                onOpenSkills = {
                                    currentScreen = Screen.SKILLS
                                    scope.launch { drawerState.close() }
                                },
                                onOpenTasks = {
                                    currentScreen = Screen.TASKS
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    }
                ) {
                    ChatScreen(
                        viewModel = chatViewModel,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenSettings = { currentScreen = Screen.SETTINGS },
                        skillSuggestions = skills.map { SkillSuggestion(it.name, it.description) },
                        promptTemplates = promptTemplates
                    )
                }
            }

            Screen.SETTINGS -> {
                SettingsScreen(
                    providers = providers,
                    onAddProvider = onAddProvider,
                    onEditProvider = onEditProvider,
                    onDeleteProvider = onDeleteProvider,
                    onSetDefault = onSetDefaultProvider,
                    onFetchModels = { baseUrl, apiKey ->
                        com.runtime.android.ModelListFetcher.fetchModels(baseUrl, apiKey)
                    },
                    onBack = { currentScreen = Screen.CHAT }
                )
            }

            Screen.SKILLS -> {
                SkillsScreen(
                    skills = skills,
                    onImportFile = onImportSkillFile,
                    onImportFolder = onImportSkillFolder,
                    onDelete = onDeleteSkill,
                    onCreate = { currentScreen = Screen.CREATE_SKILL },
                    onBack = { currentScreen = Screen.CHAT }
                )
            }

            Screen.CREATE_SKILL -> {
                CreateSkillScreen(
                    onSave = { name, desc, body, tools ->
                        onCreateSkill(name, desc, body, tools)
                        currentScreen = Screen.SKILLS
                    },
                    availableTools = availableTools,
                    onBack = { currentScreen = Screen.SKILLS }
                )
            }

            Screen.TASKS -> {
                ScheduledTasksScreen(
                    tasks = scheduledTasks,
                    skills = skills.map { it.name },
                    onAdd = onAddTask,
                    onToggle = onToggleTask,
                    onDelete = onDeleteTask,
                    onBack = { currentScreen = Screen.CHAT }
                )
            }
        }
    }
}
