package com.bimob.app.ui.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimob.app.AppVersion
import com.bimob.app.ui.blueprint.BlueprintPalette
import com.bimob.app.ui.blueprint.BlueprintTheme
import com.bimob.app.ui.blueprint.LocalBlueprintPalette
import com.bimob.app.ui.blueprint.ThemeButton

data class LauncherModuleSpec(
    val code: String,
    val statusBadge: String,
    val title: String,
    val subtitle: String,
    val actionLabel: String,
    val isActive: Boolean,
    val iconType: Int
)

fun launcherModuleSpecs(): List<LauncherModuleSpec> = listOf(
    LauncherModuleSpec("МОДУЛЬ 01", "АКТИВЕН", "NAGAPLAS CAD", "2D/3D Черчение · Узлы · Оси", "ОТКРЫТЬ ФАСАД →", true, 0),
    LauncherModuleSpec("МОДУЛЬ 02", "ЭТАП STAGE I", "NAGAPLAS BIM", "Информационная модель · IFC", "[ В РАЗРАБОТКЕ ]", false, 1),
    LauncherModuleSpec("МОДУЛЬ 03", "СКОРО", "УЗЛЫ И КМ", "Сопряжения · Фасонки · Болты", "[ ЗАПЛАНИРОВАНО ]", false, 2),
    LauncherModuleSpec("МОДУЛЬ 04", "СКОРО", "2D ЛИСТЫ ГОСТ", "Планы · Разрезы · Компоновка", "[ ЗАПЛАНИРОВАНО ]", false, 3),
    LauncherModuleSpec("МОДУЛЬ 05", "СКОРО", "РАСЧЁТ МКЭ", "Эпюры моментов · Прогибы", "[ ЗАПЛАНИРОВАНО ]", false, 4),
    LauncherModuleSpec("МОДУЛЬ 06", "СКОРО", "СПЕЦИФИКАЦИИ", "Ведомости стали и бетона", "[ ЗАПЛАНИРОВАНО ]", false, 5),
    LauncherModuleSpec("МОДУЛЬ 07", "СКОРО", "ОБМЕН DXF / IFC", "Импорт и экспорт форматов", "[ ЗАПЛАНИРОВАНО ]", false, 6),
    LauncherModuleSpec("МОДУЛЬ 08", "STAGE H", "AI-МОСТ АГЕНТОВ", "JSON-RPC · Команды в ядро", "[ АРХИТЕКТУРА STAGE H ]", false, 7)
)

/** Existing MainActivity route remains source-compatible. */
@Composable
fun ProgramLauncherScreen(onCad: () -> Unit) {
    ProgramLauncherScreen(
        currentTheme = BlueprintTheme.active.value,
        versionLabel = AppVersion.VERSION_LABEL,
        onSelectTheme = BlueprintTheme::select,
        onOpenCadFacade = onCad
    )
}

