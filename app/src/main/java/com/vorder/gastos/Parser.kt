package com.vorder.gastos

import java.text.Normalizer
import java.util.Locale

object Parser {

    data class Result(val monto: Double, val tipo: Tipo, val comercio: String, val categoria: String)

    private val ingresoKw = listOf(
        "recibiste", "te transfirieron", "te enviaron", "te acreditaron", "acreditamos",
        "te pagaron", "cobraste", "te depositaron", "dinero recibido", "transferencia recibida",
        "ingreso de dinero", "te mandaron"
    )
    private val transfKw = listOf(
        "enviaste", "transferiste", "transferencia enviada", "transferencia realizada",
        "transferencia exitosa", "mandaste", "enviamos tu transferencia"
    )
    private val pagoKw = listOf(
        "pagaste", "pago realizado", "pago aprobado", "pago exitoso", "compraste",
        "compra aprobada", "compra con tarjeta", "compra realizada", "consumo", "debito automatico",
        "debitamos", "se debito", "cargaste", "retiraste", "extraccion", "pago de servicio",
        "pago de tarjeta", "pagamos"
    )

    private val montoRx = Regex("""(?:ar\$|ars|\$)\s?(\d[\d.,]*)""")
    private val rxA = Regex(
        """\b(?:a|en)\s+([^.,\n$]{2,40}?)(?=\s+(?:por|con|el|desde|usando|mediante)\b|[.,\n]|$)""",
        RegexOption.IGNORE_CASE
    )
    private val rxDe = Regex(
        """\b(?:de|desde)\s+([^.,\n$]{2,40}?)(?=\s+(?:por|con|a|el)\b|[.,\n]|$)""",
        RegexOption.IGNORE_CASE
    )

    private val cats = listOf(
        "Comida" to listOf(
            "super", "coto", "carrefour", "jumbo", "disco", "vea", "kiosco", "restaurant", "resto",
            "pizza", "burger", "mcdonald", "starbucks", "cafe", "panaderia", "rappi", "pedidosya",
            "helader", "parrilla", "almacen", "verduler", "carnicer", "dia %"
        ),
        "Transporte" to listOf(
            "uber", "cabify", "sube", "ypf", "shell", "axion", "puma", "peaje", "estacionamiento",
            "didi", "taxi", "combustible", "nafta"
        ),
        "Servicios" to listOf(
            "edesur", "edenor", "metrogas", "naturgy", "aysa", "movistar", "claro", "telecentro",
            "fibertel", "flow", "internet", "municipalidad", "arba", "afip", "seguro", "expensas",
            "alquiler", "personal flow"
        ),
        "Salud" to listOf(
            "farmacia", "farmacity", "osde", "swiss", "galeno", "medicus", "clinica", "sanatorio",
            "medic", "hospital", "laboratorio"
        ),
        "Compras" to listOf(
            "mercadolibre", "mercado libre", "amazon", "tienda", "zara", "ropa", "falabella",
            "fravega", "garbarino", "musimundo", "ikea", "easy", "sodimac"
        ),
        "Ocio" to listOf(
            "netflix", "spotify", "disney", "hbo", "youtube", "cine", "steam", "playstation",
            "xbox", "teatro", "entrada", "despegar"
        )
    )

    fun norm(s: String): String {
        val d = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return d.replace(Regex("\\p{M}+"), "")
    }

    /** Convierte "1.234,56" / "1234.56" / "1.500" a Double. */
    fun parseMonto(t0: String): Double? {
        val t = t0.trimEnd('.', ',')
        if (t.isEmpty()) return null
        val lastDot = t.lastIndexOf('.')
        val lastComma = t.lastIndexOf(',')
        val s = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) t.replace(".", "").replace(',', '.') else t.replace(",", "")
            lastComma >= 0 ->
                if (t.length - lastComma - 1 in 1..2) t.replace(',', '.') else t.replace(",", "")
            lastDot >= 0 ->
                if (t.length - lastDot - 1 in 1..2 && t.count { it == '.' } == 1) t else t.replace(".", "")
            else -> t
        }
        return s.toDoubleOrNull()
    }

    private fun findMonto(n: String): Double? {
        for (m in montoRx.findAll(n)) {
            val from = maxOf(0, m.range.first - 14)
            val before = n.substring(from, m.range.first)
            if (before.contains("saldo") || before.contains("disponible") || before.contains("limite")) continue
            val v = parseMonto(m.groupValues[1])
            if (v != null && v > 0) return v
        }
        return null
    }

    private fun extractComercio(orig: String, tipo: Tipo): String {
        val rx = if (tipo == Tipo.INGRESO) rxDe else rxA
        val c = rx.find(orig)?.groupValues?.get(1)?.trim() ?: return ""
        val l = c.lowercase(Locale.ROOT)
        val malos = listOf("tu ", "su ", "la cuenta", "tu cuenta", "una ", "un ")
        if (malos.any { l.startsWith(it) }) return ""
        return c.take(40)
    }

    fun guessCat(n: String): String {
        for ((cat, kws) in cats) {
            if (kws.any { n.contains(it) }) return cat
        }
        return "Otros"
    }

    fun parse(text: String): Result? {
        val n = norm(text)
        val tipo = when {
            ingresoKw.any { n.contains(it) } -> Tipo.INGRESO
            transfKw.any { n.contains(it) } -> Tipo.TRANSFERENCIA
            pagoKw.any { n.contains(it) } -> Tipo.PAGO
            else -> return null
        }
        val monto = findMonto(n) ?: return null
        val comercio = extractComercio(text, tipo)
        val cat = if (tipo.egreso) guessCat(norm("$comercio $text")) else "Otros"
        return Result(monto, tipo, comercio, cat)
    }
}
