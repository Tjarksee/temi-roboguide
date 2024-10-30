package de.fhkiel.temi.robogguide
import android.content.Context
import android.util.Log
import de.fhkiel.temi.robogguide.database.DatabaseHelper
import org.json.JSONObject
import java.io.IOException

class TourHelper(private val database: DatabaseHelper) {

    fun getStartingPoint(listOfLocations: Map<String, JSONObject>): String {
        val startIds: MutableList<String> = mutableListOf()
        var check = false

        // Ermittlung der `location_from`-IDs, die als Startpunkte infrage kommen
        listOfLocations.forEach { (_, jsonObject) ->
            val fromId = jsonObject.optString("location_from")
            check = false

            listOfLocations.forEach { (_, innerJsonObject) ->
                if (fromId == innerJsonObject.optString("location_to")) {
                    check = true
                }
            }

            if (!check) {
                startIds.add(fromId)
            }
        }

        if (startIds.isEmpty()) {
            Log.e("startpunkt", "Es wurde kein Startpunkt gefunden.")
            return ""
        }

        Log.i("startpunkt", "Der Startpunkt ist ${startIds[0]}")

        // Erweiterung: Suche die `location_to` für den ermittelten Startpunkt (`location_from`)
        val locationFromId = startIds[0] // Der erste gefundene Startpunkt wird verwendet
        val nextTransfer = listOfLocations.values.firstOrNull { it.optString("location_from") == locationFromId }
        val nextLocationId = nextTransfer?.optString("location_to", "")

        if (nextLocationId.isNullOrEmpty()) {
            Log.e("TourHelper", "Keine gültige `location_to`-ID für location_from: $locationFromId gefunden.")
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

        Log.i("TourHelper", "Name der nächsten Ziel-Location (location_to): $locationName")

        return locationName
    }
}
