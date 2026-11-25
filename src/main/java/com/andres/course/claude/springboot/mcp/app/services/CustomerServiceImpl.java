package com.andres.course.claude.springboot.mcp.app.services;

import com.andres.course.claude.springboot.mcp.app.dto.CustomerDto;
import com.andres.course.claude.springboot.mcp.app.exceptions.BusinessRuleException;
import com.andres.course.claude.springboot.mcp.app.exceptions.DuplicateResourceException;
import com.andres.course.claude.springboot.mcp.app.exceptions.ResourceNotFoundException;
import com.andres.course.claude.springboot.mcp.app.mappers.CustomerMapper;
import com.andres.course.claude.springboot.mcp.app.models.Customer;
import com.andres.course.claude.springboot.mcp.app.repositories.CustomerRepository;
import com.andres.course.claude.springboot.mcp.app.repositories.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;

// @Validated activa la validación de los parámetros @Valid del contrato cuando
// las tools MCP llaman al service (Bean Validation sobre los DTOs).
@Service
@Validated
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerMapper customerMapper;

    public CustomerServiceImpl(CustomerRepository customerRepository,
                               InvoiceRepository invoiceRepository,
                               CustomerMapper customerMapper) {
        this.customerRepository = customerRepository;
        this.invoiceRepository = invoiceRepository;
        this.customerMapper = customerMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerDto> findAll(String search, Boolean active) {
        String pattern = (search == null || search.isBlank())
                ? "%"
                : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return customerRepository.search(pattern, active).stream()
                .map(customerMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDto findById(Long id) {
        return customerMapper.toDto(getOrThrow(id));
    }

    @Override
    @Transactional
    public CustomerDto create(CustomerDto dto) {
        if (customerRepository.existsByEmailIgnoreCase(dto.email())) {
            throw new DuplicateResourceException("Cliente", "email", dto.email());
        }
        Customer saved = customerRepository.save(customerMapper.toEntity(dto));
        return customerMapper.toDto(saved);
    }

    @Override
    @Transactional
    public CustomerDto update(Long id, CustomerDto dto) {
        Customer entity = getOrThrow(id);
        if (customerRepository.existsByEmailIgnoreCaseAndIdNot(dto.email(), id)) {
            throw new DuplicateResourceException("Cliente", "email", dto.email());
        }
        customerMapper.updateEntity(dto, entity);
        return customerMapper.toDto(customerRepository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Customer entity = getOrThrow(id);
        long invoices = invoiceRepository.countByCustomerId(id);
        if (invoices > 0) {
            throw new BusinessRuleException("El cliente " + id + " tiene " + invoices
                    + " factura(s) y no se puede borrar; desactívalo en su lugar (active = false)");
        }
        customerRepository.delete(entity);
    }

    private Customer getOrThrow(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", id));
    }
}
