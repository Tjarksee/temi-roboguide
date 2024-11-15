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


class TourHelper(private val context: Context):Robot.TtsListener {
    private var database : DatabaseHelper
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
        database = DatabaseHelper.getInstance(context, "roboguide.db")
        database.initializeDatabase()
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
        mRobot?.addTtsListener(this)
        isSpeechCompleted = true
        isNavigationCompleted = false
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
            locationIndex = 0
            navigateToNextLocation()
            return
        }

        val itemText = database.getItemTexts(items[locationIndex]?.get("id").toString())
        locationIndex++



        if(itemText!=null){
            val mainText = itemText["text"].toString()
            val headingText = itemText["title"].toString()
            val text = Intent("ACTION_UPDATE_SPOKEN_TEXT").apply {
                putExtra("EXTRA_SPOKEN_TEXT", mainText)
            }
            val heading = Intent("ACTION_UPDATE_HEADING").apply {
            putExtra("EXTRA_HEADING",headingText)
            }
            context.sendBroadcast(text)
            context.sendBroadcast(heading)
        }



        if (itemText != null) {
            val mediaUrl = database.getMedia(itemText["id"].toString())

            if (mediaUrl != null) {
                Log.i(TAG, "Medien-URL gefunden für Text ${itemText["id"]}: $mediaUrl")
                val media = Intent("ACTION_UPDATE_MEDIA")
                media.putExtra("EXTRA_MEDIA_URL", mediaUrl)
                context.sendBroadcast(media)
            } else {
                Log.w(TAG, "Keine Medien-URL gefunden für Text ${itemText["id"]}")
            }

            speakText(itemText["text"].toString())
        } else {
            Log.w(TAG, "Kein Text gefunden für Item bei Location ${route[currentIndex].locationId}")
            speakWithoutListener("Hierfür habe ich leider keinen Text.")
        }
    }


    private fun goToFirstLocation() {
        if (route.isNotEmpty()) {
            mRobot?.goTo(route[0].name.toString())
        } else {
            Log.w(TAG, "Die Route ist leer, Tour kann nicht gestartet werden.")
        }
    }

    private fun setLocationListener() {
        locationStatusListener = object : OnGoToLocationStatusChangedListener {
            override fun onGoToLocationStatusChanged(location: String, status: String, descriptionId: Int, description: String) {
                Log.d(TAG, "Statusänderung bei $location - Status: $status, Beschreibung: $description")

                when (status) {
                    "complete" -> {
                        retryCount = 0
                        Log.i(TAG, "Standort erreicht: $location")
                        isNavigationCompleted = true
                        checkMovementAndSpeechStatus()
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
            val transferText = database.getTransferText(route[currentIndex])
            if(transferText != null){
                val mediaUrl = database.getMedia(transferText["id"].toString())
                val heading = Intent("ACTION_UPDATE_SPOKEN_TEXT").apply {
                    putExtra("EXTRA_SPOKEN_TEXT", transferText["text"].toString())
                }
                val text = Intent("ACTION_UPDATE_HEADING").apply {
                    putExtra("EXTRA_HEADING",nextLocation.name )
                }
                val media = Intent("ACTION_UPDATE_MEDIA")
                media.putExtra("EXTRA_MEDIA_URL", mediaUrl)

                context.sendBroadcast(text)
                context.sendBroadcast(heading)
                context.sendBroadcast(media)

            }
            val progress = ((currentIndex + 1) * 100) / totalLocations
            val intent = Intent("ACTION_UPDATE_PROGRESS")
            intent.putExtra("EXTRA_PROGRESS", progress)
            context.sendBroadcast(intent)
            Log.i(TAG, "Navigiere zur nächsten Location: ${nextLocation.name}")
            isNavigationCompleted = false
            isSpeechCompleted = false
            speakWithoutListener("navigiere zu ${nextLocation.name}")

            mRobot?.goTo(nextLocation.name.toString())
        } else {
            endTour()
        }
    }


    private fun checkMovementAndSpeechStatus() {
        if (isSpeechCompleted && isNavigationCompleted && !atLocation) {
            atLocation = true
            activityForLocation()
        }
    }

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

    private fun speakText(text: String, isShowOnConversationLayer: Boolean = false) {
        mRobot?.let {
            val ttsRequest = TtsRequest.create(speech = text, isShowOnConversationLayer = false)
            it.speak(ttsRequest)
        }
    }

    private fun speakWithoutListener(text: String) {
        mRobot?.removeTtsListener(this)

        val ttsRequest = TtsRequest.create(speech = text, isShowOnConversationLayer = false)
        mRobot?.speak(ttsRequest)

        mRobot?.addTtsListener(this)
    }

    override fun onTtsStatusChanged(ttsRequest: TtsRequest) {
        if (ttsRequest.status == TtsRequest.Status.COMPLETED) {
            Log.i(TAG, "Sprachausgabe abgeschlossen.")
            if(atLocation){
                activityForLocation()
            }else{
                isSpeechCompleted = true
                checkMovementAndSpeechStatus()
            }

        }
    }

    fun resetTour(){
        route.clear()
        locationIndex = 0
        currentIndex = 0
        locationStatusListener?.let { mRobot?.removeOnGoToLocationStatusChangedListener(it)}
        atLocation = false
        mRobot?.goTo("home base")
    }

    fun endTour() {
        Log.i(TAG, "Tour abgeschlossen.")
        mRobot?.removeTtsListener(this)
        locationStatusListener?.let { mRobot?.removeOnGoToLocationStatusChangedListener(it)}
        mRobot?.stopMovement()
        mRobot?.cancelAllTtsRequests()
        speakText("Die Tour ist jetzt abgeschlossen. Wenn du möchtest kannst du dir kurz die Zeit nehmen die Tour zu bewerten")
        val intent = Intent(context, RatingActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

}
