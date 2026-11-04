package com.oryctes.micontabilidad.domain.normalizer

import com.oryctes.micontabilidad.data.local.entity.CategoryEntity

object CategoryInferer {

    // Palabras clave por defecto para las categorías oficiales
    val DEFAULT_CATEGORIES = listOf(
        // 1. Para Ingresos
        CategoryEntity(
            name = "Nóminas",
            colorHex = "#2E7D32",
            iconName = "payments",
            keywordsCsv = "nomina,nómina,salario,sueldo,pension,pensión,centro de investigaciones,ciemat",
            type = "INCOME"
        ),
        CategoryEntity(
            name = "Bizum",
            colorHex = "#00897B",
            iconName = "phone_android",
            keywordsCsv = "bizum",
            type = "INCOME"
        ),
        CategoryEntity(
            name = "Intereses",
            colorHex = "#1565C0",
            iconName = "trending_up",
            keywordsCsv = "intereses,interes,liquidación de intereses,abono de intereses,rendimiento,dividendo",
            type = "INCOME"
        ),
        CategoryEntity(
            name = "Extras",
            colorHex = "#00ACC1",
            iconName = "card_giftcard",
            keywordsCsv = "premio,devolucion hacienda,devolución hacienda,ingreso extra,loterias,apuestas",
            type = "INCOME"
        ),
        CategoryEntity(
            name = "Otros ingresos",
            colorHex = "#5C6BC0",
            iconName = "attach_money",
            keywordsCsv = "ingresos de otras entidades,abono,otros ingresos",
            type = "INCOME"
        ),

        // 2. Para Gastos
        CategoryEntity(
            name = "Luz",
            colorHex = "#FBC02D",
            iconName = "bolt",
            keywordsCsv = "electricidad,iberdrola clientes,endesa energia,naturgy energia,repsol electricidad,luz,totalenergies,holaluz",
            type = "EXPENSE"
        ),
        CategoryEntity(
            name = "Gas",
            colorHex = "#F57C00",
            iconName = "local_fire_department",
            keywordsCsv = "gas,baser,repsol butano,repsol gas,naturgy gas,endesa gas,madrileña red de gas,butano",
            type = "EXPENSE"
        ),
        CategoryEntity(
            name = "Agua",
            colorHex = "#0288D1",
            iconName = "water_drop",
            keywordsCsv = "canal de isabel,aguas,agua,aqualia,emasesa,agbar,consorcio de aguas",
            type = "EXPENSE"
        ),
        CategoryEntity(
            name = "Alimentación",
            colorHex = "#43A047",
            iconName = "shopping_cart",
            keywordsCsv = "mercadona,carrefour,dia,lidl,alcampo,eroski,aldi,consum,bonpreu,hipercor,supercor,ahorramas,panaderia,fruteria,carniceria,pescaderia,supermercado",
            type = "EXPENSE"
        ),
        CategoryEntity(
            name = "Hogar",
            colorHex = "#6D4C41",
            iconName = "home",
            keywordsCsv = "amzn,amazon,paypal,ikea,leroy merlin,bricomart,obramat,zara home,bricor,comunidad,ferreteria,muebles,bazar,limpieza,jardineria",
            type = "EXPENSE"
        ),
        CategoryEntity(
            name = "Comunicaciones",
            colorHex = "#3949AB",
            iconName = "wifi",
            keywordsCsv = "vodafone,movistar,orange,o2,o2 fibra,digi,yoigo,jazztel,pepephone,telefonica,telefónica,fibra,masmovil",
            type = "EXPENSE"
        ),
        CategoryEntity(
            name = "Ocio, Deportes y Viajes",
            colorHex = "#E91E63",
            iconName = "sports_esports",
            keywordsCsv = "restaurante,restaurantes,bar,bares,cafeteria,cafeterias,cafetería,cafeterías,burger,mcdonald,cine,netflix,spotify,hbo,amazon prime,viajes,hotel,booking,vueling,iberia,renfe,gimnasio,deportes,decathlon,entradas,teatro",
            type = "EXPENSE"
        ),
        CategoryEntity(
            name = "Coches",
            colorHex = "#546E7A",
            iconName = "directions_car",
            keywordsCsv = "gasolinera,repsol estacion,cepsa,bp,shell,galp,mutua madrileña,mutua,itv,taller,aparcamiento,parking,peaje,mantenimiento vehiculo",
            type = "EXPENSE"
        ),
        CategoryEntity(
            name = "ONGs y Asociaciones",
            colorHex = "#8E24AA",
            iconName = "volunteer_activism",
            keywordsCsv = "acnur,cruz roja,greenpeace,medicos sin fronteras,unicef,oxfam,ong,caritas,cáritas,donacion,donación,asociacion,asociación",
            type = "EXPENSE"
        ),
        CategoryEntity(
            name = "Salud",
            colorHex = "#D81B60",
            iconName = "local_hospital",
            keywordsCsv = "farmacia,clinica,clínica,dentista,hospital,optica,óptica,sanitas,adeslas,asisa,medico,médico,podologo,fisioterapia",
            type = "EXPENSE"
        ),
        CategoryEntity(
            name = "Pagas y cajeros",
            colorHex = "#7CB342",
            iconName = "atm",
            keywordsCsv = "reintegro cajero,cajero,cajero automatico,disposicion de efectivo,retirada efectivo,paga",
            type = "EXPENSE"
        ),
        CategoryEntity(
            name = "Estudios",
            colorHex = "#1E88E5",
            iconName = "school",
            keywordsCsv = "colegio,universidad,matricula,matrícula,academia,libros de texto,curso,formacion,formación,guarderia,guardería",
            type = "EXPENSE"
        ),
        CategoryEntity(
            name = "Ahorro",
            colorHex = "#26A69A",
            iconName = "savings",
            keywordsCsv = "fondos,fondo de inversion,deposito plazo,depósito plazo,compra acciones,degiro,trade republic,myinvestor,indexa,inversion,inversión,plan de pensiones",
            type = "SAVING"
        ),
        CategoryEntity(
            name = "Otros gastos",
            colorHex = "#78909C",
            iconName = "more_horiz",
            keywordsCsv = "gastos varios,comisiones,tarjeta debito,tarjeta credito,comision mantenimiento",
            type = "EXPENSE"
        )
    )

