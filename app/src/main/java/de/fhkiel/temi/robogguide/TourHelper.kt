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

    // Methode zur Ermittlung der vollständigen Route
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

    // Nur wichtige Locations für eine alternative Tour
    fun initializeAndPlanImportantRoute(): List<String> {
        val importantRoute = mutableListOf<String>()
        val importantLocations = getImportantLocations()

        // Bestimme den Startpunkt der wichtigen Tour
        val startingPointName = getStartingPoint(importantLocations)
        if (startingPointName.isNotEmpty()) {
            importantRoute.add(startingPointName) // Füge den Startpunkt zur wichtigen Route hinzu
            findImportantRoute(importantLocations, importantRoute) // Plane die Route nur mit wichtigen Locations
        }

        return importantRoute
    }

    // Methode zur Ermittlung der Route nur mit wichtigen Locations
    private fun findImportantRoute(importantLocations: Map<String, JSONObject>, importantRoute: MutableList<String>) {
        var nextLocationId: String? = currentLocationId

        while (nextLocationId != null) {
            val nextTransfer = importantLocations.values.firstOrNull { it.optString("location_from") == nextLocationId }
            nextLocationId = nextTransfer?.optString("location_to", null)

            if (nextLocationId != null) {
                val locationName = getLocationName(nextLocationId)
                if (locationName.isNotEmpty()) {
                    importantRoute.add(locationName)
                    Log.i("TourHelper", "Gefundene nächste wichtige Ziel-Location: $locationName")
                } else {
                    Log.e("TourHelper", "Kein Name für wichtige location_to $nextLocationId gefunden.")
                }
            }
        }
    }

    // Hilfsmethode: Nur wichtige Locations (`important = 1`)
    private fun getImportantLocations(): Map<String, JSONObject> {
        val query = """
            SELECT id, name
            FROM locations
            WHERE important = 1
        """.trimIndent()

        return database.getTableDataAsJsonWithQuery("locations", query)
    }
}
