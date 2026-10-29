package com.plugyourhome.androradio

import android.app.Application
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouterParams
import com.google.android.gms.cast.framework.CastContext
import com.plugyourhome.androradio.data.RadioBrowserApi
import com.plugyourhome.androradio.data.StationStore
import com.plugyourhome.androradio.data.TrackArtwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Point d'entrée de l'appli : détient les objets partagés (API, stockage). */
class AndroRadioApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val api by lazy { RadioBrowserApi() }

    val artwork by lazy { TrackArtwork() }

    val store by lazy { StationStore(this, appScope) }

    override fun onCreate() {
        super.onCreate()
        // Panneau de sortie d'Android (widget média) : il peut alors lister les appareils Cast,
        // savoir que le son est sur l'un d'eux, et revenir au téléphone.
        runCatching {
            CastContext.getSharedInstance(this)
            MediaRouter.getInstance(this).setRouterParams(
                MediaRouterParams.Builder()
                    .setOutputSwitcherEnabled(true)
                    .setTransferToLocalEnabled(true)
                    .build()
            )
        }
    }
}
