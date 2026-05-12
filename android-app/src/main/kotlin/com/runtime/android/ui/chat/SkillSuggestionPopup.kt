package com.runtime.android.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@Composable
fun SkillSuggestionPopup(
    suggestions: List<SkillSuggestion>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (suggestions.isEmpty()) return

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false)
    ) {
        Surface(
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(horizontal = 16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.heightIn(max = 200.dp)
            ) {
                items(suggestions) { suggestion ->
                    ListItem(
                        headlineContent = {
                            Text("@${suggestion.name}", style = MaterialTheme.typography.bodyMedium)
                        },
                        supportingContent = {
                            Text(
                                suggestion.description,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.clickable { onSelect(suggestion.name) }
                    )
                }
            }
        }
    }
}

data class SkillSuggestion(
    val name: String,
    val description: String
)
