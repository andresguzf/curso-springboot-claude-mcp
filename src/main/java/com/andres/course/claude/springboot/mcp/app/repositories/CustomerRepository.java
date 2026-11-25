package com.andres.course.claude.springboot.mcp.app.repositories;

import com.andres.course.claude.springboot.mcp.app.models.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /**
     * Búsqueda libre por nombre, apellido, email o ciudad, con filtro opcional de
     * estado. Se usa @Query porque una consulta derivada con cuatro OR y un filtro
     * opcional sería ilegible.
     *
     * @param pattern patrón LIKE en minúsculas (p. ej. "%gonz%"); "%" = sin filtro.
     *                Nunca null: PostgreSQL recibe un null sin tipo como bytea y
     *                lower(bytea) falla.
     */
    @Query("""
            select c from Customer c
            where (:active is null or c.active = :active)
              and (lower(c.name) like :pattern
                   or lower(c.lastName) like :pattern
                   or lower(c.email) like :pattern
                   or lower(coalesce(c.city, '')) like :pattern)
            order by c.id
            """)
    List<Customer> search(@Param("pattern") String pattern, @Param("active") Boolean active);
}