    fun matchesKeyword(normConcept: String, normKeyword: String): Boolean {
        if (normKeyword.isBlank()) return false
        val cleanKeyword = normKeyword.trim()
        // Si la keyword contiene espacios (frase compuesta como "repsol gas" o "amazon prime")
        return if (cleanKeyword.contains(" ")) {
            normConcept.contains(cleanKeyword)
        } else {
            // Si la keyword es una sola palabra:
            // 1) Coincidencia de palabra completa independiente (ej. "gas" en "repsol gas")
            // 2) Coincidencia de prefijo/plural común en español (ej. "restaurante" coincide con "restaurantes", "cafeteria" con "cafeterias")
            val escaped = Regex.escape(cleanKeyword)
            val regex = Regex("(^|\\s)${escaped}(s|es)?(\\s|$)")
            regex.containsMatchIn(normConcept)
        }
    }

    fun inferCategory(concept: String, categories: List<CategoryEntity>, amount: java.math.BigDecimal? = null): CategoryEntity? {
        val norm = DeduplicationEngine.normalizeConcept(concept)

        // 1. Probar en las categorías proporcionadas (Base de datos)
        // Se priorizan las coincidencias con palabras clave más específicas (más largas)
        // para que palabras clave concretas como "baser" o "repsol gas" ganen a genéricas como "luz" o "gas".
        val dbMatches = mutableListOf<Pair<CategoryEntity, Int>>()
        for (cat in categories) {
            val keywords = cat.keywordsCsv.split(",")
                .map { DeduplicationEngine.normalizeConcept(it.trim()) }
                .filter { it.isNotEmpty() }

            val matchingKeyword = keywords.filter { keyword -> matchesKeyword(norm, keyword) }
                .maxByOrNull { it.length }

            if (matchingKeyword != null) {
                dbMatches.add(Pair(cat, matchingKeyword.length))
            }
        }
        if (dbMatches.isNotEmpty()) {
            return dbMatches.maxByOrNull { it.second }?.first
        }

        // 2. Si no hubo coincidencia en BD, buscar en las categorías por defecto
        val defMatches = mutableListOf<Pair<CategoryEntity, Int>>()
        for (defCat in DEFAULT_CATEGORIES) {
            val keywords = defCat.keywordsCsv.split(",")
                .map { DeduplicationEngine.normalizeConcept(it.trim()) }
                .filter { it.isNotEmpty() }

            val matchingKeyword = keywords.filter { keyword -> matchesKeyword(norm, keyword) }
                .maxByOrNull { it.length }

            if (matchingKeyword != null) {
                val matchingInDb = categories.find { it.name.equals(defCat.name, ignoreCase = true) }
                defMatches.add(Pair(matchingInDb ?: defCat, matchingKeyword.length))
            }
        }
        if (defMatches.isNotEmpty()) {
            return defMatches.maxByOrNull { it.second }?.first
        }

        // 3. Fallback por defecto: si es positivo -> "Otros ingresos", si es negativo -> "Otros gastos"
        if (amount != null) {
            val isPositive = amount > java.math.BigDecimal.ZERO
            val fallbackName = if (isPositive) "Otros ingresos" else "Otros gastos"
            val fallbackInDb = categories.find { it.name.equals(fallbackName, ignoreCase = true) }
            if (fallbackInDb != null) return fallbackInDb
            return DEFAULT_CATEGORIES.find { it.name.equals(fallbackName, ignoreCase = true) }
        }

        return null
    }

    fun inferCategoryId(concept: String, categories: List<CategoryEntity>, amount: java.math.BigDecimal? = null): Long? {
        return inferCategory(concept, categories, amount)?.id?.takeIf { it > 0 }
    }
}

