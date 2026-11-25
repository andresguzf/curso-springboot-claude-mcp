package com.andres.course.claude.springboot.mcp.app.services;

import com.andres.course.claude.springboot.mcp.app.dto.CustomerDto;
import jakarta.validation.Valid;

import java.util.List;

public interface CustomerService {

    /** Lista clientes; ambos filtros son opcionales (null = sin filtro). */
    List<CustomerDto> findAll(String search, Boolean active);

    CustomerDto findById(Long id);

    CustomerDto create(@Valid CustomerDto dto);

    CustomerDto update(Long id, @Valid CustomerDto dto);

    /** Borra el cliente solo si no tiene facturas; si las tiene, hay que desactivarlo. */
    void delete(Long id);
}
