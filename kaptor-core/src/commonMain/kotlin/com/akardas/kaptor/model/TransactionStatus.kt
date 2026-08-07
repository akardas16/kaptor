package com.akardas.kaptor.model

/** Lifecycle state of a captured HTTP transaction. */
enum class TransactionStatus {
    /** Request has been sent, response not yet received. */
    Requested,

    /** Response received successfully. */
    Complete,

    /** Request failed with a network/serialization error. */
    Failed,
}
