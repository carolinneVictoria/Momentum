package com.carolinne.momentum.ui.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend

class AiLogicFragment : Fragment() {

    private lateinit var promptInput: EditText
    private lateinit var resultText: TextView
    private lateinit var generateButton: Button
    private lateinit var model: GenerativeModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(com.carolinne.momentum.R.layout.fragment_ai_logic, container, false)


        promptInput = view.findViewById(com.carolinne.momentum.R.id.prompt_input)
        resultText = view.findViewById(com.carolinne.momentum.R.id.result_text)
        generateButton = view.findViewById(com.carolinne.momentum.R.id.btn_generate)


        model = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel("gemini-2.0-flash")

        generateButton.setOnClickListener {
            val prompt = promptInput.text.toString().trim()
            resultText.text = "Aguardando resposta..."


            if (prompt.isNotEmpty()) {
                generateFromPromptOnly(prompt)
            } else {
                resultText.text = "Digite um prompt para continuar."
            }
        }
        return view
    }

    // Método para gerar resposta apenas com texto
    private fun generateFromPromptOnly(prompt: String) {
        lifecycleScope.launch {
            try {

                val response = model.generateContent(prompt)
                resultText.text = response.text ?: "Nenhuma resposta recebida."
            } catch (e: Exception) {
                resultText.text = "Erro ao gerar resposta: ${e.message}"
                android.util.Log.e("AiLogicFragment", "Erro na geração de prompt (apenas texto): ${e.message}", e)
            }
        }
    }
}
