package hu.regelo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.regelo.app.AppGraph
import hu.regelo.app.data.ProfileRepository
import hu.regelo.app.data.SignUpResult
import hu.regelo.app.ui.nav.Route
import hu.regelo.app.ui.theme.Ink
import hu.regelo.app.ui.theme.Lora
import hu.regelo.app.ui.theme.Paper
import hu.regelo.app.ui.theme.Rust
import hu.regelo.app.util.friendlyMessage
import kotlinx.coroutines.launch

private enum class AuthPage { WELCOME, LOGIN, REGISTER, FORGOT, SENT }

/** Bejelentkezés, regisztráció, elfelejtett jelszó. */
@Composable
fun AuthFlow(onOpenLegal: (Route.Legal) -> Unit) {
    var page by remember { mutableStateOf(AuthPage.WELCOME) }
    var email by remember { mutableStateOf("") }
    var sentText by remember { mutableStateOf("") }
    var sentIsSignup by remember { mutableStateOf(true) }
    val notice by AppGraph.auth.notice.collectAsState()
    androidx.activity.compose.BackHandler(enabled = page != AuthPage.WELCOME) {
        page = if (page == AuthPage.FORGOT) AuthPage.LOGIN else AuthPage.WELCOME
    }
    notice?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { AppGraph.auth.clearNotice() },
            title = { Text("A link nem működött") },
            text = { Text(msg) },
            confirmButton = { Button(onClick = { AppGraph.auth.clearNotice(); page = AuthPage.LOGIN }) { Text("Rendben") } },
        )
    }
    when (page) {
        AuthPage.WELCOME -> WelcomePage(onLogin = { page = AuthPage.LOGIN }, onRegister = { page = AuthPage.REGISTER })
        AuthPage.LOGIN -> LoginPage(
            email = email, onEmail = { email = it },
            onForgot = { page = AuthPage.FORGOT },
            onRegister = { page = AuthPage.REGISTER },
            onNeedsConfirm = { sentText = it; sentIsSignup = true; page = AuthPage.SENT },
        )
        AuthPage.REGISTER -> RegisterPage(
            email = email, onEmail = { email = it },
            onLogin = { page = AuthPage.LOGIN },
            onSent = { sentText = it; sentIsSignup = true; page = AuthPage.SENT },
            onOpenLegal = onOpenLegal,
        )
        AuthPage.FORGOT -> ForgotPage(email = email, onEmail = { email = it }, onBack = { page = AuthPage.LOGIN }, onSent = { sentText = it; sentIsSignup = false; page = AuthPage.SENT })
        AuthPage.SENT -> SentPage(text = sentText, email = email, isSignup = sentIsSignup, onBack = { page = AuthPage.LOGIN })
    }
}

@Composable
private fun AuthColumn(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
private fun Logo(size: Int = 72) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(18.dp)).background(Ink), contentAlignment = Alignment.Center) {
        Text("R", fontFamily = Lora, fontStyle = FontStyle.Italic, fontSize = (size / 2).sp, color = Rust)
    }
}

