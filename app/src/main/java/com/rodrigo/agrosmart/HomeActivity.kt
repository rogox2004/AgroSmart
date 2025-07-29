package com.rodrigo.agrosmart

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import android.widget.Toast
class HomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        auth = FirebaseAuth.getInstance()

        val logoutButton = findViewById<Button>(R.id.logoutButton)
        val welcomeText = findViewById<TextView>(R.id.welcomeText)

        val email = auth.currentUser?.email ?: "Usuario"
        val welcome = getString(R.string.welcome_message, email)
        welcomeText.text = welcome

        logoutButton.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Botones nuevos
        val btnCultivos = findViewById<Button>(R.id.btnCultivos)
        val btnMarketplace = findViewById<Button>(R.id.btnMarketplace)
        val btnForo = findViewById<Button>(R.id.btnForo)

        btnCultivos.setOnClickListener {
            startActivity(Intent(this, CultivosActivity::class.java))
        }

        btnMarketplace.setOnClickListener {

            Toast.makeText(this, "Marketplace - Próximamente", Toast.LENGTH_SHORT).show()
        }

        btnForo.setOnClickListener {

            Toast.makeText(this, "Foro - Próximamente", Toast.LENGTH_SHORT).show()
        }
    }
}