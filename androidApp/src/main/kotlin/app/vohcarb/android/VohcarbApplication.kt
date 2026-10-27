package app.vohcarb.android

import android.app.Application
import android.content.Context
import app.vohcarb.AppContainer
import com.russhwolf.settings.SharedPreferencesSettings

class VohcarbApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            settings = SharedPreferencesSettings(getSharedPreferences("vohcarb", Context.MODE_PRIVATE)),
            defaultServerUrl = BuildConfig.DEFAULT_SERVER_URL,
        )
    }
}