@Composable
private fun WelcomePage(onLogin: () -> Unit, onRegister: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(Modifier.height(40.dp))
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Paper), contentAlignment = Alignment.Center) {
                Text("R", fontFamily = Lora, fontStyle = FontStyle.Italic, fontSize = 32.sp, color = Rust)
            }
            Spacer(Modifier.height(28.dp))
            Text("Regélő", fontFamily = Lora, fontStyle = FontStyle.Italic, fontSize = 44.sp, color = Paper)
            Spacer(Modifier.height(12.dp))
            Text(
                "Magyar történetek otthona. Olvass, írj, kommentelj bekezdésenként – telefonon, tableten, offline is.",
                color = Paper.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(36.dp))
            listOf(
                "Könyv-első polc, nem végtelen hírfolyam",
                "Automatikus mentés és szinkron minden eszközön",
                "Bekezdésenkénti kommentek, csillagos értékelés",
                "Önkéntes borravaló a szerzőknek",
            ).forEach {
                Text("•  $it", color = Paper.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
            }
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onRegister, modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Rust, contentColor = Color.White),
            ) { Text("Regisztráció") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onLogin, modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Paper),
            ) { Text("Van már fiókom – belépés") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, imeAction: ImeAction = ImeAction.Done, onDone: () -> Unit = {}) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }, onNext = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (visible) "Jelszó elrejtése" else "Jelszó mutatása")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) {
        Spacer(Modifier.height(10.dp))
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LoginPage(email: String, onEmail: (String) -> Unit, onForgot: () -> Unit, onRegister: () -> Unit, onNeedsConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    fun submit() {
        if (busy) return
        if (!email.contains('@') || password.isEmpty()) { error = "Add meg az e-mail-címed és a jelszavad."; return }
        busy = true; error = null; focus.clearFocus()
        scope.launch {
            try {
                AppGraph.auth.signIn(email, password)
            } catch (e: Exception) {
                val msg = e.friendlyMessage()
                error = msg
                if (msg.contains("erősítetted")) {
                    runCatching { AppGraph.auth.resendConfirmation(email) }
                    onNeedsConfirm("A fiókod még nincs megerősítve. Újra elküldtük a megerősítő e-mailt a(z) $email címre – kattints a benne lévő gombra ezen a telefonon, majd lépj be.")
                }
            } finally { busy = false }
        }
    }

    AuthColumn {
        Logo()
        Spacer(Modifier.height(20.dp))
        Text("Üdv újra!", style = MaterialTheme.typography.headlineMedium)
        Text("Lépj be a Regélő-fiókodba", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = email, onValueChange = onEmail, label = { Text("E-mail-cím") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        PasswordField(password, { password = it }, "Jelszó", onDone = ::submit)
        ErrorText(error)
        Spacer(Modifier.height(20.dp))
        Button(onClick = ::submit, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("Belépés")
        }
        TextButton(onClick = onForgot) { Text("Elfelejtettem a jelszavam") }
        TextButton(onClick = onRegister) { Text("Még nincs fiókom – regisztrálok") }
    }
}

@Composable
private fun RegisterPage(email: String, onEmail: (String) -> Unit, onLogin: () -> Unit, onSent: (String) -> Unit, onOpenLegal: (Route.Legal) -> Unit) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var accepted by remember { mutableStateOf(false) }
    var adult by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    fun submit() {
        if (busy) return
        val cleanUser = ProfileRepository.sanitizeUsername(username)
        error = when {
            name.isBlank() -> "Add meg a megjelenített neved."
            cleanUser.length < 3 -> "A felhasználónév legalább 3 karakter legyen (betűk, számok, _ és .)."
            !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim()) -> "Érvénytelen e-mail-cím."
            password.length < 8 -> "A jelszó legalább 8 karakter legyen."
            password != password2 -> "A két jelszó nem egyezik."
            !adult -> "A regisztrációhoz legalább 16 évesnek kell lenned."
            !accepted -> "Fogadd el a felhasználási feltételeket és az adatkezelési tájékoztatót."
            else -> null
        }
        if (error != null) return
        busy = true; focus.clearFocus()
        scope.launch {
            try {
                when (AppGraph.auth.signUp(email, password, name, cleanUser)) {
                    SignUpResult.NeedsConfirmation -> onSent("Elküldtük a megerősítő e-mailt a(z) ${email.trim()} címre. Nyisd meg ezen a telefonon, és kattints az „E-mail-cím megerősítése” gombra – az app automatikusan belép.")
                    SignUpResult.SignedIn -> {}
                    SignUpResult.AlreadyRegistered -> error = "Ezzel az e-mail-címmel már van fiók. Lépj be, vagy kérj új jelszót."
                }
            } catch (e: Exception) {
                error = e.friendlyMessage().also { hu.regelo.app.util.ErrorReporter.report(e, "AuthScreens") }
            } finally { busy = false }
        }
    }

    AuthColumn {
        Logo()
        Spacer(Modifier.height(20.dp))
        Text("Fiók létrehozása", style = MaterialTheme.typography.headlineMedium)
        Text("Kezdj el olvasni és írni a Regélőn", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name, onValueChange = { if (it.length <= 80) name = it }, label = { Text("Megjelenített név") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = username, onValueChange = { if (it.length <= 30) username = it }, label = { Text("Felhasználónév") }, singleLine = true,
            prefix = { Text("@") },
            supportingText = { if (username.isNotBlank()) Text("Így fog látszani: @${ProfileRepository.sanitizeUsername(username)}") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = email, onValueChange = onEmail, label = { Text("E-mail-cím") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        PasswordField(password, { password = it }, "Jelszó (min. 8 karakter)", ImeAction.Next)
        Spacer(Modifier.height(10.dp))
        PasswordField(password2, { password2 = it }, "Jelszó mégegyszer", onDone = ::submit)
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = adult, onCheckedChange = { adult = it })
            Text("Elmúltam 16 éves", style = MaterialTheme.typography.bodyMedium)
        }
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = accepted, onCheckedChange = { accepted = it })
            Column {
                Text("Elfogadom a feltételeket", style = MaterialTheme.typography.bodyMedium)
                androidx.compose.foundation.layout.Row {
                    TextButton(onClick = { onOpenLegal(Route.Legal("terms")) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) { Text("Felhasználási feltételek", fontSize = 12.sp) }
                    TextButton(onClick = { onOpenLegal(Route.Legal("privacy")) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) { Text("Adatkezelés", fontSize = 12.sp) }
                }
            }
        }
        ErrorText(error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = ::submit, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("Regisztráció")
        }
        TextButton(onClick = onLogin) { Text("Van már fiókod? Jelentkezz be") }
    }
}

@Composable
private fun ForgotPage(email: String, onEmail: (String) -> Unit, onBack: () -> Unit, onSent: (String) -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AuthColumn {
        Logo()
        Spacer(Modifier.height(20.dp))
        Text("Új jelszó kérése", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Küldünk egy linket az e-mail-címedre. Nyisd meg ezen a telefonon, és megadhatod az új jelszavad.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = email, onValueChange = onEmail, label = { Text("E-mail-cím") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
        )
        ErrorText(error)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (!email.contains('@')) { error = "Add meg az e-mail-címed."; return@Button }
                busy = true; error = null
                scope.launch {
                    try {
                        AppGraph.auth.sendPasswordReset(email)
                        onSent("Ha van fiók a(z) ${email.trim()} címmel, elküldtük rá a jelszó-visszaállító linket. Nyisd meg ezen a telefonon.")
                    } catch (e: Exception) { error = e.friendlyMessage().also { hu.regelo.app.util.ErrorReporter.report(e, "AuthScreens") } } finally { busy = false }
                }
            },
            enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("Link küldése") }
        TextButton(onClick = onBack) { Text("Vissza a belépéshez") }
    }
}

@Composable
private fun SentPage(text: String, email: String, isSignup: Boolean, onBack: () -> Unit) {
    var info by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AuthColumn {
        Spacer(Modifier.height(32.dp))
        Box(Modifier.size(80.dp).clip(RoundedCornerShape(40.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Email, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.height(20.dp))
        Text("Nézd meg a postafiókod", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Nem jött meg? Nézd meg a Spam / Promóciók mappát is.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        if (email.contains('@')) {
            OutlinedButton(onClick = {
                scope.launch {
                    info = try {
                        if (isSignup) AppGraph.auth.resendConfirmation(email) else AppGraph.auth.sendPasswordReset(email)
                        "Újra elküldtük."
                    } catch (e: Exception) { e.friendlyMessage() }
                }
            }) { Text(if (isSignup) "Megerősítő e-mail újraküldése" else "Jelszó-visszaállító link újraküldése") }
        }
        info?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Tovább a belépéshez") }
    }
}

/** A jelszó-visszaállító linkről érkezve: új jelszó megadása. */
@Composable
fun NewPasswordScreen() {
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AuthColumn {
        Logo()
        Spacer(Modifier.height(20.dp))
        Text("Új jelszó", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        if (done) {
            Text("Kész! Az új jelszavad el van mentve.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { AppGraph.auth.dismissRecovery() }, modifier = Modifier.fillMaxWidth()) { Text("Tovább az apphoz") }
        } else {
            PasswordField(password, { password = it }, "Új jelszó (min. 8 karakter)", ImeAction.Next)
            Spacer(Modifier.height(10.dp))
            PasswordField(password2, { password2 = it }, "Új jelszó mégegyszer")
            ErrorText(error)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    error = when {
                        password.length < 8 -> "A jelszó legalább 8 karakter legyen."
                        password != password2 -> "A két jelszó nem egyezik."
                        else -> null
                    }
                    if (error != null) return@Button
                    busy = true
                    scope.launch {
                        try { AppGraph.auth.updatePassword(password); done = true } catch (e: Exception) { error = e.friendlyMessage().also { hu.regelo.app.util.ErrorReporter.report(e, "AuthScreens") } } finally { busy = false }
                    }
                },
                enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Jelszó mentése") }
            TextButton(onClick = { AppGraph.auth.dismissRecovery() }) { Text("Mégse") }
        }
    }
}

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        Text("Regélő", fontFamily = Lora, fontStyle = FontStyle.Italic, fontSize = 40.sp, color = Paper)
    }
}
