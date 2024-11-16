package de.fhkiel.temi.robogguide

import android.app.AlertDialog
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
import android.view.LayoutInflater
import android.webkit.JavascriptInterface
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
    private var isRunning = false
    private var isBound = false
    var tourService: TourService? = null
    private lateinit var wvAreaVideo: WebView
    private val TAG = "ExecutionActivity"
    private var isNavigationCompleted = false
    private var isSpeechCompleted = false
    var isVideoCompleted = false





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
            tourService?.updateSpeechStatus(true)
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

    private val errorPopUpReceiver = object : BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            errorPopUp()
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
            RECEIVER_NOT_EXPORTED)
        registerReceiver(errorPopUpReceiver, IntentFilter("ACTION_UPDATE_POPUP"),
            RECEIVER_NOT_EXPORTED)



        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.execution_view)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnPauseContinue: Button = findViewById(R.id.btnPauseContinue)
        findViewById<Button>(R.id.btnPauseContinue).setOnClickListener {
            if (!isRunning) {
                btnPauseContinue.text = "Weiter"
                tourService?.continueTour()
                btnPauseContinue.setBackgroundColor(ContextCompat.getColor(this, R.color.continueButton))
                isRunning = true

            } else {
                btnPauseContinue.text = "Pause"
                tourService?.pauseTour()
                btnPauseContinue.setBackgroundColor(ContextCompat.getColor(this, R.color.pauseButton))
                isRunning = false
            }
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
        // Bild ausblenden, Videoansicht anzeigen
        ivAreaImage.visibility = View.GONE
        wvAreaVideo.visibility = View.VISIBLE

        // WebView-Einstellungen
        wvAreaVideo.settings.javaScriptEnabled = true
        wvAreaVideo.settings.mediaPlaybackRequiresUserGesture = false

        // JavaScript-Schnittstelle hinzufügen
        wvAreaVideo.addJavascriptInterface(WebAppInterface(this), "AndroidInterface")

        // Prüfen, ob es sich um ein YouTube-Video handelt
        if (url.contains("youtube.com")) {
            val videoId = extractYouTubeVideoId(url)
            if (videoId != null) {
                // HTML-Code für den YouTube-IFrame
                val html = """
                <html>
                <body>
                <div id="player"></div>
                <script>
                    var tag = document.createElement('script');
                    tag.src = "https://www.youtube.com/iframe_api";
                    var firstScriptTag = document.getElementsByTagName('script')[0];
                    firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                    var player;
                    function onYouTubeIframeAPIReady() {
                        player = new YT.Player('player', {
                            height: '100%',
                            width: '100%',
                            videoId: '$videoId',
                             playerVars: {
                                'autoplay': 1, 
                                'controls': 1 
                            },
                            events: {
                                'onStateChange': onPlayerStateChange
                            }
                        });
                    }

                    function onPlayerStateChange(event) {
                        if (event.data == YT.PlayerState.ENDED) {
                            AndroidInterface.onVideoEnded();
                        }
                    }
                </script>
                </body>
                </html>
            """.trimIndent()

                // HTML in WebView laden
                wvAreaVideo.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
            } else {
                Log.e(TAG, "Ungültige YouTube-URL: $url")
            }
        } else {
            Log.d(TAG, "Kein YouTube-Video erkannt.")
        }

        // Setze den Video-Status auf "nicht abgeschlossen"
        isVideoCompleted = false
        tourService?.updateVideoStatus(false)
    }
    private fun extractYouTubeVideoId(url: String): String? {
        return try {
            val videoId = url.substringAfter("v=").substringBefore("&")
            if (videoId.isNotEmpty()) videoId else null
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Extrahieren der Video-ID: ${e.message}")
            null
        }
    }






    fun checkMovementAndSpeechStatus() {
        if (isSpeechCompleted && isNavigationCompleted) {
            Log.d(TAG, "Beides abgeschlossen. Navigiere zur nächsten Location.")
            tourService?.continueTour()
        } else {
            Log.d(TAG, "Warte auf Abschluss: Sprechen: $isSpeechCompleted, Navigation: $isNavigationCompleted")
        }
    }





    private fun errorPopUp() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.popup_error, null)

        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)

        val dialog = builder.create()

        dialogView.findViewById<Button>(R.id.btn_retry).setOnClickListener {
            dialog.dismiss()
            tourService?.retryNavigation()
        }

        dialogView.findViewById<Button>(R.id.btn_skip).setOnClickListener {
            dialog.dismiss()
            tourService?.skip()
        }

        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
            tourService?.endTour()
        }

        dialog.show()
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(headingReceiver)
        unregisterReceiver(textReceiver)
        unregisterReceiver(mediaReceiver)
        unregisterReceiver(progressReceiver)

    }
}
class WebAppInterface(private val context: Context) {
    @JavascriptInterface
    fun onVideoEnded() {
        (context as ExecutionActivity).runOnUiThread {
            Log.d("WebAppInterface", "Video beendet.")
            context.isVideoCompleted = true
            context.tourService?.updateVideoStatus(true)
            context.checkMovementAndSpeechStatus()
        }
    }
}
