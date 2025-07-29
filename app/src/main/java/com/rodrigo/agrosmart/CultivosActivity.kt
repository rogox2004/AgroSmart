package com.rodrigo.agrosmart

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

class CultivosActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tvUbicacionStatus: TextView
    private lateinit var tvRecomendaciones: TextView
    private lateinit var spinnerCultivos: Spinner
    private lateinit var btnAgregarCultivo: Button
    private lateinit var tvCultivosAgregados: TextView
    private lateinit var llCultivosContainer: LinearLayout

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var plantasSugeridas: List<String> = listOf()

    private lateinit var locationCallback: LocationCallback

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            obtenerUbicacion()
        } else {
            Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
            tvUbicacionStatus.text = "Permiso de ubicación denegado"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cultivos)

        tvUbicacionStatus = findViewById(R.id.tvUbicacionStatus)
        tvRecomendaciones = findViewById(R.id.tvRecomendaciones)
        spinnerCultivos = findViewById(R.id.spinnerCultivos)
        btnAgregarCultivo = findViewById(R.id.btnAgregarCultivo)
        tvCultivosAgregados = findViewById(R.id.tvCultivosAgregados)
        llCultivosContainer = findViewById(R.id.llCultivosContainer)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val btnSolicitarUbicacion = findViewById<Button>(R.id.btnSolicitarUbicacion)

        btnSolicitarUbicacion.setOnClickListener {
            solicitarPermisoUbicacion()
        }

        btnAgregarCultivo.setOnClickListener {
            val plantaSeleccionada = spinnerCultivos.selectedItem as? String
            if (plantaSeleccionada != null) {
                agregarCultivoABaseDatos(plantaSeleccionada)
            } else {
                Toast.makeText(this, "Selecciona una planta", Toast.LENGTH_SHORT).show()
            }
        }

        cargarCultivosDelUsuario()
    }

    private fun solicitarPermisoUbicacion() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                obtenerUbicacion()
            }
            else -> {
                locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun obtenerUbicacion() {
        try {
            val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(this, "Activa el GPS para obtener la ubicación", Toast.LENGTH_LONG).show()
                tvUbicacionStatus.text = "GPS apagado"
                return
            }

            val locationRequest = LocationRequest.create().apply {
                interval = 10000
                fastestInterval = 5000
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                numUpdates = 1
            }

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location: Location? = locationResult.lastLocation
                    if (location != null) {
                        tvUbicacionStatus.text = "Ubicación: ${location.latitude}, ${location.longitude}"
                        consultarClimaYRecomendarPlantas(location.latitude, location.longitude)
                    } else {
                        tvUbicacionStatus.text = "No se pudo obtener la ubicación"
                    }
                    fusedLocationClient.removeLocationUpdates(this)
                }
            }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

        } catch (e: SecurityException) {
            Toast.makeText(this, "Permiso de ubicación no concedido", Toast.LENGTH_SHORT).show()
            tvUbicacionStatus.text = "Permiso de ubicación no concedido"
        }
    }

    private fun consultarClimaYRecomendarPlantas(lat: Double, lon: Double) {
        val apiKey = "fcecf64f20eb4b31ba0e708685d9795a" // Tu API key real aquí
        val url = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&units=metric&appid=$apiKey&lang=es"

        Thread {
            try {
                val response = java.net.URL(url).readText()
                val jsonObject = JSONObject(response)

                val temp = jsonObject.getJSONObject("main").getDouble("temp")
                val humidity = jsonObject.getJSONObject("main").getInt("humidity")

                plantasSugeridas = when {
                    temp > 25 && humidity < 50 -> listOf("Tomate", "Maíz", "Ají", "Pepino", "Zanahoria")
                    temp in 15.0..25.0 && humidity in 50..80 -> listOf("Lechuga", "Rábano", "Repollo", "Espinaca", "Cilantro")
                    temp < 15 && humidity > 60 -> listOf("Perejil", "Brócoli", "Coliflor", "Ajo", "Cebolla")
                    else -> listOf("Albahaca", "Apio", "Remolacha", "Chía", "Mostaza")
                }

                runOnUiThread {
                    tvRecomendaciones.text =
                        "Temp: $temp°C | Humedad: $humidity%\nPlantas recomendadas:\n${plantasSugeridas.joinToString("\n")}"

                    actualizarSpinner()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    tvRecomendaciones.text = "Error al obtener clima"
                }
            }
        }.start()
    }

    private fun actualizarSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, plantasSugeridas)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCultivos.adapter = adapter
    }

    private fun agregarCultivoABaseDatos(planta: String) {
        val usuarioId = auth.currentUser?.uid
        if (usuarioId == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        val cultivo = hashMapOf(
            "nombre" to planta,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("usuarios").document(usuarioId).collection("cultivos")
            .add(cultivo)
            .addOnSuccessListener {
                Toast.makeText(this, "Cultivo agregado: $planta", Toast.LENGTH_SHORT).show()
                cargarCultivosDelUsuario()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al agregar cultivo", Toast.LENGTH_SHORT).show()
            }
    }

    private fun cargarCultivosDelUsuario() {
        val usuarioId = auth.currentUser?.uid ?: return

        db.collection("usuarios").document(usuarioId).collection("cultivos")
            .get()
            .addOnSuccessListener { documentos ->
                val listaCultivos = documentos.map { doc -> doc.getString("nombre") ?: "Desconocido" }
                mostrarCultivosConImagenes(listaCultivos)
            }
            .addOnFailureListener {
                tvCultivosAgregados.text = "Error al cargar cultivos"
            }
    }

    private fun mostrarCultivosConImagenes(cultivos: List<String>) {
        llCultivosContainer.removeAllViews()

        if (cultivos.isEmpty()) {
            val tv = TextView(this)
            tv.text = "No tienes cultivos agregados"
            llCultivosContainer.addView(tv)
            return
        }

        cultivos.forEach { nombreCultivo ->
            val layoutItem = LinearLayout(this)
            layoutItem.orientation = LinearLayout.HORIZONTAL
            layoutItem.setPadding(0, 16, 0, 16)

            val iv = ImageView(this)
            iv.setImageResource(obtenerImagenParaCultivo(nombreCultivo))
            iv.layoutParams = LinearLayout.LayoutParams(100, 100)

            val tv = TextView(this)
            tv.text = nombreCultivo
            tv.textSize = 18f
            tv.setPadding(16, 0, 0, 0)

            layoutItem.addView(iv)
            layoutItem.addView(tv)

            llCultivosContainer.addView(layoutItem)
        }
    }

    private fun obtenerImagenParaCultivo(nombre: String): Int {
        return when (nombre.lowercase()) {
            "tomate" -> R.drawable.tomate
            "lechuga" -> R.drawable.lechuga
            "zanahoria" -> R.drawable.zanahoria
            "perejil" -> R.drawable.perejil
            "rábano" -> R.drawable.rabano
            else -> R.drawable.ic_launcher_foreground
        }
    }
}
