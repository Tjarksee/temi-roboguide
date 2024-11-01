package de.fhkiel.temi.robogguide

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.RadioButton
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
    private var route: List<String> = listOf()
    private var currentIndex = 0
    private val TAG = "MainActivity-Tour"

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
            val selectedTourType = findViewById<RadioButton>(tourLengthGroup.checkedRadioButtonId)
            val listOfLocations = database.getTransferDataAsJson()

            // Wähle die richtige Route basierend auf dem RadioButton
            route = if (selectedTourType.id == R.id.shortTour) {
                // Wenn kurze Tour (nur wichtige Locations) ausgewählt ist
                tourHelper.initializeAndPlanImportantRoute()
            } else {
                // Wenn lange Tour (alle Locations) ausgewählt ist
                tourHelper.initializeAndPlanRoute(listOfLocations)
            }

            if (route.isNotEmpty()) {
                startTour() // Starte die entsprechende Tour
            } else {
                Log.e(TAG, "Keine gültige Route gefunden, Navigation nicht möglich.")
            }

            // Wechsle zu ExecutionActivity, nachdem die Tour gestartet wurde
            intent = Intent(this, ExecutionActivity::class.java)
            startActivity(intent)
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



    // Startet die Tour mit allen Locations in der Route
    private fun startTour() {
        currentIndex = 0
        var retryCount = 0
        val maxRetries = 3
        val retryDelayMillis = 5000L // 5 Sekunden Wartezeit

        Log.i(TAG, "Tour gestartet.")

        // Definiere den Listener
        val listener = object : OnGoToLocationStatusChangedListener {
            override fun onGoToLocationStatusChanged(
                location: String,
                status: String,
                descriptionId: Int,
                description: String
            ) {
                Log.d(TAG, "Statusänderung bei $location - Status: $status, Beschreibung: $description")

                when (status) {
                    "complete" -> {
                        retryCount = 0 // Reset der Wiederholungszähler, wenn Standort erreicht wird
                        Log.i(TAG, "Position erreicht: $location")

                        try {
                            speakText("Ich bin bei $location angekommen.")
                        } catch (e: Exception) {
                            Log.e(TAG, "Fehler bei speakText (Standort erreicht): ${e.localizedMessage}")
                        }

                        // Weiter zur nächsten Location in der Route
                        currentIndex++
                        if (currentIndex < route.size) {
                            navigateToNextLocation()
                        } else {
                            Log.i(TAG, "Tour abgeschlossen.")
                            try {
                                speakText("Die Tour ist abgeschlossen.")
                            } catch (e: Exception) {
                                Log.e(TAG, "Fehler bei speakText (Tour abgeschlossen): ${e.localizedMessage}")
                            }
                            mRobot?.removeOnGoToLocationStatusChangedListener(this)
                        }
                    }
                    "abort" -> {
                        if (retryCount < maxRetries) {
                            retryCount++
                            Log.w(TAG, "Navigation zu $location abgebrochen. Versuch $retryCount von $maxRetries in ${retryDelayMillis / 1000} Sekunden.")

                            // Erneuter Versuch nach Verzögerung
                            Handler(Looper.getMainLooper()).postDelayed({
                                mRobot?.goTo(location)
                            }, retryDelayMillis)
                        } else {
                            Log.e(TAG, "Navigation zu $location fehlgeschlagen nach $maxRetries Versuchen. Fortsetzung der Tour.")
                            retryCount = 0
                            currentIndex++
                            if (currentIndex < route.size) {
                                navigateToNextLocation()
                            } else {
                                Log.i(TAG, "Tour abgeschlossen.")
                                mRobot?.removeOnGoToLocationStatusChangedListener(this)
                            }
                        }
                    }
                    else -> {
                        // Behandlung anderer Status wie START, CALCULATING, GOING, REPOSING
                        Log.d(TAG, "Nicht behandelter Status: $status bei $location")
                    }
                }
            }
        }

        // Füge den Listener zum Roboter hinzu
        mRobot?.addOnGoToLocationStatusChangedListener(listener)

        // Starte die Tour zur ersten Location und sage das an
        if (route.isNotEmpty()) {
            navigateToNextLocation()
        } else {
            Log.w(TAG, "Die Route ist leer, Tour kann nicht gestartet werden.")
        }
    }

    // Navigiert zur nächsten Location in der Route
    private fun navigateToNextLocation() {
        val nextLocation = route[currentIndex]
        Log.i(TAG, "Navigiere zu: $nextLocation")

        try {
            speakText("Ich navigiere jetzt zu $nextLocation.")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei speakText (nächster Standort): ${e.localizedMessage}")
        }

        mRobot?.goTo(nextLocation)
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