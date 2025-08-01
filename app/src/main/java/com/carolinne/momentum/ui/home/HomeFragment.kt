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
import android.view.Gravity
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth
import com.carolinne.momentum.ui.dashboard.DashboardFragment
import androidx.navigation.fragment.findNavController

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private lateinit var currentAddressTextView: TextView
    private lateinit var distanceToFixedPointTextView: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest

    private lateinit var auth: FirebaseAuth

    private val fixedPointLocation = Location("PontoFixo").apply {
        latitude = -23.587122
        longitude = -46.677516
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        currentAddressTextView = view.findViewById(R.id.currentAddressTextView)
        distanceToFixedPointTextView = view.findViewById(R.id.distanceToFixedPointTextView)

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

                val currentUserId = auth.currentUser?.uid

                if (snapshot.childrenCount == 0L) {
                    val noItemsTextView = TextView(requireContext()).apply {
                        text = "Nenhum hábito/tarefa disponível."

                                gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(0, 50, 0, 0)
                    }
                    container.addView(noItemsTextView)
                    return
                }

                var hasItemsForCurrentUser = false

                for (userSnapshot in snapshot.children) {
                    val userIdForItem = userSnapshot.key
                    val isCurrentUserItem = userIdForItem == currentUserId

                    for (itemSnapshot in userSnapshot.children) {
                        val item = itemSnapshot.getValue(Item::class.java) ?: continue
                        val itemId = itemSnapshot.key

                        val itemView = LayoutInflater.from(container.context)
                            .inflate(R.layout.item_template, container, false)

                        val imageView = itemView.findViewById<ImageView>(R.id.item_image)
                        val tarefaView = itemView.findViewById<TextView>(R.id.item_tarefa)
                        val statusDisplayView = itemView.findViewById<TextView>(R.id.item_status_display) // ID CORRIGIDO

                        tarefaView.text = "Tarefa: ${item.tarefa ?: "Não informada"}"
                        statusDisplayView.text = "Status: ${item.statusTarefa ?: "Não informado"}" // PROPRIEDADE CORRIGIDA E EXIBIÇÃO CORRETA

                        if (!item.imageUrl.isNullOrEmpty()) {
                            Glide.with(container.context).load(item.imageUrl).into(imageView)
                        } else if (!item.base64Image.isNullOrEmpty()) {
                            try {
                                val bytes = Base64.decode(item.base64Image, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                imageView.setImageBitmap(bitmap)
                            } catch (_: Exception) {
                                // Erro ao decodificar Base64, pode ser útil logar
                                Log.e("HomeFragment", "Erro ao decodificar Base64 para item ${itemId}")
                            }
                        }

                        val deleteButton = itemView.findViewById<ImageButton>(R.id.delete_item_button)
                        val editButton = itemView.findViewById<ImageButton>(R.id.edit_item_button)

                        if (isCurrentUserItem && itemId != null) {
                            deleteButton.visibility = View.VISIBLE
                            editButton.visibility = View.VISIBLE
                            hasItemsForCurrentUser = true

                            deleteButton.setOnClickListener {
                                AlertDialog.Builder(requireContext())
                                    .setTitle("Confirmar Exclusão de Hábito")
                                    .setMessage("Tem certeza que deseja excluir '${item.tarefa ?: "este hábito"}'?")
                                    .setPositiveButton("Excluir") { dialog, which ->
                                        deleteItem(userIdForItem!!, itemId, container)
                                    }
                                    .setNegativeButton("Cancelar", null)
                                    .show()
                            }

                            editButton.setOnClickListener {
                                val args = Bundle().apply {
                                    putSerializable("item_object", item)
                                    putString("item_id", itemId)
                                    putString("user_id", userIdForItem)
                                }
                                findNavController().navigate(R.id.action_navigation_home_to_navigation_dashboard, args)
                            }

                        } else {
                            deleteButton.visibility = View.GONE
                            editButton.visibility = View.GONE
                        }

                        container.addView(itemView)
                    }
                }

                // Ajusta a mensagem de "Nenhum item" baseado no usuário atual
                if (currentUserId != null) {
                    val userItemsSnapshot = snapshot.child(currentUserId)
                    if (!userItemsSnapshot.exists() || userItemsSnapshot.childrenCount == 0L) {
                        val noMyItemsTextView = TextView(requireContext()).apply {
                            text = "Você ainda não tem hábitos/tarefas cadastradas."

                                    gravity = Gravity.CENTER_HORIZONTAL
                            setPadding(0, 50, 0, 0)
                        }
                        // Remove a mensagem genérica se já foi adicionada e adiciona a específica
                        if (container.childCount == 0 || container.getChildAt(0) is TextView && (container.getChildAt(0) as TextView).text.contains("Nenhum hábito/tarefa disponível")) {
                            container.removeAllViews()
                        }
                        container.addView(noMyItemsTextView)
                    } else if (!hasItemsForCurrentUser && container.childCount > 0) {
                        // Este caso é para quando o usuário tem itens, mas não há visualização para eles
                        // ou se houver itens de outros usuários, mas não do atual.
                        // A lógica acima com 'hasItemsForCurrentUser' já lida com a visibilidade dos botões.
                        // Esta condição específica aqui pode ser removida para simplificar.
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(container.context, "Erro ao carregar dados: ${error.message}", Toast.LENGTH_SHORT)
                    .show()
                Log.e("HomeFragment", "Erro ao carregar itens do Firebase: ${error.message}", error.toException())
            }
        })
    }

    private fun deleteItem(userId: String, itemId: String, container: LinearLayout) {
        val itemRef = FirebaseDatabase.getInstance().getReference("itens").child(userId).child(itemId)
        itemRef.removeValue()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Hábito/Tarefa excluída com sucesso!", Toast.LENGTH_SHORT).show()
                carregarItens(container)
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Erro ao excluir hábito/tarefa: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("HomeFragment", "Erro ao excluir hábito/tarefa do Firebase: ${e.message}", e)
            }
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
                Manifest.PERMISSION.ACCESS_COARSE_LOCATION
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
