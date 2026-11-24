package com.vorder.gastos

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---------- Paleta ----------
val NeoBg = Color(0xFFE7EDF5)
val NeoLight = Color(0xFFFFFFFF)
val NeoDark = Color(0xFFADBACF)
val InkDark = Color(0xFF243447)
val InkSoft = Color(0xFF66768C)
val GBlue = Color(0xFF4285F4)
val GRed = Color(0xFFEA4335)
val GYellow = Color(0xFFF9AB00)
val GGreen = Color(0xFF34A853)
val GPurple = Color(0xFF7C4DFF)
val PALETTE = listOf(GBlue, GGreen, GYellow, GRed, GPurple)

// ---------- Sombras neomórficas ----------

/** Superficie "elevada": luz arriba-izquierda, sombra abajo-derecha. */
fun Modifier.neuRaised(
    corner: Dp = 22.dp,
    depth: Dp = 6.dp,
    fill: Color = NeoBg
): Modifier = this.drawBehind {
    val r = corner.toPx()
    val d = depth.toPx()
    val outline = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, r, r))
    }
    clipPath(outline, ClipOp.Difference) {
        drawIntoCanvas { c ->
            val p = android.graphics.Paint()
            p.isAntiAlias = true
            p.color = NeoBg.toArgb()
            p.setShadowLayer(d * 1.8f, d, d, NeoDark.toArgb())
            c.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, p)
            p.setShadowLayer(d * 1.8f, -d, -d, NeoLight.toArgb())
            c.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, p)
        }
    }
    drawRoundRect(fill, cornerRadius = CornerRadius(r, r))
}

/** Superficie "hundida": campos, pistas, botones presionados. */
fun Modifier.neuInset(
    corner: Dp = 18.dp,
    depth: Dp = 4.dp
): Modifier = this.drawBehind {
    val r = corner.toPx()
    val d = depth.toPx()
    drawRoundRect(NeoBg, cornerRadius = CornerRadius(r, r))
    val shape = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, r, r))
    }
    clipPath(shape) {
        drawIntoCanvas { c ->
            val frame = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(-size.width, -size.height, size.width * 2f, size.height * 2f))
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, r, r))
            }
            val p = android.graphics.Paint()
            p.isAntiAlias = true
            p.color = NeoBg.toArgb()
            p.setShadowLayer(d * 1.8f, d, d, NeoDark.toArgb())
            c.nativeCanvas.drawPath(frame.asAndroidPath(), p)
            p.setShadowLayer(d * 1.8f, -d, -d, NeoLight.toArgb())
            c.nativeCanvas.drawPath(frame.asAndroidPath(), p)
        }
    }
}

/** Click sin ripple (el feedback lo da la sombra). */
fun Modifier.tap(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

/** Fondo de vidrio translúcido. */
fun Modifier.glass(corner: Dp = 20.dp, alpha: Float = 0.5f): Modifier = this
    .background(Color.White.copy(alpha = alpha), RoundedCornerShape(corner))
    .border(
        1.dp,
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.15f))),
        RoundedCornerShape(corner)
    )

// ---------- Fondo animado ----------

@Composable
fun NeoBackground() {
    val t = rememberInfiniteTransition(label = "bg")
    val a = t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "a"
    )
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFF3F6FB), NeoBg, Color(0xFFDCE4EF))))
        val w = size.width
        val h = size.height
        val k = a.value
        fun blob(c: Color, x: Float, y: Float, r: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c.copy(alpha = 0.32f), Color.Transparent),
                    center = Offset(x, y), radius = r
                ),
                radius = r, center = Offset(x, y)
            )
        }
        blob(GBlue, w * (0.15f + 0.35f * k), h * 0.12f, w * 0.7f)
        blob(GYellow, w * (0.95f - 0.3f * k), h * 0.45f, w * 0.55f)
        blob(GGreen, w * (0.2f + 0.2f * k), h * 0.85f, w * 0.65f)
        blob(GRed, w * (0.85f - 0.2f * k), h * 0.98f, w * 0.45f)
    }
}

