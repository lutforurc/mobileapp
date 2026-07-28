package com.example.cashbookbd.requisition

import com.example.cashbookbd.session.Permission
import com.example.cashbookbd.session.Permissions

/**
 * One entry in the Requisition menu, mirroring the web sidebar's "Requisition"
 * group: the requisition list, the create form and the comparison report. Each
 * item is gated by its own permission; the parent section is shown when the
 * user holds any of them.
 */
data class RequisitionItem(
    val key: String,
    val title: String,
    val anyOf: List<String>,
)

/** The Requisition menu registry and its permission rules. */
object RequisitionMenu {

    val all: List<RequisitionItem> = listOf(
        RequisitionItem("requisitions", "Requisitions", listOf("requisition.view")),
        RequisitionItem("requisitionCreate", "New Requisition", listOf("requisition.create")),
        RequisitionItem("requisitionComparison", "Comparison", listOf("requisition.comparison")),
    )

    private val byKey: Map<String, RequisitionItem> = all.associateBy { it.key }

    fun byKey(key: String?): RequisitionItem? = key?.let { byKey[it] }

    /** True when the user can see the Requisition parent section at all. */
    fun hasParentAccess(permissions: List<Permission>?): Boolean =
        Permissions.hasAny(permissions, all.flatMap { it.anyOf })

    /** Requisition entries the user is allowed to open, in registry order. */
    fun visible(permissions: List<Permission>?): List<RequisitionItem> =
        all.filter { Permissions.hasAny(permissions, it.anyOf) }
}
