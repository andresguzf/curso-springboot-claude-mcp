package com.andres.course.claude.springboot.mcp.app.dto;

import com.andres.course.claude.springboot.mcp.app.models.InvoiceStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceDto(
        Long id,

        // Opcional al crear: si no viene, se genera FAC-{año}-{correlativo}.
        @Size(max = 30, message = "El número de factura no puede superar los {max} caracteres")
        String invoiceNumber,

        @NotNull(message = "El cliente es obligatorio")
        Long customerId,

        // Nombre completo del cliente, solo informativo en la respuesta.
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        String customerName,

        // Opcional al crear: si no viene, se usa la fecha de hoy.
        LocalDate issueDate,

        LocalDate dueDate,

        // Montos calculados por el sistema a partir de los items.
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        BigDecimal subtotal,

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        BigDecimal tax,

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        BigDecimal total,

        // Opcional al crear: PENDING por defecto.
        InvoiceStatus status,

        @Size(max = 255, message = "Las notas no pueden superar los {max} caracteres")
        String notes,

        // Obligatorio al crear (al menos uno). Al actualizar: null = mantener las líneas.
        List<@Valid InvoiceItemDto> items
) {
}
