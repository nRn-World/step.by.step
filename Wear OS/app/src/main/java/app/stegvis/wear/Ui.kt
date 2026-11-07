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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

private val EaseOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

// ---- Root ----------------------------------------------------------------------------------

@Composable
fun StegvisApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember { AppModel(Services(ctx), scope) }
    val nav = rememberSwipeDismissableNavController()
    SwipeDismissableNavHost(navController = nav, startDestination = "main") {
        composable("main") { MainPager(model) { nav.navigate("login") } }
        composable("login") { LoginScreen(model, onBack = { nav.popBackStack() }) }
    }
}

@Composable
fun MainPager(model: AppModel, onLogin: () -> Unit) {
    val pager = rememberPagerState(pageCount = { 4 })
    LaunchedEffect(pager.currentPage) { if (pager.currentPage in 1..2) model.load() }
    Box(Modifier.fillMaxSize().background(Bg)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> TodayPage(model, onLogin)
                1 -> GroupPage(model, onLogin)
                2 -> WeekPage(model)
                else -> SettingsPage(model, onLogin)
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(4) { i ->
                val on = pager.currentPage == i
                Box(Modifier.height(6.dp).width(if (on) 16.dp else 6.dp).clip(CircleShape).background(if (on) Coral else Surface2))
            }
        }
    }
}

@Composable
fun Page(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 20.dp), contentAlignment = Alignment.Center) { content() }
}

// ---- Idag ----------------------------------------------------------------------------------

private fun hasActivityPermission(c: android.content.Context) =
    androidx.core.content.ContextCompat.checkSelfPermission(c, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

@Composable
fun TodayPage(model: AppModel, onLogin: () -> Unit) {
    val context = LocalContext.current
    val reduced = remember { context.reducedMotion() }
    val lang = remember { effectiveLanguage(model.vault.language, android.content.res.Resources.getSystem().configuration.locales[0].language) }
    var permitted by remember { mutableStateOf(hasActivityPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permitted = ok
        denied = !ok
    }

    // Read the step counter now and then while the app is open; re-check the permission so returning from Settings works.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { model.svc.syncNow() } } // sync on start; failures are retried by the worker
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
            runCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        })
        return
    }

    val steps = model.steps
    val goal = model.goal
    val reached = steps >= goal
    val today = dayKey(System.currentTimeMillis())

    // Celebration: once per day, remembered on the watch.
    var celebrating by remember { mutableStateOf(false) }
    val pulse = remember { Animatable(1f) }
    val feet = remember { Animatable(0f) }
    LaunchedEffect(reached, today) {
        if (reached && model.vault.celebratedDay != today) {
            model.vault.celebratedDay = today
            celebrating = true
            vibrateGoal(context)
            if (!reduced) {
                launch { pulse.animateTo(1.06f, tween(300)); pulse.animateTo(1f, tween(300)) }
                feet.animateTo(1f, tween(250)); delay(150); feet.animateTo(2f, tween(250))
                delay(900)
            } else delay(1500)
            feet.snapTo(0f)
            celebrating = false
        }
    }

    val progress = remember { Animatable(0f) }
    val p = goalProgress(steps, goal)
    LaunchedEffect(p) {
        if (reduced) progress.snapTo(p)
        else progress.animateTo(p, tween(if (progress.value == 0f) 900 else 400, easing = EaseOutCubic))
    }
    val ringColor = if (reached) Green else Coral

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ring = minOf(150.dp, maxWidth * 0.68f)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(top = 12.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (model.offline) { StatusPill(stringResource(R.string.offline)); Spacer(Modifier.height(4.dp)) }
            Box(Modifier.size(ring).scale(pulse.value), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 10.dp.toPx()
                    val tl = Offset(stroke / 2, stroke / 2)
                    val sz = Size(size.width - stroke, size.height - stroke)
                    drawArc(Surface2, -90f, 360f, false, tl, sz, style = Stroke(stroke))
                    if (progress.value > 0.001f) drawArc(ringColor, -90f, 360f * progress.value, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (celebrating && feet.value > 0f) FeetRow(feet.value) else Txt(formatSteps(steps), Type.hero, maxLines = 1)
                    Txt(stringResource(R.string.today_of_goal, formatSteps(goal)), Type.caption, maxLines = 1)
                }
            }
            Spacer(Modifier.height(4.dp))
            if (celebrating) Txt(stringResource(R.string.goal_reached), Type.title, color = Green)
            else Txt(stringResource(R.string.today_stats, formatKm(steps, lang), (steps * 100L / goal.coerceAtLeast(1)).toInt().toString()), Type.body, color = Muted, maxLines = 1)
            if (!model.hasSensor) Txt(stringResource(R.string.no_sensor), Type.caption)
            if (!model.signedIn) {
                Spacer(Modifier.height(6.dp))
                Pill(stringResource(R.string.sign_in), onLogin, primary = true)
            }
        }
    }
}

