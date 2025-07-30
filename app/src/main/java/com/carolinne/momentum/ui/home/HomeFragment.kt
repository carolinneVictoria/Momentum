package com.carolinne.momentum.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import android.util.Base64
import android.widget.*
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.carolinne.momentum.R
import com.carolinne.momentum.baseclasses.Item
import com.carolinne.momentum.databinding.FragmentHomeBinding
import com.carolinne.momentum.ui.ai.AiLogicActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.io.IOException
import android.util.Log

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private lateinit var currentAddressTextView: TextView
    private lateinit var distanceToFixedPointTextView: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest


    private val fixedPointLocation = Location("PontoFixo").apply {
        latitude = -23.587122 // Latitude do Parque Ibirapuera
        longitude = -46.677516 // Longitude do Parque Ibirapuera
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)


        currentAddressTextView = view.findViewById(R.id.currentAddressTextView)
        distanceToFixedPointTextView = view.findViewById(R.id.distanceToFixedPointTextView) // NOVO: Inicialização

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    displayAddress(location)
                    calculateDistanceToFixedPoint(location) 
                }
            }
        }



        val itemContainer = view.findViewById<LinearLayout>(R.id.itemContainer)
        carregarItens(itemContainer)
        

        val switch = view.findViewById<SwitchCompat>(R.id.darkModeSwitch)
        habilitaDarkMode(switch)

        val fab = view.findViewById<FloatingActionButton>(R.id.fab_ai)
        // val scrollView = view.findViewById<ScrollView>(R.id.scrollView)
        // val fragmentContainer = view.findViewById<FrameLayout>(R.id.fragment_container)

        fab.setOnClickListener {
            val context = view.context
            val intent = Intent(context, AiLogicActivity::class.java)
            context.startActivity(intent)
        }

        return view
    }

    override fun onResume() {
        super.onResume()

        if (checkLocationPermissions()) {
            getCurrentLocation()
        } else {
            requestLocationPermission()
        }
    }

    override fun onPause() {
        super.onPause()

        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    fun carregarItens(container: LinearLayout) {
        val databaseRef = FirebaseDatabase.getInstance().getReference("itens")

        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                container.removeAllViews()

                for (userSnapshot in snapshot.children) {
                    for (itemSnapshot in userSnapshot.children) {
                        val item = itemSnapshot.getValue(Item::class.java) ?: continue

                        val itemView = LayoutInflater.from(container.context)
                            .inflate(R.layout.item_template, container, false)

                        val imageView = itemView.findViewById<ImageView>(R.id.item_image)
                        val tarefaView = itemView.findViewById<TextView>(R.id.item_tarefa)
                        val statusView = itemView.findViewById<TextView>(R.id.item_status)

                        tarefaView.text = item.endereco?: "Não informado"
                        statusView.text = item.tarefa?: "Não informado"

                        if (!item.imageUrl.isNullOrEmpty()) {
                            Glide.with(container.context).load(item.imageUrl).into(imageView)
                        } else if (!item.statusTarefa.isNullOrEmpty()) {
                            try {
                                val bytes = Base64.decode(item.statusTarefa, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                imageView.setImageBitmap(bitmap)
                            } catch (_: Exception) {
                            }
                        }

                        container.addView(itemView)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(container.context, "Erro ao carregar dados", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }


    private fun checkLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                view?.let {
                    Snackbar.make(
                        it,
                        "Permissão negada. Não é possível acessar a localização.",
                        Snackbar.LENGTH_LONG
                    ).show()
                } ?: run {
                    android.util.Log.e("HomeFragment", "View nula ao tentar mostrar Snackbar de permissão negada.")
                }
                if (::currentAddressTextView.isInitialized && view != null) {
                    currentAddressTextView.text = "Permissão negada."
                }

                if (::distanceToFixedPointTextView.isInitialized && view != null) {
                    distanceToFixedPointTextView.text = "Distância: Permissão negada."
                }
            }
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun displayAddress(location: Location) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val addresses: List<android.location.Address>? = geocoder.getFromLocation(location.latitude, location.longitude, 1)

                val address = addresses?.firstOrNull()?.getAddressLine(0) ?: "Endereço não encontrado"
                withContext(Dispatchers.Main) {
                    if (::currentAddressTextView.isInitialized) {
                        currentAddressTextView.text = address
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    if (::currentAddressTextView.isInitialized) {
                        currentAddressTextView.text = "Erro: ${e.message}"
                    }
                }
                android.util.Log.e("HomeFragment", "Geocoder IOException: ${e.message}")
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (::currentAddressTextView.isInitialized) {
                        currentAddressTextView.text = "Erro: ${e.message}"
                    }
                }
                android.util.Log.e("HomeFragment", "Erro geral em displayAddress: ${e.message}")
            }
        }
    }

    /**
     * NOVO: Método para calcular e exibir a distância até o ponto fixo.
     * @param currentLocation A localização atual do usuário.
     */
    private fun calculateDistanceToFixedPoint(currentLocation: Location) {
        val distanceInMeters = currentLocation.distanceTo(fixedPointLocation)
        val distanceInKm = distanceInMeters / 1000.0


        if (::distanceToFixedPointTextView.isInitialized) {
            distanceToFixedPointTextView.text = "Distância para o Parque Ibirapuera: %.2f km".format(distanceInKm)
        }
        Log.d("HomeFragment", "Distância calculada: %.2f km".format(distanceInKm))
    }

    fun habilitaDarkMode(switch: SwitchCompat){
        val prefs = requireActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)


        val darkMode = prefs.getBoolean("dark_mode", false)
        switch.isChecked = darkMode
        AppCompatDelegate.setDefaultNightMode(
            if (darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )


        switch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }
}
