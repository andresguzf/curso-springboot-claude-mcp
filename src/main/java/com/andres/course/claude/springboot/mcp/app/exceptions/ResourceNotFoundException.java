package com.andres.course.claude.springboot.mcp.app.exceptions;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " con id " + id + " no encontrado");
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
