package com.carolinne.momentum.ui.dashboard

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.carolinne.momentum.databinding.FragmentDashboardBinding
import com.bumptech.glide.Glide
import com.carolinne.momentum.R
import com.carolinne.momentum.baseclasses.Item
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.StorageReference
import java.io.Serializable // Importação para Serializable

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null

    private lateinit var tarefaEditText: EditText
    private lateinit var itemImageView: ImageView
    private var imageUri: Uri? = null // Uri da nova imagem selecionada

    private lateinit var salvarButton: Button
    private lateinit var selectImageButton: Button
    private lateinit var databaseReference: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var statusTarefaEditText: EditText // Campo para o status da tarefa

    // Variáveis para armazenar o ID do item e do usuário se estiver no modo de edição
    private var editingItemId: String? = null
    private var editingUserId: String? = null
    // Armazena o item original para preservar a imagem se não for alterada
    private var originalItem: Item? = null

    companion object {
        private const val PICK_IMAGE_REQUEST = 1

        // Chaves para os argumentos do Bundle - DEVE CORRESPONDER AO mobile_navigation.xml
        private const val BUNDLE_KEY_ITEM = "item_object"
        private const val BUNDLE_KEY_ITEM_ID = "item_id"
        private const val BUNDLE_KEY_USER_ID = "user_id"

        /**
         * Método de fábrica para criar uma nova instância de DashboardFragment
         * com argumentos para edição.
         * @param item O objeto Item a ser editado.
         * @param itemId O ID do item no Firebase.
         * @param userId O ID do usuário proprietário do item.
         */
        fun newInstance(item: Item, itemId: String, userId: String): DashboardFragment {
            val fragment = DashboardFragment()
            val args = Bundle().apply {
                putSerializable(BUNDLE_KEY_ITEM, item)
                putString(BUNDLE_KEY_ITEM_ID, itemId)
                putString(BUNDLE_KEY_USER_ID, userId)
            }
            fragment.arguments = args
            return fragment
        }
    }

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        // Use o binding para inflar o layout e obter a View raiz
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root // A View raiz do fragmento

        // Captura os elementos de UI usando a View raiz do binding (usando 'root' em vez de 'view')
        tarefaEditText = root.findViewById(R.id.enderecoItemEditText) // ID no seu XML é enderecoItemEditText
        statusTarefaEditText = root.findViewById(R.id.statusItemEditText) // ID no seu XML é statusItemEditText
        itemImageView = root.findViewById(R.id.image_item)
        salvarButton = root.findViewById(R.id.salvarItemButton)
        selectImageButton = root.findViewById(R.id.button_select_image)

        // Comentado ou removido: 'dashboardViewModel' e 'textView' não são usados no contexto de edição aqui
        // val dashboardViewModel = ViewModelProvider(this).get(DashboardViewModel::class.java)
        // val textView: TextView = binding.textDashboard
        // dashboardViewModel.text.observe(viewLifecycleOwner) {
        //     textView.text = it
        // }

        auth = FirebaseAuth.getInstance()
        // databaseReference deve ser inicializada aqui para ser usada por salvarItem
        databaseReference = FirebaseDatabase.getInstance().getReference("itens")

        // Bloco try-catch para storageReference removido, pois não é usado diretamente aqui
        // (o upload agora é feito via Base64 diretamente no Firebase Realtime Database).
        /*
        try {
            // ... (código anterior para storageReference)
        } catch (e: Exception) {
            Log.e("FirebaseStorage", "Erro ao obter referência para o Firebase Storage", e)
            Toast.makeText(context, "Erro ao acessar o Firebase Storage", Toast.LENGTH_SHORT).show()
        }
        */

        // Verifica se o fragmento foi iniciado com argumentos para edição
        arguments?.let { args ->
            val itemToEdit = args.getSerializable(BUNDLE_KEY_ITEM) as? Item
            editingItemId = args.getString(BUNDLE_KEY_ITEM_ID)
            editingUserId = args.getString(BUNDLE_KEY_USER_ID)
            originalItem = itemToEdit // Armazena o item original para preservar a imagem

            itemToEdit?.let { item ->
                // Preenche os campos com os dados do item
                tarefaEditText.setText(item.tarefa)
                statusTarefaEditText.setText(item.statusTarefa) // Usar statusTarefa conforme seu Item.kt

                // Carrega a imagem se existir (seja URL ou Base64)
                if (!item.imageUrl.isNullOrEmpty()) {
                    Glide.with(this).load(item.imageUrl).into(itemImageView)
                    // Não define imageUri aqui para não forçar upload se não houver alteração
                } else if (!item.base64Image.isNullOrEmpty()) {
                    try {
                        val bytes = Base64.decode(item.base64Image, Base64.DEFAULT)
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        itemImageView.setImageBitmap(bitmap)
                        // Não define imageUri aqui
                    } catch (e: Exception) {
                        Log.e("DashboardFragment", "Erro ao decodificar Base64 da imagem existente: ${e.message}")
                    }
                }
                // Altera o texto do botão de salvar para "Atualizar"
                salvarButton.text = "Atualizar Hábito"
            }
        }

        selectImageButton.setOnClickListener {
            openFileChooser()
        }

        salvarButton.setOnClickListener {
            salvarItem()
        }

        return root // Retorna a View raiz do binding
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun openFileChooser() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    private fun salvarItem() {
        val tarefa = tarefaEditText.text.toString().trim()
        val status = statusTarefaEditText.text.toString().trim() // Usar statusTarefaEditText

        if (tarefa.isEmpty() || status.isEmpty()) {
            Toast.makeText(context, "Por favor, preencha a Tarefa e o Status.", Toast.LENGTH_SHORT).show()
            return
        }

        // Lógica para lidar com a imagem ao salvar/atualizar
        if (editingItemId != null && editingUserId != null) {
            // Modo de edição
            if (imageUri != null) {
                // Nova imagem selecionada, faz upload e salva/atualiza
                uploadImageAndSaveItem(tarefa, status, editingUserId!!, editingItemId!!)
            } else {
                // Nenhuma nova imagem selecionada, preserva a imagem original
                // Crie o Item com os dados originais da imagem se não for alterada
                val item = Item(
                    endereco = originalItem?.endereco, // Preserva endereco se existir
                    tarefa = tarefa,
                    statusTarefa = status, // Usar statusTarefa
                    base64Image = originalItem?.base64Image, // Preserva a base64Image original
                    imageUrl = originalItem?.imageUrl       // Preserva a imageUrl original
                )
                saveItemIntoDatabase(item, editingUserId!!, editingItemId!!)
            }
        } else {
            // Modo de criação (nova tarefa)
            if (imageUri == null) {
                Toast.makeText(context, "Por favor, selecione uma imagem para o novo hábito.", Toast.LENGTH_SHORT).show()
                return
            }
            uploadImageAndSaveItem(tarefa, status, null, null) // Cria um novo item
        }
    }

    // Método auxiliar para lidar com upload de imagem (para Base64) antes de salvar/atualizar
    private fun uploadImageAndSaveItem(
        tarefa: String,
        status: String,
        userId: String?,
        itemId: String?
    ) {
        if (imageUri != null) {
            val inputStream = context?.contentResolver?.openInputStream(imageUri!!)
            val bytes = inputStream?.readBytes()
            inputStream?.close()

            if (bytes != null) {
                val base64Image = Base64.encodeToString(bytes, Base64.DEFAULT)
                // Crie o objeto Item com a nova imagem base64
                val item = Item(tarefa = tarefa, statusTarefa = status, base64Image = base64Image, imageUrl = null)
                saveItemIntoDatabase(item, userId, itemId)
            } else {
                Toast.makeText(context, "Erro ao ler a imagem selecionada.", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Este 'else' só é atingido se imageUri for null, o que para 'uploadImageAndSaveItem'
            // significa que não há uma nova imagem para fazer upload.
            // A lógica de 'salvarItem()' já deve ter decidido se chamaria este método com imageUri nulo
            // (que seria o caso de edição sem nova seleção de imagem).
            val item = Item(
                tarefa = tarefa,
                statusTarefa = status,
                base64Image = originalItem?.base64Image,
                imageUrl = originalItem?.imageUrl
            )
            saveItemIntoDatabase(item, userId!!, itemId!!)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK
            && data != null && data.data != null
        ) {
            imageUri = data.data
            Glide.with(this).load(imageUri).into(itemImageView)
        }
    }

    // Método atualizado para salvar ou atualizar um item no Firebase Realtime Database
    private fun saveItemIntoDatabase(item: Item, userId: String?, itemId: String?) {
        val currentAuthUid = auth.uid.toString() // Obtém o UID do usuário logado

        val finalUserId = userId ?: currentAuthUid // Usa o userId passado (se edição) ou o do usuário logado (se criação)

        // Cria o objeto Item a ser salvo com todos os dados
        val itemToSave = Item(
            endereco = item.endereco, // Preserva endereco, se existir
            tarefa = item.tarefa,
            statusTarefa = item.statusTarefa,
            base64Image = item.base64Image,
            imageUrl = item.imageUrl
        )

        if (itemId == null) {
            // Modo de criação: cria uma nova chave única
            val newId = databaseReference.child(finalUserId).push().key
            if (newId != null) {
                databaseReference.child(finalUserId).child(newId).setValue(itemToSave)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Hábito cadastrado com sucesso!", Toast.LENGTH_SHORT).show()
                        requireActivity().supportFragmentManager.popBackStack() // Volta para a tela anterior
                    }.addOnFailureListener { e ->
                        Toast.makeText(context, "Falha ao cadastrar o hábito: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("DashboardFragment", "Erro ao cadastrar hábito: ${e.message}", e)
                    }
            } else {
                Toast.makeText(context, "Erro ao gerar o ID do hábito", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Modo de edição: atualiza o item existente
            databaseReference.child(finalUserId).child(itemId).setValue(itemToSave)
                .addOnSuccessListener {
                    Toast.makeText(context, "Hábito atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                    requireActivity().supportFragmentManager.popBackStack() // Volta para a tela anterior
                }.addOnFailureListener { e ->
                    Toast.makeText(context, "Falha ao atualizar o hábito: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("DashboardFragment", "Erro ao atualizar hábito: ${e.message}", e)
                }
        }
    }
}
