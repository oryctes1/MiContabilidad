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
    version = 3,
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

        fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Precarga de categorías básicas
                    CoroutineScope(Dispatchers.IO).launch {
                        val initialCategories = listOf(
                            CategoryEntity(name = "Nómina / Ingresos", colorHex = "#4CAF50", iconName = "payments", keywordsCsv = "nomina,transferencia a su favor,salario,ingreso,abono,honorarios"),
                            CategoryEntity(name = "Supermercado y Alimentación", colorHex = "#FF9800", iconName = "shopping_cart", keywordsCsv = "mercadona,carrefour,dia,lidl,alcampo,eroski,aldi,consum,bonpreu"),
                            CategoryEntity(name = "Vivienda y Suministros", colorHex = "#2196F3", iconName = "home", keywordsCsv = "alquiler,comunidad,iberdrola,endesa,naturgy,vodafone,movistar,orange,aqua,gas"),
                            CategoryEntity(name = "Restauración y Ocio", colorHex = "#E91E63", iconName = "restaurant", keywordsCsv = "restaurante,bar,cafeteria,burger,mcdonald,uber eats,glovo,cine,netflix,spotify"),
                            CategoryEntity(name = "Transporte y Combustible", colorHex = "#9C27B0", iconName = "directions_car", keywordsCsv = "repsol,cepsa,bp,gasolinera,renfe,metro,emt,peaje,uber,cabify"),
                            CategoryEntity(name = "Salud y Cuidado Personal", colorHex = "#00BCD4", iconName = "local_hospital", keywordsCsv = "farmacia,clinica,dentista,hospital,optica,peluqueria"),
                            CategoryEntity(name = "Compras y Tecnología", colorHex = "#607D8B", iconName = "shopping_bag", keywordsCsv = "amazon,aliexpress,zara,el corte ingles,ikea,mediamarkt,apple"),
                            CategoryEntity(name = "Finanzas e Impuestos", colorHex = "#795548", iconName = "account_balance", keywordsCsv = "comision,interes,hacienda,irpf,tributo,seguro,cuota")
                        )
                        val database = buildDatabase(context)
                        initialCategories.forEach { database.categoryDao().insertCategory(it) }
                    }
                }
            }).build()
        }
    }
}
