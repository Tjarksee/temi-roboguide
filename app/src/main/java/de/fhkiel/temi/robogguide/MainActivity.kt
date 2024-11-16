package de.fhkiel.temi.robogguide

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.robotemi.sdk.Robot
import com.robotemi.sdk.listeners.OnRobotReadyListener
import com.robotemi.sdk.map.MapDataModel
import com.robotemi.sdk.permission.OnRequestPermissionResultListener
import com.robotemi.sdk.permission.Permission
import de.fhkiel.temi.robogguide.database.DatabaseHelper
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), OnRobotReadyListener, OnRequestPermissionResultListener {
    private var tourService: TourService? = null
    private var isBound = false
    private var mRobot: Robot? = null
    private lateinit var database: DatabaseHelper
    private var tourLengthGroupSelected = false
    private var textLengthGroupSelected = false
    private lateinit var sharedPreferences: SharedPreferences

    private val TAG = "MainActivity-Tour"

    private val singleThreadExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val tourLengthGroup = findViewById<RadioGroup>(R.id.tourLengthRadioGroup)
        val textLengthGroup = findViewById<RadioGroup>(R.id.textLengthRadioGroup)
        val startTourButton = findViewById<Button>(R.id.btnStartTour)
        sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
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
        val transfers = database.getTransferDataAsJson()
        Log.d("MainActivity", "Loaded transfers from DB: $transfers")

// Check if PLACE_ID is set
        val placeId = sharedPreferences.getInt("PLACE_ID", -1)
        if (placeId == -1) {
            // Redirect to PlaceSelectionActivity if PLACE_ID is not set
            redirectToPlaceSelection()
        } else {
            Log.i(TAG, "PLACE_ID geladen: $placeId")
        }
        // Button to switch back to PlaceSelectionActivity
        findViewById<Button>(R.id.btnBackToSelection).setOnClickListener {
            sharedPreferences.edit().remove("PLACE_ID").apply()
            redirectToPlaceSelection()
        }



        findViewById<Button>(R.id.btnStartTour).setOnClickListener {
            val selectedTourType = findViewById<RadioButton>(tourLengthGroup.checkedRadioButtonId)

            // Wähle die richtige Route basierend auf dem RadioButton
            if (selectedTourType.id == R.id.shortTour) {
                Log.i(TAG,"Kurze Tour wurde gestartet")
               tourService!!.startTour("short")
            } else {
                Log.i(TAG,"Lange tour wurde gestartet")
                tourService!!.startTour("long")
            }
            // Starte die ExecutionActivity nach dem Tourstart
            val intent = Intent(this, ExecutionActivity::class.java)
            startActivity(intent)

        }

        findViewById<Button>(R.id.btnList).setOnClickListener {
            val placeId = sharedPreferences.getInt("PLACE_ID", -1)
            if (placeId == -1) {
                Log.e(TAG, "PLACE_ID ist nicht gesetzt. Redirecting to PlaceSelectionActivity.")
                redirectToPlaceSelection()
                return@setOnClickListener
            }

            val locations = database.getLocationDataAsJson(placeId)
            Log.d(TAG, "Gefilterte Locations für placeId $placeId: $locations")
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

    private fun redirectToPlaceSelection() {
        val intent = Intent(this, PlaceSelectionActivity::class.java)
        startActivity(intent)
        finish()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as TourService.TourBinder
            tourService = binder.getService()
            isBound = true

            // PLACE_ID aus SharedPreferences laden
            val placeId = sharedPreferences.getInt("PLACE_ID", -1)
            if (placeId != -1) {
                // PLACE_ID an TourService übergeben
                tourService?.setPlaceId(placeId)
                Log.d(TAG, "Place ID an TourService übergeben: $placeId")
            } else {
                Log.e(TAG, "PLACE_ID nicht gefunden.")
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
            tourService = null
        }
    }


    private fun checkIfBothSelected(nextButton: Button) {
        if (tourLengthGroupSelected && textLengthGroupSelected) {
            nextButton.isEnabled = true
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, TourService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        Robot.getInstance().addOnRobotReadyListener(this)
        Robot.getInstance().addOnRequestPermissionResultListener(this)
    }


    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        Robot.getInstance().removeOnRobotReadyListener(this)
        Robot.getInstance().removeOnRequestPermissionResultListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
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