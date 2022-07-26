package com.rerere.iwara4a.ui.screen.image

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rerere.iwara4a.data.dao.AppDatabase
import com.rerere.iwara4a.data.dao.insertSmartly
import com.rerere.iwara4a.data.model.detail.image.ImageDetail
import com.rerere.iwara4a.data.model.history.HistoryData
import com.rerere.iwara4a.data.model.history.HistoryType
import com.rerere.iwara4a.data.model.session.SessionManager
import com.rerere.iwara4a.data.repo.MediaRepo
import com.rerere.iwara4a.util.DataState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val mediaRepo: MediaRepo,
    private val database: AppDatabase
) : ViewModel() {
    val imageId: String = checkNotNull(savedStateHandle["imageId"])
    var imageDetail = MutableStateFlow<DataState<ImageDetail>>(DataState.Empty)

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        imageDetail.value = DataState.Loading
        val response = mediaRepo.getImageDetail(sessionManager.session, imageId)
        if (response.isSuccess()) {
            imageDetail.value = DataState.Success(response.read())

            // insert history
            database.getHistoryDao().insertSmartly(
                HistoryData(
                    date = System.currentTimeMillis(),
                    title = response.read().title,
                    preview = response.read().imageLinks.getOrNull(0) ?: "",
                    route = "image/$imageId",
                    historyType = HistoryType.IMAGE
                )
            )
        } else {
            imageDetail.value = DataState.Error(response.errorMessage())
        }
    }
}