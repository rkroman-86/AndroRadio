package com.plugyourhome.androradio.cast

import android.content.Context
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Un appareil Google Cast (Chromecast, Google TV, enceinte « Chromecast built-in »). */
data class CastDevice(val id: String, val name: String)

/**
 * Appareils Google Cast du réseau : découverte, sélection, arrêt de la diffusion.
 * La lecture elle-même passe par CastPlayer dans PlaybackService, qui bascule
 * automatiquement entre le téléphone et l'appareil choisi.
 * Sans services Google (ou en cas d'erreur), la classe ne fait simplement rien.
 */
class CastDevices(context: Context) {

    private val appContext = context.applicationContext
    private val castContext: CastContext? = runCatching { CastContext.getSharedInstance(appContext) }.getOrNull()
    private val router: MediaRouter? = if (castContext != null) runCatching { MediaRouter.getInstance(appContext) }.getOrNull() else null

    // Même récepteur que celui configuré pour Cast (DefaultCastOptionsProvider de Media3)
    private val selector = MediaRouteSelector.Builder()
        .addControlCategory(
            CastMediaControlIntent.categoryForCast(
                castContext?.castOptions?.receiverApplicationId
                    ?: CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
            )
        )
        .build()

    val available: Boolean get() = router != null

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    /** Nom de l'appareil Cast connecté, null si aucun. */
    private val _connected = MutableStateFlow<String?>(null)
    val connected: StateFlow<String?> = _connected.asStateFlow()

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = updateConnected()
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = updateConnected()
        override fun onSessionEnded(session: CastSession, error: Int) = updateConnected()
        override fun onSessionSuspended(session: CastSession, reason: Int) = updateConnected()
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) = updateConnected()
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) = updateConnected()
    }

    init {
        castContext?.sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
        updateConnected()
    }

    /** Recherche active, pendant que la fenêtre « Diffuser vers… » est ouverte. */
    fun startDiscovery() {
        router?.addCallback(selector, routerCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        refresh()
    }

    fun stopDiscovery() {
        router?.removeCallback(routerCallback)
    }

    fun select(device: CastDevice) {
        router?.routes?.firstOrNull { it.id == device.id }?.select()
    }

    /** Termine la diffusion Cast ; la lecture revient sur le téléphone. */
    fun disconnect() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    val isConnected: Boolean get() = castContext?.sessionManager?.currentCastSession?.isConnected == true

    private fun refresh() {
        val r = router ?: return
        _devices.value = r.routes
            .filter { !it.isDefault && it.isEnabled && it.matchesSelector(selector) }
            .map { CastDevice(it.id, it.name) }
            .sortedBy { it.name.lowercase() }
    }

    private fun updateConnected() {
        val session = castContext?.sessionManager?.currentCastSession
        _connected.value = session?.takeIf { it.isConnected }?.castDevice?.friendlyName
    }

    fun release() {
        stopDiscovery()
        castContext?.sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
    }
}
