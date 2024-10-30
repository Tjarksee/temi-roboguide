package de.fhkiel.temi.robogguide
import android.content.Context
import android.util.Log
import de.fhkiel.temi.robogguide.database.DatabaseHelper
import org.json.JSONObject
import java.io.IOException

class TourHelper(private val database: DatabaseHelper) {

    private var currentLocationId: String? = null // Speichert die aktuelle Position des Roboters
    private val route: MutableList<String> = mutableListOf() // Array zum Speichern der Locations in der Reihenfolge

    // Methode zum Ermitteln des Startpunkts (ohne die Route zu planen)
    fun getStartingPoint(listOfLocations: Map<String, JSONObject>): String {
        val startIds = mutableListOf<String>()

        // Bestimme die `location_from` ohne passende `location_to` als Startpunkt
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

        // Falls ein Startpunkt gefunden wurde, setze ihn als aktuellen Standort und gib seinen Namen zurück
        return if (startIds.isNotEmpty()) {
            currentLocationId = startIds[0]
            getLocationName(currentLocationId!!).also {
                Log.i("TourHelper", "Der Startpunkt ist $currentLocationId mit dem Namen: $it")
            }
        } else {
            Log.e("TourHelper", "Es wurde kein Startpunkt gefunden.")
            ""
        }
    }

    // Methode zum Initialisieren des Startpunkts und Festlegen der Route
    fun initializeAndPlanRoute(listOfLocations: Map<String, JSONObject>): List<String> {
        // Verwende `getStartingPoint`, um den Startpunkt zu bestimmen und setze `currentLocationId`
        val startingPointName = getStartingPoint(listOfLocations)
        if (startingPointName.isNotEmpty()) {
            route.add(startingPointName) // Füge den Startpunkt zur Route hinzu
            findRoute(listOfLocations)   // Starte die Routenplanung ab dem Startpunkt
        }
        return route
    }

    // Methode zur Ermittlung der Route
    private fun findRoute(listOfLocations: Map<String, JSONObject>) {
        var nextLocationId: String? = currentLocationId

        while (nextLocationId != null) {
            // Suche die `location_to` für die aktuelle `location_from`
            val nextTransfer = listOfLocations.values.firstOrNull { it.optString("location_from") == nextLocationId }
            nextLocationId = nextTransfer?.optString("location_to", null)

            if (nextLocationId != null) {
                // Hole den Namen der `location_to` und füge ihn der Route hinzu
                val locationName = getLocationName(nextLocationId)
                if (locationName.isNotEmpty()) {
                    route.add(locationName)
                    Log.i("TourHelper", "Gefundene nächste Ziel-Location: $locationName")
                } else {
                    Log.e("TourHelper", "Kein Name für location_to $nextLocationId gefunden.")
                }
            }
        }
    }

    // Hilfsmethode zum Abrufen des Location-Namens anhand der ID
    private fun getLocationName(locationId: String): String {
        val locationQuery = "SELECT * FROM `locations` WHERE `id` = '$locationId'"
        val locations = database.getTableDataAsJsonWithQuery("locations", locationQuery)

        return locations.values.firstOrNull()?.optString("name", "") ?: ""
    }
}

