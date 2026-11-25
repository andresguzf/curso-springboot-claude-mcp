package com.andres.course.claude.springboot.mcp.app.models;

/**
 * Estados posibles de una factura. Coinciden con el CHECK de la columna
 * invoices.status en la base de datos.
 */
public enum InvoiceStatus {
    PENDING,
    PAID,
    CANCELLED
}
