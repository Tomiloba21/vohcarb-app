package app.vohcarb

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.vohcarb.db.VohcarbDatabase
import com.russhwolf.settings.SharedPreferencesSettings

fun createAppContainer(context: Context, defaultServerUrl: String): AppContainer {
    val container = AppContainer(
        settings = SharedPreferencesSettings(context.getSharedPreferences("vohcarb", Context.MODE_PRIVATE)),
        defaultServerUrl = defaultServerUrl,
        driver = AndroidSqliteDriver(VohcarbDatabase.Schema, context, "vohcarb.db"),
    )
    syncWhenOnline(context, container)
    return container
}

/** Sends what was done offline as soon as the phone is back online. */
private fun syncWhenOnline(context: Context, container: AppContainer) {
    val main = Handler(Looper.getMainLooper())
    context.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                main.post { if (container.sessions.session.value != null) container.sync.requestSync() }
            }
        },
    )
}
