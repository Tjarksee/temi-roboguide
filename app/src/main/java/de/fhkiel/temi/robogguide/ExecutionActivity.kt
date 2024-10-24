package de.fhkiel.temi.robogguide

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ExecutionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_execution)
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
        }
    }
}