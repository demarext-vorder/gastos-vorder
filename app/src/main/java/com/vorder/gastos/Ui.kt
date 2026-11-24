@file:OptIn(ExperimentalMaterial3Api::class)

package com.vorder.gastos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

private val ROJO = Color(0xFFC62828)
private val VERDE = Color(0xFF2E7D32)
private val ARG = Locale("es", "AR")

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1B6B50),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFB7F0D5),
            onPrimaryContainer = Color(0xFF002114),
            secondaryContainer = Color(0xFFCDE9DB),
            surface = Color(0xFFFBFDF9),
            background = Color(0xFFFBFDF9)
        ),
        content = content
    )
}

// ---------- Utilidades ----------

fun money(d: Double): String = NumberFormat.getCurrencyInstance(ARG).format(d)

private val fmtCorto = DateTimeFormatter.ofPattern("dd/MM HH:mm")
private val fmtLargo = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun fmtFecha(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(fmtCorto)

fun fmtFechaLarga(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(fmtLargo)

fun monthLabel(ym: YearMonth): String {
    val n = ym.month.getDisplayName(JTextStyle.FULL, ARG)
    return n.replaceFirstChar { it.uppercase() } + " " + ym.year
}

private fun titulo(m: Mov): String = if (m.comercio.isBlank()) m.tipo.label else m.comercio

private fun fmtInput(d: Double): String =
    if (d == Math.floor(d)) d.toLong().toString() else String.format(Locale.US, "%.2f", d)

private fun csv(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

fun exportCsv(ctx: Context, list: List<Mov>) {
    val sb = StringBuilder("fecha,tipo,billetera,comercio,categoria,monto,nota\n")
    for (m in list) {
        sb.append(csv(fmtFechaLarga(m.fecha))).append(',')
            .append(csv(m.tipo.label)).append(',')
            .append(csv(m.billetera)).append(',')
            .append(csv(m.comercio)).append(',')
            .append(csv(m.categoria)).append(',')
            .append(String.format(Locale.US, "%.2f", m.monto)).append(',')
            .append(csv(m.nota)).append('\n')
    }
    val f = File(ctx.cacheDir, "gastos.csv")
    f.writeText(sb.toString())
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
    val i = Intent(Intent.ACTION_SEND)
    i.type = "text/csv"
    i.putExtra(Intent.EXTRA_STREAM, uri)
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(i, "Exportar CSV")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(chooser)
}

// ---------- App ----------

@Composable
fun App() {
    val all by Db.movs.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var offset by rememberSaveable { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Mov?>(null) }
    var showNew by remember { mutableStateOf(false) }

    val ym = remember(offset) { YearMonth.now().plusMonths(offset.toLong()) }
    val month = remember(all, ym) {
        val z = ZoneId.systemDefault()
        val s = ym.atDay(1).atStartOfDay(z).toInstant().toEpochMilli()
        val e = ym.plusMonths(1).atDay(1).atStartOfDay(z).toInstant().toEpochMilli()
        all.filter { it.fecha >= s && it.fecha < e }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Text("📋") }, label = { Text("Registro") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Text("👛") }, label = { Text("Consumos") }
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Text("⚙️") }, label = { Text("Ajustes") }
                )
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(onClick = { showNew = true }) {
                    Text("+", fontSize = 28.sp)
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (tab != 2) {
                MonthBar(
                    ym = ym,
                    canNext = offset < 0,
                    onPrev = { offset -= 1 },
                    onNext = { if (offset < 0) offset += 1 }
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> RegistroScreen(month) { editing = it }
                    1 -> ConsumosScreen(month) { editing = it }
                    else -> AjustesScreen(all)
                }
            }
        }
    }

    val cur = editing
    if (showNew || cur != null) {
        MovDialog(
            initial = cur,
            wallets = all.map { it.billetera }.distinct(),
            onDismiss = { showNew = false; editing = null },
            onSave = { m ->
                if (m.id == 0L) Db.insert(m) else Db.update(m)
                showNew = false
                editing = null
            },
            onDelete = if (cur != null) {
                {
                    Db.delete(cur.id)
                    editing = null
                }
            } else null
        )
    }
}

@Composable
fun MonthBar(ym: YearMonth, canNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = onPrev) { Text("‹ Anterior") }
        Text(monthLabel(ym), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        TextButton(onClick = onNext, enabled = canNext) { Text("Siguiente ›") }
    }
}

// ---------- Registro ----------

