package de.fhkiel.temi.robogguide

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.robotemi.sdk.Robot
import java.net.URL
import kotlin.concurrent.thread
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient


class ExecutionActivity : AppCompatActivity() {
    private lateinit var tvDescription: TextView
    private lateinit var ivAreaImage: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvHeading: TextView
    private var mRobot: Robot? = null
    private var isRunning = true
    private var isBound = false
    private var tourService: TourService? = null
    private lateinit var wvAreaVideo: WebView
    private val TAG = "ExecutionActivity"
    private var isNavigationCompleted = false
    private var isSpeechCompleted = false
    private var isVideoCompleted = false





    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val tourBinder = binder as TourService.TourBinder
            tourService = tourBinder.getService()
            isBound = true
            Log.d("ExecutionActivity", "TourService verbunden")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            tourService = null
            isBound = false
            Log.d("ExecutionActivity", "TourService getrennt")
        }
    }

    private val textReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val spokenText = intent?.getStringExtra("EXTRA_SPOKEN_TEXT")
            tvDescription.text = spokenText
            Log.d(TAG, "Text empfangen: $spokenText")
            isSpeechCompleted = true
            checkMovementAndSpeechStatus()
        }
    }

    private val headingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val heading = intent?.getStringExtra("EXTRA_HEADING")
            tvHeading.text = heading
        }
    }

    private val mediaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val mediaUrl = intent?.getStringExtra("EXTRA_MEDIA_URL")
            Log.d("ExecutionActivity", "Empfangene Media URL: $mediaUrl")
            mediaUrl?.let { url ->
                handleMedia(url) // Entscheidet zwischen Bild und Video
            }
        }
    }


    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val progress = intent?.getIntExtra("EXTRA_PROGRESS", 0) ?: 0
            progressBar.progress = progress
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val serviceIntent = Intent(this, TourService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        setContentView(R.layout.activity_execution)
        tvHeading = findViewById(R.id.tvCurrentArea)
        tvDescription = findViewById(R.id.tvDescription)
        ivAreaImage = findViewById(R.id.ivAreaImage)
        progressBar = findViewById(R.id.progressBar)
        mRobot = Robot.getInstance()
        wvAreaVideo = findViewById(R.id.wvAreaVideo)



        // Registriere den BroadcastReceiver
        registerReceiver(headingReceiver, IntentFilter("ACTION_UPDATE_HEADING"),
            RECEIVER_NOT_EXPORTED)
        registerReceiver(textReceiver, IntentFilter("ACTION_UPDATE_SPOKEN_TEXT"),
            RECEIVER_NOT_EXPORTED)
        registerReceiver(mediaReceiver, IntentFilter("ACTION_UPDATE_MEDIA"),
            RECEIVER_NOT_EXPORTED)
        registerReceiver(progressReceiver, IntentFilter("ACTION_UPDATE_PROGRESS"),
            RECEIVER_NOT_EXPORTED
        )


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.execution_view)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnPauseContinue: Button = findViewById(R.id.btnPauseContinue)
        fun updateButtonState() {
            if (isRunning) {
                btnPauseContinue.text = "Pause"
                tourService?.pauseTour()
                btnPauseContinue.setBackgroundColor(ContextCompat.getColor(this, R.color.pauseButton))  // Button auf Orange setzen
            } else {
                btnPauseContinue.text = "Weiter"
                tourService?.continueTour()
                btnPauseContinue.setBackgroundColor(ContextCompat.getColor(this, R.color.continueButton))  // Button auf Grün setzen
            }
        }

        findViewById<Button>(R.id.btnPauseContinue).setOnClickListener {

            isRunning = !isRunning
            updateButtonState()
        }

        findViewById<Button>(R.id.btnSkip).setOnClickListener {
            //to Do
        }

        findViewById<Button>(R.id.btnExit).setOnClickListener {
            tourService!!.endTour()
        }
    }
    private fun handleMedia(url: String) {
        // Statusvariablen zurücksetzen
        isNavigationCompleted = false
        isSpeechCompleted = false
        isVideoCompleted = false

        if (url.endsWith(".mp4") || url.endsWith(".webm") || url.contains("youtube.com")) {
            showVideo(url)
        } else {
            isVideoCompleted = true // Bilder erfordern keine weitere Aktion
            checkMovementAndSpeechStatus() // Überprüfung starten
            showImage(url)
        }
    }


    private fun showImage(url: String) {
        ivAreaImage.visibility = View.VISIBLE
        wvAreaVideo.visibility = View.GONE

        thread {
            try {
                Log.d("ExecutionActivity", "Lade Bild von URL: $url")
                val bitmap = BitmapFactory.decodeStream(URL(url).openStream())
                runOnUiThread {
                    Log.d("ExecutionActivity", "Bild erfolgreich geladen von URL: $url")
                    ivAreaImage.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                Log.e("ExecutionActivity", "Fehler beim Laden des Bildes von URL: $url", e)
                runOnUiThread {
                }
            }
        }
    }

    private fun showVideo(url: String) {
        ivAreaImage.visibility = View.GONE
        wvAreaVideo.visibility = View.VISIBLE

        wvAreaVideo.settings.javaScriptEnabled = true
        wvAreaVideo.settings.mediaPlaybackRequiresUserGesture = false

        wvAreaVideo.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = false
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
        }

        wvAreaVideo.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) {
                    Log.d(TAG, "Video geladen.")
                }
            }
        }

        if (url.contains("youtube.com")) {
            val videoId = url.substringAfter("v=").substringBefore("&")
            val embeddedUrl = "https://www.youtube.com/embed/$videoId?autoplay=1&enablejsapi=1"
            wvAreaVideo.loadUrl(embeddedUrl)
        } else {
            wvAreaVideo.loadUrl("$url?autoplay=1")
        }

        // Callback für Videoende hinzufügen
        wvAreaVideo.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun onVideoEnded() {
                runOnUiThread {
                    Log.d(TAG, "Video beendet.")
                    isVideoCompleted = true
                    checkMovementAndSpeechStatus()
                }
            }
        }, "Android")
    }



    private fun checkMovementAndSpeechStatus() {
        if (isSpeechCompleted && isNavigationCompleted) {
            Log.d(TAG, "Beides abgeschlossen. Navigiere zur nächsten Location.")
            tourService?.continueTour()
        } else {
            Log.d(TAG, "Warte auf Abschluss: Sprechen: $isSpeechCompleted, Navigation: $isNavigationCompleted")
        }
    }





    override fun onDestroy() {
        super.onDestroy()
        // Unregister the receiver to avoid memory leaks
        unregisterReceiver(headingReceiver)
        unregisterReceiver(textReceiver)
        unregisterReceiver(mediaReceiver)
        unregisterReceiver(progressReceiver)

    }
}
