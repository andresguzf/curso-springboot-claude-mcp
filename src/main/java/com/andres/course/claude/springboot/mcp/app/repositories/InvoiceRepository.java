package com.andres.course.claude.springboot.mcp.app.repositories;

import com.andres.course.claude.springboot.mcp.app.models.Invoice;
import com.andres.course.claude.springboot.mcp.app.models.InvoiceStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // El EntityGraph trae cliente e items en la misma consulta (evita N+1).
    @EntityGraph(attributePaths = {"customer", "items"})
    Optional<Invoice> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"customer", "items"})
    Optional<Invoice> findByInvoiceNumberIgnoreCase(String invoiceNumber);

    @EntityGraph(attributePaths = {"customer", "items"})
    @Query("""
            select distinct i from Invoice i
            where (:customerId is null or i.customer.id = :customerId)
              and (:status is null or i.status = :status)
            order by i.issueDate desc, i.id desc
            """)
    List<Invoice> search(@Param("customerId") Long customerId, @Param("status") InvoiceStatus status);

    @EntityGraph(attributePaths = {"customer", "items"})
    List<Invoice> findByCustomerId(Long customerId);

    long countByCustomerId(Long customerId);

    boolean existsByInvoiceNumberIgnoreCase(String invoiceNumber);

    boolean existsByInvoiceNumberIgnoreCaseAndIdNot(String invoiceNumber, Long id);

    /** Último número con el prefijo dado (p. ej. "FAC-2026-"), para el correlativo. */
    Optional<Invoice> findTopByInvoiceNumberStartingWithOrderByInvoiceNumberDesc(String prefix);
}
