package com.djportablepro.app.ui.library

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LibraryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryState())
    val uiState: StateFlow<LibraryState> = _uiState.asStateFlow()

    fun updateSearchQuery(deck: Deck, query: String) {
        _uiState.update { currentState ->
            when (deck) {
                Deck.A -> currentState.copy(deckA = currentState.deckA.copy(searchQuery = query))
                Deck.B -> currentState.copy(deckB = currentState.deckB.copy(searchQuery = query))
            }
        }
    }

    // Additional methods for folder navigation and track selection would go here
    
    enum class Deck { A, B }
}
