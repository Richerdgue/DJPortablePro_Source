package com.djportablepro.app.ui.library

data class LibraryState(
    val deckA: DeckLibraryState = DeckLibraryState(),
    val deckB: DeckLibraryState = DeckLibraryState()
)

data class DeckLibraryState(
    val searchQuery: String = "",
    val currentFolder: String = "Root",
    val selectedTrack: String? = null,
    val items: List<String> = emptyList()
)
