package com.runtime.android.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InputBar(
    onSend: (String) -> Unit,
    enabled: Boolean,
    skillSuggestions: List<SkillSuggestion> = emptyList(),
    promptTemplates: List<PromptTemplate> = emptyList(),
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    var showSkillSuggestions by remember { mutableStateOf(false) }
    var showTemplateSuggestions by remember { mutableStateOf(false) }

    // Filter skill suggestions
    val filteredSkills = remember(text, skillSuggestions) {
        val trimmed = text.trimStart()
        if (trimmed.startsWith("@") && !trimmed.contains(' ')) {
            val prefix = trimmed.removePrefix("@").lowercase()
            skillSuggestions.filter { it.name.lowercase().startsWith(prefix) }
        } else emptyList()
    }

    // Filter template suggestions
    val filteredTemplates = remember(text, promptTemplates) {
        val trimmed = text.trimStart()
        if (trimmed.startsWith("/") && !trimmed.contains(' ')) {
            val prefix = trimmed.removePrefix("/").lowercase()
            promptTemplates.filter { it.command.lowercase().startsWith(prefix) }
        } else emptyList()
    }

    LaunchedEffect(filteredSkills) { showSkillSuggestions = filteredSkills.isNotEmpty() }
    LaunchedEffect(filteredTemplates) { showTemplateSuggestions = filteredTemplates.isNotEmpty() }

    Box(modifier = modifier) {
        // Skill popup
        if (showSkillSuggestions) {
            Box(modifier = Modifier.align(Alignment.TopStart)) {
                SkillSuggestionPopup(
                    suggestions = filteredSkills,
                    onSelect = { name -> text = "@$name "; showSkillSuggestions = false },
                    onDismiss = { showSkillSuggestions = false }
                )
            }
        }

        // Template popup
        if (showTemplateSuggestions) {
            Box(modifier = Modifier.align(Alignment.TopStart)) {
                PromptTemplatePopup(
                    templates = filteredTemplates,
                    onSelect = { template ->
                        text = template.promptText.replace("{input}", "")
                        showTemplateSuggestions = false
                    },
                    onDismiss = { showTemplateSuggestions = false }
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("消息 / @技能 / /模板") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    enabled = enabled
                )

                Spacer(modifier = Modifier.width(8.dp))

                FilledIconButton(
                    onClick = {
                        if (text.isNotBlank()) {
                            onSend(text)
                            text = ""
                        }
                    },
                    enabled = enabled && text.isNotBlank(),
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    if (!enabled) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送"
                        )
                    }
                }
            }
        }
    }
}
