@file:OptIn(ExperimentalMaterial3Api::class)

package com.vorder.gastos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

private val ARG = Locale("es", "AR")

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = GBlue,
            onPrimary = Color.White,
            surface = NeoBg,
            background = NeoBg,
            onSurface = InkDark
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

private fun kindFor(t: Tipo): IconKind = when (t) {
    Tipo.PAGO -> IconKind.CARD
    Tipo.TRANSFERENCIA -> IconKind.SWAP
    Tipo.GASTO -> IconKind.BAG
    Tipo.INGRESO -> IconKind.INCOME
}

private fun colorFor(t: Tipo): Color = when (t) {
    Tipo.PAGO -> GBlue
    Tipo.TRANSFERENCIA -> GPurple
    Tipo.GASTO -> GYellow
    Tipo.INGRESO -> GGreen
}

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

    CompositionLocalProvider(LocalContentColor provides InkDark) {
        Box(Modifier.fillMaxSize()) {
            NeoBackground()
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Header(tab)
                if (tab != 2) {
                    MonthBar(
                        ym = ym,
                        canNext = offset < 0,
                        onPrev = { offset -= 1 },
                        onNext = { if (offset < 0) offset += 1 }
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Crossfade(targetState = tab, animationSpec = tween(250), label = "tab") { t ->
                        when (t) {
                            0 -> RegistroScreen(month) { editing = it }
                            1 -> ConsumosScreen(month) { editing = it }
                            else -> AjustesScreen(all)
                        }
                    }
                }
            }
            if (tab == 0) {
                FabAdd(
                    active = showNew,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 26.dp, bottom = 112.dp)
                ) { showNew = true }
            }
            NeoNav(
                tab = tab,
                onTab = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
            )
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
fun Header(tab: Int) {
    val titles = listOf("Registro", "Consumos", "Ajustes")
    Row(
        Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Gastos VORDER", fontSize = 13.sp, color = InkSoft, fontWeight = FontWeight.Medium)
            Text(titles[tab], fontSize = 30.sp, fontWeight = FontWeight.Bold, color = InkDark)
        }
        Box(Modifier.size(50.dp).neuRaised(25.dp, 5.dp), contentAlignment = Alignment.Center) {
            AnimIcon(IconKind.SPARK, GYellow, Modifier.size(26.dp), loop = true)
        }
    }
}

@Composable
fun MonthBar(ym: YearMonth, canNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        NeoRoundButton(IconKind.LEFT, onPrev)
        Text(monthLabel(ym), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        NeoRoundButton(IconKind.RIGHT, onNext, enabled = canNext)
    }
}

// ---------- Registro ----------

@Composable
fun StatBlock(icon: IconKind, label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).neuInset(20.dp, 3.dp), contentAlignment = Alignment.Center) {
            AnimIcon(icon, color, Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, fontSize = 12.sp, color = InkSoft)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }
}

