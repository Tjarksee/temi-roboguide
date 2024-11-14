package de.fhkiel.temi.robogguide

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import de.fhkiel.temi.robogguide.database.DatabaseHelper
import org.json.JSONObject


class TourHelper(private val database: DatabaseHelper,private val context: Context):Robot.TtsListener {
    private var mRobot: Robot? = null
    private var retryCount = 0
    var route: MutableList<Location> = mutableListOf()
    private var isNavigationCompleted = false
    private var isSpeechCompleted = false
    private var atLocation = false
    private var locationIndex = 0
    private var currentIndex = 0
    private var totalLocations = 0
    private val TAG = "TourHelper-Tour"
    private val maxRetries = 3
    private val retryDelayMillis = 5000L
    private var locationStatusListener: OnGoToLocationStatusChangedListener? = null

    init {
        mRobot = Robot.getInstance()
    }

    fun setIndividualRoute(selectedLocationIds: List<String>) {
        route.clear()
        val locationList: MutableList<Location> = mutableListOf()
        for (location in selectedLocationIds) {
            val locationName = database.getLocationName(location)
            val transferId = database.getTransferId(location)
            locationList.add(Location(locationName, transferId, location))
        }
        route = locationList
        totalLocations = route.size
        Log.d(TAG, "setIndividualRoute aufgerufen mit ${selectedLocationIds.size} Standorten.")
    }

    fun startLongTour() {
        val listOfLocations = database.getLocations()
        for (location in listOfLocations) {
            val transferId = location?.get("id")?.let { database.getTransferId(it.toString()) }
            route.add(Location(location?.get("name").toString(), transferId, location?.get("id").toString()))
            Log.i(TAG, "Lange Route geplant.")
        }
        totalLocations = route.size
        Log.d(TAG, "startLongTour mit ${route.size} Standorten initialisiert.")
        startTour()
    }



    fun startShortTour() {
        val listOfLocations = database.getImportantLocations()
        for (location in listOfLocations) {
            val transferId = database.getTransferId(location.get("id").toString())
            route.add(Location(location.get("name").toString(), transferId, location.get("id").toString()))
            Log.i(TAG, "Kurze Route geplant.")
        }
        totalLocations = route.size
        Log.d(TAG, "startShortTour mit ${route.size} Standorten initialisiert.")
        startTour()
    }

    fun startTour() {
        planRightOrder()
        currentIndex = 0
        Log.i(TAG, "Individuelle Tour gestartet.")
        setLocationListener()
        goToFirstLocation()
    }

    private fun planRightOrder(){
        val rightOderList: MutableList<Location> = mutableListOf()
        val transfers = getTransfers()
        val startingPoint = getStartingPoint(transfers)
        var fromLocationId = startingPoint.locationId
        var foundEnd = false

        while(!foundEnd) {
            val foundLocation = route.find {it.locationId == fromLocationId}
            if(foundLocation!= null) {
                rightOderList.add(foundLocation)
                if(route.size == rightOderList.size){
                    foundEnd = true
                    continue
                }
            }
            val nextLocation = transfers.find {it["location_from"].toString() == fromLocationId}
            if (nextLocation==null) {
                foundEnd = true
                continue
            }
            fromLocationId = nextLocation["location_to"].toString()
        }
        route = rightOderList
    }

    private fun getTransfers() : Collection<JSONObject>{
        val query = "SELECT * FROM transfers"
        return database.getTableDataAsJsonWithQuery("transfers", query).values
    }

    private fun getStartingPoint(listOfLocations: Collection<JSONObject>): Location {
        val startLocations = mutableListOf<Location>()
        var isStart: Boolean
        for(location in listOfLocations){
            val locationFrom = location["location_from"].toString()
            isStart= true
            for(compareLocation in listOfLocations){
                if(locationFrom == compareLocation["location_to"].toString()){
                    isStart = false
                }
            }
            if(isStart){
                startLocations.add(Location(null,location["id"].toString(),locationFrom))
            }

        }
        if(startLocations.isNotEmpty()){
            startLocations[0].name = database.getLocationName(startLocations[0].locationId!!)
            Log.i("Tour-Helper","The starting Point has the name ${startLocations[0].name}")
            return startLocations[0]}

            else {
                Log.e("TourHelper", "Es wurde kein Startpunkt gefunden.")
                val startingPoint = Location(null,null,null)
                return startingPoint
            }

    }

