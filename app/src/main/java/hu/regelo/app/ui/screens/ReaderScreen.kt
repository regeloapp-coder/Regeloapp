@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package hu.regelo.app.ui.screens

import hu.regelo.app.util.orReport
import hu.regelo.app.util.orReportNull

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import hu.regelo.app.AppGraph
import hu.regelo.app.data.AppSettings
import hu.regelo.app.data.BookDto
import hu.regelo.app.data.ChapterDto
import hu.regelo.app.data.ChapterMetaDto
import hu.regelo.app.data.CommentItem
import hu.regelo.app.data.HighlightColor
import hu.regelo.app.data.ProfileDto
import hu.regelo.app.data.ReaderFont
import hu.regelo.app.data.ReaderNote
import hu.regelo.app.data.ReaderSettings
import hu.regelo.app.data.ReaderTheme
import hu.regelo.app.ui.components.AdultGate
import hu.regelo.app.ui.components.Avatar
import hu.regelo.app.ui.components.ConfirmDialog
import hu.regelo.app.ui.components.ErrorState
import hu.regelo.app.ui.components.LoadingBox
import hu.regelo.app.ui.components.LocalSnackbar
import hu.regelo.app.ui.components.ReportDialog
import hu.regelo.app.ui.components.notify
import hu.regelo.app.ui.components.runAction
import hu.regelo.app.ui.nav.Navigator
import hu.regelo.app.ui.nav.Route
import hu.regelo.app.ui.theme.Anthracite
import hu.regelo.app.ui.theme.LocalDarkTheme
import hu.regelo.app.ui.theme.Lora
import hu.regelo.app.ui.theme.Manrope
import hu.regelo.app.ui.theme.Paper
import hu.regelo.app.ui.theme.Sepia
import hu.regelo.app.ui.theme.SepiaInk
import hu.regelo.app.util.Block
import hu.regelo.app.util.ReadAloud
import hu.regelo.app.util.friendlyMessage
import hu.regelo.app.util.inlineMarkdown
import hu.regelo.app.util.parseBlocks
import hu.regelo.app.util.readingTimeLabel
import hu.regelo.app.util.relativeTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalUriHandler
import hu.regelo.app.util.InternalLink
import hu.regelo.app.util.parseInternalLink
import hu.regelo.app.util.openSafe

private data class ReaderColors(val bg: Color, val text: Color, val muted: Color, val accent: Color, val dark: Boolean)

@Composable
private fun readerColors(theme: ReaderTheme): ReaderColors {
    val dark = LocalDarkTheme.current
    return when (theme) {
        ReaderTheme.PAPER -> ReaderColors(Paper, Color(0xFF23262D), Color(0xFF6A6F7A), Color(0xFFB0662B), false)
        ReaderTheme.SEPIA -> ReaderColors(Sepia, SepiaInk, Color(0xFF85725A), Color(0xFF9C5A22), false)
        ReaderTheme.NIGHT -> ReaderColors(Color(0xFF121316), Color(0xFFCFCBC2), Color(0xFF8A877F), Color(0xFFE6B48A), true)
        ReaderTheme.SYSTEM -> if (dark) ReaderColors(Anthracite, Color(0xFFE2DED6), Color(0xFF9A968E), Color(0xFFE6B48A), true)
        else ReaderColors(Paper, Color(0xFF23262D), Color(0xFF6A6F7A), Color(0xFFB0662B), false)
    }
}

private data class ReaderBook(val book: BookDto, val author: ProfileDto?, val chapters: List<ChapterMetaDto>)

private sealed interface TtsEvent {
    data class Ready(val hungarian: Boolean) : TtsEvent
    data class Done(val id: String) : TtsEvent
    data class Failed(val message: String) : TtsEvent
}

private val ttsRates = listOf(0.8f, 1.0f, 1.25f, 1.5f)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** A szövegben lévő linkek kezelése (belső hivatkozás → képernyő, külső → biztonsági szűrés után böngésző). */
private val LocalReaderLink = staticCompositionLocalOf<((String) -> Unit)?> { null }

/** „Naprakész vagy” üzenet könyvenként egyszer (amíg az app fut). */
private val caughtUpShown = java.util.Collections.synchronizedSet(mutableSetOf<String>())

@Composable
fun ReaderScreen(nav: Navigator, bookId: String, startChapterId: String?, startParagraph: Int) {
    val settings by AppGraph.settings.settings.collectAsState(initial = null)
    val uriHandler = LocalUriHandler.current
    val onLink: (String) -> Unit = remember(nav, uriHandler) {
        { url ->
            when (val link = parseInternalLink(url)) {
                is InternalLink.Book -> nav.push(Route.BookDetail(link.id))
                is InternalLink.Profile -> nav.push(Route.Profile(link.id))
                null -> { uriHandler.openSafe(url) }
            }
        }
    }
    CompositionLocalProvider(LocalReaderLink provides onLink) {
        ReaderScreenBody(nav, bookId, startChapterId, startParagraph, settings)
    }
}

@Composable
private fun ReaderScreenBody(nav: Navigator, bookId: String, startChapterId: String?, startParagraph: Int, settings: AppSettings?) {
    // Nagy kijelzőn (≥ 840 dp) kétoszlopos „könyv mód” is választható.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        ReaderContent(nav, bookId, startChapterId, startParagraph, settings, wide = maxWidth >= 840.dp)
    }
}

