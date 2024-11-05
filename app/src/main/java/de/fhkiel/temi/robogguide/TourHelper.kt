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
    private val TAG = "TourHelper-Tour"

    fun setIndividualRoute(selectedLocationIds : List<String>){
        route.clear()
        val locationList : MutableList<Location> = mutableListOf()
        for(location in selectedLocationIds){
            val locationName = getLocationName(location)
            val transferId = getTransferId(location)
            locationList.add(Location(locationName,transferId,location))
        }
        route = locationList
    }

    fun startLongTour(){
        val listOfLocations = getLocations()
        for (location in listOfLocations) {
            val transferId = location?.get("id")?.let { getTransferId(it.toString()) }
            route.add(Location(location?.get("name").toString(),transferId, location?.get("id").toString()))
            Log.i(TAG,"lange route geplant")
        }
        startTour()
    }

    fun startShortTour(){
        val listOfLocations = getImportantLocations()
        for (location in listOfLocations) {
            val transferId = getTransferId(location.get("id").toString())
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

    }

    private fun getTransfers() : Collection<JSONObject>{
        val query = "SELECT * FROM transfers"
        return database.getTableDataAsJsonWithQuery("transfers", query).values
    }

    private fun getStartingPoint(listOfLocations: Collection<JSONObject>): Location {
        val startLocations = mutableListOf<Location>()
        var isStart = false
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
            startLocations[0].name = getLocationName(startLocations[0].locationId!!)
            Log.i("Tour-Helper","The starting Point has the name ${startLocations[0].name}")
            return startLocations[0]}

            else {
                Log.e("TourHelper", "Es wurde kein Startpunkt gefunden.")
                val startingPoint = Location(null,null,null)
                return startingPoint
            }

    }

    private fun getLocationName(locationId: String): String {
        val locationQuery = "SELECT * FROM `locations` WHERE `id` = '$locationId'"
        val locations = database.getTableDataAsJsonWithQuery("locations", locationQuery)
        return locations.values.firstOrNull()?.optString("name", "")?.lowercase() ?: ""
    }

    private fun getImportantLocations(): Collection<JSONObject>{
        val query = "SELECT * FROM locations WHERE important = 1"
        return database.getTableDataAsJsonWithQuery("locations", query).values
    }

    private fun getItems(locationsId: String): List<JSONObject?>{
        val query = "SELECT * FROM items WHERE locations_id = $locationsId"
        return database.getTableDataAsJsonWithQuery("items",query).values.toList()
    }

    private fun getLocations(): Collection<JSONObject?> {
        val query = "SELECT * FROM locations"
        return database.getTableDataAsJsonWithQuery("locations", query).values
    }

    private fun getTransferId(fromId : String) : String{
        val query = "SELECT * FROM transfers WHERE location_from = '$fromId'"
        val test = database.getTableDataAsJsonWithQuery("transfers", query)
        return test.values.firstOrNull()?.get("id").toString()
    }

    private fun getTransferText(location: Location) : JSONObject? {
        val query = "SELECT * FROM texts WHERE transfers_id = '${location.transferId}'"
        return database.getTableDataAsJsonWithQuery("texts", query).values.firstOrNull()
    }
    private fun getItemTexts(itemId : String) : JSONObject? {
        val query = "SELECT * FROM texts WHERE items_id = $itemId"
        return database.getTableDataAsJsonWithQuery("texts", query).values.firstOrNull()
    }

    private fun activityForLocation(){
        val items = getItems(route[currentIndex].locationId!!)
        if(items.size < locationIndex){
            atLocation = false
            navigateToNextLocation()
        }else{
            val itemText = getItemTexts(items[locationIndex]?.get("id").toString())
            val itemMedia = database.getTableDataAsJsonWithQuery("transfers", "SELECT * FROM media WHERE text_id = '${itemText?.get("text_id")}'")

            //hier zeige die sachen an

            val ttsRequest = TtsRequest.create(itemText?.get("text").toString(), true)
            mRobot?.speak(ttsRequest)
        }
    }




    private fun executeTour() {
        mRobot = Robot.getInstance()
        Log.i(TAG, "Individuelle Tour gestartet.")

        val listener = object : OnGoToLocationStatusChangedListener {
            override fun onGoToLocationStatusChanged(location: String, status: String, descriptionId: Int, description: String) {
                Log.d(TAG, "Statusänderung bei $location - Status: $status, Beschreibung: $description")

                when (status) {
                    "complete" -> {
                        Log.i(TAG, "Position erreicht: $location")
                        speakText("Ich bin bei $location angekommen.")
                        currentIndex++
                        if (currentIndex < route.size) {
                            navigateToNextLocation()
                        } else {
                            Log.i(TAG, "Tour abgeschlossen.")
                            speakText("Die Tour ist abgeschlossen.")
                            mRobot?.removeOnGoToLocationStatusChangedListener(this)

                        }
                    }
                    "abort" -> retryNavigation(location, this)
                    else -> Log.d(TAG, "Nicht behandelter Status: $status bei $location")
                }
            }
        }

        mRobot?.addOnGoToLocationStatusChangedListener(listener)
        if (route.isNotEmpty()) {
            navigateToNextLocation()
        } else {
            Log.w(TAG, "Die Route ist leer, Tour kann nicht gestartet werden.")
        }
        val intent = Intent(context, ExecutionActivity::class.java)
        context.startActivity(intent)
    }



    // Navigiert zur nächsten Location in der Route
    private fun navigateToNextLocation() {
        val nextLocation = route[currentIndex]
        val text = getTransferText(nextLocation)
        if(text?.get("text") != null){
            speakText(text["text"].toString())
        }
        Log.i(TAG, "Navigiere zu: $nextLocation")
        mRobot?.goTo(nextLocation.name.toString())

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
            currentIndex++
            if (currentIndex < route.size) {
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
            robot.speak(ttsRequest)
        }
    }

    companion object {
        private const val EXTRA_LOCATIONS = "extra_locations"

        // Methode zum Erstellen eines Intents mit den übergebenen Daten
        fun newIntent(context: Context, locations: Map<String, JSONObject>): Intent {
            val intent = Intent(context, IndividualGuideActivity::class.java)
            val locationsMap = HashMap<String, String>()
            locations.forEach { (key, jsonObject) ->
                locationsMap[key] = jsonObject.toString()
            }
            intent.putExtra(EXTRA_LOCATIONS, locationsMap)
            return intent
        }
    }

    override fun onTtsStatusChanged(ttsRequest: TtsRequest) {
        if(ttsRequest.status==TtsRequest.Status.COMPLETED){
            Log.i(TAG,"speech completed")
            if(atLocation){
                activityForLocation()
            }
            isSpeechCompleted = true
        }
    }
}
