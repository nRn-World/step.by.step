package app.stegvis.wear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay

// Views from the "later" list in the design: Pepp, Live, Race and the two-team analysis.

private val CardBg = Color(0xFF1C1A2B)

@Composable
fun PeppCard(model: AppModel, info: PeppInfo, onClose: () -> Unit) {
    val u = rememberScale()
    LaunchedEffect(info) { if (!model.demo) { delay(12_000); onClose() } }
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width((164 * u).dp).clip(RoundedCornerShape((16 * u).dp)).background(CardBg).border(1.dp, Color(0xFF2A2840), RoundedCornerShape((16 * u).dp)).padding((10 * u).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size((24 * u).dp).clip(RoundedCornerShape((7 * u).dp)).background(Color(0xFF4A2B2E)), contentAlignment = Alignment.Center) {
                    Txt(info.from.take(2).uppercase(), Type.title.copy(fontSize = (10 * u).sp), color = CoralSoft, maxLines = 1)
                }
                Spacer(Modifier.width((6 * u).dp))
                Txt(info.from, Type.title.copy(fontSize = (11 * u).sp), Modifier.weight(1f), align = TextAlign.Start, maxLines = 1)
                Txt(stringResource(R.string.now_label), Type.caption.copy(fontSize = (9 * u).sp), maxLines = 1)
            }
            Spacer(Modifier.height((6 * u).dp))
            val text: AnnotatedString = buildAnnotatedString {
                append(stringResource(R.string.pepp_before))
                withStyle(SpanStyle(color = CoralSoft)) { append(stringResource(R.string.pepp_heart)) }
                append(stringResource(R.string.pepp_after))
            }
            Text(text, style = Type.body.copy(fontSize = (11 * u).sp), color = Ink, maxLines = 2)
            Spacer(Modifier.height((6 * u).dp))
            // The server tells how many hearts you got, not who gave them, so "send one back" only works in the demo.
            Box(
                Modifier.fillMaxWidth().height((28 * u).dp).shadow((6 * u).dp, RoundedCornerShape(50), ambientColor = Coral, spotColor = Coral).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Coral, CoralSoft)))
                    .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Txt(stringResource(if (model.demo) R.string.pepp_back else R.string.close), Type.title.copy(fontSize = (11 * u).sp), color = Color.White, maxLines = 1) }
            Spacer(Modifier.height((5 * u).dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("♥", Type.title.copy(fontSize = (11 * u).sp), color = Coral, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                Txt(stringResource(R.string.pepp_today, info.total), Type.title.copy(fontSize = (10 * u).sp), maxLines = 1)
            }
        }
    }
}

@Composable
fun LivePage(model: AppModel) {
    val u = rememberScale()
    val g = model.activeGroup ?: return
    val walking = g.members.filter { it.active }
    val total = g.members.sumOf { if (it.isMe && model.steps > 0) model.steps else stepsFromMeters(it.meters) }
    val rings = listOf(Color(0xFF4B6B3A), Color(0xFFA44A37), Color(0xFF5B4694))
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Label(stringResource(R.string.live_title, g.name), u)
            Txt(walking.size.toString(), Type.hero.copy(fontSize = (52 * u).sp), maxLines = 1)
            Txt(stringResource(R.string.live_out), Type.body.copy(fontSize = (11 * u).sp), color = Muted, maxLines = 1)
            Spacer(Modifier.height((6 * u).dp))
            Row(horizontalArrangement = Arrangement.spacedBy((-1 * u).dp)) {
                walking.take(4).forEachIndexed { i, m ->
                    Box(
                        Modifier.size((22 * u).dp).clip(CircleShape).border((2 * u).dp, rings[i % rings.size], CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Txt(m.name.take(2).uppercase(), Type.title.copy(fontSize = (8 * u).sp), maxLines = 1) }
                }
            }
            Spacer(Modifier.height((6 * u).dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF2A1A18)).padding(horizontal = (10 * u).dp, vertical = (3 * u).dp)) {
                Txt(stringResource(R.string.live_group_steps, formatSteps(total)), Type.title.copy(fontSize = (11 * u).sp), color = CoralSoft, maxLines = 1)
            }
        }
    }
}