// ---------- Componentes ----------

@Composable
fun NeoCard(
    modifier: Modifier = Modifier,
    corner: Dp = 24.dp,
    glass: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = if (glass) {
        modifier.neuRaised(corner, 7.dp, Color.White.copy(alpha = 0.55f))
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.2f))),
                RoundedCornerShape(corner)
            )
    } else {
        modifier.neuRaised(corner, 7.dp)
    }
    Column(base, content = content)
}

@Composable
fun NeoButton(
    text: String,
    icon: IconKind?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = GBlue
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val depth by animateDpAsState(if (pressed) 2.dp else 6.dp, tween(120), label = "depth")
    val sc by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "sc")
    Row(
        modifier
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .neuRaised(18.dp, depth)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) AnimIcon(icon, color, Modifier.size(20.dp), loop = true)
        Text(text, color = InkDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun NeoRoundButton(
    icon: IconKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = GBlue
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val depth by animateDpAsState(if (pressed && enabled) 2.dp else 5.dp, tween(120), label = "d")
    Box(
        modifier
            .size(44.dp)
            .neuRaised(22.dp, depth)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimIcon(icon, if (enabled) tint else InkSoft.copy(alpha = 0.4f), Modifier.size(22.dp), active = pressed)
    }
}

@Composable
fun NeoChip(label: String, selected: Boolean, onClick: () -> Unit, accent: Color = GBlue) {
    val a by animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "chip")
    val shape = if (selected) Modifier.neuInset(16.dp, 3.dp) else Modifier.neuRaised(16.dp, 4.dp)
    Box(shape.tap(onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(
            label,
            color = lerp(InkSoft, accent, a),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}

@Composable
fun NeoSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "sw"
    )
    val f = x.coerceIn(0f, 1f)
    Box(
        Modifier
            .size(width = 56.dp, height = 30.dp)
            .neuInset(15.dp, 3.dp)
            .tap { onChange(!checked) }
    ) {
        Box(
            Modifier
                .offset(x = (3f + 26f * x).dp, y = 3.dp)
                .size(24.dp)
                .neuRaised(12.dp, 2.dp, fill = lerp(Color(0xFFF2F5FA), GBlue, f))
        )
    }
}

@Composable
fun NeoField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text
) {
    Column(modifier) {
        Text(
            label, fontSize = 12.sp, color = InkSoft,
            modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = InkDark, fontSize = 16.sp),
            cursorBrush = SolidColor(GBlue),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier
                .fillMaxWidth()
                .neuInset(16.dp, 3.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )
    }
}

@Composable
fun AnimBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        anim.animateTo(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing))
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(12.dp)
            .neuInset(6.dp, 2.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth(anim.value)
                .fillMaxHeight()
                .padding(2.dp)
                .background(
                    Brush.horizontalGradient(listOf(color.copy(alpha = 0.65f), color)),
                    RoundedCornerShape(4.dp)
                )
        )
    }
}

/** Número que "cuenta" hasta su valor. */
@Composable
fun animatedMoney(target: Double): String {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) {
        a.animateTo(target.toFloat(), tween(800, easing = FastOutSlowInEasing))
    }
    return money(a.value.toDouble())
}

@Composable
fun PulseDot(color: Color, active: Boolean, dot: Dp = 14.dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val p = t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "p"
    )
    Canvas(Modifier.size(dot * 2)) {
        val base = dot.toPx() / 2f
        if (active) {
            val k = p.value
            drawCircle(color.copy(alpha = (1f - k) * 0.45f), base + k * base * 1.9f, center)
        }
        drawCircle(color, base, center)
    }
}

// ---------- Barra de navegación ----------

