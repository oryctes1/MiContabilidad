package com.oryctes.micontabilidad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.oryctes.micontabilidad.ui.components.AppBottomBar
import com.oryctes.micontabilidad.ui.navigation.NavGraph
import com.oryctes.micontabilidad.ui.theme.MiContabilidadTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDbTransferIntent()
        enableEdgeToEdge()
        setContent {
            MiContabilidadTheme {
                MainAppContent()
            }
        }
    }

    private fun handleDbTransferIntent() {
        val action = intent?.action
        if (action == "com.oryctes.micontabilidad.EXPORT_DB") {
            try {
                val dbFile = getDatabasePath("micontabilidad.db")
                val walFile = java.io.File(dbFile.path + "-wal")
                val shmFile = java.io.File(dbFile.path + "-shm")

                val targetDir = java.io.File(getExternalFilesDir(null), "db_transfer")
                targetDir.mkdirs()

                if (dbFile.exists()) dbFile.copyTo(java.io.File(targetDir, "micontabilidad.db"), overwrite = true)
                if (walFile.exists()) walFile.copyTo(java.io.File(targetDir, "micontabilidad.db-wal"), overwrite = true)
                if (shmFile.exists()) shmFile.copyTo(java.io.File(targetDir, "micontabilidad.db-shm"), overwrite = true)

                android.util.Log.d("DB_TRANSFER", "Export successful to: ${targetDir.absolutePath}")
            } catch (e: Exception) {
                android.util.Log.e("DB_TRANSFER", "Export failed", e)
            }
        } else if (action == "com.oryctes.micontabilidad.IMPORT_DB") {
            try {
                val sourceDir = java.io.File(getExternalFilesDir(null), "db_transfer")
                val dbFile = getDatabasePath("micontabilidad.db")
                val walFile = java.io.File(dbFile.path + "-wal")
                val shmFile = java.io.File(dbFile.path + "-shm")

                val srcDb = java.io.File(sourceDir, "micontabilidad.db")
                val srcWal = java.io.File(sourceDir, "micontabilidad.db-wal")
                val srcShm = java.io.File(sourceDir, "micontabilidad.db-shm")

                if (srcDb.exists()) {
                    dbFile.parentFile?.mkdirs()
                    srcDb.copyTo(dbFile, overwrite = true)
                    if (srcWal.exists()) srcWal.copyTo(walFile, overwrite = true) else if (walFile.exists()) walFile.delete()
                    if (srcShm.exists()) srcShm.copyTo(shmFile, overwrite = true) else if (shmFile.exists()) shmFile.delete()
                    android.util.Log.d("DB_TRANSFER", "Import successful from: ${sourceDir.absolutePath}")
                }
            } catch (e: Exception) {
                android.util.Log.e("DB_TRANSFER", "Import failed", e)
            }
        }
    }
}

@Composable
fun MainAppContent() {
    val navController = rememberNavController()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AppBottomBar(navController = navController)
        }
    ) { innerPadding ->
        NavGraph(
            navController = navController,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        )
    }
}
