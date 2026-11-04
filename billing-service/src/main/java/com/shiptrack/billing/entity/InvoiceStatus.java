package com.shiptrack.billing.entity;

public enum InvoiceStatus {
    /** Created automatically when the shipment was delivered; waiting for payment. */
    ISSUED,
    /** Payment received (marked by an admin). */
    PAID
}