/** Two footprints (left coral, right gold) that fade in one after the other during the goal celebration. */
@Composable
private fun FeetRow(t: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Foot(Coral, t.coerceIn(0f, 1f), -12f)
        Foot(Gold, (t - 1f).coerceIn(0f, 1f), 12f)
    }
}

@Composable
private fun Foot(color: Color, alpha: Float, angle: Float) {
    Canvas(Modifier.size(width = 18.dp, height = 28.dp).rotate(angle)) {
        val c = color.copy(alpha = alpha)
        drawOval(c, Offset(size.width * 0.1f, size.height * 0.28f), Size(size.width * 0.8f, size.height * 0.72f))
        drawCircle(c, size.width * 0.17f, Offset(size.width * 0.3f, size.height * 0.12f))
        drawCircle(c, size.width * 0.14f, Offset(size.width * 0.68f, size.height * 0.1f))
    }
}

@Composable
fun PermissionScreen(denied: Boolean, onContinue: () -> Unit, onOpenSettings: () -> Unit) {
    Page {
        Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Foot(Coral, 1f, -12f); Foot(Gold, 1f, 12f) }
            Txt(stringResource(if (denied) R.string.perm_denied else R.string.perm_body), Type.body)
            if (denied) Pill(stringResource(R.string.perm_open_settings), onOpenSettings)
            else Pill(stringResource(R.string.perm_continue), onContinue, primary = true)
        }
    }
}

// ---- Grupp ---------------------------------------------------------------------------------

@Composable
fun Spinner(reduced: Boolean) {
    val angle = if (reduced) 0f else rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "a",
    ).value
    Canvas(Modifier.size(24.dp)) {
        val s = 3.dp.toPx()
        drawArc(Gold, angle, 270f, false, Offset(s / 2, s / 2), Size(size.width - s, size.height - s), style = Stroke(s, cap = StrokeCap.Round))
    }
}

private val avatarColors = listOf(Coral, Green, Gold)

