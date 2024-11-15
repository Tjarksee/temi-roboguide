package de.fhkiel.temi.robogguide

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import de.fhkiel.temi.robogguide.database.DatabaseHelper

class TourService : Service() {

    private val binder = TourBinder()
    private lateinit var tourHelper: TourHelper

    inner class TourBinder : Binder() {
        fun getService(): TourService = this@TourService
    }

    override fun onCreate() {
        super.onCreate()
        tourHelper = TourHelper(this)
        Log.d("TourService", "TourService erstellt und TourHelper initialisiert")
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun startTour(routeType: String) {
        when (routeType) {
            "long" -> tourHelper.startLongTour()
            "short" -> tourHelper.startShortTour()
            "individual" -> tourHelper.startTour()
            else -> Log.e("TourService", "Unbekannter Routentyp: $routeType")
        }
    }

    fun setIndividualRoute( selectedItems: MutableList<String>){
        tourHelper.setIndividualRoute(selectedItems)
    }

    fun isRouteEmpty(): Boolean{
        return tourHelper.route.isNotEmpty()
    }

    fun pauseTour() {

        Log.d("TourService", "Tour pausiert")
    }

    fun skipToNextLocation() {

        Log.d("TourService", "Nächste Location übersprungen")
    }
}