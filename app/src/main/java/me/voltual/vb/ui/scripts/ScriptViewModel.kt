package me.voltual.vb.ui.scripts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.voltual.vb.core.database.dao.ScriptDao
import me.voltual.vb.core.database.entity.ScriptEntry

class ScriptViewModel(private val scriptDao: ScriptDao) : ViewModel() {

    val allScripts: StateFlow<List<ScriptEntry>> = scriptDao.getAllScripts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveScript(name: String, content: String, targetPackage: String?, id: Int = 0) {
        viewModelScope.launch {
            val script = ScriptEntry(
                id = id,
                name = name,
                content = content,
                targetPackage = targetPackage,
                lastModified = System.currentTimeMillis()
            )
            scriptDao.insertScript(script)
        }
    }

    fun deleteScript(script: ScriptEntry) {
        viewModelScope.launch {
            scriptDao.deleteScript(script)
        }
    }
    
    suspend fun getScriptById(id: Int): ScriptEntry? {
        return scriptDao.getScriptById(id)
    }
}