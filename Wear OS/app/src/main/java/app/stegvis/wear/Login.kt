package app.stegvis.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Mode { LOGIN, SIGNUP, PHONE }

private fun errorRes(f: AuthFailure): Int = when (f) {
    AuthFailure.BAD_CREDENTIALS -> R.string.err_bad
    AuthFailure.INVALID_NAME -> R.string.err_invalid_name
    AuthFailure.INVALID_PASSWORD -> R.string.err_password_len
    AuthFailure.USERNAME_TAKEN -> R.string.err_taken
    AuthFailure.UNAVAILABLE -> R.string.err_unconfigured
    AuthFailure.RATE_LIMITED -> R.string.err_rate
    AuthFailure.OFFLINE -> R.string.err_offline
    AuthFailure.SESSION_EXPIRED -> R.string.err_session
    AuthFailure.OTHER -> R.string.err_other
}

@Composable
fun LoginScreen(model: AppModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(Mode.LOGIN) }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val backDesc = stringResource(R.string.back)

    fun finish(name: String?) { password = ""; model.onSignedIn(name); onBack() }

    fun submit() {
        if (working) return
        if (!model.svc.hasSignInConfig) { error = R.string.err_not_built; return }
        // A name that fails cleanUsername never reaches the network (A14); e-mail is sent as typed.
        if (mode == Mode.LOGIN && !user.contains('@') && Username.clean(user) == null) { error = R.string.err_invalid_name; return }
        error = null
        working = true
        val signup = mode == Mode.SIGNUP
        val typed = user.trim()
        scope.launch {
            val failure = withContext(Dispatchers.IO) {
                runCatching { if (signup) model.svc.coordinator.signUp(typed, password) else model.svc.coordinator.login(typed, password) }
                    .getOrElse { AuthFailure.OTHER }
            }
            working = false
            if (failure == null) finish(typed) else { password = ""; error = errorRes(failure) }
        }
    }

    ScalingLazyColumn(
        Modifier.fillMaxSize().background(Bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(top = 26.dp, bottom = 30.dp, start = 10.dp, end = 10.dp),
    ) {
        item {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).semantics { contentDescription = backDesc }
                    .clickable(role = Role.Button) { if (mode == Mode.LOGIN) onBack() else { mode = Mode.LOGIN; error = null } },
                contentAlignment = Alignment.Center,
            ) { Txt("‹", Type.display, color = Ink, maxLines = 1) }
        }
        when (mode) {
            Mode.PHONE -> {
                item { Txt(stringResource(R.string.login_via_phone), Type.title) }
                item { Txt(stringResource(R.string.phone_instruction), Type.body, color = Muted) }
                error?.let { e -> item { ErrorText(e) } }
                item {
                    WidePill(stringResource(if (working) R.string.login_working else R.string.phone_done), {
                        working = true; error = null
                        scope.launch {
                            val found = withContext(Dispatchers.IO) { runCatching { model.svc.pullPhoneToken() }.getOrDefault(false) }
                            working = false
                            if (found) finish(null) else error = R.string.phone_none
                        }
                    }, primary = true, enabled = !working)
                }
            }
            else -> {
                item { Txt(stringResource(if (mode == Mode.SIGNUP) R.string.signup_title else R.string.login_title), Type.title) }
                item { Field(stringResource(if (mode == Mode.SIGNUP) R.string.signup_user_field else R.string.login_user_field), user, KeyboardType.Email, false) { user = it } }
                item { Field(stringResource(if (mode == Mode.SIGNUP) R.string.signup_password_field else R.string.login_password_field), password, KeyboardType.Password, !show) { password = it } }
                item {
                    Box(
                        Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).toggleable(value = show, role = Role.Switch) { show = it }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Txt(stringResource(if (show) R.string.hide_password else R.string.show_password), Type.caption, color = Coral, maxLines = 1) }
                }
                error?.let { e -> item { ErrorText(e) } }
                item {
                    val label = if (mode == Mode.SIGNUP) R.string.signup_submit else R.string.login_button
                    WidePill(stringResource(if (working) R.string.login_working else label), { submit() }, primary = true, enabled = !working && user.isNotBlank() && password.isNotEmpty())
                }
                item {
                    WidePill(stringResource(if (mode == Mode.SIGNUP) R.string.login_button else R.string.signup_open), {
                        mode = if (mode == Mode.SIGNUP) Mode.LOGIN else Mode.SIGNUP; error = null
                    }, enabled = !working)
                }
                item { WidePill(stringResource(R.string.login_via_phone), { mode = Mode.PHONE; error = null }, enabled = !working) }
            }
        }
    }
}

@Composable
private fun ErrorText(res: Int) {
    ErrorLine(stringResource(res), Modifier.fillMaxWidth())
}

@Composable
fun Field(label: String, value: String, keyboard: KeyboardType, secret: Boolean, onChange: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn48().clip(RoundedCornerShape(16.dp)).background(Surface2).padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Txt(label, Type.caption, align = TextAlign.Start, maxLines = 2)
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.take(200)) },
            singleLine = true,
            textStyle = Type.body,
            cursorBrush = SolidColor(Coral),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}

private fun Modifier.heightIn48() = this.then(Modifier.height(48.dp))

/** Same width as the text fields. */
@Composable
private fun WidePill(label: String, onClick: () -> Unit, primary: Boolean = false, enabled: Boolean = true) =
    Pill(label, onClick, Modifier.fillMaxWidth(), primary = primary, enabled = enabled)