@Composable
fun GroupPage(model: AppModel, onLogin: () -> Unit) {
    val context = LocalContext.current
    val fetch = model.fetch
    val state = model.state
    var choosing by remember { mutableStateOf(false) }

    if (!model.signedIn && fetch !is Fetch.PhoneExpired) {
        Page { MessageWithButton(stringResource(R.string.group_login), stringResource(R.string.sign_in), onLogin, primary = true) }
        return
    }
    if (fetch is Fetch.PhoneExpired) {
        Page { MessageWithButton(stringResource(R.string.group_phone_expired), stringResource(R.string.group_retry), { model.load(true) }) }
        return
    }
    if (state == null) {
        Page {
            if (fetch == null || model.loading) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Spinner(context.reducedMotion()); Txt(stringResource(R.string.loading), Type.caption)
            } else MessageWithButton(stringResource(R.string.group_error), stringResource(R.string.group_retry), { model.load(true) })
        }
        return
    }
    val groups = state.groups
    if (groups.isEmpty()) {
        Page { Txt(stringResource(R.string.group_empty), Type.body, color = Muted) }
        return
    }
    val group = groups.firstOrNull { it.id == model.selectedGroup } ?: groups.first()
    val ranked = group.members.sortedByDescending { it.meters }

    ScalingLazyColumn(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 28.dp, bottom = 34.dp, start = 8.dp, end = 8.dp),
    ) {
        if (model.offline) item { StatusPill(stringResource(R.string.offline)) }
        if (groups.size > 1) {
            item {
                Chip(stringResource(R.string.group_label, group.name) + " ▾") { choosing = !choosing }
            }
            if (choosing) items(groups) { g ->
                Chip(g.name, selected = g.id == group.id) { model.selectGroup(g.id); choosing = false }
            }
        } else {
            item { Txt(group.name, Type.title, maxLines = 1) }
        }
        items(ranked.size) { i -> MemberRow(i + 1, ranked[i], stepsFromMeters(ranked[i].meters)) }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(if (selected) Coral else Surface2)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(label, Type.body, color = if (selected) CoralOn else Ink, maxLines = 1) }
}

@Composable
fun MemberRow(rank: Int, m: Member, steps: Int) {
    val color = if (rank <= 3) avatarColors[rank - 1] else Purple
    val textOnAvatar = if (rank <= 3) CoralOn else Ink
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(if (m.isMe) Surface2 else Surface).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(rank.toString(), Type.caption, Modifier.width(14.dp), maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(28.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Txt(m.name.take(1).uppercase(), Type.body, color = textOnAvatar, maxLines = 1)
        }
        Spacer(Modifier.width(6.dp))
        Txt(shortName(m.name), Type.caption, Modifier.weight(1f), color = Ink, align = TextAlign.Start, maxLines = 1)
        Txt(formatSteps(steps), Type.number, color = if (m.isMe) Coral else Ink, maxLines = 1)
    }
}

/** Ellipsis after 14 characters (design). */
fun shortName(name: String) = if (name.length > 14) name.take(13) + "…" else name

@Composable
fun MessageWithButton(message: String, button: String, onClick: () -> Unit, primary: Boolean = false) {
    Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt(message, Type.body)
        Pill(button, onClick, primary = primary)
    }
}

// ---- Vecka ---------------------------------------------------------------------------------