@Composable
fun NeoNav(tab: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .neuRaised(30.dp, 8.dp, Color.White.copy(alpha = 0.6f))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        NavItem(IconKind.LIST, "Registro", tab == 0) { onTab(0) }
        NavItem(IconKind.WALLET, "Consumos", tab == 1) { onTab(1) }
        NavItem(IconKind.GEAR, "Ajustes", tab == 2) { onTab(2) }
    }
}

@Composable
private fun RowScope.NavItem(kind: IconKind, label: String, selected: Boolean, onClick: () -> Unit) {
    val m = if (selected) Modifier.neuInset(22.dp, 4.dp) else Modifier
    Column(
        Modifier
            .weight(1f)
            .padding(horizontal = 4.dp)
            .then(m)
            .tap(onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimIcon(kind, if (selected) GBlue else InkSoft, Modifier.size(26.dp), active = selected)
        Text(
            label, fontSize = 11.sp,
            color = if (selected) GBlue else InkSoft,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun FabAdd(active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val depth by animateDpAsState(if (pressed) 3.dp else 8.dp, tween(120), label = "fab")
    Box(
        modifier
            .size(64.dp)
            .neuRaised(32.dp, depth, fill = GBlue)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimIcon(IconKind.PLUS, Color.White, Modifier.size(30.dp), active = active)
    }
}

// ---------- Íconos animados ----------

enum class IconKind {
    LIST, WALLET, GEAR, PLUS, CARD, SWAP, BAG, INCOME, LEFT, RIGHT, EXPORT, SPARK, EXPAND
}

@Composable
fun AnimIcon(
    kind: IconKind,
    tint: Color,
    modifier: Modifier = Modifier.size(24.dp),
    active: Boolean = false,
    loop: Boolean = false
) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(kind) {
        enter.snapTo(0f)
        enter.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    val act by animateFloatAsState(
        if (active) 1f else 0f, tween(450, easing = FastOutSlowInEasing), label = "act"
    )
    val needLoop = loop || (active && kind == IconKind.GEAR)
    val ph: State<Float>? = if (needLoop) {
        rememberInfiniteTransition(label = "loop").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
            label = "ph"
        )
    } else null
    Canvas(modifier) {
        drawIcon(kind, tint, enter.value, act, ph?.value ?: 0f)
    }
}

private fun DrawScope.drawIcon(kind: IconKind, c: Color, enter: Float, act: Float, ph: Float) {
    val s = size.minDimension / 24f
    val sw = 2f * s
    val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val a = c.copy(alpha = (0.35f + 0.65f * enter).coerceIn(0f, 1f))
    val tau = (2.0 * PI).toFloat()

    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    fun ln(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(a, p(x1, y1), p(x2, y2), sw, StrokeCap.Round)

    when (kind) {
        IconKind.LIST -> {
            val lens = floatArrayOf(13f, 9f, 11f)
            for (i in 0..2) {
                val y = 7f + 5f * i
                val len = lens[i] * (0.3f + 0.7f * enter) * (1f + 0.12f * act)
                ln(6f, y, 6f + len, y)
            }
        }
        IconKind.WALLET -> {
            drawRoundRect(a, p(3f, 7f), Size(18f * s, 12f * s), CornerRadius(3f * s), style = st)
            rotate(-22f * act, p(5f, 7f)) { ln(5f, 7f, 15f, 7f) }
            drawRoundRect(a, p(14f, 11f), Size(7f * s, 4f * s), CornerRadius(2f * s), style = st)
            drawCircle(a, 1.1f * s, p(17.5f, 13f))
        }
        IconKind.GEAR -> {
            val ctr = p(12f, 12f)
            rotate(act * 90f + ph * 360f, ctr) {
                drawCircle(a, 3f * s, ctr, style = st)
                drawCircle(a, 6.5f * s, ctr, style = st)
                for (k in 0 until 8) {
                    val ang = k * 45.0 * PI / 180.0
                    val x1 = 12f + 6.5f * cos(ang).toFloat()
                    val y1 = 12f + 6.5f * sin(ang).toFloat()
                    val x2 = 12f + 9.5f * cos(ang).toFloat()
                    val y2 = 12f + 9.5f * sin(ang).toFloat()
                    ln(x1, y1, x2, y2)
                }
            }
        }
        IconKind.PLUS -> {
            rotate(act * 135f, p(12f, 12f)) {
                ln(12f, 5f, 12f, 19f)
                ln(5f, 12f, 19f, 12f)
            }
        }
        IconKind.CARD -> {
            drawRoundRect(a, p(3f, 6f), Size(18f * s, 12f * s), CornerRadius(2.5f * s), style = st)
            drawLine(a, p(3f, 10f), p(3f + 18f * enter, 10f), sw * 1.4f, StrokeCap.Butt)
            ln(6.5f, 15f, 10.5f, 15f)
        }
        IconKind.SWAP -> {
            val off = (1f - enter) * 5f + sin(ph * tau) * 1.2f
            translate(left = -off * s) {
                ln(5f, 9f, 19f, 9f); ln(15f, 5f, 19f, 9f); ln(15f, 13f, 19f, 9f)
            }
            translate(left = off * s) {
                ln(19f, 15f, 5f, 15f); ln(9f, 11f, 5f, 15f); ln(9f, 19f, 5f, 15f)
            }
        }
        IconKind.BAG -> {
            translate(top = (1f - enter) * -4f * s) {
                drawRoundRect(a, p(5f, 9f), Size(14f * s, 11f * s), CornerRadius(2.5f * s), style = st)
                val h = Path().apply {
                    moveTo(9f * s, 9f * s)
                    cubicTo(9f * s, 4f * s, 15f * s, 4f * s, 15f * s, 9f * s)
                }
                drawPath(h, a, style = st)
            }
        }
        IconKind.INCOME -> {
            val dy = (1f - enter) * -6f + sin(ph * tau) * 1.2f
            translate(top = dy * s) {
                ln(12f, 4f, 12f, 14f); ln(7.5f, 9.5f, 12f, 14f); ln(16.5f, 9.5f, 12f, 14f)
            }
            ln(5f, 19f, 19f, 19f)
        }
        IconKind.LEFT -> {
            ln(15f, 5f, 8f, 12f); ln(8f, 12f, 15f, 19f)
        }
        IconKind.RIGHT -> {
            ln(9f, 5f, 16f, 12f); ln(16f, 12f, 9f, 19f)
        }
        IconKind.EXPORT -> {
            val dy = sin(ph * tau) * 1.2f - (1f - enter) * 4f
            translate(top = dy * s) {
                ln(12f, 15f, 12f, 4f); ln(7.5f, 8.5f, 12f, 4f); ln(16.5f, 8.5f, 12f, 4f)
            }
            ln(5f, 15f, 5f, 19f); ln(5f, 19f, 19f, 19f); ln(19f, 19f, 19f, 15f)
        }
        IconKind.SPARK -> {
            val k = (0.85f + 0.15f * sin(ph * tau)) * (0.4f + 0.6f * enter)
            scale(k, p(12f, 12f)) {
                rotate(ph * 90f, p(12f, 12f)) {
                    val star = Path().apply {
                        moveTo(12f * s, 3f * s); lineTo(14f * s, 10f * s)
                        lineTo(21f * s, 12f * s); lineTo(14f * s, 14f * s)
                        lineTo(12f * s, 21f * s); lineTo(10f * s, 14f * s)
                        lineTo(3f * s, 12f * s); lineTo(10f * s, 10f * s)
                        close()
                    }
                    drawPath(star, a)
                }
            }
        }
        IconKind.EXPAND -> {
            rotate(180f * act, p(12f, 12f)) {
                ln(6f, 9f, 12f, 15f); ln(12f, 15f, 18f, 9f)
            }
        }
    }
}
