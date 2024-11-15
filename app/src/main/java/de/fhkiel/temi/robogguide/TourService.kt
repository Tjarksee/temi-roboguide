import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import de.fhkiel.temi.robogguide.TourHelper
import de.fhkiel.temi.robogguide.database.DatabaseHelper

class TourService : Service() {

    // Binder zum Kommunizieren mit dem Service
    private val binder = TourBinder()
    private lateinit var tourHelper: TourHelper

    // Initialisierung des TourHelper im Service
    override fun onCreate() {
        super.onCreate()
        val database = DatabaseHelper(this,"database")
        tourHelper = TourHelper(database, this)
    }


    // Binder-Klasse, um TourHelper-Funktionen von der Activity aufzurufen
    inner class TourBinder : Binder() {
        fun getService(): TourService = this@TourService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    // Beispielmethode zum Zugriff auf TourHelper-Funktionen
    fun startTour() {
        tourHelper.startTour()
    }

    fun pauseTour() {
        // Hier eine Methode im TourHelper ansprechen, z. B. eine Pause-Funktion
        // tourHelper.pause()
    }

    fun skipToNextLocation() {
        // tourHelper.navigateToNextLocation()
    }
}
