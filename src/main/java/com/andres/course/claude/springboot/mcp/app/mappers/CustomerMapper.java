package com.andres.course.claude.springboot.mcp.app.mappers;

import com.andres.course.claude.springboot.mcp.app.dto.CustomerDto;
import com.andres.course.claude.springboot.mcp.app.models.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    /** entity -> dto */
    public CustomerDto toDto(Customer entity) {
        if (entity == null) {
            return null;
        }
        return new CustomerDto(
                entity.getId(),
                entity.getName(),
                entity.getLastName(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getAddress(),
                entity.getCity(),
                entity.getBirthDate(),
                entity.isActive()
        );
    }

    /** dto -> entity (nueva; el id lo genera la base de datos) */
    public Customer toEntity(CustomerDto dto) {
        if (dto == null) {
            return null;
        }
        Customer entity = new Customer();
        updateEntity(dto, entity);
        return entity;
    }

    /** Copia los campos editables del dto sobre una entidad existente. */
    public void updateEntity(CustomerDto dto, Customer entity) {
        entity.setName(dto.name());
        entity.setLastName(dto.lastName());
        entity.setEmail(dto.email());
        entity.setPhone(dto.phone());
        entity.setAddress(dto.address());
        entity.setCity(dto.city());
        entity.setBirthDate(dto.birthDate());
        if (dto.active() != null) {
            entity.setActive(dto.active());
        }
    }
}
