package me.voltual.vb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel()
) {
    val scripts by viewModel.allScripts.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Frida 注入控制台", style = MaterialTheme.typography.headlineSmall)

        // 1. 脚本选择下拉框
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = viewModel.selectedScript?.name ?: "请选择脚本",
                onValueChange = {},
                readOnly = true,
                label = { Text("选择脚本") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                scripts.forEach { script ->
                    DropdownMenuItem(
                        text = { Text(script.name) },
                        onClick = {
                            viewModel.selectedScript = script
                            expanded = false
                        }
                    )
                }
            }
        }

        // 2. 包名输入框
        OutlinedTextField(
            value = viewModel.targetPackage,
            onValueChange = { viewModel.targetPackage = it },
            label = { Text("目标 App 包名") },
            placeholder = { Text("例如: com.android.settings") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // 3. 注入按钮
        Button(
            onClick = { viewModel.performInjection() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.isInjecting && viewModel.selectedScript != null,
            shape = RoundedCornerShape(8.dp)
        ) {
            if (viewModel.isInjecting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("开始注入")
            }
        }

        // 4. 日志区域
        Text("运行日志", style = MaterialTheme.typography.titleMedium)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(8.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(viewModel.logs) { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = if (log.contains("[Error]")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        TextButton(
            onClick = { viewModel.clearLogs() },
            modifier = Modifier.align(androidx.compose.ui.Alignment.End)
        ) {
            Text("清空日志")
        }
    }
}