package com.jarvis.assistant.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import java.io.ByteArrayOutputStream
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.databinding.FragmentChatBinding
import com.jarvis.assistant.ui.home.MainActivity
import com.jarvis.assistant.ui.home.MainViewModel
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel get() = (requireActivity() as MainActivity).viewModel

    private val chatAdapter = JarvisChatAdapter(
        onEditUserMessage = { text ->
            binding.etMessageInput.setText(text)
            binding.etMessageInput.setSelection(text.length)
            binding.etMessageInput.requestFocus()
        }
    )

    // DEV STYLE: chat image attachment — pick, compress, stream to the live session
    private val imagePicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        val caption = binding.etMessageInput.text?.toString()?.trim().orEmpty()
            .ifBlank { "Boss, what is in this image?" }
        val jpeg = compressImage(uri) ?: run {
            Toast.makeText(requireContext(), "Could not read the image", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        if (!JarvisConversationService.isServiceRunning()) {
            (activity as? MainActivity)?.handleMicOrOrbClick()
        }
        JarvisConversationService.sendImageMessage(b64, caption)
        Toast.makeText(requireContext(), "Image sent to Jarvis", Toast.LENGTH_SHORT).show()
    }

    private fun compressImage(uri: Uri): ByteArray? = runCatching {
        val ctx = requireContext()
        val probe = ctx.contentResolver.openInputStream(uri) ?: return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(probe, null, bounds)
        probe.close()
        var sample = 1
        while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2
        val input = ctx.contentResolver.openInputStream(uri) ?: return@runCatching null
        val bmp = BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sample })
        input.close()
        val bitmap = bmp ?: return@runCatching null
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        out.toByteArray()
    }.getOrNull()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.root)

        // Setup Chat List
        binding.rvChatMessages.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.rvChatMessages.adapter = chatAdapter

        // Send Message
        binding.btnSendMessage.setOnClickListener {
            sendMessage()
        }

        // New Chat / Clear
        binding.btnChatNew.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("New Conversation")
                .setMessage("Start a new conversation and clear current history?")
                .setPositiveButton("New Chat") { _, _ ->
                    JarvisApp.instance.chatRepository.clearHistory()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Back / Menu
        binding.btnChatMenu.setOnClickListener {
            (activity as? MainActivity)?.selectTab(MainActivity.TAB_HOME)
        }

        // Mode Chips
        binding.chipPersona.setOnClickListener {
            Toast.makeText(requireContext(), "Persona: Jarvis Active", Toast.LENGTH_SHORT).show()
        }

        // Premium speech pause/resume toggle (DEV HUB removed — no GitHub/Vercel keys in-app)
        binding.chipMode.setOnClickListener {
            if (!com.jarvis.assistant.security.InstallationManager.isPremium(requireContext())) {
                Toast.makeText(requireContext(), "Pause / Resume is a Premium feature", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!com.jarvis.assistant.background.JarvisConversationService.isSessionOn.value) {
                Toast.makeText(requireContext(), "Start Jarvis first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (com.jarvis.assistant.background.JarvisConversationService.isSpeechPaused()) {
                com.jarvis.assistant.background.JarvisConversationService.resumePlayback()
                (binding.chipMode.getChildAt(1) as? android.widget.TextView)?.text = "Pause"
                Toast.makeText(requireContext(), "Speech resumed", Toast.LENGTH_SHORT).show()
            } else {
                com.jarvis.assistant.background.JarvisConversationService.pausePlayback()
                (binding.chipMode.getChildAt(1) as? android.widget.TextView)?.text = "Resume"
                Toast.makeText(requireContext(), "Speech paused", Toast.LENGTH_SHORT).show()
            }
        }

        binding.chipGoogleWorkspace.setOnClickListener {
            startActivity(Intent(requireContext(), com.jarvis.assistant.google.GoogleWorkspaceActivity::class.java))
        }

        binding.btnAttach.setOnClickListener {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        observeChatTurns()
    }

    private fun sendMessage() {
        val text = binding.etMessageInput.text?.toString()?.trim() ?: ""
        if (text.isBlank()) return

        if (!JarvisConversationService.isServiceRunning()) {
            (activity as? MainActivity)?.handleMicOrOrbClick()
        }

        JarvisConversationService.sendTextMessage(text)
        binding.etMessageInput.text?.clear()
    }

    private fun observeChatTurns() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.chatTurns.collect { turns ->
                    chatAdapter.submitList(turns) {
                        if (turns.isNotEmpty()) {
                            binding.rvChatMessages.smoothScrollToPosition(turns.size - 1)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.root)
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) {
            binding.root.visibility = View.VISIBLE
            com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.root)
        } else if (hidden && _binding != null) {
            binding.root.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
