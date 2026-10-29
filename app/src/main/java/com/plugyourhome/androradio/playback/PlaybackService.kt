package com.plugyourhome.androradio.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.cast.CastPlayer
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.plugyourhome.androradio.AndroRadioApp
import com.plugyourhome.androradio.MainActivity
import com.plugyourhome.androradio.data.RadioBrowserApi
import com.plugyourhome.androradio.data.Station
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

/**
 * Service de lecture : ExoPlayer + MediaSession.
 * Il fournit gratuitement la notification, l'écran verrouillé, les boutons Bluetooth,
 * la gestion du focus audio et la reprise de la dernière station.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private var localPlayer: ExoPlayer? = null

    private val app get() = application as AndroRadioApp
    private val store get() = app.store

    override fun onCreate() {
        super.onCreate()

        val httpFactory = OkHttpDataSource.Factory(RadioBrowserApi.defaultClient())
            .setUserAgent(RadioBrowserApi.USER_AGENT)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause si le casque est débranché
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(RetryListener(player))
        localPlayer = player

        // Google Cast : CastPlayer bascule tout seul entre ce lecteur local et un appareil Cast
        // (choisi dans l'appli ou dans le panneau de sortie d'Android). Sans services Google, lecture locale seule.
        val sessionPlayer: Player = runCatching { CastPlayer.Builder(this).setLocalPlayer(player).build() }
            .getOrElse { player }
        sessionPlayer.repeatMode = Player.REPEAT_MODE_ALL // ⏭ après la dernière favorite revient à la première

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, NowPlayingPlayer(sessionPlayer))
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        val casting = player?.deviceInfo?.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
        if (player == null || (!casting && (!player.playWhenReady || player.mediaItemCount == 0))) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        localPlayer?.release() // au cas où CastPlayer ne libère pas le lecteur local
        localPlayer = null
        mediaSession = null
        super.onDestroy()
    }

    /**
     * Métadonnées présentées à la notification et à l'appli : le morceau en cours en titre,
     * le nom de la station en artiste. Sans morceau connu, le nom de la station en titre.
     */
    private class NowPlayingPlayer(player: Player) : ForwardingPlayer(player) {

        // Radio en direct : ⏮ ⏭ changent toujours de station (jamais « retour au début »).
        override fun seekToNext() = seekToNextMediaItem()
        override fun seekToPrevious() = seekToPreviousMediaItem()

        // Pas de durée ni de position exploitable : pas de barre 00:00.
        override fun isCurrentMediaItemSeekable() = false
        override fun getDuration() = C.TIME_UNSET
        override fun getContentDuration() = C.TIME_UNSET

        override fun getMediaMetadata(): MediaMetadata {
            val metadata = super.getMediaMetadata()
            val station = currentMediaItem?.mediaMetadata?.station ?: metadata.station ?: return metadata
            val builder = metadata.buildUpon()
            if (metadata.title.isNullOrBlank()) builder.setTitle(station)
            if (metadata.artist.isNullOrBlank()) builder.setArtist(station)
            return builder.build()
        }
    }

    /** Reconnexion automatique (3 essais) quand un flux décroche. */
    private inner class RetryListener(private val player: Player) : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) retryCount = 0
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            retryCount = 0
            handler.removeCallbacksAndMessages(null)
            // Changement par ⏮ ⏭ (notification, casque, voiture) : la station rejoint l'historique.
            // Radio Browser demande de signaler chaque écoute lancée (compteur de clics).
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                Station.fromMetadata(mediaItem?.mediaMetadata)?.let { station ->
                    store.addRecent(station)
                    serviceScope.launch { app.api.registerClick(station.uuid) }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player.seekToDefaultPosition()
                player.prepare()
                return
            }
            if (retryCount >= MAX_RETRIES || player.mediaItemCount == 0) return
            retryCount++
            handler.postDelayed({
                if (player.playerError != null) {
                    player.prepare()
                    player.play()
                }
            }, RETRY_DELAY_MS * retryCount)
        }
    }

    private inner class SessionCallback : MediaSession.Callback {

        /** Retire les commandes de déplacement dans le flux : la barre de progression disparaît. */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(
                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .removeAll(
                            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                            Player.COMMAND_SEEK_BACK,
                            Player.COMMAND_SEEK_FORWARD,
                        )
                        .build()
                )
                .build()

        /**
         * Lancer une station charge en fait la liste des favoris autour d'elle,
         * pour que ⏮ ⏭ passent à la favorite précédente / suivante.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val station = mediaItems.singleOrNull()?.let { Station.fromMetadata(it.mediaMetadata) }
                ?: return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
            return Futures.immediateFuture(playlistAround(station))
        }

        /** Les URI ne traversent pas le contrôleur : on reconstruit l'élément depuis la station. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                Station.fromMetadata(item.mediaMetadata)?.toMediaItem()
                    ?: item.requestMetadata.mediaUri?.let { item.buildUpon().setUri(it).build() }
                    ?: item
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        /** « Lecture » depuis un casque Bluetooth ou les commandes système après redémarrage. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
            store.awaitLoaded()
            val last = store.recent.value.firstOrNull()
                ?: throw UnsupportedOperationException("No station played yet")
            serviceScope.launch { app.api.registerClick(last.uuid) }
            playlistAround(last)
        }
    }

    /** Favorites dans leur ordre ; une station hors favoris est placée en tête. */
    private fun playlistAround(station: Station): MediaSession.MediaItemsWithStartPosition {
        val favorites = store.favorites.value.filterNotNull()
        val index = favorites.indexOfFirst { it.uuid == station.uuid }
        val list = if (index >= 0) favorites else listOf(station) + favorites
        return MediaSession.MediaItemsWithStartPosition(
            list.map { it.toMediaItem() },
            if (index >= 0) index else 0,
            C.TIME_UNSET,
        )
    }

    companion object {
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2_000L
    }
}
