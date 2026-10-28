package com.plugyourhome.androradio

import android.app.Application
import com.plugyourhome.androradio.data.RadioBrowserApi
import com.plugyourhome.androradio.data.StationStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/** Point d'entrée de l'appli : détient les objets partagés (API, stockage). */
class AndroRadioApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val api by lazy { RadioBrowserApi() }

    val store by lazy { StationStore(this, appScope) }
}
