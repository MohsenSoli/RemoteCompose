package com.example.backend

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RcProfiles
import androidx.compose.remote.core.operations.Header
import androidx.compose.remote.core.operations.layout.MultiClickModifier
import androidx.compose.remote.core.operations.layout.modifiers.HostNamedActionOperation
import androidx.compose.remote.creation.JvmRcPlatformServices
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.actions.HostAction
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcFontWeight
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcInteger
import androidx.compose.remote.creation.dsl.RcProfile
import androidx.compose.remote.creation.dsl.RcRowScope
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.dsl.RcSp
import androidx.compose.remote.creation.dsl.RcTextFromFloatSpec
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.Spacer
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.createRcBuffer
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.onClick
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.ripple
import androidx.compose.remote.creation.dsl.rsp
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.verticalScroll
import androidx.compose.remote.creation.dsl.width
import androidx.compose.remote.creation.modifiers.ClickActionModifier
import androidx.compose.remote.creation.modifiers.RecordingModifier
import androidx.compose.remote.creation.modifiers.RoundedRectShape
import androidx.compose.remote.creation.profile.Profile
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

data class User(
    val id: Int,
    val name: String,
    val role: String,
    val email: String,
    val location: String,
    val joined: String,
)

private val users = listOf(
    User(1, "Ada Lovelace", "Staff Engineer", "ada@demo.dev", "London, UK", "2019"),
    User(2, "Alan Turing", "Cryptography Lead", "alan@demo.dev", "Manchester, UK", "2020"),
    User(3, "Grace Hopper", "Compiler Architect", "grace@demo.dev", "New York, US", "2018"),
    User(4, "Katherine Johnson", "Numerical Analyst", "katherine@demo.dev", "Hampton, US", "2021"),
    User(5, "Margaret Hamilton", "Flight Software Lead", "margaret@demo.dev", "Boston, US", "2019"),
    User(6, "Linus Torvalds", "Kernel Maintainer", "linus@demo.dev", "Portland, US", "2022"),
    User(7, "Barbara Liskov", "Distinguished Engineer", "barbara@demo.dev", "Cambridge, US", "2017"),
    User(8, "Dennis Ritchie", "Language Designer", "dennis@demo.dev", "Murray Hill, US", "2018"),
    User(9, "Radia Perlman", "Network Architect", "radia@demo.dev", "Seattle, US", "2020"),
    User(10, "Donald Knuth", "Algorithms Researcher", "donald@demo.dev", "Stanford, US", "2016"),
    User(11, "Hedy Lamarr", "Wireless Researcher", "hedy@demo.dev", "Vienna, AT", "2023"),
    User(12, "Tim Berners-Lee", "Web Platform Lead", "tim@demo.dev", "Oxford, UK", "2021"),
)

private fun User.initials(): String =
    name.split(' ', '-').filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }

