package de.fhkiel.temi.robogguide

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import com.robotemi.sdk.listeners.OnRobotReadyListener
import com.robotemi.sdk.map.MapDataModel
import com.robotemi.sdk.permission.OnRequestPermissionResultListener
import com.robotemi.sdk.permission.Permission
import de.fhkiel.temi.robogguide.database.DatabaseHelper
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import de.fhkiel.temi.robogguide.TourHelper

class MainActivity : AppCompatActivity(), OnRobotReadyListener, OnRequestPermissionResultListener {
    private var mRobot: Robot? = null
    private lateinit var database: DatabaseHelper
    private lateinit var tourHelper: TourHelper
    private var tourLengthGroupSelected = false
    private var textLengthGroupSelected = false

    private val singleThreadExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val tourLengthGroup = findViewById<RadioGroup>(R.id.tourLengthRadioGroup)
        val textLengthGroup = findViewById<RadioGroup>(R.id.textLengthRadioGroup)
        val startTourButton = findViewById<Button>(R.id.btnStartTour)
        // ---- DATABASE ACCESS ----
        val databaseName = "roboguide.db"
        database = DatabaseHelper.getInstance(this, databaseName)
        try {
            database.initializeDatabase() // Initialize the database and copy it from assets

            /*
            // EXAMPLE CODE TO ONLY COPY DATABASE TO DIRECTLY USE THE DATABASE FILE FOR ORM
            database.initializeDatabase(withOpen = false)
            val dbFile = database.getDBFile()
            val sqLiteDatabase = database.getDatabase()
            */

            // use json code to get database objects


        } catch (e: IOException) {
            e.printStackTrace()
        }
        tourHelper = TourHelper(database)
        val transfers = database.getTransferDataAsJson()
        Log.d("MainActivity", "Loaded transfers from DB: $transfers")



        findViewById<Button>(R.id.btnStartTour).setOnClickListener {
            // Startet die ExecutionActivity
            intent = Intent(this, ExecutionActivity::class.java)
            startActivity(intent)

            // TourHelper verwenden, um die Transferdaten zu laden und den Startpunkt zu initialisieren
            val listOfLocations = database.getTransferDataAsJson() // Holen der Transferdaten als JSON
            tourHelper.initializeStartLocation(listOfLocations) // Startpunkt festlegen

            // Navigation nur starten, wenn ein gültiger Startpunkt gefunden wurde
            var nextLocationName = tourHelper.getNextLocationName(listOfLocations)
            if (nextLocationName.isNotEmpty()) {
                // Schleife, um den Roboter zu jeder Location zu navigieren, bis kein weiterer Punkt vorhanden ist
                while (nextLocationName.isNotEmpty()) {
                    mRobot?.goTo("home base")

                    // Warte, bis der Roboter die aktuelle Location erreicht hat (abhängig von der API)
                    // Beispiel (sollte angepasst werden, um die tatsächliche Ankunft des Roboters zu prüfen):
                    Thread.sleep(5000) // Wartezeit simulieren

                    // Hole den Namen der nächsten Location
                    nextLocationName = tourHelper.getNextLocationName(listOfLocations)
                }
                Log.i("MainActivity", "Tour abgeschlossen, keine weiteren Ziel-Standorte.")
            } else {
                Log.e("MainActivity", "Keine gültige Startlocation gefunden, Navigation nicht möglich.")
            }
        }



        findViewById<Button>(R.id.btnList).setOnClickListener {
            val locations = database.getLocationDataAsJson()
            val intent = IndividualGuideActivity.newIntent(this, locations)
            startActivity(intent)
        }

