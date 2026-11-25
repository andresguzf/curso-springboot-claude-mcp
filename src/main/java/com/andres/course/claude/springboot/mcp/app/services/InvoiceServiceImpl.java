package com.andres.course.claude.springboot.mcp.app.services;

import com.andres.course.claude.springboot.mcp.app.dto.CustomerBillingSummaryDto;
import com.andres.course.claude.springboot.mcp.app.dto.InvoiceDto;
import com.andres.course.claude.springboot.mcp.app.dto.InvoiceItemDto;
import com.andres.course.claude.springboot.mcp.app.exceptions.BusinessRuleException;
import com.andres.course.claude.springboot.mcp.app.exceptions.DuplicateResourceException;
import com.andres.course.claude.springboot.mcp.app.exceptions.ResourceNotFoundException;
import com.andres.course.claude.springboot.mcp.app.mappers.InvoiceMapper;
import com.andres.course.claude.springboot.mcp.app.models.Customer;
import com.andres.course.claude.springboot.mcp.app.models.Invoice;
import com.andres.course.claude.springboot.mcp.app.models.InvoiceItem;
import com.andres.course.claude.springboot.mcp.app.models.InvoiceStatus;
import com.andres.course.claude.springboot.mcp.app.repositories.CustomerRepository;
import com.andres.course.claude.springboot.mcp.app.repositories.InvoiceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@Validated
public class InvoiceServiceImpl implements InvoiceService {

    private static final String NUMBER_PREFIX = "FAC-";

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceMapper invoiceMapper;
    private final BigDecimal taxRate;