@Composable
fun SummaryCard(gastos: Double, ingresos: Double) {
    val balance = ingresos - gastos
    val total = gastos + ingresos
    NeoCard(Modifier.fillMaxWidth(), glass = true, corner = 28.dp) {
        Column(Modifier.padding(20.dp)) {
            Text("Balance del mes", fontSize = 13.sp, color = InkSoft)
            Text(
                animatedMoney(balance), fontSize = 32.sp, fontWeight = FontWeight.Bold,
                color = if (balance >= 0) GGreen else GRed, maxLines = 1
            )
            Spacer(Modifier.height(12.dp))
            AnimBar(if (total > 0) (gastos / total).toFloat() else 0f, GRed)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBlock(IconKind.CARD, "Gastos", animatedMoney(gastos), GRed, Modifier.weight(1f))
                StatBlock(IconKind.INCOME, "Ingresos", animatedMoney(ingresos), GGreen, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MovCard(m: Mov, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(20.dp, 0.5f)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).neuRaised(23.dp, 3.dp), contentAlignment = Alignment.Center) {
            AnimIcon(kindFor(m.tipo), colorFor(m.tipo), Modifier.size(23.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                titulo(m), maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold, fontSize = 15.sp
            )
            Text(
                m.billetera + " · " + m.categoria + " · " + fmtFecha(m.fecha),
                fontSize = 12.sp, color = InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            (if (m.tipo.egreso) "-" else "+") + money(m.monto),
            color = if (m.tipo.egreso) GRed else GGreen,
            fontWeight = FontWeight.Bold, fontSize = 15.sp
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

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 170.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SummaryCard(gastos, ingresos) }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NeoChip("Todos", filtro == "TODOS", { filtro = "TODOS" })
                for (t in Tipo.values()) {
                    NeoChip(t.label, filtro == t.name, { filtro = t.name }, colorFor(t))
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                NeoCard(Modifier.fillMaxWidth(), glass = true) {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AnimIcon(IconKind.SPARK, GBlue, Modifier.size(40.dp), loop = true)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No hay movimientos en este período.\n\nCuando llegue una notificación de tus billeteras o bancos se carga sola (activá el acceso en Ajustes), o tocá + para cargar uno a mano.",
                            color = InkSoft, fontSize = 14.sp
                        )
                    }
                }
            }
        } else {
            items(shown, key = { it.id }) { m -> MovCard(m) { onEdit(m) } }
        }
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

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            NeoCard(Modifier.fillMaxWidth(), glass = true, corner = 28.dp) {
                Column(Modifier.padding(20.dp)) {
                    Text("Total consumido en el mes", fontSize = 13.sp, color = InkSoft)
                    Text(
                        animatedMoney(total), fontSize = 32.sp,
                        fontWeight = FontWeight.Bold, color = GRed, maxLines = 1
                    )
                    Text(
                        stats.size.toString() + " billeteras / medios de pago",
                        fontSize = 12.sp, color = InkSoft
                    )
                }
            }
        }
        if (stats.isEmpty()) {
            item {
                Text(
                    "Todavía no hay consumos en este período.",
                    modifier = Modifier.padding(16.dp), color = InkSoft
                )
            }
        }
        items(stats, key = { it.name }) { w ->
            val col = PALETTE[stats.indexOf(w).coerceAtLeast(0) % PALETTE.size]
            val open = expanded[w.name] == true
            val share = if (total > 0) (w.egresos / total).toFloat().coerceIn(0f, 1f) else 0f
            NeoCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().tap { expanded[w.name] = !open }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(50.dp).neuInset(25.dp, 4.dp), contentAlignment = Alignment.Center) {
                        AnimIcon(IconKind.WALLET, col, Modifier.size(26.dp), active = open)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            w.name, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(w.movs.size.toString() + " movimientos", fontSize = 12.sp, color = InkSoft)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("-" + money(w.egresos), color = GRed, fontWeight = FontWeight.Bold)
                        if (w.ingresos > 0) {
                            Text("+" + money(w.ingresos), color = GGreen, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    AnimIcon(IconKind.EXPAND, InkSoft, Modifier.size(22.dp), active = open)
                }
                AnimBar(share, col, Modifier.padding(horizontal = 16.dp))
                Text(
                    "%.0f%% del total".format(share * 100),
                    fontSize = 11.sp, color = InkSoft,
                    modifier = Modifier.padding(start = 18.dp, top = 6.dp, bottom = 14.dp)
                )
                AnimatedVisibility(
                    visible = open,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    WalletDetail(w, onEdit)
                }
            }
        }
    }
}

