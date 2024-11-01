package de.fhkiel.temi.robogguide

import android.util.Log
import de.fhkiel.temi.robogguide.database.DatabaseHelper
import org.json.JSONObject

class TourHelper(private val database: DatabaseHelper) {

    private var currentLocationId: String? = null // Speichert die aktuelle Position des Roboters
    private val route: MutableList<String> = mutableListOf() // Array zum Speichern der Locations in der Reihenfolge

    // Methode zum Ermitteln des Startpunkts für die vollständige Route (ohne die Route zu planen)
    fun getStartingPoint(listOfLocations: Map<String, JSONObject>): String {
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

    // Methode zum Initialisieren der vollständigen Tour
    fun initializeAndPlanRoute(listOfLocations: Map<String, JSONObject>): List<String> {
        val startingPointName = getStartingPoint(listOfLocations)
        if (startingPointName.isNotEmpty()) {
            route.add(startingPointName) // Füge den Startpunkt zur Route hinzu
            findRoute(listOfLocations)   // Starte die Routenplanung ab dem Startpunkt
        }
        return route
    }

    fun initializeAndPlanImportantRoute(): List<String> {
        val importantLocations = getImportantLocations()
        Log.d("TourHelper", "Geladene wichtige Locations: $importantLocations")

        // Lade die Transfers für die wichtigen Locations
        val importantTransfers = getImportantTransfers(importantLocations)

        val startingPointName = getStartingPoint(importantTransfers)
        if (startingPointName.isNotEmpty()) {
            route.add(startingPointName)
            findRoute(importantTransfers) // Verwende die gleiche Methode für die Routenplanung
        }

        Log.i("TourHelper", "Geplante wichtige Route: $route")
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

    private fun getImportantLocations(): Map<String, JSONObject> {
        val query = "SELECT * FROM locations WHERE important = 1"
        return database.getTableDataAsJsonWithQuery("locations", query)
    }

    // Neue Methode, um nur die Transfers für wichtige Locations zu erhalten
    private fun getImportantTransfers(importantLocations: Map<String, JSONObject>): Map<String, JSONObject> {
        val locationIds = importantLocations.keys.joinToString(",") { "'$it'" }
        val query = "SELECT * FROM transfers WHERE location_from IN ($locationIds) AND location_to IN ($locationIds)"
        return database.getTableDataAsJsonWithQuery("transfers", query)
    }
}
