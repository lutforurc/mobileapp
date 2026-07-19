package com.example.cashbookbd.ui.components

/**
 * How many characters must be typed before a type-ahead dropdown queries the
 * server. Shared by every searchable dropdown so the threshold is defined once.
 *
 * Short keywords match most of the table, so searching on one or two characters
 * costs a round trip to return a list nobody can usefully scan.
 */
const val MIN_SEARCH_CHARS = 3
