package com.oryctes.micontabilidad.domain.normalizer

import com.oryctes.micontabilidad.data.local.entity.CategoryEntity

object CategoryInferer {

    private val KEYWORD_MAP = mapOf(
        "Nómina / Ingresos" to listOf(
            "nomina", "salario", "pension", "prestaciones", "ingreso", "abono",
            "honorarios", "transferencia a su favor", "centro de investigaciones",
            "sueldo", "nómina"
        ),
        "Supermercado y Alimentación" to listOf(
            "mercadona", "carrefour", "dia", "lidl", "alcampo", "eroski", "aldi",
            "consum", "bonpreu", "alimentacion", "alimentación", "supermercado",
            "hipercor", "panaderia", "fruteria", "carniceria"
        ),
        "Vivienda y Suministros" to listOf(
            "hogar", "luz y gas", "repsol electricidad", "repsol gas", "endesa",
            "iberdrola", "naturgy", "canal de isabel", "agua", "o2 fibra", "o2",
            "fibra", "telefono", "teléfono", "internet", "alquiler", "comunidad",
            "vodafone", "movistar", "orange", "baser comer"
        ),
        "Restauración y Ocio" to listOf(
            "restaurante", "bar", "cafeteria", "cafetería", "burger", "mcdonald",
            "uber eats", "glovo", "cine", "netflix", "spotify", "ciemat arava",
            "mountain curry", "ocio y viajes", "ocio", "viajes", "wenga",
            "la moraleja", "taberna", "pizzeria"
        ),
        "Transporte y Combustible" to listOf(
            "movilidad", "aparcamiento", "parking", "peaje", "gasolinera", "cepsa",
            "bp", "repsol estacion", "renfe", "metro", "emt", "uber", "cabify",
            "mutua madrileña", "mutua", "coche", "moto", "taller", "itv"
        ),
        "Salud y Cuidado Personal" to listOf(
            "farmacia", "clinica", "clínica", "dentista", "hospital", "optica",
            "óptica", "peluqueria", "peluquería", "salud", "sanitas", "adeslas"
        ),
        "Compras y Tecnología" to listOf(
            "paypal", "google", "play app", "amazon", "aliexpress", "zara",
            "el corte ingles", "ikea", "mediamarkt", "apple", "suscripcion",
            "canal red", "tarjeta credito", "tarjeta", "tienda", "compras"
        ),
        "Finanzas e Impuestos" to listOf(
            "santander consumer", "nationale nederlanden", "seguro de vida",
            "seguro", "comision", "comisión", "interes", "interés", "hacienda",
            "tributo", "irpf", "cuota", "prestamo", "préstamo", "hipoteca",
            "ahorro", "podemos", "acnur"
        )
    )

    fun inferCategoryId(concept: String, categories: List<CategoryEntity>): Long? {
        val norm = DeduplicationEngine.normalizeConcept(concept)

        // 1. Coincidencia con keywords extendidas
        for (cat in categories) {
            val keywordsFromMap = KEYWORD_MAP[cat.name] ?: emptyList()
            val keywordsFromDb = cat.keywordsCsv.split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
            val allKeywords = (keywordsFromMap + keywordsFromDb).distinct()

            if (allKeywords.any { norm.contains(it) }) {
                return cat.id
            }
        }

        return null
    }
}
