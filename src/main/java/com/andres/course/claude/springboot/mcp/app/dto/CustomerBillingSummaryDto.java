package com.andres.course.claude.springboot.mcp.app.dto;

import java.math.BigDecimal;

/**
 * Resumen de facturación de un cliente (solo lectura). Las facturas anuladas
 * se cuentan aparte y no suman al total facturado.
 */
public record CustomerBillingSummaryDto(
        Long customerId,
        String customerName,
        long totalInvoices,
        long pendingInvoices,
        long paidInvoices,
        long cancelledInvoices,
        BigDecimal totalBilled,
        BigDecimal totalPaid,
        BigDecimal totalPending
) {
}
