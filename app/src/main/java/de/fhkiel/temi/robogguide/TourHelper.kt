package de.fhkiel.temi.robogguide

import android.util.Log
import de.fhkiel.temi.robogguide.database.DatabaseHelper
import org.json.JSONObject

class TourHelper(private val database: DatabaseHelper) {

    private var currentLocationId: String? = null // Speichert die aktuelle Position des Roboters
    private val route: MutableList<String> = mutableListOf() // Array zum Speichern der Locations in der Reihenfolge


    // Methode zum Ermitteln des Startpunkts für die vollständige Route
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

    // Methode zur Planung einer individuellen Route
    fun planIndividualRoute(selectedLocations: List<String>): List<String> {
            val locationNames = selectedLocations.mapNotNull { id -> getLocationName(id) }

            val selectedTransfers = getTransfersForSelectedLocations(locationNames)
        if (selectedTransfers.isEmpty()) {
            Log.e("TourHelper", "Keine Transfers für die individuellen Locations gefunden.")
        } else {
            Log.d("TourHelper", "Transfers gefunden: $selectedTransfers")
        }

        val startPoint = getStartingPoint(selectedTransfers)
        if (startPoint.isNotEmpty()) {
            route.add(startPoint)
            findRoute(selectedTransfers)
        } else {
            Log.e("TourHelper", "Kein Startpunkt gefunden für die Transfers.")
        }

        addUnconnectedLocations(locationNames)
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
                    route.add(locationName)
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

    private fun addUnconnectedLocations(selectedLocations: List<String>) {
        selectedLocations.forEach { locationName ->
            if (!route.contains(locationName)) {
                route.add(locationName)
                Log.i("TourHelper", "Unverbundene Location zur Route hinzugefügt: $locationName")
            }
        }
    }
}
