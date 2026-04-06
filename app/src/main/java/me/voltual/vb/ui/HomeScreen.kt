package me.voltual.vb.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.termux.terminal.TerminalSession
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel()
) {
    val scripts by viewModel.allScripts.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var terminalSession by remember { mutableStateOf<TerminalSession?>(null) }

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
            onClick = {
                // 将注入命令写入终端
                terminalSession?.let { session ->
                    val scriptFile = viewModel.selectedScript?.let { script ->
                        // 这里需要生成注入命令，可以复用 ViewModel 中的逻辑
                        // 为了简洁，我们调用 ViewModel 的注入方法，但改为通过终端执行
                        viewModel.performInjectionInTerminal(session)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = viewModel.rootStatus == RootStatus.GRANTED && viewModel.selectedScript != null && viewModel.targetPackage.isNotBlank()
        ) {
            Text("执行注入（在终端中）")
        }

        // ---------- 终端区域 ----------
        Text("终端输出", style = MaterialTheme.typography.titleMedium)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            TerminalViewAndroidView(
                modifier = Modifier.fillMaxSize(),
                onSessionCreated = { session ->
                    terminalSession = session
                    viewModel.setTerminalSession(session)
                }
            )
        }
    }
}