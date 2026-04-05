package me.voltual.vb.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.selection.SelectionContainer
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
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ---------- Root 权限横幅 ----------
        AnimatedVisibility(
            visible = viewModel.rootStatus != RootStatus.GRANTED,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                color = when (viewModel.rootStatus) {
                    RootStatus.DENIED -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (viewModel.rootStatus) {
                            RootStatus.DENIED -> Icons.Default.Warning
                            else -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = when (viewModel.rootStatus) {
                            RootStatus.DENIED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (viewModel.rootStatus) {
                                RootStatus.DENIED -> "Root 权限被拒绝"
                                RootStatus.GRANTING -> "正在请求 Root 权限..."
                                else -> "检查 Root 状态中..."
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (viewModel.rootStatus == RootStatus.DENIED) {
                            Text(
                                text = "本应用需要 Root 权限才能注入进程",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (viewModel.rootStatus == RootStatus.DENIED) {
                        Button(onClick = { viewModel.checkRootPermission() }) {
                            Text("重试")
                        }
                    }
                }
            }
        }

        // ---------- 标题 ----------
        Text("Frida 注入控制台", style = MaterialTheme.typography.headlineSmall)

        // ---------- 脚本选择下拉框 ----------
        ExposedDropdownMenuBox(
            expanded = menuExpanded,
            onExpandedChange = {
                if (viewModel.rootStatus == RootStatus.GRANTED) menuExpanded = !menuExpanded
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = viewModel.selectedScript?.name ?: "请选择脚本",
                onValueChange = {},
                readOnly = true,
                enabled = viewModel.rootStatus == RootStatus.GRANTED,
                label = { Text("选择脚本") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                scripts.forEach { script ->
                    DropdownMenuItem(
                        text = { Text(script.name) },
                        onClick = {
                            viewModel.selectedScript = script
                            menuExpanded = false
                        }
                    )
                }
            }
        }

        // ---------- 包名输入框 ----------
        OutlinedTextField(
            value = viewModel.targetPackage,
            onValueChange = { viewModel.targetPackage = it },
            label = { Text("目标 App 包名") },
            placeholder = { Text("例如: com.android.settings") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = viewModel.rootStatus == RootStatus.GRANTED
        )

        // ---------- 注入按钮 ----------
        Button(
            onClick = { viewModel.performInjection() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.isInjecting &&
                    viewModel.selectedScript != null &&
                    viewModel.rootStatus == RootStatus.GRANTED,
            shape = RoundedCornerShape(8.dp)
        ) {
            if (viewModel.isInjecting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("开始注入")
            }
        }

        // ---------- 日志区域 ----------
        // 日志区域
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
            //  关键修改：用 SelectionContainer 包裹 Text
            SelectionContainer {
                Text(
                    text = log,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = if (log.contains("[Error]")) MaterialTheme.colorScheme.error 
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

        TextButton(
            onClick = { viewModel.clearLogs() },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("清空日志")
        }
    }
}