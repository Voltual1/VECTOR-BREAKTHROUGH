package me.voltual.fridainjector

import android.content.Context
import java.io.*

object Utils {
    @Throws(IOException::class)
    fun extractAsset(context: Context, assetName: String, dest: File) {
        context.assets.open(assetName).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }

    fun writeToFile(dest: File, data: String) {
        try {
            dest.writeText(data)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun readFromFile(src: File): String {
        return try {
            src.readText()
        } catch (e: IOException) {
            e.printStackTrace()
            ""
        }
    }

    @Throws(IOException::class)
    fun readFromFile(inputStream: InputStream): String {
        return inputStream.bufferedReader().use { it.readText() }
    }
}