        tourLengthGroup.setOnCheckedChangeListener { _, _ ->
            tourLengthGroupSelected = true
            checkIfBothSelected(startTourButton)
        }
        textLengthGroup.setOnCheckedChangeListener { _, _ ->
            textLengthGroupSelected = true
            checkIfBothSelected(startTourButton)
        }

    }


    private fun checkIfBothSelected(nextButton: Button) {
        if (tourLengthGroupSelected && textLengthGroupSelected) {
            nextButton.isEnabled = true
        }
    }

    override fun onStart() {
        super.onStart()
        Robot.getInstance().addOnRobotReadyListener(this)
        Robot.getInstance().addOnRequestPermissionResultListener(this)
    }

    override fun onStop() {
        super.onStop()
        Robot.getInstance().removeOnRobotReadyListener(this)
        Robot.getInstance().removeOnRequestPermissionResultListener(this)
    }
     fun onGoToLocationStatusChanged(location: String, status: String, descriptionId: Int, trackingId: String) {
        when (status) {
            OnGoToLocationStatusChangedListener.START -> {
                Log.i("MainActivity", "Der Roboter navigiert zu: $location")
            }
            OnGoToLocationStatusChangedListener.COMPLETE -> {
                Log.i("MainActivity", "Der Roboter hat die Position erreicht: $location")
            }
            OnGoToLocationStatusChangedListener.ABORT -> {
                Log.e("MainActivity", "Die Navigation zum Standort $location wurde abgebrochen.")
            }
            else -> {
                Log.w("MainActivity", "Unbekannter Navigationsstatus: $status für Standort: $location")
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        database.closeDatabase()
    }

    override fun onRobotReady(isReady: Boolean) {
        if (isReady){
            mRobot = Robot.getInstance()

            // ---- DISABLE TEMI UI ELEMENTS ---
            // mRobot?.hideTopBar()        // hide top action bar

            // hide pull-down bar
            // val activityInfo: ActivityInfo = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)
            // Robot.getInstance().onStart(activityInfo)

            showMapData()
        }
    }


    /**
     * Speaks a text using the tmi tts
     * @param text                          [String] text to speak
     * @param isShowOnConversationLayer     [Boolean] true (default) to show conversation layer while speaking, false to hide it.
     */
    private fun speakText(text: String, isShowOnConversationLayer: Boolean = true){
        mRobot?.let { robot ->
            val ttsRequest: TtsRequest = TtsRequest.create(speech = text, isShowOnConversationLayer = isShowOnConversationLayer)
            robot.speak(ttsRequest)
        }
    }

    private fun getMapName() : String{
        val mapName = mRobot?.getMapData()!!.mapName
        return mapName
    }

    /**
     * Uses temi tts to speak every listed location on the active map
     */
    private fun speakLocations(){
        mRobot?.let { robot ->
            var text = "Das sind alle Orte an die ich gehen kann:"
            robot.locations.forEach {
                text += " $it,"
            }
            speakText(text, isShowOnConversationLayer = false)
        }
    }

    /**
     * Uses temi sdk function to go to home base
     */
    private fun gotoHomeBase(){
        mRobot?.goTo(location = "home base")
    }


    /**
     * Gets the [MapDataModel] of the robot and shows its data in Logcat
     */
    private fun showMapData(){
        singleThreadExecutor.execute {
            getMap()?.let { mapDataModel ->
                Log.i("Map-mapImage", mapDataModel.mapImage.typeId)
                Log.i("Map-mapId", mapDataModel.mapId ?: "Map ID not found")
                Log.i("Map-mapInfo", mapDataModel.mapInfo.toString())
                Log.i("Map-greenPaths", mapDataModel.greenPaths.toString())
                Log.i("Map-virtualWalls", mapDataModel.virtualWalls.toString())
                Log.i("Map-locations", mapDataModel.locations.toString())
            }
            return@execute
        }

        Log.i( "Map-List", "${mRobot?.getMapList()}")
    }

    /**
     * Gets the robot [MapDataModel] data.
     * Therefor it checks if the required permission is granted
     * @return      [MapDataModel] of robots map data, or null if no robot found, no permissions are granted or data is null.
     */
    private fun getMap(): MapDataModel? {
        // check if permission is missing
        if (requestPermissionsIfNeeded(Permission.MAP, REQUEST_CODE_MAP) == true){
            return null
        }

        return mRobot?.let { robot ->
            return robot.getMapData()
        }
    }


    /**
     * Requests a permission
     * @param   permission      [Permission] robot temi permission to request
     * @param   requestCode     [Int] request code to identify request response
     * @return                  null if no robot accessible,  true if [Permission] was requested, false if [Permission] is already granted.
     */
    @Suppress("SameParameterValue")
    private fun requestPermissionsIfNeeded(permission: Permission, requestCode: Int): Boolean? {
        return mRobot?.let { robot ->
            if (robot.checkSelfPermission(permission) == Permission.GRANTED) {
                return@let false
            }

            robot.requestPermissions(listOf(permission), requestCode)

            return true
        }
    }

    /**
     * Permission request callback
     * @param   permission      [Permission] that was requested.
     * @param   grantResult     [Int] with request result (see [Permission] constants)
     * @param   requestCode     [Int] custom request code set in [Permission] request
     */
    override fun onRequestPermissionResult(
        permission: Permission,
        grantResult: Int,
        requestCode: Int,
    ) {
        Log.d("PERMISSION RESULT", "permission $permission with request code $requestCode with result = $grantResult")

        when (permission){
            Permission.MAP -> when (requestCode){
                REQUEST_CODE_MAP -> {
                    showMapData()
                }
            }

            // Permission.FACE_RECOGNITION -> TODO
            // Permission.SETTINGS -> TODO
            // Permission.UNKNOWN -> TODO

            else -> {
                // do nothing
            }
        }
    }

    companion object{
        const val REQUEST_CODE_MAP = 10
    }

}