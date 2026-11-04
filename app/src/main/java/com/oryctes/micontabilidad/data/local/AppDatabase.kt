package com.oryctes.micontabilidad.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.oryctes.micontabilidad.data.local.dao.AccountDao
import com.oryctes.micontabilidad.data.local.dao.CategoryDao
import com.oryctes.micontabilidad.data.local.dao.ImportBatchDao
import com.oryctes.micontabilidad.data.local.dao.ImportProfileDao
import com.oryctes.micontabilidad.data.local.dao.SourceFileDao
import com.oryctes.micontabilidad.data.local.dao.TransactionDao
import com.oryctes.micontabilidad.data.local.entity.AccountEntity
import com.oryctes.micontabilidad.data.local.entity.CategoryEntity
import com.oryctes.micontabilidad.data.local.entity.ImportBatchEntity
import com.oryctes.micontabilidad.data.local.entity.ImportProfileEntity
import com.oryctes.micontabilidad.data.local.entity.SourceFileEntity
import com.oryctes.micontabilidad.data.local.entity.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        SourceFileEntity::class,
        ImportBatchEntity::class,
        ImportProfileEntity::class,
        CategoryEntity::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun sourceFileDao(): SourceFileDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun importProfileDao(): ImportProfileDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        const val DATABASE_NAME = "micontabilidad.db"

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN isExcludedFromTotals INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN isCreditCard INTEGER NOT NULL DEFAULT 0")
                // Migración inmediata de los movimientos existentes
                db.execSQL("UPDATE transactions SET isCreditCard = 1 WHERE concept LIKE '%Liquidado%'")
                db.execSQL("UPDATE transactions SET isExcludedFromTotals = 1 WHERE concept LIKE '%Cargo mensual Tarjeta%' OR concept LIKE '%Liquidaci%tarjeta%' OR concept LIKE '%Pago tarjeta%'")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE import_profiles ADD COLUMN conceptDetailsIndex INTEGER NOT NULL DEFAULT -1")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN type TEXT NOT NULL DEFAULT 'EXPENSE'")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Comprobar si la columna ya existe antes de añadirla para evitar duplicados si se migró manualmente
                var columnExists = false
                val cursor = db.query("PRAGMA table_info(transactions)")
                while (cursor.moveToNext()) {
                    val nameIndex = cursor.getColumnIndex("name")
                    if (nameIndex != -1 && cursor.getString(nameIndex) == "isCategoryManuallyAssigned") {
                        columnExists = true
                        break
                    }
                }
                cursor.close()

                if (!columnExists) {
                    db.execSQL("ALTER TABLE transactions ADD COLUMN isCategoryManuallyAssigned INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                var columnExists = false
                val cursor = db.query("PRAGMA table_info(import_profiles)")
                while (cursor.moveToNext()) {
                    val nameIndex = cursor.getColumnIndex("name")
                    if (nameIndex != -1 && cursor.getString(nameIndex) == "invertAmountSign") {
                        columnExists = true
                        break
                    }
                }
                cursor.close()

                if (!columnExists) {
                    db.execSQL("ALTER TABLE import_profiles ADD COLUMN invertAmountSign INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Precarga de categorías básicas oficiales
                    CoroutineScope(Dispatchers.IO).launch {
                        val database = buildDatabase(context)
                        com.oryctes.micontabilidad.domain.normalizer.CategoryInferer.DEFAULT_CATEGORIES.forEach {
                            database.categoryDao().insertCategory(it)
                        }
                    }
                }
            }).build()
        }
    }
}
