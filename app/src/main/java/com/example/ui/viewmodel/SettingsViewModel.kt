package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.UserEntity
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.ChatDepRepository
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userId: String,
    private val repository: ChatDepRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val user: StateFlow<UserEntity?> = repository.observeUser(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserPreferences())

    val conversationCount: StateFlow<Int> = repository.observeConversationCount(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val messageCount: StateFlow<Int> = repository.observeMessageCount(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val attachmentCount: StateFlow<Int> = repository.observeAttachmentCount(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setAutoSpeakResponses(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoSpeakResponses(enabled)
        }
    }

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch {
            preferencesRepository.setSpeechRate(rate)
        }
    }

    fun updateDisplayName(newName: String) {
        viewModelScope.launch {
            repository.updateUserName(userId, newName)
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            repository.clearAllConversations(userId)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
        }
    }

    companion object {
        fun provideFactory(
            userId: String,
            repository: ChatDepRepository,
            preferencesRepository: UserPreferencesRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(userId, repository, preferencesRepository) }
        }
    }
}
