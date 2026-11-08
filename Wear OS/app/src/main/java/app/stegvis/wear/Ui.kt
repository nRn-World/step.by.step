package app.stegvis.wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as rotateScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val EaseOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

// Design values are written for a 192 dp wide watch (the 384 px round emulator) and scale with the screen width.
@Composable
fun rememberScale(): Float = LocalConfiguration.current.screenWidthDp / 192f

/** The language the screen is drawn in: what the activity applied (Settings choice or the watch language). */
@Composable
fun uiLang(): String = LocalConfiguration.current.locales[0].language.let { if (it == "sv" || it == "da") it else "en" }

val LabelStyle get() = Type.caption.copy(fontFamily = Outfit, letterSpacing = 1.6.sp)

/** Small spaced-out capital label, e.g. "IDAG". */
@Composable
fun Label(text: String, u: Float, color: Color = Muted) {
    // Long labels (e.g. "VECKA · GEMENSAMT MÅL") shrink a little so they stay inside the round screen.
    val long = text.length > 18
    Txt(text.uppercase(), LabelStyle.copy(fontSize = ((if (long) 8 else 10) * u).sp, letterSpacing = (if (long) 1.0 else 1.6).sp), color = color, maxLines = 1)
}

// ---- Root ----------------------------------------------------------------------------------

@Composable
fun StegvisApp(demo: DemoConfig? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember { if (demo == null) AppModel(Services(ctx), scope) else AppModel(Services(ctx, MemoryStore()), scope).also { Demo.apply(it, demo) } }
    val nav = rememberSwipeDismissableNavController()
    SwipeDismissableNavHost(navController = nav, startDestination = if (demo?.page == "login") "login" else "main") {
        composable("main") { MainPager(model) { nav.navigate("login") } }
        composable("login") { LoginScreen(model, onBack = { nav.popBackStack() }) }
    }
}

private val demoPageIds = mapOf("goal_reached" to "today", "pepp" to "today", "pair" to "top")

/** The pages in swipe order. Live, Race and the analysis only exist when the data does. */
private fun pageIds(model: AppModel): List<String> = buildList {
    add("today"); add("week"); add("goals"); add("top")
    if (model.activeGroup != null) add("live")
    if (model.activeGroup?.partners?.isNotEmpty() == true) { add("race"); add("analysis") }
    add("group"); add("settings")
}

@Composable
fun MainPager(model: AppModel, onLogin: () -> Unit) {
    val ids = pageIds(model)
    val start = remember {
        val resume = model.vault.resumePage
        model.vault.resumePage = null
        (resume ?: ids.indexOf(demoPageIds[model.demoPage] ?: model.demoPage).takeIf { it >= 0 } ?: 0).coerceIn(0, ids.lastIndex)
    }
    val pager = rememberPagerState(initialPage = start, pageCount = { ids.size })
    LaunchedEffect(pager.currentPage) { if (ids.getOrNull(pager.currentPage) in setOf("week", "goals", "top", "live", "race", "analysis", "group")) model.load() }
    var demoPepp by remember { mutableStateOf(model.demoPage == "pepp") }
    if (AmbientState.ambient || model.demoPage == "ambient") {
        AmbientScreen(model)
        return
    }
    val pepp = model.pepp ?: if (demoPepp) PeppInfo("Morgonstjärnan", 8) else null
    if (pepp != null) {
        PeppCard(model, pepp) { model.pepp = null; demoPepp = false }
        return
    }
    Box(Modifier.fillMaxSize().background(Bg)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when (ids.getOrNull(page)) {
                "today" -> TodayPage(model)
                "week" -> WeekPage(model)
                "goals" -> GoalsPage(model)
                "top" -> TopPage(model, onLogin)
                "live" -> LivePage(model)
                "race" -> RacePage(model)
                "analysis" -> AnalysisPage(model)
                "group" -> GroupPickPage(model, onLogin)
                else -> SettingsPage(model, ids.lastIndex)
            }
        }
    }
}

// ---- Shared drawing ------------------------------------------------------------------------

