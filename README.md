# MiContabilidad - App Android Nativa de Contabilidad Personal Local-First

**MiContabilidad** es una aplicación Android nativa diseñada para gestionar la contabilidad personal a partir de extractos bancarios y de tarjetas de crédito importados manualmente (archivos **CSV**, **XLSX** y **PDF**).

Diseñada bajo el principio de **máxima privacidad y soberanía de datos (Local-First)**:
- **0 Conexiones a bancos**: no solicita credenciales, no usa agregadores de terceros ni APIs bancarias externas.
- **0 Telemetría y sin permisos de red**: todos los datos residen exclusivamente en la base de datos local SQLite/Room de tu dispositivo.
- **Acceso seguro a archivos**: uso de **Storage Access Framework (SAF)** de Android, sin requerir permisos amplios de almacenamiento (`MANAGE_EXTERNAL_STORAGE` o `READ_EXTERNAL_STORAGE`).

---

## 1. Arquitectura del Proyecto

El proyecto sigue una arquitectura **Clean Architecture + MVI/MVVM reactiva**:

```
app/src/main/java/com/oryctes/micontabilidad/
├── MiContabilidadApp.kt              # Inicialización de la aplicación y Koin
├── MainActivity.kt                  # Activity principal con Scaffold, tema y Navigation
├── di/
│   └── AppModule.kt                 # Inyección de dependencias con Koin
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt           # Room Database con migraciones y callbacks
│   │   ├── Converters.kt            # TypeConverters (LocalDate, BigDecimal, Enums)
│   │   ├── dao/
│   │   │   ├── AccountDao.kt
│   │   │   ├── TransactionDao.kt
│   │   │   ├── SourceFileDao.kt
│   │   │   ├── ImportBatchDao.kt
│   │   │   ├── ImportProfileDao.kt
│   │   └── entity/                  # Entidades de base de datos
│   ├── parser/
│   │   ├── StatementParser.kt       # Interfaz común para lectura de extractos
│   │   ├── TabularRow.kt            # Abstracción desacoplada de fila tabular
│   │   ├── CsvStatementParser.kt    # Parser CSV con auto-delimitador y codificación
│   │   ├── XlsxStatementParser.kt   # Parser XLSX de streaming con FastExcel
│   │   ├── PdfStatementParser.kt    # Extractor tabular con PdfBox-Android
│   │   └── ParserFactory.kt         # Factoría según MIME / extensión
│   └── repository/
│       ├── AccountRepositoryImpl.kt
│       ├── TransactionRepositoryImpl.kt
│       └── ImportRepositoryImpl.kt
├── domain/
│   ├── model/                       # Modelos de negocio inmutables
│   │   ├── Account.kt
│   │   ├── Transaction.kt
│   │   ├── ColumnMapping.kt
│   │   ├── ImportProfile.kt
│   │   └── Summaries.kt
│   ├── normalizer/
│   │   ├── DateNormalizer.kt        # Soporta dd/MM/yyyy, yyyy-MM-dd, etc.
│   │   ├── AmountNormalizer.kt      # BigDecimal con soporte europeo, anglosajón y contable
│   │   └── DeduplicationEngine.kt   # Hash SHA-256 + Detección heurística difusa
│   └── repository/                  # Interfaces de repositorio
└── ui/
    ├── theme/                       # Jetpack Compose Material 3 (Claro / Oscuro)
    ├── navigation/                  # NavHost y Rutas
    ├── components/                  # Gráficos Canvas nativos interactivos
    │   ├── BalanceEvolutionChart.kt
    │   ├── MonthlyBarChart.kt
    │   ├── CategoryDonutChart.kt
    │   └── EmptyState.kt
    ├── dashboard/                   # Resumen global y accesos rápidos
    ├── accounts/                    # Gestión de cuentas y tarjetas
    ├── transactions/                # Listado centrado en los 5 campos de negocio
    ├── import/                      # Asistente en 4 pasos para SAF y mapeo
    ├── summaries/                   # Gráficas anuales y mensuales por categoría
    └── settings/                    # Exportación CSV y perfiles de mapeo
```

---

## 2. Decisiones Técnicas y Justificación del Stack

