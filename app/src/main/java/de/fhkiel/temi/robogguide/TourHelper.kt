package de.fhkiel.temi.robogguide

import android.content.Context
import android.content.Intent
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
    private var locationStatusListenerManager: OnGoToLocationStatusChangedListener? = null
    private var paused = false
    private var isVideoCompleted = true


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

    fun startLongTour(placeId: Int) {
        val listOfLocations = database.getLocations(placeId)
        for (location in listOfLocations) {
            val transferId = location?.get("id")?.let { database.getTransferId(it.toString()) }
            route.add(Location(location?.get("name").toString(), transferId, location?.get("id").toString()))
            Log.i(TAG, "Lange Route geplant.")
        }
        totalLocations = route.size
        Log.d(TAG, "startLongTour mit ${route.size} Standorten initialisiert.")
        startTour()
    }

    fun startShortTour(placeId: Int) {
        val listOfLocations = database.getImportantLocations(placeId)
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

    // Definiere den Listener separat
    private val locationStatusListener = object : OnGoToLocationStatusChangedListener {
        override fun onGoToLocationStatusChanged(location: String, status: String, descriptionId: Int, description: String) {
            Log.d(TAG, "Statusänderung bei $location - Status: $status, Beschreibung: $descriptionId")

            when (descriptionId) {
                1003, 1004, 1005, 1006, 1060, 2000, 2001, 2002,
                2003, 2004, 2005, 2006, 2007, 2008, 2009 -> errorPopUp()
                500 -> {
                    retryCount = 0
                    Log.i(TAG, "Standort erreicht: $location")
                    isNavigationCompleted = true
                    checkMovementAndSpeechStatus()
                }
                else -> Log.d(TAG, "Nicht behandelter Status: $status bei $location")
            }
        }
    }

    // Methode zum Setzen des Listeners
    private fun setLocationListener() {
        // Entferne den vorherigen Listener, falls vorhanden
        locationStatusListenerManager?.let {
            mRobot?.removeOnGoToLocationStatusChangedListener(it)
        }

        // Setze den aktuellen Listener als aktiven Listener im Manager
        locationStatusListenerManager = locationStatusListener

        // Registriere den neuen Listener
        mRobot?.addOnGoToLocationStatusChangedListener(locationStatusListener)
    }

    // Optional: Wenn du den Listener entfernen möchtest
    private fun removeLocationListener() {
        locationStatusListenerManager?.let {
            mRobot?.removeOnGoToLocationStatusChangedListener(it)
        }

        // Setze den Manager auf null, um den Listener zu löschen
        locationStatusListenerManager = null
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
        } else if (currentIndex >= route.size && isVideoCompleted) {
            endTour()
        }
    }


    private fun checkMovementAndSpeechStatus() {
        Log.d(TAG, "Status prüfen: Speech=$isSpeechCompleted, Navigation=$isNavigationCompleted, Video=$isVideoCompleted")
        if (isSpeechCompleted && isNavigationCompleted && isVideoCompleted && !atLocation) {
            Log.d(TAG, "Alle Bedingungen erfüllt. Fahre mit der Tour fort.")
            atLocation = true
            activityForLocation()
        } else {
            Log.d(TAG, "Warte auf weitere Aktionen. Status: Speech=$isSpeechCompleted, Navigation=$isNavigationCompleted, Video=$isVideoCompleted")
        }
    }



    private fun retryNavigation(location: String) {
            mRobot?.goTo(location)
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
        removeLocationListener()
        atLocation = false
        mRobot?.goTo("home base")
    }

    fun endTour() {
        Log.i(TAG, "Tour abgeschlossen.")
        mRobot?.removeTtsListener(this)
        removeLocationListener()
        mRobot?.stopMovement()
        mRobot?.cancelAllTtsRequests()
        speakText("Die Tour ist jetzt abgeschlossen. Wenn du möchtest kannst du dir kurz die Zeit nehmen die Tour zu bewerten")
        val intent = Intent(context, RatingActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    fun pauseTour(){
        mRobot?.removeTtsListener(this)
        removeLocationListener()
        mRobot?.stopMovement()
        mRobot?.cancelAllTtsRequests()
    }
    fun continueTour(){
        setLocationListener()

        mRobot?.addTtsListener(this)
        if(atLocation){
            locationIndex--
            activityForLocation()
        }else{
            currentIndex--
            navigateToNextLocation()
        }
    }
    fun skip(){
        mRobot?.cancelAllTtsRequests()
        if(paused){
            setLocationListener()
            mRobot?.addTtsListener(this)
            paused = false
        }
        if(atLocation){
            locationIndex--
            activityForLocation()
        }else{
            mRobot?.stopMovement()
            navigateToNextLocation()
        }
    }

    fun retryNavigationFromError(){
        retryNavigation(route[currentIndex].name.toString())
    }
    fun setSpeechStatus(isCompleted: Boolean) {
        isSpeechCompleted = isCompleted
        checkMovementAndSpeechStatus()
    }

    fun setVideoStatus(isCompleted: Boolean) {
        Log.d(TAG, "Video-Status aktualisiert: $isCompleted")
        isVideoCompleted = isCompleted
        checkMovementAndSpeechStatus()
    }


    fun onDestroy(){
        Log.i(TAG, "TourHelper wird zerstört. Ressourcen werden freigegeben.")

        mRobot?.removeTtsListener(this)
        removeLocationListener()
        mRobot?.stopMovement()
        mRobot?.cancelAllTtsRequests()
        route.clear()
        retryCount = 0
        atLocation = false
        isSpeechCompleted = false
        isNavigationCompleted = false
        locationIndex = 0
        currentIndex = 0
    }
    private fun errorPopUp(){
        mRobot?.stopMovement()
        val error = Intent("ACTION_UPDATE_POPUP")
        mRobot?.cancelAllTtsRequests()
        context.sendBroadcast(error)
    }
}
