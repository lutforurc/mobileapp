package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jAsArr
import com.example.cashbookbd.core.jAsObj
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jObjects
import com.example.cashbookbd.core.jStr
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** One product line of an invoice loaded for editing. */
data class InvoiceEditLine(
    val productId: String,
    val name: String,
    val code: String,
    val unit: String,
    val qty: Double,
    val price: Double,
    val warehouseId: String,
    val serialNo: String,
    val bag: String,
    val variance: String,
    val varianceType: String,
    val measurement: String,
    val measurementUnit: String,
    val packageSize: String,
    val packageUnit: String,
)

/**
 * A sales or purchase invoice as the inventory edit endpoints answer it
 * (`trading/sales/api-edit`, `electronics/sales/edit`, `{construction|trading}/
 * purchase/api-edit`, `electronics/purchase/edit`): `{mtmId, trackedProductId,
 * transaction}` where the transaction carries a sales_master/purchase_master
 * with its details and the posted acc_transaction_master legs. Mapped the way
 * the web's edit effects map it.
 */
data class InvoiceEdit(
    val mtmId: String,
    val partyId: String,
    val partyName: String,
    val lines: List<InvoiceEditLine>,
    /** The master's netpayment — what General sales and every purchase reload. */
    val netPayment: String,
    /** Cash (17) debits across the voucher — what Trading/Electronics/Tiles sales reload. */
    val cashDebit: Double,
    val discount: Double,
    val notes: String,
    val vehicleNo: String,
    val invoiceNo: String,
    val invoiceDate: String,
    val purchaseOrderId: String,
    val purchaseOrderText: String,
    val trackedProductId: String,
    /** Electronics/Tiles sales' extra legs: coa4 42 service, 41 TDS, 198 transport. */
    val serviceCharge: Double,
    val tdsAmount: Double,
    val transportationAmt: Double,
    /** Electronics sales' saved schedule, already in the update's shape. */
    val installments: JsonArray,
    val manualVoucherNo: String,
    val manualVoucherDate: String,
    val manualChallanNo: String,
    val manualChallanDate: String,
    /** Tiles sales: "1" the invoice takes the stock, "0" a delivery will. */
    val deductsStock: String,
    val referrerId: String,
)

/**
 * Reads an edit endpoint's reply. A refusal ("This voucher is approved!",
 * "Don't find sales invoice!") arrives as success with an empty payload, so
 * a missing transaction is an error carrying the server's own message.
 */
fun parseInvoiceEdit(body: JsonObject, sales: Boolean): Resource<InvoiceEdit> {
    val message = body.jStr("message")
    if (body.get("success")?.takeIf { it.isJsonPrimitive }?.asBoolean == false) {
        return Resource.Error(message.ifBlank { "Invoice not found." })
    }
    val data = body.get("data").jAsObj()?.get("data").jAsObj()
    val trx = data?.get("transaction").jAsObj()
    val master = trx?.get(if (sales) "sales_master" else "purchase_master").jAsObj()
    if (data == null || trx == null || master == null) {
        return Resource.Error(message.ifBlank { "Invoice not found." })
    }
    val partyId = master.jStr(if (sales) "customer_id" else "supplier_id")
    val legs = trx.get("acc_transaction_master").jAsArr()?.jObjects().orEmpty()
    val details = legs.flatMap { it.get("acc_transaction_details").jAsArr()?.jObjects().orEmpty() }
    val partyName = details.firstOrNull { it.get("coa_l4").jAsObj()?.jStr("id") == partyId }
        ?.get("coa_l4").jAsObj()?.jStr("name").orEmpty()
    // The extra charges sit on the first leg, as the web's findCoaCredit reads them.
    val firstLeg = legs.firstOrNull()?.get("acc_transaction_details").jAsArr()?.jObjects().orEmpty()
    fun creditOf(coa: String) = firstLeg.firstOrNull { it.jStr("coa4_id") == coa }?.jNum("credit") ?: 0.0

    val lines = master.get("details").jAsArr()?.jObjects().orEmpty().map { d ->
        val product = d.get("product").jAsObj()
        InvoiceEditLine(
            productId = product?.jStr("id")?.ifBlank { null } ?: d.jStr("product_id"),
            name = product?.jStr("name").orEmpty(),
            code = product?.jStr("code").orEmpty(),
            unit = product?.get("unit").jAsObj()?.jStr("name").orEmpty(),
            qty = d.jNum("quantity"),
            price = d.jNum(if (sales) "sales_price" else "purchase_price"),
            warehouseId = d.jStr("godown_id"),
            serialNo = d.jStr("serial_no"),
            bag = d.jStr("bag"),
            variance = d.jStr("weight_variance"),
            varianceType = d.jStr("variance_type"),
            measurement = d.jStr("measurement"),
            measurementUnit = d.jStr("measurement_unit"),
            packageSize = d.jStr("package_size"),
            packageUnit = d.jStr("package_unit"),
        )
    }

    // The schedule goes back as the web's editedInstallments: due dates the
    // server's Carbon::parse reads (ISO, not dd/MM/yyyy).
    val installments = JsonArray().apply {
        trx.get("installments").jAsArr()?.jObjects().orEmpty().forEach { i ->
            add(JsonObject().apply {
                addProperty("id", i.jStr("id"))
                addProperty("customer_id", i.jStr("customer_id"))
                addProperty("main_trx_id", i.jStr("main_trx_id"))
                addProperty("installment_no", i.jStr("installment_no"))
                val due = i.jStr("due_date")
                val parts = due.split("/")
                addProperty("due_date", if (parts.size == 3) "${parts[2]}-${parts[1]}-${parts[0]}" else due)
                addProperty("amount", i.jNum("amount"))
                add("payments", i.get("payments").jAsArr() ?: JsonArray())
            })
        }
    }

    val order = master.get("purchase_order").jAsObj()
    return Resource.Success(
        InvoiceEdit(
            mtmId = data.jStr("mtmId"),
            partyId = partyId,
            partyName = partyName.ifBlank { "-" },
            lines = lines,
            netPayment = master.jStr("netpayment"),
            cashDebit = details.filter { it.jStr("coa4_id") == "17" }.sumOf { it.jNum("debit") },
            discount = master.jNum("discount"),
            notes = master.jStr("notes"),
            vehicleNo = master.jStr("vehicle_no"),
            invoiceNo = master.jStr("invoice_no"),
            invoiceDate = master.jStr("invoice_date"),
            purchaseOrderId = order?.jStr("id").orEmpty(),
            purchaseOrderText = order?.jStr("order_number").orEmpty(),
            trackedProductId = data.jStr("trackedProductId"),
            serviceCharge = creditOf("42"),
            tdsAmount = creditOf("41"),
            transportationAmt = creditOf("198"),
            installments = installments,
            manualVoucherNo = trx.jStr("manual_voucher_no"),
            manualVoucherDate = trx.jStr("manual_voucher_date"),
            manualChallanNo = trx.jStr("manual_challan_no"),
            manualChallanDate = trx.jStr("manual_challan_date"),
            deductsStock = master.jStr("invoice_deducts_stock").ifBlank { "1" },
            referrerId = data.jStr("referrer_id"),
        )
    )
}
