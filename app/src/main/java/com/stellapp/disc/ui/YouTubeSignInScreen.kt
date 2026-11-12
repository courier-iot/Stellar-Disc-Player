package com.stellapp.disc.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.stellapp.disc.PlayerViewModel
import com.stellapp.disc.ui.theme.Mono
import com.stellapp.disc.ui.theme.PanelSolid
import com.stellapp.disc.ui.theme.TextHi
import com.stellapp.disc.ui.theme.TextLo
import com.stellapp.disc.ui.theme.Void

private const val SIGN_IN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2F"

/** Cookies that only exist once you're signed in to YouTube. */
private val SIGNED_IN_MARKERS = listOf("SAPISID", "__Secure-3PAPISID", "LOGIN_INFO")

/**
 * Sign in to YouTube inside the app. Once signed in, the app turns the
 * browser's YouTube cookies into a cookies.txt for yt-dlp.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeSignInScreen(vm: PlayerViewModel, onClose: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    var signedIn by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf("") }

    fun check() {
        val cookies = CookieManager.getInstance().getCookie("https://www.youtube.com") ?: ""
        signedIn = SIGNED_IN_MARKERS.any { cookies.contains("$it=") }
    }

    BackHandler(onBack = onClose)

    Box(
        Modifier
            .fillMaxSize()
            .background(Void)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextHi)
                }
                Column(Modifier.weight(1f)) {
                    Text("YOUTUBE SIGN-IN", style = neonTitleStyle(18))
                    Text(page.ifBlank { "accounts.google.com" }, color = TextLo, fontFamily = Mono, fontSize = 10.sp, maxLines = 1)
                }
            }
            Text(
                "Sign in with the Google account you want Uplink to use. Stellar Disc only keeps the YouTube cookies, never your password.",
                color = TextLo,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .neonPanel(primary.copy(alpha = 0.5f), glow = 0.dp, fill = PanelSolid)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    page = url?.substringAfter("://")?.substringBefore("/") ?: ""
                                    check()
                                }
                            }
                            loadUrl(SIGN_IN_URL)
                        }
                    },
                    onRelease = { it.destroy() },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .then(
                        if (signedIn) Modifier
                            .neonPanel(primary, glow = 18.dp, fill = androidx.compose.ui.graphics.Color.Transparent)
                            .background(Brush.horizontalGradient(listOf(primary, secondary)))
                        else Modifier.neonPanel(TextLo.copy(alpha = 0.4f), glow = 0.dp)
                    )
                    .clickable(enabled = signedIn) {
                        val raw = CookieManager.getInstance().getCookie("https://www.youtube.com") ?: ""
                        vm.saveYtSignIn(raw)
                        onClose()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (signedIn) "SIGNED IN · SAVE FOR UPLINK ▶" else "SIGN IN ABOVE FIRST",
                    color = if (signedIn) Void else TextLo,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
            }
            Text(
                "If Google says this browser isn't secure, close this screen and use Import cookies.txt in Settings instead.",
                color = TextLo,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
    }
}

/** Turns "name=value; name2=value2" (what the browser hands over) into a Netscape cookies.txt. */
fun toNetscapeCookies(raw: String): String {
    val expiry = System.currentTimeMillis() / 1000 + 180L * 24 * 3600
    val sb = StringBuilder("# Netscape HTTP Cookie File\n# Saved by Stellar Disc in-app sign-in\n")
    raw.split(";").map { it.trim() }.filter { it.contains("=") }.forEach { pair ->
        val name = pair.substringBefore("=").trim()
        val value = pair.substringAfter("=").trim()
        if (name.isNotEmpty()) {
            sb.append(".youtube.com\tTRUE\t/\tTRUE\t").append(expiry).append('\t').append(name).append('\t').append(value).append('\n')
        }
    }
    return sb.toString()
}
