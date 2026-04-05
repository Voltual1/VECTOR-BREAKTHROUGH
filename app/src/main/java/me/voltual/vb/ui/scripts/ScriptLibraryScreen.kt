package me.voltual.vb.ui.scripts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.voltual.vb.core.database.entity.ScriptEntry
import me.voltual.vb.ui.LocalNavigator
import me.voltual.vb.ui.ScriptEditor
import org.koin.androidx.compose.koinViewModel

@Composable
fun ScriptLibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: ScriptViewModel = koinViewModel()
) {
    val scripts by viewModel.allScripts.collectAsState()
    val navigator = LocalNavigator.current

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { navigator.navigate(ScriptEditor()) }) {
                Icon(Icons.Default.Add, contentDescription = "Add Script")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(scripts, key = { it.id }) { script ->
                ScriptItem(
                    script = script,
                    onClick = { navigator.navigate(ScriptEditor(script.id)) },
                    onDelete = { viewModel.deleteScript(script) }
                )
            }
        }
    }
}

@Composable
fun ScriptItem(
    script: ScriptEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = script.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = script.targetPackage ?: "No target package",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}