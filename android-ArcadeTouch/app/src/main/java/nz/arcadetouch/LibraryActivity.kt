package nz.arcadetouch

import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LibraryActivity : ComponentActivity() {
    private val roms by lazy { File(filesDir, "roms").apply { mkdirs() } }
    private val validName = Regex("[A-Za-z0-9_-]+\\.zip", RegexOption.IGNORE_CASE)
    private lateinit var library: LinearLayout
    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        lifecycleScope.launch {
            val failed = withContext(Dispatchers.IO) {
                uris.count { uri ->
                    runCatching {
                        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { if (it.moveToFirst()) it.getString(0) else null }
                            ?: error("Missing ROM name")
                        require(validName.matches(name))
                        val temp = File.createTempFile("import-", ".tmp", roms)
                        try {
                            val stream = contentResolver.openInputStream(uri) ?: error("Cannot open ROM")
                            stream.use { input -> temp.outputStream().use { input.copyTo(it) } }
                            java.util.zip.ZipFile(temp).use { require(it.size() > 0) }
                            check(temp.renameTo(File(roms, name.lowercase(java.util.Locale.ROOT))))
                        } finally {
                            temp.delete()
                        }
                    }.isFailure
                }
            }
            showLibrary()
            if (failed > 0) Toast.makeText(this@LibraryActivity, "有 ${failed} 个 ZIP 无法导入", Toast.LENGTH_LONG).show()
        }
    }

    private val cheatPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        lifecycleScope.launch {
            val imported = withContext(Dispatchers.IO) {
                val directory = File(filesDir, "fbneo/cheats").apply { mkdirs() }
                uris.count { uri ->
                    runCatching {
                        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { if (it.moveToFirst()) it.getString(0) else null }
                            ?: error("Missing cheat name")
                        require(name.matches(Regex("[A-Za-z0-9_-]+[.]ini", RegexOption.IGNORE_CASE)))
                        val temporary = File.createTempFile("cheats-", ".tmp", directory)
                        try {
                            val source = contentResolver.openInputStream(uri) ?: error("Cannot open cheats")
                            source.use { input -> temporary.outputStream().use { input.copyTo(it) } }
                            require(temporary.length() > 0)
                            check(temporary.renameTo(File(directory, name.lowercase(java.util.Locale.ROOT))))
                        } finally {
                            temporary.delete()
                        }
                    }.isSuccess
                }
            }
            Toast.makeText(this@LibraryActivity, "已导入 $imported / ${uris.size} 个作弊文件", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        library = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setContentView(android.widget.ScrollView(this).apply { addView(library) })
        showLibrary()
    }

    private fun showLibrary() {
        library.removeAllViews()
        addButton("导入 FBNeo ROM（ZIP）") { picker.launch(arrayOf("*/*")) }
        addButton("导入 FBNeo 作弊文件（INI）") { cheatPicker.launch(arrayOf("*/*")) }
        roms.listFiles().orEmpty().filter { it.isFile && validName.matches(it.name) }.sortedBy { it.name }
            .forEach { rom ->
                val label = if (rom.name == "captcomm.zip") "名将 · Captain Commando" else rom.name
                addButton(label) {
                    startActivity(Intent(this, GameActivity::class.java).putExtra("rom", rom.absolutePath))
                }
            }
    }

    private fun addButton(label: String, click: () -> Unit) {
        library.addView(Button(this).apply { text = label; setOnClickListener { click() } })
    }
}
