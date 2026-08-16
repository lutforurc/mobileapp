package com.example.cashbookbd.labour

import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions

/** One entry of the Labour Items menu. */
data class LabourItem(
    val key: String,
    val title: String,
    val anyOf: List<String>,
)

/**
 * The web's "Labour Items" sidebar group (react b1cfc84): the category and
 * item lists a labour bill is built from. Their own menu rather than a corner
 * of Invoice — they are master data, set up once and rarely touched, while
 * everything under Invoice is a daily entry. Both entries are AppLists keys;
 * their add/edit forms ride the shared CRUD engine (HrmCrudForms).
 */
object LabourMenu {

    val all: List<LabourItem> = listOf(
        LabourItem("labourCategories", "Category", listOf("labour.category.view")),
        LabourItem("labourItems", "Item", listOf("labour.item.view")),
    )

    private val byKey: Map<String, LabourItem> = all.associateBy { it.key }

    fun byKey(key: String?): LabourItem? = key?.let { byKey[it] }

    /** The web gates the group on the two view permissions (menuPermissions.ts). */
    fun hasParentAccess(permissions: List<Permission>?): Boolean =
        Permissions.hasAny(permissions, all.flatMap { it.anyOf })

    /** Entries the user is allowed to open, in registry order. */
    fun visible(permissions: List<Permission>?): List<LabourItem> =
        all.filter { Permissions.hasAny(permissions, it.anyOf) }
}
