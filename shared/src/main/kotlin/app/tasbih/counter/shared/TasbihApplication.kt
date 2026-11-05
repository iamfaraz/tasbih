package app.tasbih.counter.shared

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/** Application class for both the phone and watch apps. */
open class TasbihApplication : Application() {
    /** Set by the phone's ring code. On the watch it arrives through [WearSync]. */
    val ringSummary = MutableStateFlow(RingSummary())

    lateinit var repository: TasbihRepository
        private set
    lateinit var settings: TasbihSettings
        private set
    lateinit var prayerStore: PrayerStore
        private set

    override fun onCreate() {
        super.onCreate()
        settings = TasbihSettings(this)
        repository = TasbihRepository(this, settings)
        prayerStore = PrayerStore(this)
        WearSync(this, repository, settings, prayerStore, ringSummary, CoroutineScope(SupervisorJob() + Dispatchers.IO)).start()
    }
}
