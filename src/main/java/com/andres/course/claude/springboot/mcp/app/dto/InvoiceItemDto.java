package com.andres.course.claude.springboot.mcp.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record InvoiceItemDto(
        // Lo genera la base de datos. Sin @JsonProperty, el esquema JSON de las
        // tools MCP lo marcaría como obligatorio en cada item.
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        Long id,

        // Lo asigna el sistema según el orden de las líneas.
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        Integer lineNumber,

        @NotBlank(message = "La descripción del item es obligatoria")
        @Size(max = 255, message = "La descripción no puede superar los {max} caracteres")
        String description,

        @NotNull(message = "La cantidad es obligatoria")
        @DecimalMin(value = "0.0", inclusive = false, message = "La cantidad debe ser mayor que 0")
        @Digits(integer = 8, fraction = 2, message = "La cantidad admite hasta {integer} enteros y {fraction} decimales")
        BigDecimal quantity,

        @NotNull(message = "El precio unitario es obligatorio")
        @DecimalMin(value = "0.0", message = "El precio unitario no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "El precio admite hasta {integer} enteros y {fraction} decimales")
        BigDecimal unitPrice,

        // cantidad × precio unitario; lo calcula el sistema.
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        BigDecimal lineTotal
) {
}