/** Ring that starts at the top and runs clockwise: thin track, thick gradient arc and a soft glow. */
@Composable
private fun GlowRing(progress: Float, light: Color, deep: Color, modifier: Modifier = Modifier, strokeDp: Dp = 6.5.dp, trackDp: Dp = 3.5.dp) {
    Canvas(modifier) {
        val sw = strokeDp.toPx()
        val margin = 13.dp.toPx() // ring diameter as in the design (about 85 % of the screen), with room for the glow
        val d = size.minDimension - margin * 2
        val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
        val sz = Size(d, d)
        drawArc(Track, 0f, 360f, false, topLeft, sz, style = Stroke(trackDp.toPx()))
        if (progress > 0.002f) {
            val sweep = 360f * progress.coerceIn(0f, 1f)
            rotateScope(-90f) {
                for (i in 5 downTo 1) drawArc(deep.copy(alpha = 0.045f * (6 - i) / 2f), 0f, sweep, false, topLeft, sz, style = Stroke(sw + i * 2.5.dp.toPx(), cap = StrokeCap.Round))
                drawArc(Brush.sweepGradient(listOf(light, deep, light), center), 0f, sweep, false, topLeft, sz, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
    }
}

// ---- Idag ----------------------------------------------------------------------------------

private fun hasActivityPermission(c: android.content.Context) =
    androidx.core.content.ContextCompat.checkSelfPermission(c, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

@Composable
fun TodayPage(model: AppModel) {
    val context = LocalContext.current
    val reduced = remember { context.reducedMotion() }
    val u = rememberScale()
    val lang = uiLang()
    var permitted by remember { mutableStateOf(if (model.demo) model.demoPage != "permission" else hasActivityPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> permitted = ok; denied = !ok }

    // Read the step counter now and then while the app is open; re-check the permission so returning from Settings works.
    LaunchedEffect(Unit) {
        if (model.demo) return@LaunchedEffect
        if (!model.signedIn) {
            // Google/Apple accounts sign in on the phone. When the phone app is signed in, its token is waiting in the Data Layer.
            val found = withContext(Dispatchers.IO) { runCatching { model.svc.pullPhoneToken() }.getOrDefault(false) }
            if (found) model.onSignedIn(null)
        }
        withContext(Dispatchers.IO) { runCatching { model.svc.syncNow() } }
        model.load()
        while (true) {
            permitted = hasActivityPermission(context)
            if (permitted) {
                val before = model.vault.sensor
                model.readSteps()
                model.hasSensor = before != null || (withContext(Dispatchers.IO) { runCatching { context.getSystemService(android.hardware.SensorManager::class.java)?.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER) != null }.getOrDefault(false) })
            }
            delay(if (permitted) 10_000 else 2_000)
        }
    }

    if (!permitted) {
        PermissionScreen(denied = denied, onContinue = { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }, onOpenSettings = {
            runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        })
        return
    }

    val goals = model.goals
    val steps = model.steps
    val goal = goals.day.target
    val reached = steps >= goal
    val todayKey = model.today.toString()

    // Goal reached: the celebration page shows for a few seconds, once per day.
    var celebrating by remember { mutableStateOf(false) }
    LaunchedEffect(reached, todayKey) {
        if (reached && model.vault.celebratedDay != todayKey) {
            model.vault.celebratedDay = todayKey
            celebrating = true
            vibrateGoal(context)
            delay(if (reduced) 3000 else 4500)
            celebrating = false
        }
    }
    if (celebrating || model.demoPage == "goal_reached") {
        GoalReachedScreen(steps, model.demoDone ?: (goals.doneCount to 3), u)
        return
    }

    val progress = remember { Animatable(0f) }
    val p = goalProgress(steps, goal)
    LaunchedEffect(p) {
        if (reduced) progress.snapTo(p)
        else progress.animateTo(p, tween(if (progress.value == 0f) 900 else 400, easing = EaseOutCubic))
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlowRing(progress.value, CoralSoft, CoralDeep, Modifier.fillMaxSize(), strokeDp = (6.5 * u).dp, trackDp = (3.5 * u).dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Label(stringResource(R.string.today_label), u)
            Txt(formatSteps(steps), Type.hero.copy(fontSize = (when { steps >= 100_000 -> 28; steps >= 10_000 -> 36 else -> 40 } * u).sp), maxLines = 1)
            Txt(stringResource(R.string.steps_km, formatKm(steps, lang)), Type.body.copy(fontSize = (12 * u).sp), color = Muted, maxLines = 1)
            Spacer(Modifier.height((5 * u).dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF2A1A18)).padding(horizontal = (10 * u).dp, vertical = (3 * u).dp)) {
                Txt(stringResource(R.string.goal_pill, formatSteps(goal), goals.day.pct), Type.title.copy(fontSize = (11 * u).sp), color = CoralSoft, maxLines = 1)
            }
            if (!model.hasSensor) Txt(stringResource(R.string.no_sensor), Type.caption.copy(fontSize = (10 * u).sp), maxLines = 2)
        }
        if (model.offline) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = (14 * u).dp)) { StatusPill(stringResource(R.string.offline)) }
    }
}

@Composable
private fun GoalReachedScreen(steps: Int, done: Pair<Int, Int>, u: Float) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlowRing(1f, Color(0xFFA6E58A), Color(0xFF6FB85A), Modifier.fillMaxSize(), strokeDp = (6.5 * u).dp, trackDp = (3.5 * u).dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size((32 * u).dp).clip(CircleShape).background(Color(0xFF1B2A1B)), contentAlignment = Alignment.Center) { Trophy(Color(0xFFA6E58A), Modifier.size((18 * u).dp)) }
            Spacer(Modifier.height((5 * u).dp))
            Label(stringResource(R.string.reached_title), u)
            Txt(formatSteps(steps), Type.hero.copy(fontSize = (36 * u).sp), maxLines = 1)
            Txt(stringResource(R.string.steps_today_unit), Type.body.copy(fontSize = (12 * u).sp), color = Muted, maxLines = 1)
            Spacer(Modifier.height((5 * u).dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF1F2B1D)).padding(horizontal = (10 * u).dp, vertical = (3 * u).dp)) {
                Txt(stringResource(R.string.done_of, done.first, done.second), Type.title.copy(fontSize = (11 * u).sp), color = Color(0xFFA6E58A), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Trophy(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val st = Stroke(w * 0.1f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val bowl = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.24f, h * 0.06f); lineTo(w * 0.76f, h * 0.06f)
            cubicTo(w * 0.76f, h * 0.42f, w * 0.64f, h * 0.6f, w * 0.5f, h * 0.6f)
            cubicTo(w * 0.36f, h * 0.6f, w * 0.24f, h * 0.42f, w * 0.24f, h * 0.06f); close()
        }
        drawPath(bowl, color, style = st)
        drawArc(color, 90f, 180f, false, Offset(w * 0.0f, h * 0.12f), Size(w * 0.3f, h * 0.26f), style = st)
        drawArc(color, -90f, 180f, false, Offset(w * 0.7f, h * 0.12f), Size(w * 0.3f, h * 0.26f), style = st)
        drawLine(color, Offset(w * 0.5f, h * 0.62f), Offset(w * 0.5f, h * 0.84f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.3f, h * 0.94f), Offset(w * 0.7f, h * 0.94f), strokeWidth = w * 0.12f, cap = StrokeCap.Round)
    }
}

