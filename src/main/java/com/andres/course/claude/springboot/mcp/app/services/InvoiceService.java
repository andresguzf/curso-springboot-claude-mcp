package com.andres.course.claude.springboot.mcp.app.services;

import com.andres.course.claude.springboot.mcp.app.dto.CustomerBillingSummaryDto;
import com.andres.course.claude.springboot.mcp.app.dto.InvoiceDto;
import com.andres.course.claude.springboot.mcp.app.dto.InvoiceItemDto;
import com.andres.course.claude.springboot.mcp.app.models.InvoiceStatus;
import jakarta.validation.Valid;

import java.util.List;

/**
 * Reglas de negocio:
 * - Subtotal, IVA y total se calculan siempre a partir de los items.
 * - Solo las facturas PENDING se pueden editar (cabecera, items o cliente).
 * - PAID y CANCELLED son estados finales; solo se llega a ellos desde PENDING.
 * - Una factura PAID no se puede borrar (se conserva como registro contable).
 *
 * Los parámetros @Valid se validan cuando llaman las tools MCP (ver @Validated
 * en la implementación).
 */
public interface InvoiceService {

    /** Lista facturas; ambos filtros son opcionales (null = sin filtro). */
    List<InvoiceDto> findAll(Long customerId, InvoiceStatus status);

    InvoiceDto findById(Long id);

    InvoiceDto findByNumber(String invoiceNumber);

    InvoiceDto create(@Valid InvoiceDto dto);

    /** Actualiza la cabecera y, si dto.items() no es null, reemplaza todas las líneas. */
    InvoiceDto update(Long id, @Valid InvoiceDto dto);

    InvoiceDto changeStatus(Long id, InvoiceStatus status);

    InvoiceDto addItem(Long invoiceId, @Valid InvoiceItemDto item);

    InvoiceDto updateItem(Long invoiceId, Integer lineNumber, @Valid InvoiceItemDto item);

    InvoiceDto removeItem(Long invoiceId, Integer lineNumber);

    void delete(Long id);

    CustomerBillingSummaryDto summaryByCustomer(Long customerId);
}
