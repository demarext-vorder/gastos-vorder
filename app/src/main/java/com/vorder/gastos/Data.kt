package com.vorder.gastos

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow

enum class Tipo(val label: String, val emoji: String, val egreso: Boolean) {
    PAGO("Pago", "💳", true),
    TRANSFERENCIA("Transferencia", "🔁", true),
    GASTO("Gasto", "🧾", true),
    INGRESO("Ingreso", "💰", false)
}

data class Mov(
    val id: Long,
    val fecha: Long,
    val monto: Double,
    val tipo: Tipo,
    val billetera: String,
    val comercio: String,
    val categoria: String,
    val nota: String,
    val origen: String,
    val raw: String
)

val CATEGORIAS = listOf("Comida", "Transporte", "Servicios", "Salud", "Compras", "Ocio", "Otros")

object Db {
    private var helper: Helper? = null
    val movs = MutableStateFlow<List<Mov>>(emptyList())

    private class Helper(c: Context) : SQLiteOpenHelper(c, "gastos.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE mov (id INTEGER PRIMARY KEY AUTOINCREMENT, fecha INTEGER NOT NULL, " +
                    "monto REAL NOT NULL, tipo TEXT NOT NULL, billetera TEXT NOT NULL, " +
                    "comercio TEXT NOT NULL, categoria TEXT NOT NULL, nota TEXT NOT NULL, " +
                    "origen TEXT NOT NULL, raw TEXT NOT NULL, hash TEXT NOT NULL DEFAULT '')"
            )
            db.execSQL("CREATE INDEX idx_fecha ON mov(fecha)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }

    @Synchronized
    fun init(ctx: Context) {
        if (helper == null) {
            helper = Helper(ctx.applicationContext)
            reload()
        }
    }

    @Synchronized
    fun reload() {
        val h = helper ?: return
        val out = ArrayList<Mov>()
        h.readableDatabase.rawQuery(
            "SELECT id,fecha,monto,tipo,billetera,comercio,categoria,nota,origen,raw FROM mov ORDER BY fecha DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val tipo = try {
                    Tipo.valueOf(c.getString(3))
                } catch (e: Exception) {
                    Tipo.GASTO
                }
                out.add(
                    Mov(
                        c.getLong(0), c.getLong(1), c.getDouble(2), tipo,
                        c.getString(4), c.getString(5), c.getString(6),
                        c.getString(7), c.getString(8), c.getString(9)
                    )
                )
            }
        }
        movs.value = out
    }

    private fun cv(m: Mov): ContentValues {
        val v = ContentValues()
        v.put("fecha", m.fecha)
        v.put("monto", m.monto)
        v.put("tipo", m.tipo.name)
        v.put("billetera", m.billetera)
        v.put("comercio", m.comercio)
        v.put("categoria", m.categoria)
        v.put("nota", m.nota)
        v.put("origen", m.origen)
        v.put("raw", m.raw)
        return v
    }

    @Synchronized
    fun insert(m: Mov, hash: String = ""): Long {
        val h = helper ?: return -1
        val v = cv(m)
        v.put("hash", hash)
        val id = h.writableDatabase.insert("mov", null, v)
        reload()
        return id
    }

    @Synchronized
    fun update(m: Mov) {
        val h = helper ?: return
        h.writableDatabase.update("mov", cv(m), "id=?", arrayOf(m.id.toString()))
        reload()
    }

    @Synchronized
    fun delete(id: Long) {
        val h = helper ?: return
        h.writableDatabase.delete("mov", "id=?", arrayOf(id.toString()))
        reload()
    }

    @Synchronized
    fun recentHash(hash: String, windowMs: Long): Boolean {
        val h = helper ?: return false
        val since = (System.currentTimeMillis() - windowMs).toString()
        h.readableDatabase.rawQuery(
            "SELECT 1 FROM mov WHERE hash=? AND fecha>=? LIMIT 1",
            arrayOf(hash, since)
        ).use { return it.moveToFirst() }
    }
}