    public InvoiceServiceImpl(InvoiceRepository invoiceRepository,
                              CustomerRepository customerRepository,
                              InvoiceMapper invoiceMapper,
                              @Value("${billing.tax-rate:0.19}") BigDecimal taxRate) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.invoiceMapper = invoiceMapper;
        this.taxRate = taxRate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceDto> findAll(Long customerId, InvoiceStatus status) {
        return invoiceRepository.search(customerId, status).stream()
                .map(invoiceMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceDto findById(Long id) {
        return invoiceMapper.toDto(getOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceDto findByNumber(String invoiceNumber) {
        return invoiceRepository.findByInvoiceNumberIgnoreCase(invoiceNumber.trim())
                .map(invoiceMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Factura con número '" + invoiceNumber + "' no encontrada"));
    }

    @Override
    @Transactional
    public InvoiceDto create(InvoiceDto dto) {
        if (dto.items() == null || dto.items().isEmpty()) {
            throw new IllegalArgumentException("La factura debe tener al menos un item");
        }
        Invoice invoice = invoiceMapper.toEntity(dto);
        invoice.setCustomer(getActiveCustomer(dto.customerId()));
        if (invoice.getIssueDate() == null) {
            invoice.setIssueDate(LocalDate.now());
        }
        invoice.setInvoiceNumber(resolveNewNumber(dto.invoiceNumber(), invoice.getIssueDate()));
        if (dto.status() != null) {
            invoice.setStatus(dto.status());
        }
        dto.items().forEach(item -> appendItem(invoice, item));
        validateDates(invoice);
        recalculateTotals(invoice);
        return invoiceMapper.toDto(invoiceRepository.save(invoice));
    }

    @Override
    @Transactional
    public InvoiceDto update(Long id, InvoiceDto dto) {
        Invoice invoice = getOrThrow(id);
        requirePending(invoice, "modificar");

        if (dto.customerId() != null && !dto.customerId().equals(invoice.getCustomer().getId())) {
            invoice.setCustomer(getActiveCustomer(dto.customerId()));
        }
        if (dto.invoiceNumber() != null && !dto.invoiceNumber().isBlank()
                && !dto.invoiceNumber().equalsIgnoreCase(invoice.getInvoiceNumber())) {
            if (invoiceRepository.existsByInvoiceNumberIgnoreCaseAndIdNot(dto.invoiceNumber(), id)) {
                throw new DuplicateResourceException("Factura", "número", dto.invoiceNumber());
            }
            invoice.setInvoiceNumber(dto.invoiceNumber().trim());
        }
        invoiceMapper.updateEntity(dto, invoice);

        if (dto.items() != null) {
            if (dto.items().isEmpty()) {
                throw new IllegalArgumentException("La factura debe tener al menos un item");
            }
            invoice.getItems().clear();
            // Hibernate ejecuta los INSERT antes que los DELETE: sin este flush, las
            // líneas nuevas chocarían con UNIQUE (invoice_id, line_number) de las viejas.
            invoiceRepository.flush();
            dto.items().forEach(item -> appendItem(invoice, item));
        }
        validateDates(invoice);
        recalculateTotals(invoice);

        if (dto.status() != null && dto.status() != invoice.getStatus()) {
            invoice.setStatus(dto.status());
        }
        return invoiceMapper.toDto(invoiceRepository.save(invoice));
    }

    @Override
    @Transactional
    public InvoiceDto changeStatus(Long id, InvoiceStatus status) {
        Invoice invoice = getOrThrow(id);
        if (invoice.getStatus() == status) {
            return invoiceMapper.toDto(invoice);
        }
        requirePending(invoice, "cambiar de estado");
        invoice.setStatus(status);
        return invoiceMapper.toDto(invoiceRepository.save(invoice));
    }

    @Override
    @Transactional
    public InvoiceDto addItem(Long invoiceId, InvoiceItemDto item) {
        Invoice invoice = getOrThrow(invoiceId);
        requirePending(invoice, "agregar items a");
        appendItem(invoice, item);
        recalculateTotals(invoice);
        return invoiceMapper.toDto(invoiceRepository.save(invoice));
    }

    @Override
    @Transactional
    public InvoiceDto updateItem(Long invoiceId, Integer lineNumber, InvoiceItemDto item) {
        Invoice invoice = getOrThrow(invoiceId);
        requirePending(invoice, "modificar items de");
        invoiceMapper.updateItemEntity(item, getItemOrThrow(invoice, lineNumber));
        recalculateTotals(invoice);
        return invoiceMapper.toDto(invoiceRepository.save(invoice));
    }

    @Override
    @Transactional
    public InvoiceDto removeItem(Long invoiceId, Integer lineNumber) {
        Invoice invoice = getOrThrow(invoiceId);
        requirePending(invoice, "quitar items de");
        InvoiceItem item = getItemOrThrow(invoice, lineNumber);
        if (invoice.getItems().size() == 1) {
            throw new BusinessRuleException("La factura " + invoice.getInvoiceNumber()
                    + " debe tener al menos un item; agrega otro antes de quitar este o anula la factura");
        }
        invoice.removeItem(item);
        recalculateTotals(invoice);
        return invoiceMapper.toDto(invoiceRepository.save(invoice));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Invoice invoice = getOrThrow(id);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new BusinessRuleException("La factura " + invoice.getInvoiceNumber()
                    + " está pagada y no se puede borrar");
        }
        invoiceRepository.delete(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerBillingSummaryDto summaryByCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", customerId));
        List<Invoice> invoices = invoiceRepository.findByCustomerId(customerId);

        long pending = countByStatus(invoices, InvoiceStatus.PENDING);
        long paid = countByStatus(invoices, InvoiceStatus.PAID);
        long cancelled = countByStatus(invoices, InvoiceStatus.CANCELLED);
        BigDecimal totalPaid = sumByStatus(invoices, InvoiceStatus.PAID);
        BigDecimal totalPending = sumByStatus(invoices, InvoiceStatus.PENDING);

        return new CustomerBillingSummaryDto(
                customer.getId(),
                customer.getName() + " " + customer.getLastName(),
                invoices.size(),
                pending,
                paid,
                cancelled,
                totalPaid.add(totalPending),
                totalPaid,
                totalPending
        );
    }

    // ----------------------------------------------------------------- helpers

    private Invoice getOrThrow(Long id) {
        return invoiceRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Factura", id));
    }

    private Customer getActiveCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", customerId));
        if (!customer.isActive()) {
            throw new BusinessRuleException("El cliente " + customerId
                    + " está inactivo; actívalo antes de asignarle facturas");
        }
        return customer;
    }

    private InvoiceItem getItemOrThrow(Invoice invoice, Integer lineNumber) {
        return invoice.getItems().stream()
                .filter(i -> i.getLineNumber().equals(lineNumber))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("La factura " + invoice.getInvoiceNumber()
                        + " no tiene la línea " + lineNumber));
    }

    private void requirePending(Invoice invoice, String action) {
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new BusinessRuleException("No se puede " + action + " la factura " + invoice.getInvoiceNumber()
                    + " porque está " + invoice.getStatus() + "; solo las facturas PENDING son editables");
        }
    }

    /** Agrega la línea con el siguiente número correlativo dentro de la factura. */
    private void appendItem(Invoice invoice, InvoiceItemDto dto) {
        int nextLine = invoice.getItems().stream()
                .mapToInt(InvoiceItem::getLineNumber)
                .max()
                .orElse(0) + 1;
        InvoiceItem item = invoiceMapper.toItemEntity(dto);
        item.setLineNumber(nextLine);
        invoice.addItem(item);
    }

    private void recalculateTotals(Invoice invoice) {
        BigDecimal subtotal = invoice.getItems().stream()
                .map(InvoiceItem::calculateLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        invoice.setSubtotal(subtotal);
        invoice.setTax(tax);
        invoice.setTotal(subtotal.add(tax));
    }

    private void validateDates(Invoice invoice) {
        if (invoice.getDueDate() != null && invoice.getDueDate().isBefore(invoice.getIssueDate())) {
            throw new IllegalArgumentException("La fecha de vencimiento (" + invoice.getDueDate()
                    + ") no puede ser anterior a la de emisión (" + invoice.getIssueDate() + ")");
        }
    }

    /** Usa el número indicado (si está libre) o genera FAC-{año}-{correlativo de 4 dígitos}. */
    private String resolveNewNumber(String requested, LocalDate issueDate) {
        if (requested != null && !requested.isBlank()) {
            if (invoiceRepository.existsByInvoiceNumberIgnoreCase(requested)) {
                throw new DuplicateResourceException("Factura", "número", requested);
            }
            return requested.trim();
        }
        String prefix = NUMBER_PREFIX + issueDate.getYear() + "-";
        int next = invoiceRepository.findTopByInvoiceNumberStartingWithOrderByInvoiceNumberDesc(prefix)
                .map(last -> parseSequence(last.getInvoiceNumber().substring(prefix.length())) + 1)
                .orElse(1);
        return prefix + String.format("%04d", next);
    }

    private int parseSequence(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private long countByStatus(List<Invoice> invoices, InvoiceStatus status) {
        return invoices.stream().filter(i -> i.getStatus() == status).count();
    }

    private BigDecimal sumByStatus(List<Invoice> invoices, InvoiceStatus status) {
        return invoices.stream()
                .filter(i -> i.getStatus() == status)
                .map(Invoice::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