@Composable
fun Stat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun MovRow(m: Mov, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(m.tipo.emoji, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                titulo(m), maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Text(
                m.billetera + " · " + m.categoria + " · " + fmtFecha(m.fecha),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            (if (m.tipo.egreso) "-" else "+") + money(m.monto),
            color = if (m.tipo.egreso) ROJO else VERDE,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun RegistroScreen(list: List<Mov>, onEdit: (Mov) -> Unit) {
    var filtro by rememberSaveable { mutableStateOf("TODOS") }
    val shown = remember(list, filtro) {
        if (filtro == "TODOS") list else list.filter { it.tipo.name == filtro }
    }
    val gastos = list.filter { it.tipo.egreso }.sumOf { it.monto }
    val ingresos = list.filter { !it.tipo.egreso }.sumOf { it.monto }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Stat("Gastos", money(gastos), ROJO)
                    Stat("Ingresos", money(ingresos), VERDE)
                    Stat("Balance", money(ingresos - gastos), if (ingresos - gastos >= 0) VERDE else ROJO)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filtro == "TODOS", onClick = { filtro = "TODOS" },
                    label = { Text("Todos") }
                )
                for (t in Tipo.values()) {
                    FilterChip(
                        selected = filtro == t.name, onClick = { filtro = t.name },
                        label = { Text(t.emoji + " " + t.label) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    "No hay movimientos en este período.\n\nCuando llegue una notificación de tus billeteras o bancos se carga sola (activá el acceso en Ajustes), o tocá + para cargar uno a mano.",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(shown, key = { it.id }) { m ->
                MovRow(m) { onEdit(m) }
                HorizontalDivider()
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

// ---------- Consumos ----------

private class WalletStat(
    val name: String,
    val egresos: Double,
    val ingresos: Double,
    val movs: List<Mov>
)

@Composable
fun ConsumosScreen(list: List<Mov>, onEdit: (Mov) -> Unit) {
    val stats = remember(list) {
        list.groupBy { it.billetera }
            .map { (w, ms) ->
                WalletStat(
                    w,
                    ms.filter { it.tipo.egreso }.sumOf { it.monto },
                    ms.filter { !it.tipo.egreso }.sumOf { it.monto },
                    ms
                )
            }
            .sortedByDescending { it.egresos }
    }
    val total = stats.sumOf { it.egresos }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total consumido en el mes", style = MaterialTheme.typography.labelMedium)
                    Text(money(total), fontWeight = FontWeight.Bold, fontSize = 26.sp, color = ROJO)
                    Text(
                        stats.size.toString() + " billeteras / medios de pago",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        if (stats.isEmpty()) {
            item {
                Text(
                    "Todavía no hay consumos en este período.",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(stats, key = { it.name }) { w ->
            val open = expanded[w.name] == true
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable { expanded[w.name] = !open }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("👛 " + w.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                w.movs.size.toString() + " movimientos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("-" + money(w.egresos), color = ROJO, fontWeight = FontWeight.Bold)
                            if (w.ingresos > 0) {
                                Text("+" + money(w.ingresos), color = VERDE, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (open) "▲" else "▼")
                    }
                    val share = if (total > 0) (w.egresos / total).toFloat().coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(
                        progress = { share },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                    Text(
                        "%.0f%% del total".format(share * 100),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 10.dp)
                    )
                    if (open) {
                        WalletDetail(w, onEdit)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun WalletDetail(w: WalletStat, onEdit: (Mov) -> Unit) {
    val egr = w.movs.filter { it.tipo.egreso }
    Column(Modifier.padding(bottom = 8.dp)) {
        HorizontalDivider()
        val porTipo = Tipo.values().filter { it.egreso }.map { t ->
            t to egr.filter { it.tipo == t }.sumOf { it.monto }
        }.filter { it.second > 0 }
        if (porTipo.isNotEmpty()) {
            Text(
                porTipo.joinToString("  ·  ") { it.first.label + "s: " + money(it.second) },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        val porCat = egr.groupBy { it.categoria }
            .map { (c, ms) -> c to ms.sumOf { it.monto } }
            .sortedByDescending { it.second }
        if (porCat.isNotEmpty()) {
            Text(
                "Por categoría", fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
            for ((c, v) in porCat) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(c)
                        Text(money(v))
                    }
                    LinearProgressIndicator(
                        progress = { if (w.egresos > 0) (v / w.egresos).toFloat().coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Text(
            "Detalle", fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp)
        )
        for (m in w.movs) {
            MovRow(m) { onEdit(m) }
            HorizontalDivider()
        }
    }
}

// ---------- Ajustes ----------

@Composable
fun AjustesScreen(all: List<Mov>) {
    val ctx = LocalContext.current
    val tick = resumeTick.intValue
    val enabled = remember(tick) {
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    }
    val apps = remember {
        Prefs.launcherApps(ctx).sortedWith(
            compareByDescending<Pair<String, String>> { Prefs.looksFinancial(it.first, it.second) }
                .thenBy { it.first.lowercase() }
        )
    }
    var selected by remember { mutableStateOf(Prefs.monitored(ctx)) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
                    else Color(0xFFFFE0B2)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (enabled) "✅ Lectura de notificaciones activada"
                        else "⚠️ Lectura de notificaciones desactivada",
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Para cargar los gastos solos, la app necesita acceso a las notificaciones. " +
                            "Los datos quedan solo en tu teléfono.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text("Abrir acceso a notificaciones") }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Si Android no te deja activarlo (app instalada desde un APK): entrá a Info de la app → menú ⋮ → «Permitir ajustes restringidos» y volvé a intentar.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = {
                        val i = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + ctx.packageName)
                        )
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(i)
                    }) { Text("Abrir info de la app") }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    try {
                        exportCsv(ctx, all)
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "No se pudo exportar", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("📤 Exportar CSV") }
                OutlinedButton(onClick = {
                    val sample = "Pagaste \$1.500 a Kiosco Don Pepe"
                    val r = Parser.parse(sample)
                    if (r != null) {
                        Db.insert(
                            Mov(0, System.currentTimeMillis(), r.monto, r.tipo, "Prueba",
                                r.comercio, r.categoria, "", "auto", sample)
                        )
                        Toast.makeText(ctx, "Movimiento de prueba cargado", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("🧪 Probar lector") }
            }
            Spacer(Modifier.height(16.dp))
            Text("Apps que se leen", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                "Activá las billeteras y bancos que usás. Las que parecen financieras aparecen primero.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = {
                val s = HashSet(selected)
                s.addAll(Prefs.detect(ctx))
                selected = s
                Prefs.setMonitored(ctx, s)
            }) { Text("Detectar billeteras y bancos de nuevo") }
        }
        items(apps, key = { it.second }) { app ->
            val label = app.first
            val pkg = app.second
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        pkg, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = pkg in selected,
                    onCheckedChange = { on ->
                        val s = HashSet(selected)
                        if (on) s.add(pkg) else s.remove(pkg)
                        selected = s
                        Prefs.setMonitored(ctx, s)
                    }
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ---------- Diálogo alta / edición ----------

@Composable
fun MovDialog(
    initial: Mov?,
    wallets: List<String>,
    onDismiss: () -> Unit,
    onSave: (Mov) -> Unit,
    onDelete: (() -> Unit)?
) {
    var monto by remember { mutableStateOf(if (initial != null) fmtInput(initial.monto) else "") }
    var tipo by remember { mutableStateOf(initial?.tipo ?: Tipo.GASTO) }
    var billetera by remember { mutableStateOf(initial?.billetera ?: "Efectivo") }
    var comercio by remember { mutableStateOf(initial?.comercio ?: "") }
    var categoria by remember { mutableStateOf(initial?.categoria ?: "Otros") }
    var nota by remember { mutableStateOf(initial?.nota ?: "") }
    val quick = remember(wallets) { (listOf("Efectivo") + wallets).distinct() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo movimiento" else "Editar movimiento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = monto, onValueChange = { monto = it },
                    label = { Text("Monto") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (t in Tipo.values()) {
                        FilterChip(
                            selected = tipo == t, onClick = { tipo = t },
                            label = { Text(t.emoji + " " + t.label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = billetera, onValueChange = { billetera = it },
                    label = { Text("Billetera / banco") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (w in quick) {
                        FilterChip(
                            selected = billetera == w, onClick = { billetera = w },
                            label = { Text(w) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = comercio, onValueChange = { comercio = it },
                    label = { Text("Comercio / destinatario") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in CATEGORIAS) {
                        FilterChip(
                            selected = categoria == c, onClick = { categoria = c },
                            label = { Text(c) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = nota, onValueChange = { nota = it },
                    label = { Text("Nota") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (initial != null && initial.raw.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Notificación original: " + initial.raw,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = monto.replace(',', '.').toDoubleOrNull()
                if (v != null && v > 0) {
                    onSave(
                        Mov(
                            initial?.id ?: 0L,
                            initial?.fecha ?: System.currentTimeMillis(),
                            v, tipo, billetera.trim().ifBlank { "Efectivo" },
                            comercio.trim(), categoria, nota.trim(),
                            initial?.origen ?: "manual", initial?.raw ?: ""
                        )
                    )
                }
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Eliminar", color = ROJO) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}