@Composable
fun WeekPage(model: AppModel) {
    val context = LocalContext.current
    val reduced = remember { context.reducedMotion() }
    val today = LocalDate.now(STOCKHOLM)
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val letters = listOf(R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu, R.string.day_fri, R.string.day_sat, R.string.day_sun)
    val local = model.vault.history
    val week = model.state?.weekMeters.orEmpty()
    val values = days.map { d ->
        val key = d.toString()
        val localSteps = if (d == today) model.steps else local[key] ?: 0
        maxOf(localSteps, week[key]?.let { stepsFromMeters(it) } ?: 0)
    }
    var selected by remember { mutableStateOf(6) }
    val goal = model.goal
    val top = maxOf(goal, values.max(), 1)

    Page {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(stringResource(R.string.week_title), Type.title, maxLines = 1)
            Txt(stringResource(R.string.steps_unit, formatSteps(values[selected])), Type.display, color = if (values[selected] >= goal) Green else Ink, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                days.forEachIndexed { i, d ->
                    val v = values[i]
                    val color = when {
                        d == today -> if (v >= goal) Green else Coral
                        v >= goal -> Green
                        else -> Purple
                    }
                    val frac = remember { Animatable(0f) }
                    LaunchedEffect(v, top) {
                        val target = v.toFloat() / top
                        if (reduced) frac.snapTo(target) else frac.animateTo(target, tween(600, easing = EaseOutCubic))
                    }
                    Column(
                        Modifier.width(24.dp).fillMaxHeight().clickable(role = Role.Button) { selected = i },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            val h = minOf(maxHeight, 96.dp)
                            val barH = maxOf(2.dp, h * frac.value)
                            Box(Modifier.align(Alignment.BottomCenter).width(14.dp).height(barH).clip(RoundedCornerShape(7.dp)).background(color))
                            // Gold goal mark at the height of the daily goal.
                            Box(Modifier.align(Alignment.BottomCenter).offset(y = -(h * goal.toFloat() / top)).width(20.dp).height(2.dp).background(Gold.copy(alpha = 0.9f)))
                        }
                        Txt(stringResource(letters[d.dayOfWeek.value - 1]), Type.caption, color = if (i == selected) Ink else Muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---- Inställningar -------------------------------------------------------------------------

@Composable
fun SettingsPage(model: AppModel, onLogin: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var syncText by remember { mutableStateOf<Pair<String, Color>?>(null) }
    var syncing by remember { mutableStateOf(false) }
    val systemLang = android.content.res.Resources.getSystem().configuration.locales[0].language
    val activeLang = effectiveLanguage(model.vault.language, systemLang)
    val syncDone = stringResource(R.string.sync_done)
    val syncMessages = mapOf(
        SyncResult.OK to syncDone,
        SyncResult.NOT_SIGNED_IN to stringResource(R.string.sync_login),
        SyncResult.PHONE_EXPIRED to stringResource(R.string.group_phone_expired),
        SyncResult.NO_PERMISSION to stringResource(R.string.sync_permission),
        SyncResult.OFFLINE to stringResource(R.string.sync_offline),
        SyncResult.FAILED to stringResource(R.string.sync_failed),
    )
    val accountText = when {
        !model.signedIn -> stringResource(R.string.account_none)
        model.accountName != null -> model.accountName!!
        model.phoneSession -> stringResource(R.string.account_phone)
        else -> stringResource(R.string.sign_in)
    }

    ScalingLazyColumn(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 28.dp, bottom = 36.dp, start = 8.dp, end = 8.dp),
    ) {
        item { Txt(stringResource(R.string.goal_label), Type.caption) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                StepButton("−", stringResource(R.string.goal_minus), model.goal > MIN_GOAL) { model.changeGoal(model.goal - 500) }
                Txt(formatSteps(model.goal), Type.display, Modifier.width(78.dp), maxLines = 1)
                StepButton("+", stringResource(R.string.goal_plus), model.goal < MAX_GOAL) { model.changeGoal(model.goal + 500) }
            }
        }
        item { Txt(stringResource(R.string.language_label), Type.caption) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("sv", "da", "en").forEach { code ->
                    val on = activeLang == code
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).background(if (on) Coral else Surface2)
                            .clickable(role = Role.RadioButton) {
                                if (!on) {
                                    model.vault.language = code
                                    context.findActivity()?.recreate()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Txt(code, Type.title, color = if (on) CoralOn else Ink, maxLines = 1) }
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Txt(stringResource(R.string.account_label), Type.caption, maxLines = 1)
                Txt(accountText, Type.body, color = if (model.signedIn) Ink else Muted, maxLines = 1)
            }
        }
        item {
            Pill(stringResource(if (syncing) R.string.loading else R.string.sync_now), {
                syncing = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { model.svc.syncNow() }.getOrDefault(SyncResult.FAILED) }
                    model.readSteps()
                    syncText = (syncMessages[r] ?: syncDone) to (if (r == SyncResult.OK) Green else Coral)
                    syncing = false
                }
            }, enabled = !syncing)
        }
        syncText?.let { (msg, color) -> item { Txt((if (color == Coral) "! " else "") + msg, Type.caption, color = color) } }
        item {
            if (model.signedIn) Pill(stringResource(R.string.sign_out), { model.signOut(); syncText = null })
            else Pill(stringResource(R.string.sign_in), onLogin, primary = true)
        }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(if (enabled) Surface2 else Surface)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Txt(symbol, Type.display, color = if (enabled) Ink else Muted, maxLines = 1) }
}
