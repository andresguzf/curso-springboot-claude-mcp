package com.andres.course.claude.springboot.mcp.app.mappers;

import com.andres.course.claude.springboot.mcp.app.dto.InvoiceDto;
import com.andres.course.claude.springboot.mcp.app.dto.InvoiceItemDto;
import com.andres.course.claude.springboot.mcp.app.models.Customer;
import com.andres.course.claude.springboot.mcp.app.models.Invoice;
import com.andres.course.claude.springboot.mcp.app.models.InvoiceItem;
import org.springframework.stereotype.Component;

/**
 * Convierte facturas y sus líneas. El cliente, el número, los montos y el estado
 * no se copian desde el dto: los resuelve y valida el service.
 */
@Component
public class InvoiceMapper {

    /** entity -> dto (incluye las líneas) */
    public InvoiceDto toDto(Invoice entity) {
        if (entity == null) {
            return null;
        }
        Customer customer = entity.getCustomer();
        return new InvoiceDto(
                entity.getId(),
                entity.getInvoiceNumber(),
                customer.getId(),
                customer.getName() + " " + customer.getLastName(),
                entity.getIssueDate(),
                entity.getDueDate(),
                entity.getSubtotal(),
                entity.getTax(),
                entity.getTotal(),
                entity.getStatus(),
                entity.getNotes(),
                entity.getItems().stream().map(this::toItemDto).toList()
        );
    }

    /** dto -> entity (nueva, sin líneas: el service las agrega y numera) */
    public Invoice toEntity(InvoiceDto dto) {
        if (dto == null) {
            return null;
        }
        Invoice entity = new Invoice();
        updateEntity(dto, entity);
        return entity;
    }

    /** Copia los campos editables de la cabecera sobre una factura existente. */
    public void updateEntity(InvoiceDto dto, Invoice entity) {
        if (dto.issueDate() != null) {
            entity.setIssueDate(dto.issueDate());
        }
        entity.setDueDate(dto.dueDate());
        entity.setNotes(dto.notes());
    }

    public InvoiceItemDto toItemDto(InvoiceItem item) {
        return new InvoiceItemDto(
                item.getId(),
                item.getLineNumber(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.calculateLineTotal()
        );
    }

    public InvoiceItem toItemEntity(InvoiceItemDto dto) {
        InvoiceItem item = new InvoiceItem();
        updateItemEntity(dto, item);
        return item;
    }

    public void updateItemEntity(InvoiceItemDto dto, InvoiceItem item) {
        item.setDescription(dto.description());
        item.setQuantity(dto.quantity());
        item.setUnitPrice(dto.unitPrice());
    }
}
