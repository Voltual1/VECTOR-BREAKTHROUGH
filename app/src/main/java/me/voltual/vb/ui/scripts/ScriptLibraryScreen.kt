package me.voltual.vb.ui.scripts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.voltual.vb.core.database.entity.ScriptEntry
import org.koin.androidx.compose.koinViewModel

@Composable
fun ScriptLibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: ScriptViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scripts by viewModel.allScripts.collectAsState()

    // 文件选择器：过滤 .js 文件（虽然有些管理器不严格遵守 mime，但通常设为 text/* 或 application/javascript）
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importScript(context, it) }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { launcher.launch("application/javascript") },
                icon = { Icon(Icons.Default.FileUpload, null) },
                text = { Text("导入 JS 脚本") }
            )
        }
    ) { padding ->
        if (scripts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无脚本，请点击右下角导入", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(scripts, key = { it.id }) { script ->
                    ScriptListItem(
                        script = script,
                        onDelete = { viewModel.deleteScript(script) }
                    )
                }
            }
        }
    }
}

@Composable
fun ScriptListItem(
    script: ScriptEntry,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(script.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "大小: ${script.content.length} 字符",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}