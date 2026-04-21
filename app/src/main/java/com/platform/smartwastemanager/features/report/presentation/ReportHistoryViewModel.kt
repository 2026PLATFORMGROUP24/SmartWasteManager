package com.platform.smartwastemanager.features.report.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.platform.smartwastemanager.features.report.data.ReportRepository
import com.platform.smartwastemanager.features.report.domain.WasteReport
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ReportHistoryUiState {
    object Loading : ReportHistoryUiState()
    data class Success(val reports: List<WasteReport>) : ReportHistoryUiState()
    data class Error(val message: String) : ReportHistoryUiState()
}

class ReportHistoryViewModel(
    private val reportRepository: ReportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReportHistoryUiState>(ReportHistoryUiState.Loading)
    val uiState: StateFlow<ReportHistoryUiState> = _uiState.asStateFlow()

    private var currentUserUid: String = ""
    private var reportsJob: Job? = null

    fun loadReports(userUid: String, forceRefresh: Boolean = false) {
        if (userUid.isBlank()) {
            currentUserUid = ""
            reportsJob?.cancel()
            _uiState.value = ReportHistoryUiState.Success(emptyList())
            return
        }
        if (!forceRefresh && currentUserUid == userUid && reportsJob != null) return

        currentUserUid = userUid
        reportsJob?.cancel()
        reportsJob = viewModelScope.launch {
            _uiState.value = ReportHistoryUiState.Loading
            reportRepository.getUserReports(userUid).collect { reports ->
                _uiState.value = ReportHistoryUiState.Success(reports)
            }
        }
    }

    fun refresh() {
        loadReports(currentUserUid, forceRefresh = true)
    }

    fun deleteReport(reportId: String) {
        if (reportId.isBlank()) return
        viewModelScope.launch {
            val result = reportRepository.dismissReport(reportId)
            if (result.isFailure) {
                val exception = result.exceptionOrNull()
                val reason = when (exception) {
                    is FirebaseFirestoreException -> when (exception.code) {
                        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                            "You do not have permission to delete this report."
                        FirebaseFirestoreException.Code.UNAVAILABLE ->
                            "Network unavailable. Please check your connection and try again."
                        else -> exception.message
                    }
                    else -> exception?.message
                }?.takeIf { it.isNotBlank() }
                _uiState.value = ReportHistoryUiState.Error(
                    reason ?: "Failed to delete report. Please check your connection and try again."
                )
            }
        }
    }

    companion object {
        fun factory(reportRepository: ReportRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ReportHistoryViewModel(reportRepository) as T
            }
    }
}
