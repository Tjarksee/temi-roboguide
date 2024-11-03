package de.fhkiel.temi.robogguide

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat.startActivity
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import de.fhkiel.temi.robogguide.database.DatabaseHelper
import org.json.JSONObject


class TourHelper(private val database: DatabaseHelper,private val context: Context) {
    private var mRobot: Robot? = null
    private var currentLocationId: String? = null // Speichert die aktuelle Position des Roboters
    var route: MutableList<Location> = mutableListOf() // Array zum Speichern der Locations in der Reihenfolge

    private var currentIndex = 0
    private val TAG = "TourHelper-Tour"

    // Methode zum Ermitteln des Startpunkts für die vollständige Route
    fun getStartingPoint(listOfLocations: Map<String, JSONObject>): Location {
        val startIds = mutableListOf<String>()
        listOfLocations.forEach { (_, jsonObject) ->
            val fromId = jsonObject.optString("location_from")
            var isStart = true

            listOfLocations.forEach { (_, innerJsonObject) ->
                if (fromId == innerJsonObject.optString("location_to")) {
                    isStart = false
                }
            }

            if (isStart) {
                startIds.add(fromId)
            }
        }

       if (startIds.isNotEmpty()) {
            val locationId = startIds[0]
            val name = getLocationName(locationId)
            val startingPoint = Location(name, null ,locationId)
            return startingPoint
        } else {
            Log.e("TourHelper", "Es wurde kein Startpunkt gefunden.")
            val startingPoint = Location(null,null,null)
            return startingPoint
        }
    }

    // Methode zum Initialisieren der vollständigen Tour
    fun initializeAndPlanRoute(listOfLocations: Map<String, JSONObject>): List<Location> {
        val startingPointName = getStartingPoint(listOfLocations)
        if (startingPointName.name !=null) {
            route.add(startingPointName) // Füge den Startpunkt zur Route hinzu
            findRoute(listOfLocations)   // Starte die Routenplanung ab dem Startpunkt
        }
        return route
    }

    fun initializeAndPlanImportantRoute(): List<Location> {
        val importantLocations = getImportantLocations()
        Log.d("TourHelper", "Geladene wichtige Locations: $importantLocations")

        // Lade die Transfers für die wichtigen Locations
        val importantTransfers = getImportantTransfers(importantLocations)

        val startingPointName = getStartingPoint(importantTransfers)
        if (startingPointName.name!=null) {
            route.add(startingPointName)
            findRoute(importantTransfers) // Verwende die gleiche Methode für die Routenplanung
        }

        Log.i("TourHelper", "Geplante wichtige Route: $route")
        return route
    }

    // Methode zur Planung einer individuellen Route
    fun planIndividualRoute(selectedLocations: List<String>): List<Location> {
            val locationNames = selectedLocations.mapNotNull { id -> getLocationName(id) }

            val selectedTransfers = getTransfersForSelectedLocations(locationNames)
        if (selectedTransfers.isEmpty()) {
            Log.e("TourHelper", "Keine Transfers für die individuellen Locations gefunden.")
        } else {
            Log.d("TourHelper", "Transfers gefunden: $selectedTransfers")
        }

        val startPoint = getStartingPoint(selectedTransfers)
        if (startPoint.name != null) {
            route.add(startPoint)
            findRoute(selectedTransfers)
        } else {
            Log.e("TourHelper", "Kein Startpunkt gefunden für die Transfers.")
        }

        //addUnconnectedLocations(locationNames)
        Log.i("TourHelper", "Final route including unconnected locations: $route")
        return route
    }

    private fun findRoute(listOfLocations: Map<String, JSONObject>) {
        var nextLocationId: String? = currentLocationId

        while (nextLocationId != null) {
            val nextTransfer = listOfLocations.values.firstOrNull { it.optString("location_from") == nextLocationId }
            nextLocationId = nextTransfer?.optString("location_to", null)

            if (nextLocationId != null) {
                val locationName = getLocationName(nextLocationId)

                if (locationName.isNotEmpty()) {
                    val transferId = nextTransfer?.get("id").toString()
                    val location = Location(locationName, transferId,nextLocationId)
                    route.add(location)
                    Log.i("TourHelper", "Gefundene nächste Ziel-Location: $locationName")
                } else {
                    Log.e("TourHelper", "Kein Name für location_to $nextLocationId gefunden.")
                }
            }
        }
    }

    private fun getLocationName(locationId: String): String {
        val locationQuery = "SELECT * FROM `locations` WHERE `id` = '$locationId'"
        val locations = database.getTableDataAsJsonWithQuery("locations", locationQuery)
        return locations.values.firstOrNull()?.optString("name", "")?.lowercase() ?: ""
    }

    private fun getImportantLocations(): Map<String, JSONObject> {
        val query = "SELECT * FROM locations WHERE important = 1"
        return database.getTableDataAsJsonWithQuery("locations", query)
    }

    private fun getTransferText(locationName: String) : Map<String, JSONObject>{
        var query = "SELECT * FROM locations WHERE name = '$locationName'"
        val locationInformation = database.getTableDataAsJsonWithQuery("locations", query)
        val locationId = locationInformation.keys.first().toString()
        query = "SELECT * FROM transfers WHERE location_to ='$locationId'"
        val transferInformation = database.getTableDataAsJsonWithQuery("transfers",query)
        val temp =  transferInformation.values.firstOrNull()
        val transferId = temp?.get("id")
        query = "SELECT * FROM texts WHERE transfer_id = '$transferId'"
        return database.getTableDataAsJsonWithQuery("texts", query)
    }
    private fun getImportantTransfers(importantLocations: Map<String, JSONObject>): Map<String, JSONObject> {
        val locationIds = importantLocations.keys.joinToString(",") { "'$it'" }
        val query = "SELECT * FROM transfers WHERE location_from IN ($locationIds) AND location_to IN ($locationIds)"
        return database.getTableDataAsJsonWithQuery("transfers", query)
    }

    private fun getTransfersForSelectedLocations(selectedLocations: List<String>): Map<String, JSONObject> {
        val locationNames = selectedLocations.joinToString("','", prefix = "'", postfix = "'")
        val query = """
            SELECT t.id, t.title, lf.id AS location_from, lt.id AS location_to
            FROM transfers t
            JOIN locations lf ON t.location_from = lf.id
            JOIN locations lt ON t.location_to = lt.id
            WHERE lf.name IN ($locationNames) AND lt.name IN ($locationNames)
        """
        Log.d("TourHelper", "Executing query for selected locations with names: $query")

        val transfers = database.getTableDataAsJsonWithQuery("transfers", query)
        Log.d("TourHelper", "Transfers retrieved with IDs: $transfers")
        return transfers
    }

    //private fun addUnconnectedLocations(selectedLocations: List<String>) {
    //    selectedLocations.forEach { locationName ->
    //        if (!route.contains(locationName)) {
    //            route.add(locationName)
    //            Log.i("TourHelper", "Unverbundene Location zur Route hinzugefügt: $locationName")
    //        }
    //    }
    //}
    // Startet die Tour mit allen Locations in der Route
    // Startet die Tour mit den ausgewählten Locations
    fun startTour() {
        currentIndex = 0
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

        Log.i(TAG, "Navigiere zu: $nextLocation")
        speakText("text")
        mRobot?.goTo(nextLocation.name.toString())

    }

    // Versucht, die Navigation bei einem Abbruch neu zu starten
    private fun retryNavigation(location: String, listener: OnGoToLocationStatusChangedListener) {
        var retryCount = 0
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
}