/** A látható kommentek száma (a törölt / helyőrző kommentek nélkül, a válaszokkal együtt). */
private fun List<CommentItem>.visibleCount(): Int = sumOf { (if (it.comment.deletedAt != null) 0 else 1) + it.replies.count { r -> r.comment.deletedAt == null } }

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
private fun ReaderContent(
    nav: Navigator,
    bookId: String,
    startChapterId: String?,
    startParagraph: Int,
    loadedSettings: AppSettings?,
    wide: Boolean,
) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    val settings = loadedSettings ?: AppSettings()
    val rs = settings.reader
    val colors = readerColors(rs.theme)
    val me = AppGraph.auth.userId

    var meta by remember { mutableStateOf<ReaderBook?>(null) }
    var metaError by remember { mutableStateOf<String?>(null) }
    var index by rememberSaveable { mutableIntStateOf(-1) }
    var chapter by remember { mutableStateOf<ChapterDto?>(null) }
    var chapterError by remember { mutableStateOf<String?>(null) }
    var comments by remember { mutableStateOf<List<CommentItem>>(emptyList()) }
    var likes by remember { mutableStateOf(0 to false) }
    var sheetParagraph by remember { mutableStateOf<Int?>(null) }
    var actionParagraph by remember { mutableStateOf<Int?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var showTip by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var restoreParagraph by remember { mutableIntStateOf(startParagraph) }
    var notes by remember { mutableStateOf<Map<String, ReaderNote>>(emptyMap()) }
    var focus by rememberSaveable { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // ---- Felolvasás állapota ----
    var readAloud by remember { mutableStateOf<ReadAloud?>(null) }
    var ttsActive by remember { mutableStateOf(false) }
    var ttsPlaying by remember { mutableStateOf(false) }
    var ttsBlock by remember { mutableIntStateOf(-1) }
    var ttsPendingStart by remember { mutableIntStateOf(-1) }
    var ttsContinueNext by remember { mutableStateOf(false) }
    var ttsNoVoice by remember { mutableStateOf(false) }
    val ttsEvents = remember { Channel<TtsEvent>(Channel.UNLIMITED) }
    DisposableEffect(Unit) { onDispose { readAloud?.shutdown() } }

    LaunchedEffect(bookId, retry) {
        metaError = null
        try {
            val book = AppGraph.books.getBook(bookId) ?: error("A történet nem található.")
            AppGraph.books.cacheBook(book)
            val chapters = AppGraph.books.chapters(bookId).filter { !it.isDraft || book.authorId == me }
            if (chapters.isEmpty()) error("Ebben a történetben még nincs olvasható fejezet.")
            val author = runCatching { AppGraph.profiles.get(book.authorId) }.orReportNull("ReaderScreen")
            meta = ReaderBook(book, author, chapters)
            if (index < 0) {
                val progress = AppGraph.library.progress(bookId)
                // A pozíció a rész azonosítója szerint (régi adatnál a sorszám szerint).
                val savedPos = progress?.let { p ->
                    chapters.indexOfFirst { it.id == p.chapterId }.takeIf { it >= 0 } ?: p.chapterIndex.coerceIn(0, chapters.lastIndex)
                }
                index = when {
                    startChapterId != null -> chapters.indexOfFirst { it.id == startChapterId }.coerceAtLeast(0)
                    // A végére ért olvasó az azóta megjelent következő részen folytatja.
                    progress != null && savedPos != null && progress.percent >= 95 && savedPos < chapters.lastIndex -> savedPos + 1
                    savedPos != null -> savedPos
                    else -> 0
                }
                if (startChapterId == null && startParagraph < 0 && progress != null && savedPos == index) restoreParagraph = progress.paragraphIndex
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            metaError = e.friendlyMessage().also { hu.regelo.app.util.ErrorReporter.report(e, "ReaderScreen") }
        }
    }
    // Saját jegyzetek és kiemelések ehhez a könyvhöz.
    LaunchedEffect(bookId) {
        notes = runCatching { AppGraph.reading.notesFor(bookId) }.orReport("ReaderScreen", emptyList()).associateBy { it.key }
    }

    val currentMeta = meta?.chapters?.getOrNull(index)
    val totalChapters = meta?.chapters?.size ?: 1
    val hasNext = index < totalChapters - 1
    val blocks = remember(chapter?.id, chapter?.content) { chapter?.let { parseBlocks(it.content) } ?: emptyList() }
    // 18+ kapu: megerősítés nélkül a tartalom nem jelenik meg (és olvasási pozíció sem mentődik).
    val adultBook = meta?.book?.let { it.ageRating == "18+" && it.authorId != me } == true
    val gated = adultBook && loadedSettings?.showAdult != true
    // A felolvasás hosszú életű visszahívásai mindig a friss adatot lássák.
    val blocksNow = androidx.compose.runtime.rememberUpdatedState(blocks)
    val hasNextNow = androidx.compose.runtime.rememberUpdatedState(hasNext)
    val rateNow = androidx.compose.runtime.rememberUpdatedState(rs.ttsRate)

    fun nextSpeakable(from: Int): Int {
        val b = blocksNow.value
        return (from.coerceAtLeast(0) until b.size).firstOrNull { blockText(b[it]) != null } ?: -1
    }

    fun stopTts() {
        readAloud?.stop()
        ttsActive = false; ttsPlaying = false; ttsBlock = -1; ttsPendingStart = -1; ttsContinueNext = false
    }

    fun speakAt(from: Int) {
        val engine = readAloud ?: return
        val b = blocksNow.value
        val idx = nextSpeakable(from)
        if (idx < 0) {
            if (hasNextNow.value) { ttsContinueNext = true; ttsPlaying = true; index++ }
            else { stopTts(); snackbar.notify("Felolvasás vége – elérted a történet végét.") }
            return
        }
        ttsBlock = idx; ttsPlaying = true
        engine.speak("b$idx-${chapter?.id}", ReadAloud.speakable(blockText(b[idx]) ?: ""), rateNow.value)
        scope.launch {
            val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
            if (visible.isNotEmpty() && (idx + 1) !in visible.dropLast(1)) runCatching { listState.animateScrollToItem(idx + 1) }
        }
    }

    fun startTts(fromBlock: Int) {
        ttsActive = true
        val engine = readAloud
        if (engine == null) {
            ttsPendingStart = fromBlock
            readAloud = ReadAloud(context, object : ReadAloud.Listener {
                override fun onReady(hungarian: Boolean) { ttsEvents.trySend(TtsEvent.Ready(hungarian)) }
                override fun onDone(utteranceId: String) { ttsEvents.trySend(TtsEvent.Done(utteranceId)) }
                override fun onError(message: String) { ttsEvents.trySend(TtsEvent.Failed(message)) }
            })
        } else if (engine.ready) speakAt(fromBlock) else ttsPendingStart = fromBlock
    }

    LaunchedEffect(Unit) {
        for (e in ttsEvents) when (e) {
            is TtsEvent.Ready -> {
                if (!e.hungarian) ttsNoVoice = true
                if (ttsActive && ttsPendingStart >= 0) { val s = ttsPendingStart; ttsPendingStart = -1; speakAt(s) }
            }
            is TtsEvent.Done -> if (ttsActive && ttsPlaying && e.id == "b$ttsBlock-${chapter?.id}") speakAt(ttsBlock + 1)
            is TtsEvent.Failed -> { stopTts(); snackbar.notify(e.message) }
        }
    }

    LaunchedEffect(currentMeta?.id, retry) {
        val cm = currentMeta ?: return@LaunchedEffect
        chapter = null; chapterError = null; comments = emptyList()
        // Kézi lapozásnál a felolvasás megáll (a fejezet végén automatikusan továbbmegy).
        if (ttsActive && !ttsContinueNext) { readAloud?.stop(); ttsPlaying = false; ttsBlock = -1 }
        try {
            val loaded = AppGraph.books.chapter(cm.id)
            chapter = loaded
            val parsed = parseBlocks(loaded.content)
            val target = restoreParagraph
            restoreParagraph = -1
            val blockIndex = if (target > 0) parsed.indexOfFirst { it.paragraphIndex == target }.takeIf { it >= 0 } ?: target.coerceAtMost(parsed.size) else -1
            listState.scrollToItem(if (blockIndex >= 0) (blockIndex + 1).coerceAtMost(parsed.size) else 0)
            comments = runCatching { AppGraph.social.comments(cm.id) }.orReport("ReaderScreen", emptyList())
            likes = runCatching { AppGraph.social.chapterLikes(cm.id) }.orReport("ReaderScreen", 0 to false)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            chapterError = e.friendlyMessage().also { hu.regelo.app.util.ErrorReporter.report(e, "ReaderScreen") }
        }
    }

    // Fejezetváltás után a felolvasás az új fejezet elején folytatódik.
    LaunchedEffect(blocks) {
        if (ttsContinueNext && blocks.isNotEmpty()) { ttsContinueNext = false; speakAt(0) }
    }

    val commentCounts = remember(comments) {
        comments.groupBy { it.comment.paragraphIndex }.mapValues { (_, v) -> v.visibleCount() }.filterValues { it > 0 }
    }
    val chapterNotes = remember(notes, currentMeta?.id) {
        notes.values.filter { it.chapterId == currentMeta?.id }.associateBy { it.paragraph }
    }

    // Olvasási pozíció mentése (helyben azonnal, felhőbe a háttérben) + végigolvasás rögzítése.
    LaunchedEffect(listState, chapter?.id, gated) {
        if (chapter == null || gated) return@LaunchedEffect
        val total = meta?.chapters?.size ?: 1
        snapshotFlow { listState.firstVisibleItemIndex to (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) }
            .distinctUntilChanged()
            .debounce(800.milliseconds)
            .collect { (first, last) ->
                val blockIdx = (first - 1).coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
                val paragraph = blocks.getOrNull(blockIdx)?.paragraphIndex?.takeIf { it >= 0 } ?: blockIdx
                // A fejezet végét (záró blokk) elérve a fejezet teljesen elolvasottnak számít.
                val atEnd = blocks.isNotEmpty() && last >= blocks.size + 1
                val inChapter = when {
                    blocks.isEmpty() -> 0f
                    atEnd -> 1f
                    else -> blockIdx.toFloat() / blocks.size
                }
                val percent = (((index + inChapter) / total) * 100).toInt().coerceIn(0, 100)
                AppGraph.library.saveProgress(bookId, index, percent, paragraph, meta?.chapters?.getOrNull(index)?.id)
            }
    }

    val twoColumns = wide && rs.twoColumns

    // Végigolvasás rögzítése (olvasási kihívás): az utolsó fejezet végén, egy kis olvasási idő után.
    val atEndNow by remember(blocks) {
        androidx.compose.runtime.derivedStateOf { blocks.isNotEmpty() && (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= blocks.size + 1 }
    }
    LaunchedEffect(chapter?.id, atEndNow, twoColumns, gated) {
        val b = meta?.book ?: return@LaunchedEffect
        if (chapter == null || gated || b.authorId == me || index != totalChapters - 1) return@LaunchedEffect
        when {
            twoColumns -> delay(60.seconds)
            !atEndNow -> return@LaunchedEffect
            // Ha a fejezet görgetés nélkül is kifér, legyen idő elolvasni.
            listState.firstVisibleItemIndex > 0 -> delay(1500.milliseconds)
            else -> delay(20.seconds)
        }
        if (!b.isCompleted) {
            // Folyamatban lévő történet: nem számít kiolvasottnak; ha új rész jön, visszakerül a Folytatásba.
            if (caughtUpShown.add(b.id)) snackbar.notify(
                if (AppGraph.official.isOfficial(b.id)) "Naprakész vagy! Az új részekről az adatlapon kérhetsz értesítést."
                else "Naprakész vagy! Ha új rész jelenik meg, értesítünk."
            )
            return@LaunchedEffect
        }
        runCatching {
            if (AppGraph.reading.markFinished(b.id)) {
                val c = AppGraph.reading.challenge()
                snackbar.notify(
                    if (c.goal > 0) "Kiolvastad! 🎉 Idei olvasási kihívás: ${c.count}/${c.goal}."
                    else "Kiolvastad! 🎉 Ez az idei ${c.count}. elolvasott könyved."
                )
            }
        }.onFailure { hu.regelo.app.util.ErrorReporter.report(it, "ReaderScreen.markFinished") }
    }
    // Kétoszlopos módban nincs görgetési pozíció: a fejezet megnyitását mentjük.
    LaunchedEffect(chapter?.id, twoColumns, gated) {
        val ch = chapter ?: return@LaunchedEffect
        if (!twoColumns || gated) return@LaunchedEffect
        val total = meta?.chapters?.size ?: 1
        AppGraph.library.saveProgress(bookId, index, ((index.toFloat() / total) * 100).toInt(), 0, ch.id)
    }

    // A kijelző ébren marad olvasás közben (beállítható).
    DisposableEffect(rs.keepScreenOn) {
        view.keepScreenOn = rs.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    // Fókusz („Ne zavarj”) mód: teljes képernyő, rejtett rendszersávok és vezérlők.
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(focus) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (focus) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) } }
    }
    LaunchedEffect(chrome) { if (chrome) { delay(4.seconds); chrome = false } }
    val showChrome = !focus || chrome

    val bodyStyle = TextStyle(
        fontFamily = if (rs.font == ReaderFont.SERIF) Lora else Manrope,
        fontSize = rs.fontSize.sp,
        lineHeight = (rs.fontSize * rs.lineHeight).sp,
        color = colors.text,
        textAlign = if (rs.justify) TextAlign.Justify else TextAlign.Start,
    )

    fun toggleLike() {
        val ch = currentMeta ?: return
        val newState = !likes.second
        likes = (likes.first + if (newState) 1 else -1) to newState
        scope.runAction(snackbar, onError = { likes = (likes.first + if (newState) -1 else 1) to !newState }) {
            AppGraph.social.setChapterLike(ch.id, newState)
        }
    }

    fun jumpTo(chapterId: String, paragraph: Int) {
        val i = meta?.chapters?.indexOfFirst { it.id == chapterId } ?: -1
        if (i < 0) { snackbar.notify("Ez a fejezet már nem érhető el."); return }
        if (i == index) {
            val bi = blocks.indexOfFirst { it.paragraphIndex == paragraph }
            if (bi >= 0) scope.launch { listState.scrollToItem(bi + 1) }
        } else { restoreParagraph = paragraph.coerceAtLeast(1); index = i }
    }

    val onParagraphTap: (Int) -> Unit = { p -> if (focus) chrome = !chrome else sheetParagraph = p }
    val onParagraphLongPress: (Int) -> Unit = { p -> actionParagraph = p }

    if (adultBook && (loadedSettings == null || gated)) {
        Surface(Modifier.fillMaxSize()) {
            if (loadedSettings == null) LoadingBox()
            else AdultGate(meta?.book?.ageRating, isMine = false, onBack = { nav.back() }) {}
        }
        return
    }

    Surface(color = colors.bg, contentColor = colors.text, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (showChrome) {
                // Felső sáv
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Vissza", tint = colors.text) }
                    Column(Modifier.weight(1f)) {
                        Text(meta?.book?.title ?: "", style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (currentMeta != null) Text("${index + 1}/${meta?.chapters?.size ?: 0} · ${currentMeta.title}", style = MaterialTheme.typography.labelSmall, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { showChapters = true }) { Icon(Icons.AutoMirrored.Filled.List, "Fejezetek", tint = colors.text) }
                    IconButton(enabled = chapter != null, onClick = {
                        if (ttsActive) stopTts() else startTts((listState.firstVisibleItemIndex - 1).coerceAtLeast(0))
                    }) { Icon(Icons.Default.Headphones, if (ttsActive) "Felolvasás leállítása" else "Felolvasás", tint = if (ttsActive) colors.accent else colors.text) }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.TextFields, "Olvasási beállítások", tint = colors.text) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Továbbiak", tint = colors.text) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (focus) "Kilépés a fókusz módból" else "Fókusz mód („Ne zavarj”)") },
                                leadingIcon = { Icon(if (focus) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, null) },
                                onClick = {
                                    menu = false
                                    focus = !focus
                                    if (focus) { chrome = false; snackbar.notify("Fókusz mód: koppints a szövegre a vezérlőkért.") }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Jegyzeteim ebben a könyvben (${notes.size})") },
                                leadingIcon = { Icon(Icons.Default.EditNote, null) },
                                onClick = { menu = false; showNotes = true },
                            )
                        }
                    }
                }
            }
            val progress by remember(blocks) { androidx.compose.runtime.derivedStateOf { if (blocks.isEmpty()) 0f else (listState.firstVisibleItemIndex.toFloat() / (blocks.size + 1)).coerceIn(0f, 1f) } }
            LinearProgressIndicator(progress = { ((index.coerceAtLeast(0) + progress) / totalChapters).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(2.dp), color = colors.accent, trackColor = colors.muted.copy(alpha = 0.2f))

            Box(Modifier.weight(1f)) {
                val mErr = metaError
                val cErr = chapterError
                val ch = chapter
                when {
                    mErr != null -> ErrorState(mErr) { retry++ }
                    cErr != null -> ErrorState(cErr) { retry++ }
                    ch == null -> LoadingBox()
                    twoColumns -> TwoColumnChapter(ch, blocks, bodyStyle, colors, if (focus) emptyMap() else commentCounts, chapterNotes, if (ttsActive) ttsBlock else -1, !focus, onParagraphTap, onParagraphLongPress)
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        item {
                            ChapterHeader(ch, colors)
                        }
                        items(blocks.size) { i ->
                            Box(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                                BlockView(
                                    blocks[i], bodyStyle, colors, if (focus) emptyMap() else commentCounts,
                                    note = chapterNotes[blocks[i].paragraphIndex], speaking = ttsActive && ttsBlock == i,
                                    showMarkers = !focus, onTap = onParagraphTap, onLongPress = onParagraphLongPress,
                                )
                            }
                        }
                        item {
                            Box(Modifier.widthIn(max = 720.dp)) {
                                ChapterEnd(
                                    colors = colors,
                                    likes = likes,
                                    commentCount = comments.visibleCount(),
                                    isLast = !hasNext,
                                    isMine = meta?.book?.authorId == me,
                                    isOfficial = AppGraph.official.isOfficial(meta?.book?.id),
                                    canTip = !meta?.author?.paypalMe.isNullOrBlank() && meta?.book?.authorId != me,
                                    authorName = meta?.author?.displayName ?: meta?.book?.penName ?: "",
                                    onLike = { toggleLike() },
                                    onComments = { sheetParagraph = -1 },
                                    onTip = { showTip = true },
                                    onNext = { if (hasNext) index++ },
                                    onFinish = { meta?.book?.let { nav.replace(Route.BookDetail(it.id)) } },
                                )
                            }
                        }
                    }
                }
            }

            // Felolvasás vezérlője
            if (ttsActive) {
                HorizontalDivider(color = colors.muted.copy(alpha = 0.25f))
                Row(Modifier.fillMaxWidth().background(colors.accent.copy(alpha = 0.08f)).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Headphones, null, tint = colors.accent, modifier = Modifier.padding(horizontal = 8.dp).size(18.dp))
                    Text(if (ttsPlaying) "Felolvasás…" else "Szünet", style = MaterialTheme.typography.labelLarge, color = colors.text, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (ttsBlock > 0) { readAloud?.stop(); speakAt((ttsBlock - 1 downTo 0).firstOrNull { blockText(blocks[it]) != null } ?: 0) } }) {
                        Icon(Icons.Default.SkipPrevious, "Előző bekezdés", tint = colors.text)
                    }
                    IconButton(onClick = {
                        if (ttsPlaying) { readAloud?.stop(); ttsPlaying = false }
                        else speakAt(if (ttsBlock >= 0) ttsBlock else (listState.firstVisibleItemIndex - 1).coerceAtLeast(0))
                    }) { Icon(if (ttsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (ttsPlaying) "Szünet" else "Folytatás", tint = colors.accent) }
                    IconButton(onClick = { readAloud?.stop(); speakAt(ttsBlock + 1) }) { Icon(Icons.Default.SkipNext, "Következő bekezdés", tint = colors.text) }
                    TextButton(onClick = {
                        val next = ttsRates[(ttsRates.indexOfFirst { it == rs.ttsRate }.coerceAtLeast(0) + 1) % ttsRates.size]
                        scope.launch {
                            AppGraph.settings.setReader(rs.copy(ttsRate = next))
                            if (ttsPlaying && ttsBlock >= 0) readAloud?.speak("b$ttsBlock-${chapter?.id}", ReadAloud.speakable(blockText(blocks[ttsBlock]) ?: ""), next)
                        }
                    }) { Text("${"%.2f".format(rs.ttsRate).trimEnd('0').trimEnd('.', ',')}×", color = colors.text) }
                    IconButton(onClick = { stopTts() }) { Icon(Icons.Default.Close, "Felolvasás leállítása", tint = colors.text) }
                }
            }

            if (showChrome) {
                // Alsó sáv: előző / kedvelés / kommentek / következő
                HorizontalDivider(color = colors.muted.copy(alpha = 0.25f))
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (index > 0) index-- }, enabled = index > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = if (index > 0) colors.text else colors.muted)
                        Text("Előző", color = if (index > 0) colors.text else colors.muted)
                    }
                    Spacer(Modifier.weight(1f))
                    val ownBook = meta?.book?.authorId == me
                    IconButton(enabled = !ownBook && currentMeta != null, onClick = { toggleLike() }) {
                        Icon(
                            if (likes.second) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            if (ownBook) "Kedvelések (a saját fejezetedet nem kedvelheted)" else "Fejezet kedvelése",
                            tint = if (ownBook) colors.muted else colors.accent,
                        )
                    }
                    Text("${likes.first}", style = MaterialTheme.typography.labelMedium, color = colors.muted)
                    Spacer(Modifier.width(12.dp))
                    IconButton(onClick = { sheetParagraph = -1 }) { Icon(Icons.AutoMirrored.Filled.Comment, "Kommentek", tint = colors.text) }
                    Text("${comments.visibleCount()}", style = MaterialTheme.typography.labelMedium, color = colors.muted)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { if (hasNext) index++ }, enabled = hasNext) {
                        Text("Következő", color = if (hasNext) colors.text else colors.muted)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = if (hasNext) colors.text else colors.muted)
                    }
                }
            }
        }
    }

    val sheetP = sheetParagraph
    if (sheetP != null && currentMeta != null) {
        CommentsSheet(
            chapterId = currentMeta.id,
            paragraphIndex = sheetP,
            paragraphText = blocks.firstOrNull { it.paragraphIndex == sheetP }?.let { blockText(it) },
            comments = if (sheetP >= 0) comments.filter { it.comment.paragraphIndex == sheetP } else comments,
            bookAuthorId = meta?.book?.authorId,
            onChanged = { scope.launch { comments = runCatching { AppGraph.social.comments(currentMeta.id) }.orReport("ReaderScreen", comments) } },
            onOpenProfile = { sheetParagraph = null; nav.push(Route.Profile(it)) },
            onDismiss = { sheetParagraph = null },
        )
    }
    val actionP = actionParagraph
    val mb = meta
    if (actionP != null && currentMeta != null && mb != null) {
        val text = blocks.firstOrNull { it.paragraphIndex == actionP }?.let { blockText(it) } ?: ""
        val existing = chapterNotes[actionP]
        ParagraphSheet(
            quote = text,
            existing = existing,
            commentCount = commentCounts[actionP] ?: 0,
            onSave = { noteText, color ->
                val base = existing ?: ReaderNote(
                    bookId = bookId, chapterId = currentMeta.id, paragraph = actionP, chapterTitle = currentMeta.title,
                    bookTitle = mb.book.title, quote = text.take(600), note = "", color = null, updatedAt = 0,
                )
                scope.runAction(snackbar, if (noteText.isBlank() && color == null) "Jegyzet törölve." else "Elmentve – csak te látod.") {
                    val saved = AppGraph.reading.saveNote(base.copy(note = noteText, color = color, quote = text.take(600), chapterTitle = currentMeta.title))
                    notes = if (saved == null) notes - base.key else notes + (saved.key to saved)
                }
                actionParagraph = null
            },
            onComments = { actionParagraph = null; sheetParagraph = actionP },
            onReadFromHere = {
                actionParagraph = null
                val bi = blocks.indexOfFirst { it.paragraphIndex == actionP }
                if (ttsActive) readAloud?.stop()
                startTts(if (bi >= 0) bi else 0)
            },
            onCopy = {
                runCatching {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Regélő", ReadAloud.speakable(text)))
                }
                actionParagraph = null
                snackbar.notify("Szöveg másolva.")
            },
            onDismiss = { actionParagraph = null },
        )
    }
    if (showNotes && mb != null) {
        val order = mb.chapters.withIndex().associate { it.value.id to it.index }
        BookNotesSheet(
            notes = notes.values.sortedWith(compareBy({ order[it.chapterId] ?: Int.MAX_VALUE }, { it.paragraph })),
            onOpen = { n -> showNotes = false; jumpTo(n.chapterId, n.paragraph) },
            onDismiss = { showNotes = false },
        )
    }
    if (showSettings) ReaderSettingsSheet(rs, onChange = { scope.launch { AppGraph.settings.setReader(it) } }, onDismiss = { showSettings = false })
    if (showChapters && mb != null) {
        ModalBottomSheet(onDismissRequest = { showChapters = false }) {
            Text("Fejezetek", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                items(mb.chapters.size) { i ->
                    val c = mb.chapters[i]
                    Row(
                        Modifier.fillMaxWidth().clickable { index = i; showChapters = false }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}.", modifier = Modifier.width(36.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text(c.title, fontWeight = if (i == index) FontWeight.Bold else FontWeight.Normal, color = if (i == index) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface)
                            Text(readingTimeLabel(c.wordCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (notes.values.any { it.chapterId == c.id }) Icon(Icons.Default.EditNote, "Van jegyzeted", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
    if (showTip && mb != null) TipDialog(mb.author?.displayName ?: "", mb.author?.paypalMe ?: "") { showTip = false }
    if (ttsNoVoice) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ttsNoVoice = false },
            title = { Text("Nincs magyar felolvasóhang") },
            text = { Text("A telefon felolvasója nem tud magyarul, ezért idegen kiejtéssel olvas. Magyar hangot a rendszerbeállításokban telepíthetsz (Szövegfelolvasás › pl. Google beszédszolgáltatások › Hangadatok › Magyar).") },
            confirmButton = {
                TextButton(onClick = {
                    ttsNoVoice = false
                    runCatching { context.startActivity(ReadAloud.settingsIntent()) }.onFailure { snackbar.notify("A beállítás nem nyitható meg ezen a telefonon.") }
                }) { Text("Beállítások") }
            },
            dismissButton = { TextButton(onClick = { ttsNoVoice = false }) { Text("Így is jó") } },
        )
    }
}

private fun blockText(b: Block): String? = when (b) {
    is Block.Paragraph -> b.text
    is Block.Heading -> b.text
    is Block.Quote -> b.text
    is Block.Bullet -> b.text
    else -> null
}

@Composable
private fun ChapterHeader(chapter: ChapterDto, colors: ReaderColors) {
    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(top = 16.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(chapter.title, fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, color = colors.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("${readingTimeLabel(chapter.wordCount)} olvasás · frissítve ${relativeTime(chapter.updatedAt)}", style = MaterialTheme.typography.labelMedium, color = colors.muted)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.width(40.dp).height(2.dp).background(colors.accent))
    }
}

private fun highlightBg(color: HighlightColor, colors: ReaderColors): Color = Color(color.argb).copy(alpha = if (colors.dark) 0.28f else 0.42f)

@Composable
private fun BlockView(
    block: Block,
    style: TextStyle,
    colors: ReaderColors,
    counts: Map<Int, Int>,
    note: ReaderNote?,
    speaking: Boolean,
    showMarkers: Boolean,
    onTap: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
) {
    val onLink = LocalReaderLink.current
    val linkColor = colors.accent
    when (block) {
        is Block.Image -> AsyncImage(
            model = block.url, contentDescription = block.alt,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(RoundedCornerShape(6.dp)),
        )
        Block.SceneBreak -> Text("⁂", color = colors.muted, textAlign = TextAlign.Center, fontSize = 20.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
        else -> {
            val p = block.paragraphIndex
            val count = counts[p] ?: 0
            val bg = when {
                speaking -> colors.accent.copy(alpha = 0.14f)
                note?.color != null -> highlightBg(note.color, colors)
                else -> Color.Transparent
            }
            Row(
                Modifier.fillMaxWidth()
                    .combinedClickable(onClick = { onTap(p) }, onLongClick = { onLongPress(p) }, onLongClickLabel = "Kiemelés, jegyzet, felolvasás")
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.Top,
            ) {
                val text = blockText(block) ?: ""
                Box(Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = if (bg != Color.Transparent) 4.dp else 0.dp, vertical = 3.dp)) {
                    when (block) {
                        is Block.Heading -> Text(inlineMarkdown(text, linkColor, onLink), style = style.copy(fontWeight = FontWeight.SemiBold, fontSize = style.fontSize * (if (block.level == 1) 1.3f else 1.15f)))
                        is Block.Quote -> Row {
                            Box(Modifier.width(3.dp).height(24.dp).background(colors.accent))
                            Spacer(Modifier.width(12.dp))
                            Text(inlineMarkdown(text, linkColor, onLink), style = style.copy(fontStyle = FontStyle.Italic))
                        }
                        is Block.Bullet -> Text(inlineMarkdown("•  $text", linkColor, onLink), style = style)
                        else -> Text(inlineMarkdown(text, linkColor, onLink), style = style)
                    }
                }
                Column(Modifier.width(34.dp).padding(start = 6.dp, top = 4.dp), horizontalAlignment = Alignment.End) {
                    if (note != null && note.note.isNotBlank()) {
                        Icon(Icons.Default.EditNote, "Saját jegyzet", tint = colors.accent, modifier = Modifier.size(18.dp).clickable { onLongPress(p) })
                        Spacer(Modifier.height(4.dp))
                    }
                    if (showMarkers) {
                        if (count > 0) {
                            Surface(color = colors.accent.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp)) {
                                Text("$count", color = colors.accent, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Comment, "Komment ehhez a bekezdéshez", tint = colors.muted.copy(alpha = 0.25f), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TwoColumnChapter(
    chapter: ChapterDto,
    blocks: List<Block>,
    style: TextStyle,
    colors: ReaderColors,
    counts: Map<Int, Int>,
    notes: Map<Int, ReaderNote>,
    speakingBlock: Int,
    showMarkers: Boolean,
    onTap: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
) {
    // Kétoszlopos „könyv mód” tableten: a szöveg két hasábba rendeződik, mint egy nyitott könyvben.
    val weights = blocks.map { (blockText(it)?.length ?: 300) + 80 }
    val half = weights.sum() / 2
    var acc = 0
    val split = weights.indexOfFirst { acc += it; acc >= half }.let { if (it < 0) blocks.size else it + 1 }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 16.dp)) {
        ChapterHeader(chapter, colors)
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Column(Modifier.weight(1f)) {
                blocks.take(split).forEachIndexed { i, b -> BlockView(b, style, colors, counts, notes[b.paragraphIndex], speakingBlock == i, showMarkers, onTap, onLongPress) }
            }
            Box(Modifier.width(1.dp).height(40.dp).background(colors.muted.copy(alpha = 0.2f)))
            Column(Modifier.weight(1f)) {
                blocks.drop(split).forEachIndexed { i, b -> BlockView(b, style, colors, counts, notes[b.paragraphIndex], speakingBlock == split + i, showMarkers, onTap, onLongPress) }
            }
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun ChapterEnd(
    colors: ReaderColors,
    likes: Pair<Int, Boolean>,
    commentCount: Int,
    isLast: Boolean,
    isMine: Boolean,
    canTip: Boolean,
    isOfficial: Boolean = false,
    authorName: String,
    onLike: () -> Unit,
    onComments: () -> Unit,
    onTip: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⁂", color = colors.muted, fontSize = 22.sp)
        Spacer(Modifier.height(16.dp))
        if (!isMine) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onLike) {
                    Icon(if (likes.second) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = colors.accent)
                    Spacer(Modifier.width(6.dp)); Text(if (likes.second) "Tetszett (${likes.first})" else "Tetszett", color = colors.text)
                }
                OutlinedButton(onClick = onComments) {
                    Icon(Icons.AutoMirrored.Filled.Comment, null, tint = colors.text)
                    Spacer(Modifier.width(6.dp)); Text("Kommentek ($commentCount)", color = colors.text)
                }
            }
        }
        if (canTip) {
            Spacer(Modifier.height(20.dp))
            Card(colors = CardDefaults.cardColors(containerColor = colors.accent.copy(alpha = 0.10f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tetszett ez a fejezet?", style = MaterialTheme.typography.titleMedium, color = colors.text)
                    Text("Önkéntes borravalóval támogathatod $authorName munkáját. Semmi nem függ tőle – minden fejezet ingyenes marad.", style = MaterialTheme.typography.bodySmall, color = colors.muted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onTip) { Text("☕ Hívd meg egy kávéra", color = colors.accent) }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (isLast) {
            Text(if (isOfficial) "Ez a legfrissebb rész – hamarosan jön a következő." else "Elérted a történet végét (egyelőre).", color = colors.muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onFinish) { Text(when { isMine -> "Vissza a könyvhöz"; isOfficial -> "Értesítés az új részekről"; else -> "Értékelés és követés" }) }
        } else {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Következő fejezet") }
        }
        Spacer(Modifier.height(16.dp))
        Text("Tipp: hosszan nyomva egy bekezdést kiemelheted, jegyzetet írhatsz hozzá, vagy onnan felolvastathatod.",
            style = MaterialTheme.typography.labelSmall, color = colors.muted, textAlign = TextAlign.Center)
    }
}

/** Bekezdés-műveletek (hosszú nyomásra): kiemelés, saját jegyzet, kommentek, felolvasás innen, másolás. */
@Composable
private fun ParagraphSheet(
    quote: String,
    existing: ReaderNote?,
    commentCount: Int,
    onSave: (String, HighlightColor?) -> Unit,
    onComments: () -> Unit,
    onReadFromHere: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    var color by remember { mutableStateOf(existing?.color) }
    var text by remember { mutableStateOf(existing?.note ?: "") }
    val changed = color != existing?.color || text.trim() != (existing?.note ?: "")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                "„${ReadAloud.speakable(quote).take(220)}${if (quote.length > 220) "…" else ""}”",
                fontFamily = Lora, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text("Kiemelés", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                HighlightColor.entries.forEach { c ->
                    val selected = color == c
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color(c.argb))
                            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { color = if (selected) null else c },
                        contentAlignment = Alignment.Center,
                    ) { if (selected) Icon(Icons.Default.Check, c.label, tint = Color(0xFF23262D), modifier = Modifier.size(18.dp)) }
                }
                if (color != null) TextButton(onClick = { color = null }) { Text("Nincs") }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text, onValueChange = { if (it.length <= 4000) text = it },
                label = { Text("Saját jegyzet (csak te látod)") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 3, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onSave(text.trim(), color) }, enabled = changed) { Text("Mentés") }
                if (existing != null) TextButton(onClick = { onSave("", null) }) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Jegyzet és kiemelés törlése")
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Kommentek ehhez a bekezdéshez ($commentCount)") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Comment, null) },
                modifier = Modifier.clickable(onClick = onComments),
            )
            ListItem(
                headlineContent = { Text("Felolvasás innen") },
                leadingContent = { Icon(Icons.Default.Headphones, null) },
                modifier = Modifier.clickable(onClick = onReadFromHere),
            )
            ListItem(
                headlineContent = { Text("Szöveg másolása") },
                leadingContent = { Icon(Icons.Default.ContentCopy, null) },
                modifier = Modifier.clickable(onClick = onCopy),
            )
        }
    }
}

@Composable
private fun BookNotesSheet(notes: List<ReaderNote>, onOpen: (ReaderNote) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Jegyzeteim és kiemeléseim", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        if (notes.isEmpty()) {
            Text("Még nincs jegyzeted ebben a könyvben. Nyomj hosszan egy bekezdésre a kiemeléshez vagy egy jegyzethez.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp))
        } else {
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                items(notes, key = { it.key }) { n -> NoteRow(n, onClick = { onOpen(n) }) }
            }
        }
    }
}

/** Egy jegyzet sora (a könyv jegyzetei között és a Jegyzeteim oldalon is). */
@Composable
fun NoteRow(n: ReaderNote, showBook: Boolean = false, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(4.dp).height(44.dp).clip(RoundedCornerShape(2.dp)).background(n.color?.let { Color(it.argb) } ?: MaterialTheme.colorScheme.outlineVariant))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (showBook) "${n.bookTitle} · ${n.chapterTitle}" else n.chapterTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("„${ReadAloud.speakable(n.quote).take(160)}${if (n.quote.length > 160) "…" else ""}”", fontFamily = Lora, fontStyle = FontStyle.Italic,
                style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (n.note.isNotBlank()) Text(n.note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
            Text(relativeTime(n.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
private fun ReaderSettingsSheet(current: ReaderSettings, onChange: (ReaderSettings) -> Unit, onDismiss: () -> Unit) {
    var s by remember { mutableStateOf(current) }
    fun update(n: ReaderSettings) { s = n; onChange(n) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Olvasási beállítások", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Text("Betűméret: ${s.fontSize.toInt()}", style = MaterialTheme.typography.labelLarge)
            Slider(value = s.fontSize, onValueChange = { update(s.copy(fontSize = it)) }, valueRange = 14f..30f, steps = 15)
            Text("Sortávolság: ${"%.1f".format(s.lineHeight)}", style = MaterialTheme.typography.labelLarge)
            Slider(value = s.lineHeight, onValueChange = { update(s.copy(lineHeight = it)) }, valueRange = 1.2f..2.2f, steps = 9)
            Spacer(Modifier.height(8.dp))
            Text("Téma", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTheme.entries.forEach { t -> FilterChip(selected = s.theme == t, onClick = { update(s.copy(theme = t)) }, label = { Text(t.label) }) }
            }
            Spacer(Modifier.height(8.dp))
            Text("Betűtípus", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderFont.entries.forEach { f -> FilterChip(selected = s.font == f, onClick = { update(s.copy(font = f)) }, label = { Text(f.label) }) }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sorkizárt szöveg", modifier = Modifier.weight(1f))
                Switch(checked = s.justify, onCheckedChange = { update(s.copy(justify = it)) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Kétoszlopos könyv mód")
                    Text("Tableten és nagy kijelzőn", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = s.twoColumns, onCheckedChange = { update(s.copy(twoColumns = it)) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Kijelző ébren tartása")
                    Text("Olvasás közben nem sötétül el a képernyő", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = s.keepScreenOn, onCheckedChange = { update(s.copy(keepScreenOn = it)) })
            }
        }
    }
}

/** Bekezdés- vagy fejezetkommentek: olvasás, írás, válasz, törlés, jelentés, letiltás. */
@Composable
private fun CommentsSheet(
    chapterId: String,
    paragraphIndex: Int,
    paragraphText: String?,
    comments: List<CommentItem>,
    bookAuthorId: String?,
    onChanged: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val me = AppGraph.auth.userId
    var text by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<CommentItem?>(null) }
    var sending by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<CommentItem?>(null) }
    var blockTarget by remember { mutableStateOf<CommentItem?>(null) }
    var deleteTarget by remember { mutableStateOf<CommentItem?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (paragraphIndex >= 0) "Kommentek a bekezdéshez" else "A fejezet kommentjei", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Bezárás") }
            }
            if (paragraphText != null) {
                Text(
                    "„${paragraphText.take(220)}${if (paragraphText.length > 220) "…" else ""}”",
                    fontFamily = Lora, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth().height(380.dp)) {
                if (comments.isEmpty()) item {
                    Text("Még nincs komment. Írd meg elsőként, mit gondolsz!", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                }
                items(comments, key = { it.comment.id }) { item ->
                    CommentRow(item, me, bookAuthorId, onReply = { replyTo = item }, onDelete = { deleteTarget = item },
                        onReport = { reportTarget = item }, onBlock = { blockTarget = item }, onOpenProfile = onOpenProfile)
                    item.replies.forEach { r ->
                        Box(Modifier.padding(start = 40.dp)) {
                            CommentRow(r, me, bookAuthorId, onReply = { replyTo = item }, onDelete = { deleteTarget = r },
                                onReport = { reportTarget = r }, onBlock = { blockTarget = r }, onOpenProfile = onOpenProfile)
                        }
                    }
                }
            }
            HorizontalDivider()
            replyTo?.let { r ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Válasz neki: ${r.author?.displayName ?: "Olvasó"}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { replyTo = null }) { Icon(Icons.Default.Close, "Válasz megszakítása", Modifier.size(18.dp)) }
                }
            }
            Row(Modifier.padding(12.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text, onValueChange = { if (it.length <= 5000) text = it },
                    placeholder = { Text("Írj egy kommentet…") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 5, modifier = Modifier.weight(1f),
                )
                IconButton(
                    enabled = text.isNotBlank() && !sending,
                    onClick = {
                        val body = text
                        val parent = replyTo
                        sending = true
                        scope.runAction(snackbar, onError = { sending = false }) {
                            AppGraph.social.addComment(chapterId, if (paragraphIndex >= 0) paragraphIndex else (parent?.comment?.paragraphIndex ?: 0), body, parent?.comment?.id)
                            text = ""; replyTo = null; sending = false
                            onChanged()
                        }
                    },
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Küldés", tint = MaterialTheme.colorScheme.secondary) }
            }
        }
    }
    reportTarget?.let { t ->
        ReportDialog(title = "Komment jelentése", onSubmit = { reason, note ->
            scope.runAction(snackbar, "Köszönjük, a moderátorok átnézik.") { AppGraph.social.reportComment(t.comment.id, t.comment.authorId, "$reason. $note".trim()) }
        }, onDismiss = { reportTarget = null })
    }
    deleteTarget?.let { t ->
        ConfirmDialog(
            title = "Komment törlése", text = "Törlöd ezt a kommentet? A rá érkezett válaszok megmaradnak.", confirm = "Törlés", destructive = true,
            onConfirm = { scope.runAction(snackbar, "Komment törölve.") { AppGraph.social.deleteComment(t.comment.id); onChanged() } },
            onDismiss = { deleteTarget = null },
        )
    }
    blockTarget?.let { t ->
        ConfirmDialog(
            title = "Letiltod?", text = "${t.author?.displayName ?: "Ez a felhasználó"} kommentjei és üzenetei többé nem jelennek meg neked, és nem írhat neked.",
            confirm = "Letiltás", destructive = true,
            onConfirm = { scope.runAction(snackbar, "Letiltva.") { AppGraph.social.block(t.comment.authorId); onChanged() } },
            onDismiss = { blockTarget = null },
        )
    }
}

@Composable
private fun CommentRow(
    item: CommentItem,
    me: String?,
    bookAuthorId: String?,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val c = item.comment
    val deleted = c.deletedAt != null
    // Helyőrző egy törölt kommenthez, amelyre még látható válaszok érkeztek (szerzője nem ismert).
    val placeholder = c.authorId.isBlank()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(if (placeholder) Modifier else Modifier.clickable { onOpenProfile(c.authorId) }) {
            Avatar(item.author?.avatarUrl, item.author?.displayName ?: "?", size = 32.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (!placeholder) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.author?.displayName ?: "Olvasó", style = MaterialTheme.typography.titleSmall)
                hu.regelo.app.ui.components.OfficialBadge(c.authorId, size = 14.dp)
                if (c.authorId == bookAuthorId) {
                    Spacer(Modifier.width(6.dp))
                    Text("szerző", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                Spacer(Modifier.width(8.dp))
                Text(relativeTime(c.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (deleted) "(törölt komment)" else c.body, style = MaterialTheme.typography.bodyMedium, fontStyle = if (deleted) FontStyle.Italic else FontStyle.Normal)
            if (!deleted) Row {
                TextButton(onClick = onReply, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Válasz", style = MaterialTheme.typography.labelMedium) }
                if (c.authorId == me) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Delete, "Törlés", Modifier.size(18.dp)) }
                } else {
                    IconButton(onClick = onReport, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Flag, "Jelentés", Modifier.size(18.dp)) }
                    IconButton(onClick = onBlock, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Block, "Letiltás", Modifier.size(18.dp)) }
                }
            }
        }
    }
}
