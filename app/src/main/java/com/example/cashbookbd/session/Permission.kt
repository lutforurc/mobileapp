package com.example.cashbookbd.session

/**
 * A single permission granted to the current user.
 *
 * The backend may send permissions either as objects
 * (`{ "id": 1, "name": "cash.received.create", "group_name": "Transaction" }`)
 * or as bare strings (`"cash.received.create"`). Both shapes normalize to this
 * type — see [com.example.cashbookbd.data.remote.dto.PermissionDto].
 */
data class Permission(
    val id: Long? = null,
    val name: String,
    val groupName: String? = null,
)
