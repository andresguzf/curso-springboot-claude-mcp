package com.andres.course.claude.springboot.mcp.app.mcp;

import com.andres.course.claude.springboot.mcp.app.dto.CustomerBillingSummaryDto;
import com.andres.course.claude.springboot.mcp.app.dto.InvoiceDto;
import com.andres.course.claude.springboot.mcp.app.dto.InvoiceItemDto;
import com.andres.course.claude.springboot.mcp.app.models.InvoiceStatus;
import com.andres.course.claude.springboot.mcp.app.services.InvoiceService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Tools MCP para administrar facturas y sus líneas. Solo traducen la llamada al
 * service; los montos (subtotal, IVA, total) siempre los calcula el sistema.
 */
@Component
public class InvoiceTools {

    private final InvoiceService invoiceService;

    public InvoiceTools(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @McpTool(name = "list_invoices",
            description = "Lista facturas con sus líneas, de la más reciente a la más antigua. "
                    + "Filtros opcionales por cliente y por estado (PENDING, PAID, CANCELLED).",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public List<InvoiceDto> listInvoices(
            @McpToolParam(description = "Id del cliente", required = false) Long customerId,
            @McpToolParam(description = "Estado: PENDING (pendiente), PAID (pagada) o CANCELLED (anulada)", required = false) InvoiceStatus status) {
        return invoiceService.findAll(customerId, status);
    }

    @McpTool(name = "get_invoice",
            description = "Obtiene una factura con sus líneas, por id o por número (p. ej. FAC-2026-0001). Envía uno de los dos.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public InvoiceDto getInvoice(
            @McpToolParam(description = "Id de la factura", required = false) Long invoiceId,
            @McpToolParam(description = "Número de la factura, p. ej. FAC-2026-0001", required = false) String invoiceNumber) {
        if (invoiceId != null) {
            return invoiceService.findById(invoiceId);
        }
        if (invoiceNumber != null && !invoiceNumber.isBlank()) {
            return invoiceService.findByNumber(invoiceNumber);
        }
        throw new IllegalArgumentException("Indica invoiceId o invoiceNumber");
    }

    @McpTool(name = "create_invoice",
            description = "Crea una factura para un cliente activo, con una o más líneas. "
                    + "El número se genera solo (FAC-{año}-{correlativo}) si no se indica. "
                    + "Subtotal, IVA (19 %) y total se calculan a partir de las líneas. Queda en estado PENDING.",
            annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public InvoiceDto createInvoice(
            @McpToolParam(description = "Id del cliente al que se asigna la factura") Long customerId,
            @McpToolParam(description = "Líneas de la factura. Cada una necesita description, quantity (> 0) y unitPrice (≥ 0); "
                    + "id, lineNumber y lineTotal los asigna el sistema.") List<InvoiceItemDto> items,
            @McpToolParam(description = "Fecha de emisión (yyyy-MM-dd); por defecto hoy", required = false) LocalDate issueDate,
            @McpToolParam(description = "Fecha de vencimiento (yyyy-MM-dd), no anterior a la emisión", required = false) LocalDate dueDate,
            @McpToolParam(description = "Notas o detalle general de la factura", required = false) String notes,
            @McpToolParam(description = "Número de factura personalizado; si se omite se genera", required = false) String invoiceNumber) {
        return invoiceService.create(new InvoiceDto(null, invoiceNumber, customerId, null, issueDate, dueDate,
                null, null, null, null, notes, items));
    }

    @McpTool(name = "update_invoice",
            description = "Modifica la cabecera de una factura PENDING: reasignarla a otro cliente, cambiar fechas o notas. "
                    + "Solo cambia los campos enviados. Para las líneas usa add_invoice_item, update_invoice_item o "
                    + "remove_invoice_item; para el estado usa change_invoice_status.",
            annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public InvoiceDto updateInvoice(
            @McpToolParam(description = "Id de la factura") Long invoiceId,
            @McpToolParam(description = "Id del nuevo cliente (para reasignar la factura)", required = false) Long customerId,
            @McpToolParam(description = "Nueva fecha de emisión (yyyy-MM-dd)", required = false) LocalDate issueDate,
            @McpToolParam(description = "Nueva fecha de vencimiento (yyyy-MM-dd)", required = false) LocalDate dueDate,
            @McpToolParam(description = "Nuevas notas; cadena vacía para borrarlas", required = false) String notes) {
        InvoiceDto current = invoiceService.findById(invoiceId);
        InvoiceDto merged = new InvoiceDto(
                invoiceId,
                null,
                customerId != null ? customerId : current.customerId(),
                null,
                issueDate != null ? issueDate : current.issueDate(),
                dueDate != null ? dueDate : current.dueDate(),
                null, null, null,
                null,
                notes == null ? current.notes() : (notes.isBlank() ? null : notes.trim()),
                null
        );
        return invoiceService.update(invoiceId, merged);
    }

    @McpTool(name = "change_invoice_status",
            description = "Cambia el estado de una factura PENDING a PAID (marcar como pagada) o CANCELLED (anular). "
                    + "PAID y CANCELLED son estados finales.",
            annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public InvoiceDto changeInvoiceStatus(
            @McpToolParam(description = "Id de la factura") Long invoiceId,
            @McpToolParam(description = "Nuevo estado: PAID o CANCELLED") InvoiceStatus status) {
        return invoiceService.changeStatus(invoiceId, status);
    }

    @McpTool(name = "add_invoice_item",
            description = "Agrega una línea al final de una factura PENDING y recalcula los totales.",
            annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public InvoiceDto addInvoiceItem(
            @McpToolParam(description = "Id de la factura") Long invoiceId,
            @McpToolParam(description = "Descripción del producto o servicio") String description,
            @McpToolParam(description = "Cantidad (> 0, admite decimales)") BigDecimal quantity,
            @McpToolParam(description = "Precio unitario sin IVA (≥ 0)") BigDecimal unitPrice) {
        return invoiceService.addItem(invoiceId, new InvoiceItemDto(null, null, description, quantity, unitPrice, null));
    }

    @McpTool(name = "update_invoice_item",
            description = "Modifica una línea de una factura PENDING, identificada por su lineNumber. "
                    + "Solo cambia los campos enviados y recalcula los totales.",
            annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public InvoiceDto updateInvoiceItem(
            @McpToolParam(description = "Id de la factura") Long invoiceId,
            @McpToolParam(description = "Número de línea dentro de la factura (lineNumber)") Integer lineNumber,
            @McpToolParam(description = "Nueva descripción", required = false) String description,
            @McpToolParam(description = "Nueva cantidad (> 0)", required = false) BigDecimal quantity,
            @McpToolParam(description = "Nuevo precio unitario (≥ 0)", required = false) BigDecimal unitPrice) {
        InvoiceItemDto current = invoiceService.findById(invoiceId).items().stream()
                .filter(i -> i.lineNumber().equals(lineNumber))
                .findFirst()
                .orElse(null);
        // Si la línea no existe, el service responde con el error correspondiente.
        InvoiceItemDto merged = new InvoiceItemDto(null, lineNumber,
                description != null ? description : (current != null ? current.description() : null),
                quantity != null ? quantity : (current != null ? current.quantity() : null),
                unitPrice != null ? unitPrice : (current != null ? current.unitPrice() : null),
                null);
        return invoiceService.updateItem(invoiceId, lineNumber, merged);
    }

    @McpTool(name = "remove_invoice_item",
            description = "Quita una línea de una factura PENDING y recalcula los totales. La factura debe conservar al menos una línea.",
            annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false))
    public InvoiceDto removeInvoiceItem(
            @McpToolParam(description = "Id de la factura") Long invoiceId,
            @McpToolParam(description = "Número de línea a quitar (lineNumber)") Integer lineNumber) {
        return invoiceService.removeItem(invoiceId, lineNumber);
    }

    @McpTool(name = "delete_invoice",
            description = "Borra una factura y sus líneas de forma permanente. No se permite con facturas PAID; "
                    + "para dejar sin efecto una factura pendiente es preferible anularla (CANCELLED). Confirma con el usuario antes de usarla.",
            annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false))
    public String deleteInvoice(
            @McpToolParam(description = "Id de la factura a borrar") Long invoiceId) {
        invoiceService.delete(invoiceId);
        return "Factura " + invoiceId + " borrada";
    }

    @McpTool(name = "get_customer_billing_summary",
            description = "Resumen de facturación de un cliente: cantidad de facturas por estado y montos pagados, "
                    + "pendientes y totales (las anuladas no suman).",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CustomerBillingSummaryDto getCustomerBillingSummary(
            @McpToolParam(description = "Id del cliente") Long customerId) {
        return invoiceService.summaryByCustomer(customerId);
    }
}
