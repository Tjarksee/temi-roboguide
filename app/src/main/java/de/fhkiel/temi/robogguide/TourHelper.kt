package de.fhkiel.temi.robogguide
import android.content.Context
import android.util.Log
import de.fhkiel.temi.robogguide.database.DatabaseHelper
import org.json.JSONObject
import java.io.IOException


class TourHelper(private val database: DatabaseHelper) {

    private var currentLocationId: String? = null // Speichert die aktuelle Position des Roboters

    // Methode zum Initialisieren des Startpunkts (nur am Anfang der Tour aufrufen)
    fun initializeStartLocation(listOfLocations: Map<String, JSONObject>) {
        val startIds: MutableList<String> = mutableListOf()

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
            currentLocationId = startIds[0] // Setze den Startpunkt als `currentLocationId`
            Log.i("TourHelper", "Der Startpunkt ist $currentLocationId")
        } else {
            Log.e("TourHelper", "Es wurde kein Startpunkt gefunden.")
        }
    }

    // Methode zum Abrufen der nächsten Ziel-Location basierend auf der aktuellen Position
    fun getNextLocationName(listOfLocations: Map<String, JSONObject>): String {
        if (currentLocationId == null) {
            Log.e("TourHelper", "Startpunkt ist nicht initialisiert.")
            return ""
        }

        // Suche die `location_to` für die aktuelle `location_from`
        val nextTransfer = listOfLocations.values.firstOrNull { it.optString("location_from") == currentLocationId }
        val nextLocationId = nextTransfer?.optString("location_to", "")

        if (nextLocationId.isNullOrEmpty()) {
            Log.e("TourHelper", "Keine gültige `location_to`-ID für location_from: $currentLocationId gefunden.")
            return ""
        }

        // Hole den Namen der Ziel-Location (`location_to`) aus der `locations`-Tabelle
        val locationQuery = "SELECT * FROM `locations` WHERE `id` = '$nextLocationId'"
        val locations = database.getTableDataAsJsonWithQuery("locations", locationQuery)

        if (locations.isEmpty()) {
            Log.e("TourHelper", "Keine Location für location_to: $nextLocationId gefunden.")
            return ""
        }

        val location = locations.values.firstOrNull()
        val locationName = location?.optString("name", "")

        if (locationName.isNullOrEmpty()) {
            Log.e("TourHelper", "Kein Name für location_to $nextLocationId gefunden.")
            return ""
        }

        // Aktualisiere `currentLocationId` für den nächsten Schritt
        currentLocationId = nextLocationId
        Log.i("TourHelper", "Name der nächsten Ziel-Location (location_to): $locationName")

        return locationName
    }
}



