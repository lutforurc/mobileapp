package com.example.cashbookbd.ui.user

import com.example.cashbookbd.data.repository.TempPassword
import com.example.cashbookbd.data.repository.UserRow

/** Fixed page size, matching the web User List's default of 10 per page. */
const val USERS_PER_PAGE = 10

data class UserListUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val rows: List<UserRow> = emptyList(),

    /** The text in the search box (applied on Search, like the web). */
    val searchQuery: String = "",

    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val total: Int = 0,

    /** Row whose temporary password is being generated (its key icon spins). */
    val tempPasswordForId: String? = null,
    /** The generated password to show in a dialog; null = no dialog. */
    val tempPassword: TempPassword? = null,

    /** One-shot snackbar text (a load/temp-password outcome). */
    val actionMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val canPrev: Boolean get() = currentPage > 1
    val canNext: Boolean get() = currentPage < lastPage
    val showPagination: Boolean get() = lastPage > 1
}
