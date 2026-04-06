package me.voltual.vb.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.termux.terminal.TerminalSession
import me.voltual.vb.ui.scripts.ScriptLibraryScreen
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) { page ->
            when (page) {
                0 -> InjectorTerminalContent(viewModel)
                1 -> ScriptLibraryScreen()  // 脚本库自己获取 ViewModel，无需传递
            }
        }
    }
}

@Composable
private fun InjectorTerminalContent(viewModel: HomeViewModel) {
    val scripts by viewModel.allScripts.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var terminalSession by remember { mutableStateOf<TerminalSession?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ---------- Root 权限横幅 ----------
        AnimatedVisibility(
            visible = viewModel.rootStatus != RootStatus.GRANTED,
            // ... 动画参数不变
        ) { /* 原有横幅代码 */ }

        // ---------- 标题 ----------
        Text("Frida 注入控制台", style = MaterialTheme.typography.headlineSmall)

        // ---------- 脚本选择下拉框 ----------
        ExposedDropdownMenuBox(
            expanded = menuExpanded,
            onExpandedChange = { if (viewModel.rootStatus == RootStatus.GRANTED) menuExpanded = !menuExpanded },
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
                terminalSession?.let { session ->
                    viewModel.performInjectionInTerminal(session)
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