package de.fhkiel.temi.robogguide

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.robotemi.sdk.Robot

class ExecutionActivity : AppCompatActivity() {
    private lateinit var tvDescription: TextView
    private val textReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val spokenText = intent?.getStringExtra("EXTRA_SPOKEN_TEXT")
            tvDescription.text = spokenText
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_execution)
        tvDescription = findViewById(R.id.tvDescription)

        // Registriere den BroadcastReceiver ohne RECEIVER_NOT_EXPORTED
        registerReceiver(textReceiver, IntentFilter("ACTION_UPDATE_SPOKEN_TEXT"),
            RECEIVER_NOT_EXPORTED
        )

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.execution_view)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.btnPause).setOnClickListener {
            //to Do
        }

        findViewById<Button>(R.id.btnSkip).setOnClickListener {
            //to Do
        }

        findViewById<Button>(R.id.btnExit).setOnClickListener {
            val intent = Intent(this, RatingActivity::class.java)
            startActivity(intent)
            Robot.getInstance().goTo("home base")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the receiver to avoid memory leaks
        unregisterReceiver(textReceiver)
    }
}