@Composable
fun ProgramLauncherScreen(
    currentTheme: BlueprintPalette,
    versionLabel: String,
    onSelectTheme: (BlueprintPalette) -> Unit,
    onOpenCadFacade: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalBlueprintPalette.current
    val density = LocalDensity.current
    fun pxToDp(px: Float) = with(density) { px.toDp() }
    fun dpToPx(dp: androidx.compose.ui.unit.Dp) = with(density) { dp.toPx() }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
            .background(palette.bgPrimary)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(6.dp)
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val framePad = w * .012f
        val towerLeft = w * .042f
        val towerRight = w * .092f
        val towerCenterX = (towerLeft + towerRight) / 2f
        val apexY = h * .042f
        val jibTopY = h * .132f
        val jibBotY = h * .180f
        val groundY = h * .948f

        val gridLeft = w * .155f
        val gridRight = w * .968f
        val colGap = w * .020f
        val colW = (gridRight - gridLeft - colGap * 3f) / 4f
        val colCenters = FloatArray(4) { i -> gridLeft + colW * (i + .5f) + colGap * i }
        val row0Top = h * .285f
        val row0Bot = h * .525f
        val row1Top = h * .635f
        val row1Bot = h * .875f

        Canvas(Modifier.fillMaxSize()) {
            val thin = dpToPx(1.dp)
            val main = dpToPx(1.6.dp)
            val thinStroke = Stroke(thin, cap = StrokeCap.Butt, join = StrokeJoin.Bevel)
            val mainStroke = Stroke(main, cap = StrokeCap.Butt, join = StrokeJoin.Bevel)

            drawRect(palette.gridMajor, Offset(framePad, framePad), Size(w - 2f * framePad, h - 2f * framePad), style = thinStroke)
            drawRect(palette.linePrimary, Offset(framePad + 4f, framePad + 4f), Size(w - 2f * (framePad + 4f), h - 2f * (framePad + 4f)), style = mainStroke)

            drawLine(palette.linePrimary, Offset(framePad + 4f, groundY), Offset(w - framePad - 4f, groundY), 2f, StrokeCap.Butt)
            var gx = framePad + 12f
            while (gx < w - framePad - 12f) {
                drawLine(palette.gridMajor, Offset(gx, groundY), Offset(gx - 8f, groundY + 10f), thin, StrokeCap.Butt)
                gx += 14f
            }

            // Tower mast: regular cross-braced bays.
            drawLine(palette.linePrimary, Offset(towerLeft, jibTopY), Offset(towerLeft, groundY), 2f, StrokeCap.Butt)
            drawLine(palette.linePrimary, Offset(towerRight, jibTopY), Offset(towerRight, groundY), 2f, StrokeCap.Butt)
            val sectionH = (towerRight - towerLeft) * 1.15f
            var sy = jibBotY
            while (sy + sectionH <= groundY) {
                val next = sy + sectionH
                drawLine(palette.linePrimary, Offset(towerLeft, sy), Offset(towerRight, sy), 1.2f, StrokeCap.Butt)
                drawLine(palette.gridMajor, Offset(towerLeft, sy), Offset(towerRight, next), thin, StrokeCap.Butt)
                drawLine(palette.gridMajor, Offset(towerRight, sy), Offset(towerLeft, next), thin, StrokeCap.Butt)
                sy = next
            }
            drawLine(palette.linePrimary, Offset(towerLeft, sy.coerceAtMost(groundY)), Offset(towerRight, sy.coerceAtMost(groundY)), 1.2f, StrokeCap.Butt)

            // Peak, stays and horizontal lattice jib.
            val jibLeftX = w * .024f
            val jibRightX = w * .968f
            drawLine(palette.linePrimary, Offset(towerLeft, jibTopY), Offset(towerCenterX, apexY), main, StrokeCap.Butt)
            drawLine(palette.linePrimary, Offset(towerRight, jibTopY), Offset(towerCenterX, apexY), main, StrokeCap.Butt)
            drawLine(palette.accentAmber, Offset(towerCenterX, apexY), Offset(jibLeftX + 12f, jibTopY), main, StrokeCap.Butt)
            drawLine(palette.accentAmber, Offset(towerCenterX, apexY), Offset(colCenters[3], jibTopY), main, StrokeCap.Butt)

            drawLine(palette.linePrimary, Offset(jibLeftX, jibTopY), Offset(jibRightX, jibTopY), main, StrokeCap.Butt)
            drawLine(palette.linePrimary, Offset(jibLeftX, jibBotY), Offset(jibRightX, jibBotY), main, StrokeCap.Butt)
            drawLine(palette.linePrimary, Offset(jibLeftX, jibTopY), Offset(jibLeftX, jibBotY), main, StrokeCap.Butt)
            drawLine(palette.linePrimary, Offset(jibRightX, jibTopY), Offset(jibRightX, jibBotY), main, StrokeCap.Butt)
            val jibStep = (jibBotY - jibTopY) * 1.2f
            var jx = towerRight
            var diagonalUp = true
            while (jx + jibStep <= jibRightX) {
                val nx = jx + jibStep
                drawLine(palette.gridMajor, Offset(jx, jibTopY), Offset(jx, jibBotY), thin, StrokeCap.Butt)
                if (diagonalUp) drawLine(palette.gridMajor, Offset(jx, jibBotY), Offset(nx, jibTopY), thin, StrokeCap.Butt)
                else drawLine(palette.gridMajor, Offset(jx, jibTopY), Offset(nx, jibBotY), thin, StrokeCap.Butt)
                diagonalUp = !diagonalUp
                jx = nx
            }

            // Four trolley rings, sling triangles and spreader beams.
            val ringY = h * .214f
            val ringR = h * .020f
            val spreaderY = h * .256f
            for (i in 0..3) {
                val cx = colCenters[i]
                val cardL = gridLeft + i * (colW + colGap)
                val cardR = cardL + colW
                val accent = if (i == 0) palette.accentAmber else palette.linePrimary
                drawRect(palette.bgPrimary, Offset(cx - 14f, jibBotY - 3f), Size(28f, 6f))
                drawRect(accent, Offset(cx - 14f, jibBotY - 3f), Size(28f, 6f), style = thinStroke)
                drawLine(accent, Offset(cx, jibBotY + 3f), Offset(cx, ringY - ringR), main, StrokeCap.Butt)
                drawCircle(palette.bgPrimary, ringR, Offset(cx, ringY))
                drawCircle(accent, ringR, Offset(cx, ringY), style = mainStroke)
                drawLine(accent, Offset(cx, ringY + ringR), Offset(cardL + colW * .22f, spreaderY), thin, StrokeCap.Butt)
                drawLine(accent, Offset(cx, ringY + ringR), Offset(cardR - colW * .22f, spreaderY), thin, StrokeCap.Butt)
                drawLine(accent, Offset(cardL + colW * .18f, spreaderY), Offset(cardR - colW * .18f, spreaderY), main, StrokeCap.Butt)
                drawLine(accent, Offset(cardL + colW * .22f, spreaderY), Offset(cardL + colW * .22f, row0Top), thin, StrokeCap.Butt)
                drawLine(accent, Offset(cardR - colW * .22f, spreaderY), Offset(cardR - colW * .22f, row0Top), thin, StrokeCap.Butt)
                val midY = (row0Bot + row1Top) / 2f
                drawLine(palette.gridMajor, Offset(cardL + colW * .22f, row0Bot), Offset(cx, midY), thin, StrokeCap.Butt)
                drawLine(palette.gridMajor, Offset(cardR - colW * .22f, row0Bot), Offset(cx, midY), thin, StrokeCap.Butt)
                drawLine(palette.gridMajor, Offset(cx, midY), Offset(cardL + colW * .22f, row1Top), thin, StrokeCap.Butt)
                drawLine(palette.gridMajor, Offset(cx, midY), Offset(cardR - colW * .22f, row1Top), thin, StrokeCap.Butt)
            }

            // Height ticks.
            listOf(row0Top, row1Top).forEach { yMark ->
                drawLine(palette.gridMajor, Offset(gridLeft - 38f, yMark), Offset(gridLeft - 6f, yMark), thin, StrokeCap.Butt)
                drawLine(palette.gridMajor, Offset(gridLeft - 24f, yMark), Offset(gridLeft - 18f, yMark - 7f), thin, StrokeCap.Butt)
                drawLine(palette.gridMajor, Offset(gridLeft - 18f, yMark - 7f), Offset(gridLeft - 12f, yMark), thin, StrokeCap.Butt)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 104.dp, end = 12.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("NAGAPLAS · ЛАУНЧЕР ИНЖЕНЕРНЫХ МОДУЛЕЙ", color = palette.textPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("ВЫБЕРИТЕ ПРОГРАММУ · C++20 CORE: IDLE (0 MB) · $versionLabel", color = palette.textMuted, fontFamily = FontFamily.Monospace, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ThemeButton(onChange = { onSelectTheme(BlueprintTheme.palettes[(BlueprintTheme.palettes.indexOf(currentTheme) + 1) % BlueprintTheme.palettes.size]) })
        }

        val specs = launcherModuleSpecs()
        specs.forEachIndexed { index, spec ->
            val row = index / 4
            val col = index % 4
            val left = gridLeft + col * (colW + colGap)
            val top = if (row == 0) row0Top else row1Top
            val cardHeight = row0Bot - row0Top
            LauncherModuleCard(
                spec = spec,
                palette = palette,
                onClick = { if (spec.isActive) onOpenCadFacade() },
                modifier = Modifier.offset(x = pxToDp(left), y = pxToDp(top)).width(pxToDp(colW)).height(pxToDp(cardHeight))
            )
        }

        Column(Modifier.offset(x = pxToDp(gridLeft - 38f), y = pxToDp(row0Top - 15f))) {
            Text("+8.400", color = palette.textMuted, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
        }
        Text("+4.200", color = palette.textMuted, fontFamily = FontFamily.Monospace, fontSize = 8.sp,
            modifier = Modifier.offset(x = pxToDp(gridLeft - 38f), y = pxToDp(row1Top - 15f)))
        Text("УР. ЗЕМЛИ 0.000 · $versionLabel", color = palette.textMuted, fontFamily = FontFamily.Monospace, fontSize = 8.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 8.dp))
    }
}

@Composable
private fun LauncherModuleCard(
    spec: LauncherModuleSpec,
    palette: BlueprintPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (spec.isActive) palette.accentCyan else palette.linePrimary
    Column(
        modifier.background(palette.bgSurface, RectangleShape)
            .border(if (spec.isActive) 1.5.dp else 1.dp, accent, RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(spec.code, color = palette.accentAmber, fontFamily = FontFamily.Monospace, fontSize = 7.sp, maxLines = 1)
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            val c = if (spec.isActive) palette.accentCyan else palette.gridMajor
            val t = 1.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f
            when (spec.iconType % 8) {
                0 -> { drawRect(c, Offset(cx - size.width * .22f, cy - size.height * .32f), Size(size.width * .44f, size.height * .64f), style = Stroke(t)); drawLine(c, Offset(cx - size.width * .22f, cy), Offset(cx + size.width * .22f, cy), t); drawLine(c, Offset(cx, cy - size.height * .32f), Offset(cx, cy + size.height * .32f), t) }
                1 -> { drawRect(c, Offset(cx - size.width * .3f, cy - size.height * .3f), Size(size.width * .6f, size.height * .6f), style = Stroke(t)); drawLine(c, Offset(cx - size.width * .3f, cy - size.height * .3f), Offset(cx + size.width * .3f, cy + size.height * .3f), t); drawLine(c, Offset(cx - size.width * .3f, cy + size.height * .3f), Offset(cx + size.width * .3f, cy - size.height * .3f), t) }
                2 -> { drawCircle(c, size.height * .25f, Offset(cx, cy), style = Stroke(t)); drawLine(c, Offset(cx - size.width * .3f, cy), Offset(cx + size.width * .3f, cy), t) }
                3 -> { val path = Path().apply { moveTo(cx - size.width * .3f, cy + size.height * .25f); lineTo(cx, cy - size.height * .3f); lineTo(cx + size.width * .3f, cy + size.height * .25f); close() }; drawPath(path, c, style = Stroke(t)) }
                4 -> { drawLine(c, Offset(cx - size.width * .28f, cy - size.height * .28f), Offset(cx + size.width * .28f, cy + size.height * .28f), t); drawLine(c, Offset(cx - size.width * .28f, cy + size.height * .28f), Offset(cx + size.width * .28f, cy - size.height * .28f), t); drawCircle(c, size.height * .1f, Offset(cx, cy)) }
                5 -> { drawRect(c, Offset(cx - size.width * .3f, cy - size.height * .32f), Size(size.width * .6f, size.height * .64f), style = Stroke(t)); for (i in 1..2) drawLine(c, Offset(cx - size.width * .3f, cy - size.height * .32f + size.height * i / 3f), Offset(cx + size.width * .3f, cy - size.height * .32f + size.height * i / 3f), t) }
                6 -> { drawRect(c, Offset(cx - size.width * .3f, cy - size.height * .25f), Size(size.width * .6f, size.height * .5f), style = Stroke(t)); drawLine(c, Offset(cx - size.width * .15f, cy), Offset(cx + size.width * .15f, cy), t) }
                else -> { drawCircle(c, size.height * .28f, Offset(cx, cy), style = Stroke(t)); drawLine(c, Offset(cx - size.width * .32f, cy), Offset(cx + size.width * .32f, cy), t); drawLine(c, Offset(cx, cy - size.height * .32f), Offset(cx, cy + size.height * .32f), t) }
            }
        }
        Text(spec.statusBadge, color = if (spec.isActive) palette.accentCyan else palette.textMuted, fontFamily = FontFamily.Monospace, fontSize = 6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(spec.title, color = if (spec.isActive) palette.accentCyan else palette.textPrimary, fontFamily = FontFamily.Monospace, fontWeight = if (spec.isActive) FontWeight.Bold else FontWeight.Normal, fontSize = 8.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(spec.subtitle, color = palette.textMuted, fontFamily = FontFamily.Monospace, fontSize = 6.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 7.sp)
        Text(spec.actionLabel, color = if (spec.isActive) palette.accentCyan else palette.accentAmber, fontFamily = FontFamily.Monospace, fontSize = 6.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
