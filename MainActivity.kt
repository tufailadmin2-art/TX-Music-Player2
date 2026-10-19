package com.txmusic.player

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

private val Ink = Color(0xFF101820)
private val Panel = Color(0xFF1A2730)
private val Teal = Color(0xFF18B7A0)
private val TextMain = Color(0xFFF4F7FA)
private val TextMuted = Color(0xFF9BAAB4)

data class Song(val id: Long, val title: String, val artist: String, val album: String, val duration: Long, val uri: Uri)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller by mutableStateOf<MediaController?>(null)
    private var songs by mutableStateOf<List<Song>>(emptyList())
    private var permissionGranted by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (granted) loadSongs()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
        }, mainExecutor)

        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        permissionGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        if (permissionGranted) loadSongs() else permissionLauncher.launch(permission)

        setContent {
            var isDark by remember { mutableStateOf(true) }
            MaterialTheme(
                colorScheme = if (isDark) darkColorScheme(
                    primary = Teal, background = Ink, surface = Panel,
                    onPrimary = Ink, onBackground = TextMain, onSurface = TextMain
                ) else lightColorScheme(primary = Teal)
            ) {
                MusicHome(
                    songs = songs,
                    controller = controller,
                    permissionGranted = permissionGranted,
                    dark = isDark,
                    onTheme = { isDark = !isDark },
                    onGrant = { permissionLauncher.launch(permission) },
                    onRefresh = { loadSongs() }
                )
            }
        }
    }

    private fun loadSongs() {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val found = mutableListOf<Song>()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        contentResolver.query(collection, projection, selection, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                found += Song(
                    id = id,
                    title = cursor.getString(titleCol) ?: "Unknown title",
                    artist = cursor.getString(artistCol) ?: "Unknown artist",
                    album = cursor.getString(albumCol) ?: "Unknown album",
                    duration = cursor.getLong(durationCol),
                    uri = Uri.withAppendedPath(collection, id.toString())
                )
            }
        }
        songs = found
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}

@Composable
private fun MusicHome(
    songs: List<Song>,
    controller: MediaController?,
    permissionGranted: Boolean,
    dark: Boolean,
    onTheme: () -> Unit,
    onGrant: () -> Unit,
    onRefresh: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("Songs") }
    var currentTitle by remember { mutableStateOf("Choose a song") }
    var playing by remember { mutableStateOf(false) }
    var favoriteIds by remember { mutableStateOf(setOf<Long>()) }
    var showPlayer by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(0f) }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentTitle = mediaItem?.mediaMetadata?.title?.toString() ?: "TX Music Player"
            }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }

    val filtered = songs.filter {
        (it.title + " " + it.artist + " " + it.album).contains(query, ignoreCase = true)
    }.let { list ->
        when (tab) {
            "Favorites" -> list.filter { it.id in favoriteIds }
            else -> list
        }
    }

    Surface(Modifier.fillMaxSize(), color = if (dark) Ink else Color(0xFFF3F6F8)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Teal),
                    contentAlignment = Alignment.Center
                ) {
                    Text("TX", color = Ink, fontWeight = FontWeight.Black, fontSize = 21.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("TX Music Player", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("YOUR MUSIC. YOUR MOMENT.", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh library") }
                IconButton(onClick = onTheme) { Icon(Icons.Default.Contrast, "Toggle theme") }
            }

            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search songs or artists") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Songs", "Albums", "Artists", "Favorites").forEach { label ->
                    FilterChip(
                        selected = tab == label,
                        onClick = { tab = label },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (!permissionGranted) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LibraryMusic, null, tint = Teal, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Allow audio access to scan your music")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onGrant) { Text("Grant permission") }
                    }
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MusicOff, null, tint = Teal, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(if (songs.isEmpty()) "No music found on this device" else "No matching songs")
                        Text("Add audio files to your phone, then refresh.", color = TextMuted, fontSize = 12.sp)
                    }
                }
            } else {
                Text("${filtered.size} TRACKS", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(vertical = 10.dp))
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(filtered, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            favorite = song.id in favoriteIds,
                            onFavorite = {
                                favoriteIds = if (song.id in favoriteIds) favoriteIds - song.id else favoriteIds + song.id
                            },
                            onClick = {
                                val c = controller
                                if (c != null) {
                                    val index = songs.indexOfFirst { it.id == song.id }
                                    c.setMediaItems(songs.map { item ->
                                        MediaItem.Builder().setUri(item.uri)
                                            .setMediaMetadata(
                                                androidx.media3.common.MediaMetadata.Builder()
                                                    .setTitle(item.title).setArtist(item.artist).setAlbumTitle(item.album).build()
                                            ).build()
                                    }, index.coerceAtLeast(0), 0L)
                                    c.prepare()
                                    c.play()
                                    currentTitle = song.title
                                    showPlayer = true
                                }
                            }
                        )
                    }
                }
            }

            if (controller?.currentMediaItem != null || currentTitle != "Choose a song") {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                        .background(Panel).clickable { showPlayer = !showPlayer }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Teal), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.GraphicEq, null, tint = Ink)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(currentTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text("TX MUSIC PLAYER", color = TextMuted, fontSize = 10.sp)
                    }
                    IconButton(onClick = { if (controller?.isPlaying == true) controller.pause() else controller?.play() }) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Teal)
                    }
                    IconButton(onClick = { controller?.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, null) }
                }
            }

            AnimatedVisibility(visible = showPlayer) {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(180.dp).clip(RoundedCornerShape(28.dp)).background(Teal), contentAlignment = Alignment.Center) {
                        Text("TX", color = Ink, fontSize = 64.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(currentTitle, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Local audio", color = TextMuted)
                    Slider(
                        value = position,
                        onValueChange = { position = it },
                        onValueChangeFinished = { controller?.let { c -> c.seekTo((c.duration.coerceAtLeast(0) * position).toLong()) } },
                        valueRange = 0f..1f
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = { shuffle = !shuffle; controller?.shuffleModeEnabled = shuffle }) {
                            Icon(Icons.Default.Shuffle, null, tint = if (shuffle) Teal else TextMuted)
                        }
                        IconButton(onClick = { controller?.seekToPreviousMediaItem() }) {
                            Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(38.dp))
                        }
                        FilledIconButton(onClick = { if (controller?.isPlaying == true) controller.pause() else controller?.play() },
                            modifier = Modifier.size(64.dp)) {
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(36.dp))
                        }
                        IconButton(onClick = { controller?.seekToNextMediaItem() }) {
                            Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(38.dp))
                        }
                        IconButton(onClick = {
                            repeat = !repeat
                            controller?.repeatMode = if (repeat) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                        }) { Icon(Icons.Default.Repeat, null, tint = if (repeat) Teal else TextMuted) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(Panel), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = Teal, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatDuration(song.duration), color = TextMuted, fontSize = 11.sp)
        IconButton(onClick = onFavorite) {
            Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (favorite) Teal else TextMuted)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
