package com.andres.course.claude.springboot.mcp.app.mcp;

import com.andres.course.claude.springboot.mcp.app.dto.CustomerDto;
import com.andres.course.claude.springboot.mcp.app.services.CustomerService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Tools MCP para administrar clientes. Son la capa de entrada de la aplicación
 * (en lugar de controllers REST): solo traducen la llamada al service, y la
 * lógica y las validaciones viven en CustomerService.
 * Si el service lanza una excepción, Spring AI devuelve un resultado de error con
 * su mensaje, que el modelo puede leer y explicar al usuario.
 */
@Component
public class CustomerTools {

    private final CustomerService customerService;

    public CustomerTools(CustomerService customerService) {
        this.customerService = customerService;
    }

    @McpTool(name = "list_customers",
            description = "Lista los clientes. Permite buscar por texto (nombre, apellido, email o ciudad) "
                    + "y filtrar por activos o inactivos. Úsala también para encontrar el id de un cliente por su nombre.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public List<CustomerDto> listCustomers(
            @McpToolParam(description = "Texto a buscar en nombre, apellido, email o ciudad", required = false) String search,
            @McpToolParam(description = "true = solo activos, false = solo inactivos, vacío = todos", required = false) Boolean active) {
        return customerService.findAll(search, active);
    }

    @McpTool(name = "get_customer",
            description = "Obtiene los datos de un cliente por su id.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CustomerDto getCustomer(
            @McpToolParam(description = "Id del cliente") Long customerId) {
        return customerService.findById(customerId);
    }

    @McpTool(name = "create_customer",
            description = "Crea un cliente nuevo. El email debe ser único. El cliente queda activo.",
            annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public CustomerDto createCustomer(
            @McpToolParam(description = "Nombre") String name,
            @McpToolParam(description = "Apellido") String lastName,
            @McpToolParam(description = "Email (único)") String email,
            @McpToolParam(description = "Teléfono", required = false) String phone,
            @McpToolParam(description = "Dirección", required = false) String address,
            @McpToolParam(description = "Ciudad", required = false) String city,
            @McpToolParam(description = "Fecha de nacimiento en formato yyyy-MM-dd", required = false) LocalDate birthDate) {
        return customerService.create(new CustomerDto(null, name, lastName, email,
                blankToNull(phone), blankToNull(address), blankToNull(city), birthDate, true));
    }

    @McpTool(name = "update_customer",
            description = "Modifica un cliente. Solo cambia los campos enviados; los omitidos se mantienen. "
                    + "Para borrar un campo opcional (teléfono, dirección, ciudad) envíalo como cadena vacía. "
                    + "Usa active=false para desactivar un cliente y active=true para reactivarlo.",
            annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CustomerDto updateCustomer(
            @McpToolParam(description = "Id del cliente a modificar") Long customerId,
            @McpToolParam(description = "Nuevo nombre", required = false) String name,
            @McpToolParam(description = "Nuevo apellido", required = false) String lastName,
            @McpToolParam(description = "Nuevo email (único)", required = false) String email,
            @McpToolParam(description = "Nuevo teléfono", required = false) String phone,
            @McpToolParam(description = "Nueva dirección", required = false) String address,
            @McpToolParam(description = "Nueva ciudad", required = false) String city,
            @McpToolParam(description = "Nueva fecha de nacimiento (yyyy-MM-dd)", required = false) LocalDate birthDate,
            @McpToolParam(description = "true = activo, false = inactivo", required = false) Boolean active) {
        CustomerDto current = customerService.findById(customerId);
        CustomerDto merged = new CustomerDto(
                customerId,
                name != null ? name : current.name(),
                lastName != null ? lastName : current.lastName(),
                email != null ? email : current.email(),
                mergeOptional(phone, current.phone()),
                mergeOptional(address, current.address()),
                mergeOptional(city, current.city()),
                birthDate != null ? birthDate : current.birthDate(),
                active != null ? active : current.active()
        );
        return customerService.update(customerId, merged);
    }

    @McpTool(name = "delete_customer",
            description = "Borra un cliente de forma permanente. Solo funciona si no tiene facturas; "
                    + "si las tiene, desactívalo con update_customer (active=false). Confirma con el usuario antes de usarla.",
            annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false))
    public String deleteCustomer(
            @McpToolParam(description = "Id del cliente a borrar") Long customerId) {
        customerService.delete(customerId);
        return "Cliente " + customerId + " borrado";
    }

    /** null = mantener el valor actual; cadena vacía = borrarlo. */
    private static String mergeOptional(String value, String current) {
        if (value == null) {
            return current;
        }
        return blankToNull(value);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
