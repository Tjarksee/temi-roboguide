package de.fhkiel.temi.robogguide
import android.content.Context
import android.util.Log
import de.fhkiel.temi.robogguide.database.DatabaseHelper
import org.json.JSONObject
import java.io.IOException


class TourHelper{
    lateinit var planedTour : Any
    private lateinit var database: DatabaseHelper


    private fun getStartingPoint(listOfLocations: Map<String, JSONObject>): String {
        val startIds: MutableList<String> = mutableListOf()
        var check = false
        listOfLocations.forEach { (key, jsonObject) ->
            val fromId = jsonObject["location_from"]
            listOfLocations.forEach{(key,jsonObject) ->
                if(fromId == jsonObject["location_to"]) {
                    check = true
                }
            }
            if(!check){
                val start = jsonObject["location_from"]
                startIds.add(start.toString())
            }
        }
        if(startIds.isEmpty()){
            Log.e("startpunkt", "there is no start id")
        }
        Log.i("startpunkt", "der Startpunkt ist ${startIds.toString()}")
        return startIds[0]
    }
}