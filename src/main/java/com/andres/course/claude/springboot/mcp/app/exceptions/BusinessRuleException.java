package com.andres.course.claude.springboot.mcp.app.exceptions;

/**
 * Operación válida en su forma pero no permitida por las reglas de negocio
 * (p. ej. editar una factura pagada o borrar un cliente con facturas).
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
