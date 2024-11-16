package de.fhkiel.temi.robogguide

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

class TourService : Service() {

    private val binder = TourBinder()
    private lateinit var tourHelper: TourHelper
    private var placeId: Int = -1 // Standardwert, wenn PLACE_ID nicht gesetzt ist

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

    fun setPlaceId(id: Int) {
        placeId = id
        Log.d("TourService", "Place ID gesetzt: $placeId")
    }

    fun startTour(routeType: String) {
        if (placeId == -1) {
            Log.e("TourService", "Place ID ist nicht gesetzt. Tour kann nicht gestartet werden.")
            return
        }
        when (routeType) {
            "long" -> tourHelper.startLongTour(placeId)
            "short" -> tourHelper.startShortTour(placeId)
            "individual" -> tourHelper.startTour()
            else -> Log.e("TourService", "Unbekannter Routentyp: $routeType")
        }
    }

    fun setIndividualRoute(selectedItems: MutableList<String>) {
        tourHelper.setIndividualRoute(selectedItems)
    }

    fun isRouteEmpty(): Boolean{
        return tourHelper.route.isNotEmpty()
    }

    fun endTour(){
        tourHelper.endTour()
    }

    fun reset(){
        tourHelper.resetTour()
    }
    fun pauseTour() {
        Log.d("TourService", "Tour pausiert")
        tourHelper.pauseTour()
    }

    fun continueTour(){
        tourHelper.continueTour()
    }

    fun skip() {
        tourHelper.skip()
        Log.d("TourService", "Nächste Location übersprungen")
    }
    override fun onDestroy() {
        super.onDestroy()
        tourHelper.onDestroy()
    }
}