@Composable
private fun Foot(color: Color, alpha: Float, angle: Float, height: Int = 28) {
    Canvas(Modifier.size(width = (height * 0.6f).dp, height = height.dp).rotate(angle)) {
        val c = color.copy(alpha = alpha)
        val w = size.width
        val h = size.height
        drawRoundRect(c, Offset(w * 0.1f, h * 0.42f), Size(w * 0.8f, h * 0.58f), CornerRadius(w * 0.4f, w * 0.4f))
        listOf(Triple(0.27f, 0.2f, 0.16f), Triple(0.5f, 0.1f, 0.11f), Triple(0.68f, 0.14f, 0.1f), Triple(0.83f, 0.25f, 0.085f))
            .forEach { (x, y, r) -> drawCircle(c, w * r, Offset(w * x, h * y)) }
    }
}

@Composable
fun PermissionScreen(denied: Boolean, onContinue: () -> Unit, onOpenSettings: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    val backDesc = stringResource(R.string.back)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Foot(Coral, 1f, -12f); Foot(Gold, 1f, 12f) }
                Txt(stringResource(if (denied) R.string.perm_denied else R.string.perm_body), Type.body)
                if (denied) Pill(stringResource(R.string.perm_open_settings), onOpenSettings)
                else Pill(stringResource(R.string.perm_continue), onContinue, primary = true)
            }
        }
        Box(
            Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 8.dp).size(48.dp).clip(CircleShape).background(Surface2)
                .semantics { contentDescription = backDesc }.clickable(role = Role.Button) { activity?.finish() },
            contentAlignment = Alignment.Center,
        ) { Txt("‹", Type.display, maxLines = 1) }
    }
}