    private fun activityForLocation() {
        // Überprüfen, ob currentIndex den Bereich der route-Liste überschreitet
        if (currentIndex >= route.size) {
            Log.w(TAG, "currentIndex $currentIndex ist außerhalb des Bereichs der Route-Liste (${route.size} Elemente). Beende die Tour.")
            endTour()
            return
        }

        val items = database.getItems(route[currentIndex].locationId!!)
        Log.d(TAG, "Anzahl der Items für Location ${route[currentIndex].locationId}: ${items.size}, aktueller locationIndex: $locationIndex")

        if (locationIndex >= items.size) {
            Log.w(TAG, "locationIndex $locationIndex ist außerhalb des Bereichs der Item-Liste (${items.size} Elemente). Wechsle zur nächsten Location.")
            atLocation = false
            locationIndex = 0 // Setze den Index für die nächste Location zurück
            navigateToNextLocation()
            return
        }

        val itemText = database.getItemTexts(items[locationIndex]?.get("id").toString())
        locationIndex++ // Erhöht locationIndex für das nächste Item

        if (itemText != null) {
            val mediaUrl = database.getMedia(itemText["id"].toString()) // Verwende die neue getMedia-Methode

            if (mediaUrl != null) {
                Log.i(TAG, "Medien-URL gefunden für Text ${itemText["id"]}: $mediaUrl")
                val intent = Intent("ACTION_UPDATE_MEDIA")
                intent.putExtra("EXTRA_MEDIA_URL", mediaUrl)
                context.sendBroadcast(intent)
            } else {
                Log.w(TAG, "Keine Medien-URL gefunden für Text ${itemText["id"]}")
            }

            speakText(itemText["text"].toString())
        } else {
            Log.w(TAG, "Kein Text gefunden für Item bei Location ${route[currentIndex].locationId}")
            speakText("Hierfür habe ich leider keinen Text.")
        }
    }




    private fun executeTour(){
        setLocationListener()
        isSpeechCompleted = true
        isNavigationCompleted = false
        goToFirstLocation()

    }

    // Gehe zur ersten Location in der Route
    private fun goToFirstLocation() {
        if (route.isNotEmpty()) {
            mRobot?.goTo(route[0].name.toString())
        } else {
            Log.w(TAG, "Die Route ist leer, Tour kann nicht gestartet werden.")
        }
    }

    // Listener für den Status der Standortänderung
    private fun setLocationListener() {
        locationStatusListener = object : OnGoToLocationStatusChangedListener {
            override fun onGoToLocationStatusChanged(location: String, status: String, descriptionId: Int, description: String) {
                Log.d(TAG, "Statusänderung bei $location - Status: $status, Beschreibung: $description")

                when (status) {
                    "complete" -> {
                        retryCount = 0
                        Log.i(TAG, "Standort erreicht: $location")
                        handleLocationArrival()
                    }
                    "abort" -> retryNavigation(location)
                    else -> Log.d(TAG, "Nicht behandelter Status: $status bei $location")
                }
            }
        }
        mRobot?.addOnGoToLocationStatusChangedListener(locationStatusListener!!)
    }


    // Navigiert zur nächsten Location in der Route und zeigt Fortschritt in der ProgressBar an
    private fun navigateToNextLocation() {
        currentIndex++

        if (currentIndex < route.size) {
            val nextLocation = route[currentIndex]

            // Aktualisiere den Fortschritt für die ProgressBar und sende ihn als Broadcast
            val progress = ((currentIndex + 1) * 100) / totalLocations
            val intent = Intent("ACTION_UPDATE_PROGRESS")
            intent.putExtra("EXTRA_PROGRESS", progress)
            context.sendBroadcast(intent)

            Log.i(TAG, "Navigiere zur nächsten Location: ${nextLocation.name}")
            isNavigationCompleted = false
            mRobot?.goTo(nextLocation.name.toString())
        } else {
            // Beende die Tour, wenn das Ende der Route erreicht ist
            endTour()
        }
    }


    private fun checkMovementAndSpeechStatus() {
        if (isSpeechCompleted && isNavigationCompleted) {
            atLocation = true
            activityForLocation()
        }
    }

    // Versucht, die Navigation bei einem Abbruch neu zu starten
    private fun retryNavigation(location: String) {
        if (retryCount < maxRetries) {
            retryCount++
            Log.w(TAG, "Navigation zu $location abgebrochen. Versuch $retryCount von $maxRetries.")
            Handler(Looper.getMainLooper()).postDelayed({ mRobot?.goTo(location) }, retryDelayMillis)
        } else {
            retryCount = 0
            Log.e(TAG, "Navigation zu $location nach $maxRetries Versuchen fehlgeschlagen. Weiter zur nächsten Location.")
            navigateToNextLocation()
        }
    }

    // Sprechen eines Textes über TTS und Senden des Textes an die ExecutionActivity
    private fun speakText(text: String, isShowOnConversationLayer: Boolean = false) {
        mRobot?.let {
            val ttsRequest = TtsRequest.create(speech = text, isShowOnConversationLayer = false)
            onTtsStatusChanged(ttsRequest)
            it.speak(ttsRequest)
            val intent = Intent("ACTION_UPDATE_SPOKEN_TEXT").apply {
                putExtra("EXTRA_SPOKEN_TEXT", text)
            }
            context.sendBroadcast(intent)
        }
    }

    // TTS-Callback-Handling
    override fun onTtsStatusChanged(ttsRequest: TtsRequest) {
        if (ttsRequest.status == TtsRequest.Status.COMPLETED) {
            Log.i(TAG, "Sprachausgabe abgeschlossen.")
            isSpeechCompleted = true
            checkMovementAndSpeechStatus()
        }
    }

    // Abschluss der Tour und Entfernen des Listeners
    private fun endTour() {
        Log.i(TAG, "Tour abgeschlossen.")
        speakText("Die Tour ist jetzt abgeschlossen.")
        locationStatusListener?.let { mRobot?.removeOnGoToLocationStatusChangedListener(it) }
    }

    // Aktionen bei Ankunft an einem Standort
    private fun handleLocationArrival() {
        activityForLocation()
        navigateToNextLocation()
    }

}
