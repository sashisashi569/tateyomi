package app.tateyomi

import android.app.Application
import app.tateyomi.data.AppRepository

class TateyomiApp : Application() {
    lateinit var repository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(this)
    }
}