@Composable
fun RacePage(model: AppModel) {
    val u = rememberScale()
    val g = model.activeGroup ?: return
    val race = model.race() ?: return
    val lead = race.lead
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Label(stringResource(R.string.race_title), u)
            Spacer(Modifier.height((6 * u).dp))
            // Dashed track with the two teams as dots and a chequered finish flag. The dots are spread so a small lead stays visible.
            Canvas(Modifier.width((120 * u).dp).height((18 * u).dp)) {
                val y = 4.dp.toPx()
                drawLine(Color(0xFF2E2D38), Offset(0f, y), Offset(size.width - 6.dp.toPx(), y), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())))
                val lead01 = (race.mine.coerceAtLeast(race.theirs).toFloat() / race.target).coerceIn(0.06f, 0.86f)
                val gap = (kotlin.math.abs(lead).toFloat() / 1240f * 0.12f).coerceIn(0.06f, 0.2f)
                val a = lead01
                val b = (lead01 - gap).coerceAtLeast(0.02f)
                val myPos = if (lead >= 0) a else b
                val theirPos = if (lead >= 0) b else a
                val r = 5.dp.toPx()
                drawCircle(Purple, r, Offset(theirPos * size.width, y + 5.dp.toPx()))
                drawCircle(Coral, r, Offset(myPos * size.width, y + 5.dp.toPx()))
                val fx = size.width - 6.dp.toPx()
                val cell = 3.dp.toPx()
                for (row in 0 until 6) for (col in 0 until 2) {
                    drawRect(if ((row + col) % 2 == 0) Color(0xFFD8D5DE) else Color(0xFF3A3944), Offset(fx + col * cell, y - 2.dp.toPx() + row * cell), Size(cell, cell))
                }
            }
            Spacer(Modifier.height((4 * u).dp))
            Txt(formatSteps(kotlin.math.abs(lead)), Type.hero.copy(fontSize = (34 * u).sp), maxLines = 1)
            Row {
                Txt(stringResource(if (lead >= 0) R.string.race_ahead else R.string.race_behind) + " ", Type.body.copy(fontSize = (11 * u).sp), color = CoralSoft, maxLines = 1)
                Txt(race.partner.name, Type.body.copy(fontSize = (11 * u).sp), color = Muted, maxLines = 1)
            }
            Spacer(Modifier.height((6 * u).dp))
            RaceLine(Coral, g.name, race.mine, u)
            Spacer(Modifier.height((3 * u).dp))
            RaceLine(Purple, race.partner.name, race.theirs, u)
        }
    }
}

@Composable
private fun RaceLine(dot: Color, name: String, steps: Int, u: Float) {
    Row(Modifier.width((150 * u).dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size((6 * u).dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width((5 * u).dp))
        Txt(name, Type.title.copy(fontSize = (9 * u).sp), Modifier.weight(1f), color = Muted, align = TextAlign.Start, maxLines = 1)
        Txt(formatSteps(steps), Type.number.copy(fontSize = (11 * u).sp), maxLines = 1)
    }
}

@Composable
fun AnalysisPage(model: AppModel) {
    val u = rememberScale()
    val g = model.activeGroup ?: return
    val race = model.race() ?: return
    val top = maxOf(race.mine, race.theirs, 1)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.width((146 * u).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Label(stringResource(R.string.analysis_title), u)
            Spacer(Modifier.height((8 * u).dp))
            TeamBar(g.name, race.mine, race.mine.toFloat() / top, CoralSoft, Coral, u)
            Spacer(Modifier.height((6 * u).dp))
            TeamBar(race.partner.name, race.theirs, race.theirs.toFloat() / top, Color(0xFFB7A2F0), Purple, u)
            Spacer(Modifier.height((8 * u).dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF2A1A18)).padding(horizontal = (10 * u).dp, vertical = (3 * u).dp)) {
                Txt(
                    stringResource(if (race.lead >= 0) R.string.analysis_lead else R.string.analysis_behind, formatSteps(kotlin.math.abs(race.lead))),
                    Type.title.copy(fontSize = (11 * u).sp), color = CoralSoft, maxLines = 1,
                )
            }
            Spacer(Modifier.height((6 * u).dp))
            Txt(stringResource(R.string.analysis_together, formatSteps(race.together), formatSteps(race.target)), Type.body.copy(fontSize = (9 * u).sp), color = Muted, maxLines = 2)
        }
    }
}

@Composable
private fun TeamBar(name: String, steps: Int, frac: Float, light: Color, deep: Color, u: Float) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Txt(name, Type.title.copy(fontSize = (10 * u).sp), Modifier.weight(1f), align = TextAlign.Start, maxLines = 1)
            Txt(formatSteps(steps), Type.number.copy(fontSize = (11 * u).sp), maxLines = 1)
        }
        Spacer(Modifier.height((2 * u).dp))
        Box(Modifier.fillMaxWidth().height((9 * u).dp).clip(RoundedCornerShape(50)).background(Color(0xFF1F1E26))) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0.02f, 1f)).fillMaxSize().clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(light, deep))))
        }
    }
}
