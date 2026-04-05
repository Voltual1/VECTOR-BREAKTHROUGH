package me.voltual.vb.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "scripts")
@Serializable
data class ScriptEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val content: String,
    val targetPackage: String? = null, // 预设的注入目标包名
    val lastModified: Long = System.currentTimeMillis()
)