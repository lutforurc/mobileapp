package com.example.cashbookbd.ui.customer

import com.example.cashbookbd.ui.reports.model.SelectorOption

/** The web's ClientType select (Partytype ids). */
val CLIENT_TYPES: List<SelectorOption> = listOf(
    SelectorOption("1", "Customer"),
    SelectorOption("2", "Supplier"),
    SelectorOption("3", "Supplier & Customer"),
    SelectorOption("4", "Advance"),
)

/** The web's sexType select — lowercase ids are what the server stores. */
val SEX_TYPES: List<SelectorOption> = listOf(
    SelectorOption("male", "Male"),
    SelectorOption("female", "Female"),
    SelectorOption("other", "Other"),
)

/**
 * The web's nomineeRelationType — used for the customer's own Relation field
 * and for nominee rows alike. Two ids carry a space, verbatim from the web.
 */
val RELATION_TYPES: List<SelectorOption> = listOf(
    SelectorOption("father", "Father"),
    SelectorOption("mother", "Mother"),
    SelectorOption("husband", "Husband"),
    SelectorOption("wife", "Wife"),
    SelectorOption("brother", "Brother"),
    SelectorOption("sister", "Sister"),
    SelectorOption("grand mother", "Grand Mother"),
    SelectorOption("grand father", "Grand Father"),
    SelectorOption("other", "Other"),
)