| Componente | Elección | Justificación |
|---|---|---|
| **Inyección de dependencias** | **Koin 4.0** | A diferencia de Hilt, Koin es 100% Kotlin puro, no requiere plugins adicionales de transformación de bytecode de Gradle (que generan frecuentes incompatibilidades con AGP 9.3+ / Gradle 9.5), reduce drásticamente el tiempo de compilación y ofrece una integración directa con Compose (`koinViewModel()`). |
| **Normalización Numérica** | **BigDecimal** | **Nunca se utiliza `Double` o `Float`** para cálculos monetarios para evitar errores de redondeo en aritmética de punto flotante IEEE 754. Se fija una escala de 2 decimales con redondeo `HALF_UP`. |
| **Normalización Temporal** | **LocalDate** | Fechas nativas del estándar `java.time`, inmunes a desfases horarios (`ZoneId` / `Timezone`). |
| **Parser CSV** | **Apache Commons CSV** | Maneja adecuadamente comillas dobles, saltos de línea dentro de campos y comas internas. Cuenta con detección automática de delimitadores (`;`, `,`, `\t`, `|`) y codificación (`UTF-8` e `ISO-8859-1` / Windows-1252 para bancos tradicionales). |
| **Parser XLSX** | **FastExcel Reader** | Apache POI completo pesa más de 30-40 MB y ralentiza el arranque en Android. FastExcel pesa menos de 100 KB, funciona mediante streaming SAX sin cargar el DOM completo en memoria RAM, siendo idóneo para dispositivos móviles. |
| **Parser PDF** | **PdfBox-Android** | Extrae el texto posicionalmente con ordenación espacial (`sortByPosition = true`), permitiendo reconstruir las columnas de extractos bancarios digitales. |
| **Gráficas** | **Canvas Jetpack Compose** | Componentes propios optimizados con `androidx.compose.foundation.Canvas` y `Animatable`. Ventajas: cero dependencias pesadas externas, adaptación total a tema claro y oscuro, soporte inmediato para animaciones y compatibilidad garantizada con Compose BOM. |

---

## 3. Estrategia de Deduplicación

### A. Deduplicación Idempotente (Exacta)
Al importar un extracto, cada fila genera una huella criptográfica SHA-256 única:
$$\text{Hash} = \text{SHA-256}(\text{accountId} \parallel \text{operationDate} \parallel \text{conceptNormalizado} \parallel \text{amountNormalizado} \parallel \text{balanceNormalizado} \parallel \text{currency})$$

- Si el usuario importa dos veces el mismo archivo o archivos de extracto que se solapan en fechas, las transacciones idénticas son ignoradas silenciosamente sin alterar la integridad de los datos.

### B. Detección Difusa de Duplicados (Heurística de Solapamiento)
Si dos apuntes coinciden en:
1. Misma cuenta bancaria.
2. Misma cuantía exacta.
3. Diferencia de fechas $\le 3$ días (p. ej. fecha de cargo en fin de semana vs fecha contable de lunes).
4. Similitud semántica en el concepto $\ge 65\%$ (calculada mediante intersección de tokens Jaccard y distancia Levenshtein).

En este caso, la transacción **se inserta pero se marca automáticamente** con `isPossibleDuplicate = true` y estado `PENDING_REVIEW`. El usuario puede confirmar el apunte o descartarlo desde la pantalla de Movimientos con un solo toque.

---

## 4. Los 5 Campos de Negocio

La interfaz visual y el listado de movimientos se centran estrictamente en los cinco campos clave solicitados:
1. **Fecha Operación**: Día en que se realizó el movimiento.
2. **Fecha Valor**: Día contable efectivo.
3. **Concepto**: Descripción clara y normalizada.
4. **Cuantía**: Importe con signo y color (Verde positivo para ingresos, Rojo negativo para gastos).
5. **Saldo**: Saldo resultante tras el movimiento (si viene informado en el extracto).

Los datos de trazabilidad técnica (hash SHA-256, ID de archivo de origen, ID de lote de importación) se mantienen en la base de datos pero se muestran únicamente en el diálogo de detalle técnico para no sobrecargar al usuario.

---

## 5. Instrucciones de Compilación y Ejecución

### Requisitos:
- Android Studio Ladybug / Meerkat o superior.
- JDK 17 o 21 (incluido en Android Studio JBR).
- Android SDK Platform 35 (`minSdk = 26`, `targetSdk = 35`).

### Desde consola:
```powershell
# Ejecutar tests unitarios
.\gradlew.bat test

# Compilar APK de depuración
.\gradlew.bat assembleDebug
```

---

## 6. Archivos de Prueba Incluidos

En la carpeta `sample_data/` encontrarás dos extractos bancarios ficticios listos para probar:
- `sample_data/extracto_bbva_ejemplo.csv`: extracto en formato estándar europeo con separador `;`, fechas `dd/MM/yyyy` y comas decimales.
- `sample_data/extracto_tarjeta_ejemplo.csv`: extracto con formato de tarjeta de crédito con columnas separadas de Cargo y Abono.

---

## 7. Limitaciones y Hoja de Ruta

- **PDF escaneados (solo imagen)**: Los extractos generados en PDF digitalmente son leídos directamente por `PdfBox-Android`. Para documentos escaneados físicamente como fotos sin capa de texto, se puede activar ML Kit Text Recognition como fallback, teniendo en cuenta que la precisión en tablas complejas puede requerir ajuste manual de columnas.
- **Categorización avanzada con reglas**: Implementada base de categorización automática por palabras clave en concepto (Nómina, Supermercado, Suministros, Transporte, etc.) que se expandirá en futuras versiones con reglas personalizadas por usuario.