// ---- Veckan --------------------------------------------------------------------------------

@Composable
fun WeekPage(model: AppModel) {
    val u = rememberScale()
    val ctx = LocalContext.current
    val reduced = remember { ctx.reducedMotion() }
    val today = model.today
    val monday = today.minusDays(((today.dayOfWeek.value + 6) % 7).toLong())
    val days = (0..6).map { monday.plusDays(it.toLong()) }
    val letters = listOf(R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu, R.string.day_fri, R.string.day_sat, R.string.day_sun)
    val local = model.vault.history
    val server = model.state?.weekMeters.orEmpty()
    val goals = model.goals
    val values = days.map { d ->
        when {
            d.isAfter(today) -> -1 // no data yet
            d == today -> maxOf(model.steps, 0)
            else -> maxOf(local[d.toString()] ?: 0, server[d.toString()]?.let { stepsFromMeters(it) } ?: 0)
        }
    }
    val dayGoal = goals.day.target
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Label(stringResource(R.string.week_title), u)
            Txt(formatSteps(goals.week.value), Type.hero.copy(fontSize = (34 * u).sp), maxLines = 1)
            Txt(stringResource(R.string.steps_pct_of, goals.week.pct, formatSteps(goals.week.target)), Type.body.copy(fontSize = (11 * u).sp), color = Muted, maxLines = 1)
            Spacer(Modifier.height((14 * u).dp))
            val chartH = 36 * u
            Box(Modifier.width((146 * u).dp).height(chartH.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val barW = 18.dp.toPx() * u
                    val gap = (size.width - 7 * barW) / 6
                    val goalY = size.height - size.height * (0.92f)
                    drawLine(Color(0xFF3A3944), Offset(0f, goalY), Offset(size.width, goalY), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
                    values.forEachIndexed { i, v ->
                        val x = i * (barW + gap)
                        if (v < 0) {
                            drawLine(Color(0xFF3F5B2E), Offset(x, size.height - 1.dp.toPx()), Offset(x + barW, size.height - 1.dp.toPx()), strokeWidth = 1.5.dp.toPx())
                        } else {
                            val isToday = days[i] == today
                            val h = maxOf(size.height * 0.92f * v / dayGoal, if (isToday) 9.dp.toPx() else 4.dp.toPx()).coerceAtMost(size.height)
                            val color = if (isToday) Coral else Color(0xFF26252C)
                            if (isToday) drawRoundRect(CoralDeep.copy(alpha = 0.18f), Offset(x - 2.dp.toPx(), size.height - h - 2.dp.toPx()), Size(barW + 4.dp.toPx(), h + 4.dp.toPx()), CornerRadius(barW / 2 + 2.dp.toPx()))
                            drawRoundRect(color, Offset(x, size.height - h), Size(barW, h), CornerRadius(barW / 2))
                        }
                    }
                }
            }
            Row(Modifier.width((146 * u).dp), horizontalArrangement = Arrangement.SpaceBetween) {
                days.forEachIndexed { i, d ->
                    Box(Modifier.width((18 * u).dp), contentAlignment = Alignment.Center) {
                        Txt(stringResource(letters[i]), Type.caption.copy(fontSize = (10 * u).sp, fontFamily = Outfit), color = if (d == today) Coral else Muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---- Målen ---------------------------------------------------------------------------------

@Composable
fun GoalsPage(model: AppModel) {
    val u = rememberScale()
    val goals = model.goals
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.width((146 * u).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Label(stringResource(R.string.goals_title), u)
            Spacer(Modifier.height((8 * u).dp))
            GoalRow(stringResource(R.string.goal_day), goals.day, CoralSoft, Coral, u)
            Spacer(Modifier.height((7 * u).dp))
            GoalRow(stringResource(R.string.goal_week), goals.week, Color(0xFFC7B4F5), Purple, u)
            Spacer(Modifier.height((7 * u).dp))
            GoalRow(stringResource(R.string.goal_month), goals.month, Color(0xFFB7D8A0), Sage, u)
            Spacer(Modifier.height((8 * u).dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF1B1A22)).padding(horizontal = (10 * u).dp, vertical = (4 * u).dp)) {
                Txt(stringResource(R.string.steps_today_pill, formatSteps(goals.day.target)), Type.title.copy(fontSize = (11 * u).sp), color = Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun GoalRow(label: String, g: GoalState, light: Color, deep: Color, u: Float) {
    val frac = remember { Animatable(0f) }
    LaunchedEffect(g.pct) { frac.animateTo(goalProgress(g.value, g.target), tween(700, easing = EaseOutCubic)) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Txt(label.uppercase(), LabelStyle.copy(fontSize = (10 * u).sp), align = TextAlign.Start, maxLines = 1)
            Txt("${g.pct} %", Type.number.copy(fontSize = (12 * u).sp), maxLines = 1)
        }
        Spacer(Modifier.height((2 * u).dp))
        Box(Modifier.fillMaxWidth().height((4.5 * u).dp).clip(RoundedCornerShape(50)).background(Color(0xFF1F1E26))) {
            Box(Modifier.fillMaxWidth(frac.value).fillMaxSize().clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(deep, light))))
        }
    }
}

// ---- Topplistan ----------------------------------------------------------------------------

@Composable
fun TopPage(model: AppModel, onLogin: () -> Unit) {
    val u = rememberScale()
    val group = model.activeGroup
    val lang = uiLang()
    if (!model.signedIn || model.fetch is Fetch.PhoneExpired) { PairScreen(model, onLogin, u); return }
    if (group == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            if (model.fetch == null || model.loading) Txt(stringResource(R.string.loading), Type.caption)
            else Txt(stringResource(R.string.group_empty), Type.body, color = Muted)
        }
        return
    }
    val ranked = group.members.map { it to (if (it.isMe && model.steps > 0) model.steps else stepsFromMeters(it.meters)) }.sortedByDescending { it.second }
    val pages = ranked.chunked(3)
    val pager = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.width((150 * u).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(stringResource(R.string.top_title), u)
                Spacer(Modifier.width((5 * u).dp))
                Box(Modifier.size((7 * u).dp).clip(CircleShape).background(if (model.offline) Muted else Sage))
            }
            Spacer(Modifier.height((8 * u).dp))
            HorizontalPager(pager, Modifier.fillMaxWidth().height((3 * 34 * u).dp)) { page ->
                Column(Modifier.fillMaxSize()) {
                    pages.getOrNull(page).orEmpty().forEachIndexed { i, (m, steps) ->
                        val rank = page * 3 + i
                        TopRow(m, steps, crown = rank == 0, u = u, lang = lang)
                    }
                }
            }
            Spacer(Modifier.height((6 * u).dp))
            if (pages.size > 1) Row(horizontalArrangement = Arrangement.spacedBy((4 * u).dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(pages.size) { i ->
                    val on = pager.currentPage == i
                    Box(Modifier.height((5 * u).dp).width(if (on) (14 * u).dp else (5 * u).dp).clip(CircleShape).background(if (on) Coral else Color(0xFF34333D)))
                }
            }
        }
    }
}

@Composable
private fun TopRow(m: Member, steps: Int, crown: Boolean, u: Float, lang: String) {
    val me = m.isMe
    val initials = if (me) stringResource(R.string.you).take(2).uppercase() else m.name.trim().take(2).uppercase()
    Row(
        Modifier.fillMaxWidth().height((34 * u).dp).clip(RoundedCornerShape((8 * u).dp)).background(if (me) Color(0xFF241517) else Color.Transparent).padding(horizontal = (6 * u).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(initials, Type.number.copy(fontSize = (11 * u).sp), Modifier.width((22 * u).dp), maxLines = 1)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(if (me) stringResource(R.string.you) else m.name, Type.title.copy(fontSize = (11 * u).sp), color = if (me) CoralSoft else Ink, align = TextAlign.Start, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (crown) { Spacer(Modifier.width(3.dp)); Txt("♛", Type.caption.copy(fontSize = (10 * u).sp), color = Gold, maxLines = 1) }
            }
            Txt(stringResource(R.string.km_only, formatKm(steps, lang)) + (if (m.likes > 0) " · " + stringResource(R.string.pepp_n, m.likes) else ""), Type.caption.copy(fontSize = (9 * u).sp), align = TextAlign.Start, maxLines = 1)
        }
        Txt(formatSteps(steps), Type.number.copy(fontSize = (13 * u).sp), color = if (me) CoralSoft else Ink, maxLines = 1)
    }
}

// ---- Välj grupp ----------------------------------------------------------------------------

@Composable
fun GroupPickPage(model: AppModel, onLogin: () -> Unit) {
    val u = rememberScale()
    if (!model.signedIn || model.fetch is Fetch.PhoneExpired) { PairScreen(model, onLogin, u); return }
    val groups = model.state?.groups.orEmpty()
    val active = model.activeGroup
    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            if (model.fetch == null || model.loading) Txt(stringResource(R.string.loading), Type.caption)
            else Txt(stringResource(R.string.group_empty), Type.body, color = Muted)
        }
        return
    }
    ScalingLazyColumn(
        Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((5 * u).dp),
        autoCentering = null, contentPadding = PaddingValues(top = (30 * u).dp, bottom = (40 * u).dp, start = (14 * u).dp, end = (14 * u).dp),
    ) {
        item { Label(stringResource(R.string.groups_title), u) }
        items(groups.size) { i ->
            val g = groups[i]
            val on = g.id == active?.id
            val tint = if (i == 0 || on) Color(0xFF7A3A2E) else Color(0xFF2A2440)
            Row(
                Modifier.fillMaxWidth().height((36 * u).dp).clip(RoundedCornerShape((10 * u).dp))
                    .background(if (on) Color(0xFF2A1714) else Color(0xFF16151C))
                    .border1(on)
                    .semantics { selected = on }.clickable(role = Role.RadioButton) { model.selectGroup(g.id) }.padding(horizontal = (6 * u).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size((26 * u).dp).clip(RoundedCornerShape((7 * u).dp)).background(if (on) Color(0xFF7A3A2E) else Color(0xFF2A2440)), contentAlignment = Alignment.Center) {
                    Txt(g.name.take(1).uppercase(), Type.title.copy(fontSize = (12 * u).sp), color = if (on) CoralSoft else Color(0xFFB7A2F0), maxLines = 1)
                }
                Spacer(Modifier.width((6 * u).dp))
                Column(Modifier.weight(1f)) {
                    Txt(g.name, Type.title.copy(fontSize = (11 * u).sp), align = TextAlign.Start, maxLines = 1)
                    Txt(stringResource(R.string.members_n, g.members.size), Type.caption.copy(fontSize = (9 * u).sp), align = TextAlign.Start, maxLines = 1)
                }
                if (on) Txt("✓", Type.title.copy(fontSize = (11 * u).sp), color = Coral, maxLines = 1)
            }
        }
        item { Txt(stringResource(R.string.more_groups_hint), Type.caption.copy(fontSize = (9 * u).sp), maxLines = 2) }
    }
}

private fun Modifier.border1(on: Boolean): Modifier =
    if (on) this.border(1.dp, Color(0xFF8A4636), RoundedCornerShape(10.dp)) else this

// ---- Parkoppla -----------------------------------------------------------------------------

@Composable
private fun PairScreen(model: AppModel, onLogin: () -> Unit, u: Float) {
    Box(Modifier.fillMaxSize().padding(horizontal = (18 * u).dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size((44 * u).dp).clip(RoundedCornerShape((12 * u).dp)).background(Color(0xFF1B1A22)), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size((52 * u).dp))
            }
            Spacer(Modifier.height((6 * u).dp))
            Label(stringResource(R.string.pair_first), u)
            Txt(stringResource(R.string.pair_title), Type.title.copy(fontSize = (18 * u).sp, fontFamily = Outfit), maxLines = 1)
            Txt(stringResource(R.string.pair_body), Type.caption.copy(fontSize = (10 * u).sp), maxLines = 4)
            Spacer(Modifier.height((5 * u).dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF24232D)).clickable(role = Role.Button, onClick = onLogin).padding(horizontal = (10 * u).dp, vertical = (4 * u).dp)) {
                Txt(stringResource(R.string.pair_pill), Type.title.copy(fontSize = (10 * u).sp), color = Muted, maxLines = 1)
            }
        }
    }
}