fun main() {
    embeddedServer(Netty, port = 8080) {
        routing {
            get("/health") {
                call.respondText("ok")
            }

            get("/ui/users") {
                val waves = call.request.queryParameters["waves"]?.toIntOrNull()
                    ?.coerceIn(0, 999) ?: 0
                val favorites = call.request.queryParameters["favs"]
                    ?.split(',')?.mapNotNull(String::toIntOrNull)?.toSet().orEmpty()
                call.respondBytes(
                    usersListDoc(users, call.viewport(), waves, favorites),
                    ContentType.Application.OctetStream,
                )
            }

            get("/ui/users/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                val user = users.firstOrNull { it.id == id }
                if (user == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                val favorite = call.request.queryParameters["fav"] == "1"
                call.respondBytes(
                    userDetailDoc(user, call.viewport(), favorite),
                    ContentType.Application.OctetStream,
                )
            }
        }
    }.start(wait = true)
}

/** Client-reported viewport: dp size + density (the player works in raw px, see [Ui]). */
data class Viewport(val widthDp: Int, val heightDp: Int, val density: Float)

private fun ApplicationCall.viewport(): Viewport = Viewport(
    widthDp = request.queryParameters["w"]?.toIntOrNull()?.coerceIn(200, 2000) ?: 412,
    heightDp = request.queryParameters["h"]?.toIntOrNull()?.coerceIn(200, 3000) ?: 917,
    density = request.queryParameters["d"]?.toFloatOrNull()?.coerceIn(0.5f, 5f) ?: 1f,
)

private val platform = JvmRcPlatformServices()

private val docProfile = RcProfile(
    Profile(
        CoreDocument.DOCUMENT_API_LEVEL,
        RcProfiles.PROFILE_ANDROIDX,
        platform,
    ) { info, profile, _ ->
        RemoteComposeWriter(
            profile,
            RemoteComposeWriter.hTag(Header.DOC_WIDTH, info.width),
            RemoteComposeWriter.hTag(Header.DOC_HEIGHT, info.height),
        )
    },
    experimental = true,
)

private fun rcDocument(
    contentDescription: String,
    viewport: Viewport,
    content: RcScope.() -> Unit,
): ByteArray = createRcBuffer(
    docProfile,
    RemoteComposeWriter.hTag(Header.DOC_WIDTH, viewport.widthDp),
    RemoteComposeWriter.hTag(Header.DOC_HEIGHT, viewport.heightDp),
    RemoteComposeWriter.hTag(Header.DOC_CONTENT_DESCRIPTION, contentDescription),
    // Without this, a legacy gesture detector fires click actions ~3x per tap.
    RemoteComposeWriter.hTag(Header.FEATURE_CLICK_VERSION, 1),
    content = content,
)

/** Click modifier carrying an int payload to the host (the DSL's hostAction can't yet). */
private class HostIntActionElement(
    private val name: String,
    private val value: Int,
) : Modifier.Element, RecordingModifier.Element {
    override fun applyTo(modifier: RecordingModifier) {
        modifier.then(this)
    }

    override fun write(writer: RemoteComposeWriter) {
        // HostAction wants the *id* of a registered integer variable, not a literal.
        val valueId = (writer.addInteger(value) % 0x100000000L).toInt()
        ClickActionModifier(
            listOf(HostAction(name, HostNamedActionOperation.INT_TYPE, valueId)),
            MultiClickModifier.CLICK_TYPE_SINGLE,
        ).write(writer)
    }
}

private fun Modifier.onClickHostAction(name: String, value: Int): Modifier =
    then(HostIntActionElement(name, value))

/** Light/dark color pairs, resolved on the player. */
private class DocTheme(scope: RcScope) {
    // remoteThemedColor(Int, Int) is broken in alpha12 (treats values as ids,
    // crashing the player) — register colors first and use the RcColor overload.
    private fun RcScope.themed(light: Long, dark: Long): RcColor =
        remoteThemedColor(remoteColor(light.toInt()), remoteColor(dark.toInt()))

    val background: RcColor = scope.themed(0xFFF3F5FA, 0xFF0F1115)
    val surface: RcColor = scope.themed(0xFFFFFFFF, 0xFF1A1D24)
    val chip: RcColor = scope.themed(0xFFE8ECF6, 0xFF252B38)
    val text: RcColor = scope.themed(0xFF171A1F, 0xFFECEEF2)
    val muted: RcColor = scope.themed(0xFF697586, 0xFF98A2B3)
    val accent: RcColor = scope.themed(0xFF4C6FFF, 0xFF8AA2FF)
}

/** Theme + dp/sp→px conversion: the alpha12 player treats every dimension, text included, as raw px. */
private class Ui(val theme: DocTheme, val density: Float) {
    fun dp(value: Int): Float = value * density
    fun dp(value: Float): Float = value * density
    fun sp(value: Int): RcSp = (value * density).rsp
}

private const val WHITE = 0xFFFFFFFF.toInt()

private val avatarColors = listOf(
    0xFF6C5CE7.toInt(),
    0xFF00B894.toInt(),
    0xFFE17055.toInt(),
    0xFF0984E3.toInt(),
    0xFFD63031.toInt(),
    0xFFE84393.toInt(),
    0xFF00A8A8.toInt(),
    0xFFB8860B.toInt(),
)

private fun User.avatarColor(): Int = avatarColors[id % avatarColors.size]

private fun pill(radius: Float) = RoundedRectShape(radius, radius, radius, radius)

private val twoDigits = RcTextFromFloatSpec.of(
    padPre = RcTextFromFloatSpec.PadPre.Zero,
    padAfter = RcTextFromFloatSpec.PadAfter.None,
)

private val plainNumber = RcTextFromFloatSpec.of(
    padPre = RcTextFromFloatSpec.PadPre.None,
    padAfter = RcTextFromFloatSpec.PadAfter.None,
)

private fun usersListDoc(
    users: List<User>,
    viewport: Viewport,
    initialWaves: Int,
    favorites: Set<Int>,
): ByteArray =
    rcDocument("Team directory", viewport) {
        val ui = Ui(DocTheme(this), viewport.density)

        Box(Modifier.fillMaxSize().background(ui.theme.background)) {
            Column(Modifier.fillMaxSize().verticalScroll().padding(ui.dp(20))) {
                Spacer(Modifier.height(ui.dp(40)))

                Row(Modifier.fillMaxWidth(), vertical = RcVerticalPositioning.Center) {
                    Column {
                        Text(
                            "Team",
                            color = ui.theme.text,
                            fontSize = ui.sp(30),
                            fontWeight = RcFontWeight.Bold,
                        )
                        Spacer(Modifier.height(ui.dp(4)))
                        Text(
                            "${users.size} members · rendered remotely",
                            color = ui.theme.muted,
                            fontSize = ui.sp(13),
                        )
                    }
                    Spacer()
                    val clock = createTextFromFloat(hour(), 2, 0, twoDigits) + ":" +
                        createTextFromFloat(minutes(), 2, 0, twoDigits)
                    Box(
                        Modifier
                            .clip(pill(ui.dp(14)))
                            .background(ui.theme.chip)
                            .padding(
                                start = ui.dp(12), top = ui.dp(6),
                                end = ui.dp(12), bottom = ui.dp(6),
                            )
                    ) {
                        Text(
                            clock,
                            color = ui.theme.accent,
                            fontSize = ui.sp(14),
                            fontWeight = RcFontWeight.SemiBold,
                        )
                    }
                }

                Spacer(Modifier.height(ui.dp(18)))

                // Document state for instant taps; "wave" lets the host mirror it
                // and bake it into the next load (?waves=N).
                val waves = remoteFloat(initialWaves.toFloat())
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(pill(ui.dp(18)))
                        .background(ui.theme.chip)
                        .onClick {
                            setValue(waves, waves + 1f)
                            hostAction("wave")
                        }
                        .ripple()
                        .padding(
                            start = ui.dp(16), top = ui.dp(12),
                            end = ui.dp(16), bottom = ui.dp(12),
                        ),
                    vertical = RcVerticalPositioning.Center,
                ) {
                    Text(
                        "👋  Say hi to the team",
                        color = ui.theme.text,
                        fontSize = ui.sp(14),
                        fontWeight = RcFontWeight.Medium,
                    )
                    Spacer()
                    Box(
                        Modifier
                            .clip(pill(ui.dp(12)))
                            .background(ui.theme.accent)
                            .padding(
                                start = ui.dp(10), top = ui.dp(4),
                                end = ui.dp(10), bottom = ui.dp(4),
                            )
                    ) {
                        Text(
                            createTextFromFloat(waves, 3, 0, plainNumber),
                            color = WHITE,
                            fontSize = ui.sp(13),
                            fontWeight = RcFontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(ui.dp(22)))
                Text(
                    "MEMBERS",
                    color = ui.theme.muted,
                    fontSize = ui.sp(12),
                    fontWeight = RcFontWeight.SemiBold,
                )
                Spacer(Modifier.height(ui.dp(10)))

                users.forEach { user ->
                    userCard(user, ui, favorite = user.id in favorites)
                    Spacer(Modifier.height(ui.dp(10)))
                }

                Spacer(Modifier.height(ui.dp(24)))
            }
        }
    }

private fun RcScope.userCard(user: User, ui: Ui, favorite: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(pill(ui.dp(18)))
            .background(ui.theme.surface)
            .onClickHostAction("open_user", user.id)
            .ripple()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(ui.dp(14)),
            vertical = RcVerticalPositioning.Center,
        ) {
            avatar(user, sizeDp = 46, fontSize = 15, ui = ui)
            Spacer(Modifier.width(ui.dp(13)))
            Column {
                Row(vertical = RcVerticalPositioning.Center) {
                    Text(
                        user.name,
                        color = ui.theme.text,
                        fontSize = ui.sp(16),
                        fontWeight = RcFontWeight.SemiBold,
                    )
                    if (favorite) {
                        Spacer(Modifier.width(ui.dp(6)))
                        Text("★", color = ui.theme.accent, fontSize = ui.sp(13))
                    }
                }
                Spacer(Modifier.height(ui.dp(3)))
                Text(user.role, color = ui.theme.muted, fontSize = ui.sp(13))
            }
            Spacer()
            Text("›", color = ui.theme.muted, fontSize = ui.sp(20))
        }
    }
}

