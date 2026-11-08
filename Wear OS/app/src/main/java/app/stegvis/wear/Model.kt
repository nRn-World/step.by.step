package app.stegvis.wear

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI state shared by all pages. Every call is safe: failures end up as states, never as crashes. */
class PeppInfo(val from: String, val total: Int)

class AppModel(val svc: Services, private val scope: CoroutineScope) {
    val vault get() = svc.vault

    var steps by mutableIntStateOf(vault.todaySteps(System.currentTimeMillis()))
    var goal by mutableIntStateOf(vault.goal)
    var signedIn by mutableStateOf(vault.session != null)
    var phoneSession by mutableStateOf(vault.session?.fromPhone == true)
    var accountName by mutableStateOf(vault.accountName)
    /** Set only by the debug demo mode: the page to show; all sensor, network and sign-in work is skipped. */
    var demoPage by mutableStateOf<String?>(null)
    val demo get() = demoPage != null
    /** Date used by the views. Fixed only in the debug demo mode. */
    var demoDate: java.time.LocalDate? = null
    val today: java.time.LocalDate get() = demoDate ?: java.time.LocalDate.now(STOCKHOLM)
    val activeGroup: Group? get() = state?.groups.orEmpty().let { gs -> gs.firstOrNull { it.id == selectedGroup } ?: gs.firstOrNull() }
    val goals: PeriodGoals get() = computeGoals(state, activeGroup, today, steps, vault.goal)
    /** Debug demo only: "done of total" for the goal-reached screen. */
    var demoDone: Pair<Int, Int>? = null
    /** Debug demo only: the group's week total from the image (the images do not add up from member data). */
    var demoGroupWeek: Int? = null
    var pepp by mutableStateOf<PeppInfo?>(null)
    private var lastLikes = -1
    fun race(): RaceState? {
        val g = activeGroup ?: return null
        val p = g.partners.firstOrNull() ?: return null
        val mine = demoGroupWeek ?: groupWeekSteps(g, today)
        return RaceState(p, mine, p.weekSteps, p.raceTarget ?: (DEFAULT_WEEK_GOAL * 2))
    }
    var hasSensor by mutableStateOf(true)

    // Group data: null fetch = first load not done yet.
    var fetch by mutableStateOf<Fetch?>(null)
    var state by mutableStateOf<ServerState?>(null)
    var offline by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var selectedGroup by mutableStateOf(vault.selectedGroup)
    private var lastLoad = 0L

    init {
        // Cached group data first, so the lists are not empty while the first request runs.
        val cached = vault.groupCache
        if (cached != null && signedIn) {
            state = runCatching { parseState(cached, dayKey(System.currentTimeMillis())) }.getOrNull()
        }
    }

    fun changeGoal(value: Int) {
        goal = clampGoal(value)
        vault.goal = goal
        svc.refreshSurfaces()
    }

    fun readSteps() {
        if (demo) return
        scope.launch {
            val now = withContext(Dispatchers.IO) { runCatching { svc.updateSteps() }.getOrDefault(vault.todaySteps(System.currentTimeMillis())) }
            steps = now
        }
    }

    /** Loads /api/state. Keeps the last good data and marks it Offline when the network fails. */
    fun load(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (demo) return
        if (loading || (!force && now - lastLoad < 60_000 && fetch is Fetch.Ok)) return
        loading = true
        scope.launch {
            val today = dayKey(now)
            val result = withContext(Dispatchers.IO) { runCatching { svc.coordinator.fetchState(today) }.getOrDefault(Fetch.Failed) }
            fetch = result
            when (result) {
                is Fetch.Ok -> {
                    state = result.state
                    offline = false
                    lastLoad = now
                    vault.groupCache = result.raw
                    if (result.state.meName.isNotBlank()) {
                        vault.accountName = result.state.meName
                        accountName = result.state.meName
                    }
                    signedIn = true
                    vault.goal = clampGoal(goals.day.target)
                    val likes = activeGroup?.members?.firstOrNull { it.isMe }?.likes ?: 0
                    if (lastLikes in 0 until likes) pepp = PeppInfo(activeGroup?.name.orEmpty(), likes) // the server does not say who gave the heart
                    lastLikes = likes
                    goal = vault.goal
                }
                Fetch.Offline -> {
                    offline = true
                    if (state == null) state = vault.groupCache?.let { runCatching { parseState(it, today) }.getOrNull() }
                }
                Fetch.Failed -> if (state == null) offline = false
                Fetch.NotLoggedIn -> { state = null; signedIn = false; phoneSession = false }
                Fetch.PhoneExpired -> phoneSession = true
            }
            loading = false
        }
    }

    fun onSignedIn(name: String?) {
        signedIn = true
        phoneSession = vault.session?.fromPhone == true
        if (!name.isNullOrBlank()) { vault.accountName = name; accountName = name }
        fetch = null
        load(force = true)
        scope.launch { withContext(Dispatchers.IO) { runCatching { svc.syncNow() } } }
    }

    fun signOut() {
        svc.coordinator.signOut()
        signedIn = false
        phoneSession = false
        accountName = null
        state = null
        fetch = null
        offline = false
    }

    fun selectGroup(id: String) {
        selectedGroup = id
        vault.selectedGroup = id
    }
}