// ---- Inställningar -------------------------------------------------------------------------

private fun languageName(code: String) = when (code) { "sv" -> "Svenska"; "da" -> "Dansk"; else -> "English" }

@Composable
fun SettingsPage(model: AppModel, settingsIndex: Int) {
    val u = rememberScale()
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var light by remember { mutableStateOf(model.vault.lightBand) }
    var haptics by remember { mutableStateOf(model.vault.haptics) }
    val current = uiLang()
    if (picking) {
        LanguagePicker(current, u, onBack = { picking = false }) { code ->
            picking = false
            if (code != current) {
                model.vault.language = code
                model.vault.resumePage = settingsIndex // stay on Settings after the restart
                context.findActivity()?.recreate()
            }
        }
        return
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.width((146 * u).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Label(stringResource(R.string.settings_title), u)
            Spacer(Modifier.height((6 * u).dp))
            SettingRow(stringResource(R.string.light_band), u, checked = light, onChange = { light = it; model.vault.lightBand = it })
            Divider()
            SettingRow(stringResource(R.string.haptics), u, checked = haptics, onChange = { haptics = it; model.vault.haptics = it })
            Divider()
            Row(
                Modifier.fillMaxWidth().height((36 * u).dp).clickable(role = Role.Button) { picking = true },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Txt(stringResource(R.string.language_label), Type.title.copy(fontSize = (13 * u).sp), align = TextAlign.Start, maxLines = 1)
                Txt(languageName(current), Type.title.copy(fontSize = (12 * u).sp), color = Muted, maxLines = 1)
            }
            Spacer(Modifier.height((6 * u).dp))
            Txt(stringResource(R.string.rest_in_phone), Type.caption.copy(fontSize = (10 * u).sp), maxLines = 2)
            if (model.signedIn && !model.demo) {
                Spacer(Modifier.height((2 * u).dp))
                Box(Modifier.height(32.dp).clickable(role = Role.Button) { model.signOut() }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Txt(stringResource(R.string.sign_out), Type.caption.copy(fontSize = (10 * u).sp), color = Coral, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F1E26)))

@Composable
private fun SettingRow(label: String, u: Float, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height((36 * u).dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Txt(label, Type.title.copy(fontSize = (13 * u).sp), align = TextAlign.Start, maxLines = 1)
        Box(Modifier.width((34 * u).dp).height((19 * u).dp).clip(RoundedCornerShape(50)).background(if (checked) CoralDeep else Color(0xFF2A2A33)).padding((2.5 * u).dp)) {
            Box(Modifier.align(if (checked) Alignment.CenterEnd else Alignment.CenterStart).size((14 * u).dp).clip(CircleShape).background(if (checked) Color.White else Color(0xFF9C97AF)))
        }
    }
}

@Composable
private fun LanguagePicker(current: String, u: Float, onBack: () -> Unit, onPick: (String) -> Unit) {
    val backDesc = stringResource(R.string.back)
    ScalingLazyColumn(
        Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
        autoCentering = null, contentPadding = PaddingValues(top = 26.dp, bottom = 40.dp, start = 18.dp, end = 18.dp),
    ) {
        item {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Surface2).semantics { contentDescription = backDesc }.clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Txt("‹", Type.display, maxLines = 1) }
        }
        item { Label(stringResource(R.string.language_label), u) }
        items(listOf("sv", "da", "en")) { code ->
            val on = code == current
            Box(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(Surface).semantics { selected = on }
                    .clickable(role = Role.RadioButton) { onPick(code) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Txt(languageName(code), Type.body, align = TextAlign.Start, maxLines = 1)
                if (on) Txt("✓", Type.title, color = Coral, modifier = Modifier.align(Alignment.CenterEnd), maxLines = 1)
            }
        }
    }
}

// ---- Alltid på (ambient) -------------------------------------------------------------------

@Composable
fun AmbientScreen(model: AppModel) {
    val u = rememberScale()
    val lang = uiLang()
    val locale = java.util.Locale.forLanguageTag(lang)
    // The watch redraws once a minute in ambient mode (onUpdateAmbient bumps tick).
    val now = remember(AmbientState.tick, model.demo) {
        if (model.demo) java.time.LocalDateTime.of(2026, 11, 7, 9, 41) else java.time.LocalDateTime.now()
    }
    val date = now.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)).replace(".", "").uppercase(locale)
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    val goals = model.goals
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFF2A2931), size.minDimension / 2 * 0.83f, style = Stroke(2.5.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(date, LabelStyle.copy(fontSize = (10 * u).sp), color = Muted, maxLines = 1)
            Txt(time, Type.hero.copy(fontFamily = OutfitLight, fontWeight = androidx.compose.ui.text.font.FontWeight.Light, fontSize = (50 * u).sp), maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                Txt(formatSteps(model.steps), Type.number.copy(fontSize = (12 * u).sp), maxLines = 1)
                Txt(" " + stringResource(R.string.steps_word) + "  ·  ${goals.day.pct} %", Type.caption.copy(fontSize = (11 * u).sp), color = Muted, maxLines = 1)
            }
        }
    }
}
