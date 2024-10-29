package de.fhkiel.temi.robogguide

import android.util.Log
import com.robotemi.sdk.Robot
import org.json.JSONObject

class TourHelper {

    private fun getStartingPoint(listOfLocations: Map<String, JSONObject>): String? {
        val startIds: MutableList<String> = mutableListOf()

        listOfLocations.forEach { (_, jsonObject) ->
            val fromId = jsonObject.optString("location_from", null)

            if (fromId != null && fromId.isNotEmpty()) {
                // Prüfen, ob "location_from" keinen Eingang hat
                if (listOfLocations.none { (_, innerJsonObject) ->
                        innerJsonObject.optString("location_to", "") == fromId
                    }) {
                    startIds.add(fromId)
                }
            } else {
                Log.e("getStartingPoint", "JSON object does not have key 'location_from'")
            }
        }

        return if (startIds.isEmpty()) {
            Log.e("startpunkt", "There is no start id")
            null
        } else {
            Log.i("startpunkt", "Der Startpunkt ist ${startIds[0]}")
            startIds[0]
        }
    }

    fun startFullTour(robot: Robot, listOfLocations: Map<String, JSONObject>) {
        val startingPoint = getStartingPoint(listOfLocations)
        if (startingPoint == null) {
            Log.e("TourHelper", "Failed to start tour: No valid start point found.")
            return
        }

        val locations = generateTourPath(listOfLocations, startingPoint)
        if (locations.isNotEmpty()) {
            val success = robot.patrol(locations, false, 1, 5)
            if (success) {
                Log.i("TourHelper", "Full tour started successfully.")
            } else {
                Log.e("TourHelper", "Failed to start full tour.")
            }
        } else {
            Log.e("TourHelper", "No valid locations available for full tour.")
        }
    }

    fun startImportantTour(robot: Robot, listOfLocations: Map<String, JSONObject>) {
        val importantLocations = getImportantLocations(listOfLocations)
        val startingPoint = getStartingPoint(importantLocations)

        if (startingPoint == null) {
            Log.e("TourHelper", "No valid start point found for the important tour.")
            return
        }

        val locations = generateTourPath(importantLocations, startingPoint)

        if (locations.isNotEmpty()) {
            val success = robot.patrol(locations, false, 1, 5)
            if (success) {
                Log.i("TourHelper", "Important tour started successfully.")
            } else {
                Log.e("TourHelper", "Failed to start important tour.")
            }
        } else {
            Log.e("TourHelper", "No valid important locations available for tour.")
        }
    }

    private fun generateTourPath(listOfLocations: Map<String, JSONObject>, start: String): List<String> {
        val tourPath = mutableListOf<String>()
        var currentLocation = start

        while (currentLocation.isNotEmpty()) {
            tourPath.add(currentLocation)
            currentLocation = listOfLocations[currentLocation]?.optString("location_to", "") ?: ""
        }

        return tourPath
    }

    private fun getImportantLocations(listOfLocations: Map<String, JSONObject>): Map<String, JSONObject> {
        return listOfLocations.filterValues { it.optInt("important", 0) == 1 }
    }
}
