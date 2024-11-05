package de.fhkiel.temi.robogguide

import android.content.Context
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
    private val TAG = "TourHelper-Tour"

    fun setIndividualRoute(selectedLocationIds : List<String>){
        route.clear()
        val locationList : MutableList<Location> = mutableListOf()
        for(location in selectedLocationIds){
            val locationName = database.getLocationName(location)
            val transferId = database.getTransferId(location)
            locationList.add(Location(locationName,transferId,location))
        }
        route = locationList
    }

    fun startLongTour(){
        val listOfLocations = database.getLocations()
        for (location in listOfLocations) {
            val transferId = location?.get("id")?.let { database.getTransferId(it.toString()) }
            route.add(Location(location?.get("name").toString(),transferId, location?.get("id").toString()))
            Log.i(TAG,"lange route geplant")
        }
        startTour()
    }

    fun startShortTour(){
        val listOfLocations = database.getImportantLocations()
        for (location in listOfLocations) {
            val transferId = database.getTransferId(location.get("id").toString())
            route.add(Location(location.get("name").toString(),transferId, location.get("id").toString()))
            Log.i(TAG,"lange route geplant")
        }
        startTour()
    }

    fun startTour(){
        planRightOrder()
        currentIndex = 0
        Log.i(TAG, "Individuelle Tour gestartet.")
        executeTour()
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


    private fun activityForLocation(){
        val items = database.getItems(route[currentIndex].locationId!!)
        if(items.size == locationIndex){
            atLocation = false
            navigateToNextLocation()
        }else{
            val itemText = database.getItemTexts(items[locationIndex]?.get("id").toString())
            locationIndex++
            if(itemText!=null){
                val itemMedia = database.getTableDataAsJsonWithQuery("media", "SELECT * FROM media WHERE id = '${itemText["id"]}'")
                //to do heir text und bild anzeigen
                speakText(itemText["text"].toString())
            }else{

                speakText("hierfür habe ich leider keinen text")
            }

        }
    }

    private fun executeTour(){
        setLocationListener()
        isSpeechCompleted = true
        isNavigationCompleted = false
        goToFirstLocation()

    }

    private fun goToFirstLocation(){
        mRobot?.goTo(route[0].name.toString())
    }

    private fun setLocationListener() {
        mRobot = Robot.getInstance()
        Log.i(TAG, "Individuelle Tour gestartet.")

        val listener = object : OnGoToLocationStatusChangedListener {
            override fun onGoToLocationStatusChanged(location: String, status: String, descriptionId: Int, description: String) {
                Log.d(TAG, "Statusänderung bei $location - Status: $status, Beschreibung: $description")

                when (status) {
                    "complete" -> {
                        isNavigationCompleted = true
                        checkMovementAndSpeechStatus()
                    }
                    "abort" -> retryNavigation(location, this)
                    else -> Log.d(TAG, "Nicht behandelter Status: $status bei $location")
                }
            }
        }
        mRobot?.addOnGoToLocationStatusChangedListener(listener)
    }



    // Navigiert zur nächsten Location in der Route
    private fun navigateToNextLocation() {
        currentIndex++
        val nextLocation = route[currentIndex]
        val text = database.getTransferText(nextLocation)
        //to do hier text anzeigen

        if(text?.get("text") != null){
            isSpeechCompleted = false
            speakText(text["text"].toString())
        }else{
            isSpeechCompleted = true
        }
        Log.i(TAG, "Navigiere zu: $nextLocation")
        isNavigationCompleted = false
        mRobot?.goTo(nextLocation.name.toString())

    }

    private fun checkMovementAndSpeechStatus() {
        if(isSpeechCompleted && isNavigationCompleted){
            atLocation = true
            activityForLocation()
        }
    }

    // Versucht, die Navigation bei einem Abbruch neu zu starten
    private fun retryNavigation(location: String, listener: OnGoToLocationStatusChangedListener) {

        val maxRetries = 3
        val retryDelayMillis = 5000L

        if (retryCount < maxRetries) {
            retryCount++
            Log.w(TAG, "Navigation zu $location abgebrochen. Versuch $retryCount von $maxRetries in ${retryDelayMillis / 1000} Sekunden.")
            Handler(Looper.getMainLooper()).postDelayed({
                mRobot?.goTo(location)
            }, retryDelayMillis)
        } else {
            Log.e(TAG, "Navigation zu $location fehlgeschlagen nach $maxRetries Versuchen. Fortsetzung der Tour.")
            retryCount = 0
            if (currentIndex+1 < route.size) {
                speakText("ich konnte die location nicht erreichen", true)
                atLocation = false
                isNavigationCompleted =false
                isSpeechCompleted = false
                navigateToNextLocation()
            } else {
                Log.i(TAG, "Tour abgeschlossen.")
                mRobot?.removeOnGoToLocationStatusChangedListener(listener)
            }
        }
    }

    // Gibt Text über den Temi-Roboter aus
    private fun speakText(text: String, isShowOnConversationLayer: Boolean = true) {
        mRobot?.let { robot ->
            val ttsRequest: TtsRequest = TtsRequest.create(speech = text, isShowOnConversationLayer = isShowOnConversationLayer)
            onTtsStatusChanged(ttsRequest)
            robot.speak(ttsRequest)
        }
    }

    override fun onTtsStatusChanged(ttsRequest: TtsRequest) {
        if(ttsRequest.status==TtsRequest.Status.COMPLETED){
            Log.i(TAG,"speech completed")
            if(atLocation){
                mRobot?.removeTtsListener(this)
                activityForLocation()
            }else {
                mRobot?.removeTtsListener(this)
                isSpeechCompleted = true
                checkMovementAndSpeechStatus()
            }
        }
        mRobot?.addTtsListener(this)
    }


}
