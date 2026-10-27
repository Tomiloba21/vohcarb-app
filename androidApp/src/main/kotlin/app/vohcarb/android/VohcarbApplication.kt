package app.vohcarb.android

import android.app.Application
import app.vohcarb.AppContainer
import app.vohcarb.createAppContainer

class VohcarbApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createAppContainer(this, BuildConfig.DEFAULT_SERVER_URL)
    }
}
