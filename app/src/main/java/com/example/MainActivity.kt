package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.AppDatabase
import com.example.data.repository.ChatRepository
import com.example.ui.ChatScreen
import com.example.data.attachment.AttachmentStore
import com.example.ui.ChatViewModel
import com.example.ui.ChatViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = ChatRepository(
            chatDao = database.chatDao(),
            apiConfigDao = database.apiConfigDao(),
            attachmentStore = AttachmentStore(applicationContext)
        )

        setContent {
            MyApplicationTheme {
                val chatViewModel: ChatViewModel = viewModel(
                    factory = ChatViewModelFactory(repository, applicationContext)
                )

                Surface(modifier = Modifier.fillMaxSize()) {
                    ChatScreen(viewModel = chatViewModel)
                }
            }
        }
    }
}