private fun RcScope.avatar(user: User, sizeDp: Int, fontSize: Int, ui: Ui) {
    // CircleShape clip is ignored for backgrounds; rounded rect at size/2 instead.
    Box(
        Modifier
            .size(ui.dp(sizeDp))
            .clip(pill(ui.dp(sizeDp) / 2f))
            .background(user.avatarColor()),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        Text(
            user.initials(),
            color = WHITE,
            fontSize = ui.sp(fontSize),
            fontWeight = RcFontWeight.SemiBold,
        )
    }
}

private fun userDetailDoc(user: User, viewport: Viewport, initialFavorite: Boolean): ByteArray =
    rcDocument("Profile of ${user.name}", viewport) {
        val ui = Ui(DocTheme(this), viewport.density)

        Box(Modifier.fillMaxSize().background(ui.theme.background)) {
            Column(Modifier.fillMaxSize().verticalScroll().padding(ui.dp(20))) {
                Spacer(Modifier.height(ui.dp(40)))

                Box(
                    Modifier
                        .clip(pill(ui.dp(16)))
                        .background(ui.theme.chip)
                        .onClick { hostAction("back") }
                        .ripple()
                        .padding(
                            start = ui.dp(14), top = ui.dp(8),
                            end = ui.dp(16), bottom = ui.dp(8),
                        )
                ) {
                    Text(
                        "‹  Back",
                        color = ui.theme.text,
                        fontSize = ui.sp(14),
                        fontWeight = RcFontWeight.Medium,
                    )
                }

                Spacer(Modifier.height(ui.dp(18)))

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(pill(ui.dp(24)))
                        .background(ui.theme.surface)
                        .padding(ui.dp(24)),
                    horizontal = RcHorizontalPositioning.Center,
                ) {
                    avatar(user, sizeDp = 84, fontSize = 26, ui = ui)
                    Spacer(Modifier.height(ui.dp(14)))
                    Text(
                        user.name,
                        color = ui.theme.text,
                        fontSize = ui.sp(24),
                        fontWeight = RcFontWeight.Bold,
                    )
                    Spacer(Modifier.height(ui.dp(4)))
                    Text(
                        user.role,
                        color = ui.theme.accent,
                        fontSize = ui.sp(14),
                        fontWeight = RcFontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(ui.dp(6)))
                    Text(user.email, color = ui.theme.muted, fontSize = ui.sp(14))
                    Spacer(Modifier.height(ui.dp(18)))

                    // Flips instantly in the document; the host persists it to
                    // disk and bakes it into the next load (?fav=0|1).
                    val favorite = remoteInteger(if (initialFavorite) 1 else 0)
                    Box(
                        Modifier
                            .clip(pill(ui.dp(18)))
                            .background(ui.theme.chip)
                            .onClick {
                                setValue(favorite, (favorite * -1) + 1)
                                hostAction("toggle_favorite")
                            }
                            .ripple()
                    ) {
                        StateLayout(favorite) {
                            Box(
                                Modifier.padding(
                                    start = ui.dp(16), top = ui.dp(8),
                                    end = ui.dp(16), bottom = ui.dp(8),
                                )
                            ) {
                                Text(
                                    "☆  Add to favorites",
                                    color = ui.theme.text,
                                    fontSize = ui.sp(14),
                                    fontWeight = RcFontWeight.Medium,
                                )
                            }
                            Box(
                                Modifier.padding(
                                    start = ui.dp(16), top = ui.dp(8),
                                    end = ui.dp(16), bottom = ui.dp(8),
                                )
                            ) {
                                Text(
                                    "★  Favorited",
                                    color = ui.theme.accent,
                                    fontSize = ui.sp(14),
                                    fontWeight = RcFontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(ui.dp(14)))

                val tab = remoteInteger(0)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(pill(ui.dp(16)))
                        .background(ui.theme.chip)
                        .padding(ui.dp(4))
                ) {
                    segmentedButton("About", index = 0, tab = tab, ui = ui)
                    segmentedButton("Details", index = 1, tab = tab, ui = ui)
                }

                Spacer(Modifier.height(ui.dp(12)))

                // StateLayout as a direct Column child paints at the column's
                // origin (alpha12 bug) — keep it wrapped in a Box.
                Box(Modifier.fillMaxWidth()) {
                    StateLayout(tab, Modifier.fillMaxWidth()) {
                        aboutCard(user, ui)
                        detailsCard(user, ui)
                    }
                }

                Spacer(Modifier.height(ui.dp(24)))
            }
        }
    }

private fun RcRowScope.segmentedButton(label: String, index: Int, tab: RcInteger, ui: Ui) {
    Box(
        Modifier
            .weight(1f)
            .clip(pill(ui.dp(12)))
            .onClick { setValue(tab, index) }
            .ripple()
    ) {
        StateLayout(tab, Modifier.fillMaxWidth()) {
            segmentedFace(label, selected = index == 0, ui)
            segmentedFace(label, selected = index == 1, ui)
        }
    }
}

private fun RcScope.segmentedFace(label: String, selected: Boolean, ui: Ui) {
    val base = Modifier.fillMaxWidth().clip(pill(ui.dp(12)))
    Box(
        (if (selected) base.background(ui.theme.surface) else base)
            .padding(top = ui.dp(8), bottom = ui.dp(8)),
        horizontal = RcHorizontalPositioning.Center,
    ) {
        Text(
            label,
            color = if (selected) ui.theme.accent else ui.theme.muted,
            fontSize = ui.sp(14),
            fontWeight = if (selected) RcFontWeight.SemiBold else RcFontWeight.Medium,
        )
    }
}

private fun RcScope.aboutCard(user: User, ui: Ui) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(pill(ui.dp(18)))
            .background(ui.theme.surface)
            .padding(ui.dp(18))
    ) {
        Text(
            "${user.name.substringBefore(' ')} is our ${user.role.lowercase()}, " +
                "based in ${user.location}. On the team since ${user.joined}.",
            color = ui.theme.text,
            fontSize = ui.sp(14),
        )
        Spacer(Modifier.height(ui.dp(12)))
        Text(
            "This screen — including the tabs and the favorite toggle — is a " +
                "RemoteCompose document generated on the server. All state and " +
                "animation run inside the player.",
            color = ui.theme.muted,
            fontSize = ui.sp(13),
        )
    }
}

private fun RcScope.detailsCard(user: User, ui: Ui) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(pill(ui.dp(18)))
            .background(ui.theme.surface)
            .padding(
                start = ui.dp(18), top = ui.dp(6),
                end = ui.dp(18), bottom = ui.dp(6),
            )
    ) {
        detailRow("Employee ID", "#%03d".format(user.id), ui)
        detailRow("Role", user.role, ui)
        detailRow("Email", user.email, ui)
        detailRow("Location", user.location, ui)
        detailRow("Joined", user.joined, ui)
    }
}

private fun RcScope.detailRow(label: String, value: String, ui: Ui) {
    Row(
        Modifier.fillMaxWidth().padding(top = ui.dp(12), bottom = ui.dp(12)),
        vertical = RcVerticalPositioning.Center,
    ) {
        Text(label, color = ui.theme.muted, fontSize = ui.sp(13))
        Spacer()
        Text(
            value,
            color = ui.theme.text,
            fontSize = ui.sp(14),
            fontWeight = RcFontWeight.Medium,
        )
    }
}