@Composable
private fun WalletDetail(w: WalletStat, onEdit: (Mov) -> Unit) {
    val egr = w.movs.filter { it.tipo.egreso }
    Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
        val porTipo = Tipo.values().filter { it.egreso }.map { t ->
            t to egr.filter { it.tipo == t }.sumOf { it.monto }
        }.filter { it.second > 0 }
        if (porTipo.isNotEmpty()) {
            Text(
                porTipo.joinToString("  ·  ") { it.first.label + "s: " + money(it.second) },
                fontSize = 12.sp, color = InkSoft,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
        }
        val porCat = egr.groupBy { it.categoria }
            .map { (c, ms) -> c to ms.sumOf { it.monto } }
            .sortedByDescending { it.second }
        if (porCat.isNotEmpty()) {
            Text(
                "Por categoría", fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp)
            )
            porCat.forEachIndexed { i, (c, v) ->
                Column(Modifier.padding(horizontal = 4.dp, vertical = 5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(c, fontSize = 14.sp)
                        Text(money(v), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    AnimBar(
                        if (w.egresos > 0) (v / w.egresos).toFloat() else 0f,
                        PALETTE[i % PALETTE.size]
                    )
                }
            }
        }
        Text(
            "Detalle", fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in w.movs) MovCard(m) { onEdit(m) }
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

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            NeoCard(Modifier.fillMaxWidth(), glass = true, corner = 28.dp) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulseDot(if (enabled) GGreen else GYellow, active = enabled)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (enabled) "Lectura de notificaciones activada"
                            else "Lectura de notificaciones desactivada",
                            fontWeight = FontWeight.Bold, fontSize = 15.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Para cargar los gastos solos, la app necesita acceso a las notificaciones. Los datos quedan solo en tu teléfono.",
                        fontSize = 13.sp, color = InkSoft
                    )
                    Spacer(Modifier.height(14.dp))
                    NeoButton("Abrir acceso a notificaciones", IconKind.GEAR, {
                        ctx.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    })
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Si Android no te deja activarlo (app instalada desde un APK): Info de la app → menú ⋮ → «Permitir ajustes restringidos» y volvé a intentar.",
                        fontSize = 12.sp, color = InkSoft
                    )
                    Spacer(Modifier.height(10.dp))
                    NeoButton("Abrir info de la app", IconKind.LIST, {
                        val i = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + ctx.packageName)
                        )
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(i)
                    }, color = GPurple)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                NeoButton("Exportar CSV", IconKind.EXPORT, {
                    try {
                        exportCsv(ctx, all)
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "No se pudo exportar", Toast.LENGTH_SHORT).show()
                    }
                }, color = GGreen)
                NeoButton("Probar lector", IconKind.SPARK, {
                    val sample = "Pagaste \$1.500 a Kiosco Don Pepe"
                    val r = Parser.parse(sample)
                    if (r != null) {
                        Db.insert(
                            Mov(
                                0, System.currentTimeMillis(), r.monto, r.tipo, "Prueba",
                                r.comercio, r.categoria, "", "auto", sample
                            )
                        )
                        Toast.makeText(ctx, "Movimiento de prueba cargado", Toast.LENGTH_SHORT).show()
                    }
                }, color = GYellow)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 6.dp)) {
                Text("Apps que se leen", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Activá las billeteras y bancos que usás. Las que parecen financieras aparecen primero.",
                    fontSize = 12.sp, color = InkSoft
                )
                Spacer(Modifier.height(10.dp))
                NeoButton("Detectar de nuevo", IconKind.SPARK, {
                    val s = HashSet(selected)
                    s.addAll(Prefs.detect(ctx))
                    selected = s
                    Prefs.setMonitored(ctx, s)
                })
            }
        }
        items(apps, key = { it.second }) { app ->
            val label = app.first
            val pkg = app.second
            Row(
                Modifier.fillMaxWidth().glass(18.dp, 0.45f).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(
                        pkg, fontSize = 10.sp, color = InkSoft,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(10.dp))
                NeoSwitch(pkg in selected) { on ->
                    val s = HashSet(selected)
                    if (on) s.add(pkg) else s.remove(pkg)
                    selected = s
                    Prefs.setMonitored(ctx, s)
                }
            }
        }
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
        containerColor = Color(0xFFEEF2F8),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
        title = {
            Text(
                if (initial == null) "Nuevo movimiento" else "Editar movimiento",
                fontWeight = FontWeight.Bold, color = InkDark
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NeoField(monto, { monto = it }, "Monto", keyboard = KeyboardType.Decimal)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp, horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (t in Tipo.values()) {
                        NeoChip(t.label, tipo == t, { tipo = t }, colorFor(t))
                    }
                }
                NeoField(billetera, { billetera = it }, "Billetera / banco")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp, horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (w in quick) NeoChip(w, billetera == w, { billetera = w })
                }
                NeoField(comercio, { comercio = it }, "Comercio / destinatario")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp, horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (c in CATEGORIAS) NeoChip(c, categoria == c, { categoria = c }, GGreen)
                }
                NeoField(nota, { nota = it }, "Nota")
                if (initial != null && initial.raw.isNotBlank()) {
                    Text(
                        "Notificación original: " + initial.raw,
                        fontSize = 12.sp, color = InkSoft
                    )
                }
            }
        },
        confirmButton = {
            NeoButton("Guardar", IconKind.SPARK, {
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
            }, modifier = Modifier.padding(4.dp))
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Eliminar", color = GRed) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar", color = InkSoft) }
            }
        }
    )
}
