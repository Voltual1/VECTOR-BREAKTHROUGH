package me.voltual.vb.ui.scripts

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.extension.baseName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.voltual.vb.core.database.dao.ScriptDao
import me.voltual.vb.core.database.entity.ScriptEntry

class ScriptViewModel(private val scriptDao: ScriptDao) : ViewModel() {

    val allScripts: StateFlow<List<ScriptEntry>> = scriptDao.getAllScripts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun importScript(context: Context, uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val file = DocumentFileCompat.fromUri(context, uri)
                    if (file != null && file.exists()) {
                        val name = file.baseName // 获取不带后缀的文件名
                        val content = context.contentResolver.openInputStream(uri)?.use { input ->
                            input.bufferedReader().use { it.readText() }
                        } ?: ""
                        
                        if (content.isNotBlank()) {
                            val entry = ScriptEntry(
                                name = name,
                                content = content,
                                lastModified = System.currentTimeMillis()
                            )
                            scriptDao.insertScript(entry)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun deleteScript(script: ScriptEntry) {
        viewModelScope.launch {
            scriptDao.deleteScript(script)
        }
    }
}