package de.fhkiel.temi.robogguide
import android.content.Context
import android.util.Log
import de.fhkiel.temi.robogguide.database.DatabaseHelper
import org.json.JSONObject
import java.io.IOException


class TourHelper{
    lateinit var planedTour : Any
    private lateinit var database: DatabaseHelper


     fun getStartingPoint(listOfLocations: Map<String, JSONObject>): String {
        val startIds: MutableList<String> = mutableListOf()
        var check = false
        listOfLocations.forEach { (key, jsonObject) ->
            val fromId = jsonObject["location_from"]
            listOfLocations.forEach { (innerKey, innerJsonObject) ->
                if (fromId == innerJsonObject["location_to"]) {
                    check = true
                }
            }
            if (!check) {
                val start = jsonObject["location_from"]
                startIds.add(start.toString())
            }
        }
        if (startIds.isEmpty()) {
            Log.e("startpunkt", "there is no start id")
        }
        Log.i("startpunkt", "der Startpunkt ist ${startIds.toString()}")

        // Erweiterung: Hole den Namen der Location aus der 'locations'-Tabelle basierend auf 'location_to'
        val locationToId = startIds[0]
        val locationQuery = "SELECT * FROM `locations` WHERE `id` = '$locationToId'"
        val locations = database.getTableDataAsJsonWithQuery("locations", locationQuery)

        if (locations.isEmpty()) {
            Log.e("TourHelper", "Keine Location für location_to: $locationToId gefunden.")
            return startIds[0]
        }

        val location = locations.values.first()
        val locationName = location.optString("name", "")

        if (locationName.isEmpty()) {
            Log.e("TourHelper", "Kein Name für location_to $locationToId gefunden.")
            return startIds[0]
        } else {
            Log.i("TourHelper", "Name für location_to $locationToId: $locationName")
        }

        return locationName
    }
}
