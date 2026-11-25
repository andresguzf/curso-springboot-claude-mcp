# AGENTS.md

Guía para agentes de IA (Claude Code, Codex y otros) que trabajen en este repositorio. Responde y escribe siempre en **español** (respuestas, commits y comentarios de código); solo los nombres técnicos van en inglés.

## Proyecto

**Servidor MCP de facturación** construido con Spring Boot y Spring AI. Permite administrar **clientes**, **facturas** y sus **líneas (items)** en lenguaje natural desde un cliente MCP (Claude Code, Codex, Claude Desktop…). Proyecto del curso de Spring Boot con Claude Code.

- Es **solo un servidor MCP**: no hay API REST, controllers, Swagger ni Actuator. El servidor web existe únicamente como transporte **Streamable HTTP** del endpoint `/mcp`. No agregues controllers REST salvo que el usuario lo pida explícitamente.
- Escucha en `http://127.0.0.1:8090/mcp` (solo localhost). **No tiene autenticación** y puede crear, modificar y borrar datos: no lo expongas fuera de localhost sin agregar antes autenticación.
- La base de datos es **PostgreSQL en Supabase**, compartida con el proyecto hermano `../3-spring-api` (API REST de clientes del curso).

## Stack

| Componente | Versión / detalle |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.3 (`spring-boot-starter-webmvc`, `-validation`, `-data-jpa`, `-devtools`) |
| Spring AI | 2.0.1, `spring-ai-starter-mcp-server-webmvc` (MCP Java SDK 2.0.0) |
| Transporte MCP | Streamable HTTP, servidor `SYNC`, endpoint `/mcp` |
| Persistencia | Spring Data JPA + Hibernate 7.4, `ddl-auto=validate` |
| Base de datos | PostgreSQL en Supabase (Session pooler), driver `org.postgresql` |
| Build | Maven con Maven Wrapper (`./mvnw`), packaging `jar` |
| Config | `application.properties` + `.env` (importado con `spring.config.import`) |

groupId `com.andres.course.claude.springboot`, artifactId `5-mcp`, package base `com.andres.course.claude.springboot.mcp.app`. Proyecto generado con Spring Initializr; Boot 4.1.3 en vez de 4.2.0 porque al generarlo springdoc aún no era compatible con 4.2 (springdoc se quitó después).

## Comandos

```bash
./mvnw -q -DskipTests compile             # compilar
./mvnw -DskipTests spring-boot:run        # desarrollo (devtools reinicia al recompilar)
./mvnw clean package -DskipTests          # generar target/5-mcp-0.0.1-SNAPSHOT.jar
java -jar target/5-mcp-0.0.1-SNAPSHOT.jar # ejecutar el JAR (desde la raíz del proyecto, para que encuentre .env)
```

- La app necesita la base de datos al arrancar (Hibernate valida el esquema). Sin `.env` válido no inicia.
- Al arrancar debe aparecer en el log `Registered tools: 15` y `Tomcat started on port 8090`.
- `src/test/.../ApplicationTests` levanta el contexto completo, así que también necesita la base de datos.

## Configuración

Variables de entorno en `.env` (no se versiona; plantilla en `.env.example`):

| Variable | Uso | Por defecto |
|---|---|---|
| `DB_URL` | JDBC de Supabase (Connect → JDBC → Session pooler) | `jdbc:postgresql://localhost:5432/mcp` |
| `DB_USERNAME` | Usuario `postgres.<ref-del-proyecto>` | `postgres` |
| `DB_PASSWORD` | Contraseña de la base de datos | `postgres` |
| `SERVER_PORT` | Puerto del servidor MCP | `8090` |
| `DB_POOL_SIZE` | Máximo de conexiones Hikari (el pooler de Supabase las limita) | `5` |

Otras propiedades relevantes: `billing.tax-rate=0.19` (IVA de Chile), `spring.ai.mcp.server.*` (nombre `facturacion`, protocolo `STREAMABLE`, instrucciones del servidor).

**Nunca** escribas credenciales, tokens ni datos personales reales en código, configuración, commits o respuestas: usa variables de entorno y placeholders genéricos.

## Base de datos

Tablas en el esquema `public` de Supabase. Las crea y modifica **Supabase con migraciones** (no Hibernate: `ddl-auto=validate`). Si cambias una entidad, primero aplica la migración (MCP de Supabase, `apply_migration`) y luego ajusta la entidad.

| Tabla | Entidad | Notas |
|---|---|---|
| `customers` | `Customer` | La creó `3-spring-api`. La columna de última edición se llama **`update_at`** (sin "d"); en la entidad es `updatedAt` con `@Column(name = "update_at")`. `email` único. |
| `invoices` | `Invoice` | `invoice_number` único, FK `customer_id → customers`, `status` con CHECK (`PENDING`, `PAID`, `CANCELLED`), montos `numeric(12,2)`, CHECK `due_date >= issue_date`. |
| `invoice_items` | `InvoiceItem` | FK `invoice_id → invoices` con `ON DELETE CASCADE`, `UNIQUE (invoice_id, line_number)`. `line_total` es columna **generada** (`quantity * unit_price`): en la entidad va con `insertable = false, updatable = false`. |
| `users`, `refresh_tokens` | — | Pertenecen a `3-spring-api` (seguridad JWT). **No las toques** desde este proyecto. |

- Migraciones aplicadas: `create_invoices_table`, `enable_rls_public_tables`, `create_invoice_items_table`.
- **RLS activado** en todas las tablas, sin políticas: la API pública de Supabase (anon/authenticated) no tiene acceso. La app se conecta como `postgres`, que tiene `BYPASSRLS`, así que no le afecta.

## Arquitectura

Sigue la skill `spring-boot-best-practices`, con una diferencia: la capa de entrada son **tools MCP** en lugar de controllers REST.

```
com.andres.course.claude.springboot.mcp.app/
├── Application.java
├── models/        # @Entity: Customer, Invoice, InvoiceItem + enum InvoiceStatus
├── dto/           # records: CustomerDto, InvoiceDto, InvoiceItemDto, CustomerBillingSummaryDto
├── mappers/       # @Component con mapeo explícito: CustomerMapper, InvoiceMapper
├── repositories/  # JpaRepository: CustomerRepository, InvoiceRepository
├── services/      # interfaz + *ServiceImpl (@Service, @Validated, @Transactional)
├── mcp/           # @Component con métodos @McpTool: CustomerTools, InvoiceTools
└── exceptions/    # ResourceNotFoundException, DuplicateResourceException, BusinessRuleException
```

- **Tools MCP** (`mcp/`): anotadas con `@McpTool` / `@McpToolParam` (`org.springframework.ai.mcp.annotation`); Spring AI las registra solas (annotation scanner). Solo traducen parámetros a DTOs y llaman al service; sin lógica de negocio ni acceso a repositorios. Llevan `annotations = @McpAnnotations(...)` con `readOnlyHint` / `destructiveHint` / `idempotentHint` correctos.
- **Errores**: no hay `@RestControllerAdvice`. Si el service lanza una excepción, Spring AI devuelve un resultado MCP con `isError: true` y el mensaje; por eso los mensajes de las excepciones deben ser claros y en español (los lee el modelo y se los explica al usuario).
- **Validación**: los services llevan `@Validated` y los parámetros `@Valid` en la **interfaz** del service, porque las tools no pasan por un controller. Los mensajes de Bean Validation van en español.
- **DTOs**: un `record` por recurso. Los campos que calcula el sistema (`id` de items, `lineNumber`, `lineTotal`, `customerName`, `subtotal`, `tax`, `total`) llevan `@JsonProperty(access = READ_ONLY)`: además de no aceptarse en la entrada, eso hace que el generador de esquemas JSON de Spring AI los marque como **opcionales** (sin anotación, todo componente del record sale como obligatorio en el `inputSchema` de la tool).
- **Actualizaciones parciales**: las tools `update_*` leen el estado actual y combinan solo los parámetros enviados (`null` = mantener; cadena vacía = borrar un campo opcional) antes de llamar a `update` del service.
- **Consultas con filtros opcionales**: no pases `null` a funciones de texto en JPQL (`lower(:param)`): PostgreSQL recibe el null sin tipo (`bytea`) y falla. Usa un valor centinela (p. ej. patrón `"%"`), como en `CustomerRepository.search`.
- **Reemplazo de líneas**: Hibernate ejecuta los `INSERT` antes que los `DELETE`; al reemplazar todos los items se hace `flush()` tras limpiar la colección para no chocar con `UNIQUE (invoice_id, line_number)`.

## Reglas de negocio

- Subtotal, IVA (`billing.tax-rate`, 19 %) y total se **calculan siempre** a partir de las líneas (redondeo `HALF_UP` a 2 decimales); nunca se aceptan desde la entrada.
- Toda factura tiene **al menos una línea**.
- Número de factura: si no se indica, se genera `FAC-{año de emisión}-{correlativo de 4 dígitos}`; debe ser único.
- `issueDate` por defecto es hoy; `dueDate` no puede ser anterior a `issueDate`.
- Estados: `PENDING → PAID` o `PENDING → CANCELLED`. **`PAID` y `CANCELLED` son finales**.
- Solo las facturas `PENDING` se pueden editar (cabecera, cliente o líneas).
- Una factura `PAID` no se puede borrar.
- No se pueden asignar facturas a un cliente **inactivo**.
- Un cliente con facturas no se puede borrar: se desactiva (`active = false`).
- El email del cliente es único (sin distinguir mayúsculas).

## Tools MCP del servidor `facturacion`

| Tool | Tipo | Descripción |
|---|---|---|
| `list_customers` | lectura | Lista clientes; búsqueda por texto (nombre, apellido, email o ciudad) y filtro por activos/inactivos. Sirve para encontrar el id de un cliente por su nombre. |
| `get_customer` | lectura | Datos de un cliente por id. |
| `create_customer` | escritura | Crea un cliente activo (`name`, `lastName`, `email` obligatorios; teléfono, dirección, ciudad y fecha de nacimiento opcionales). |
| `update_customer` | escritura | Cambio parcial de un cliente; `active=false/true` lo desactiva o reactiva. |
| `delete_customer` | destructiva | Borra un cliente sin facturas. Pide confirmación al usuario antes de usarla. |
| `list_invoices` | lectura | Lista facturas con sus líneas, de la más reciente a la más antigua; filtros por cliente y estado. |
| `get_invoice` | lectura | Factura con sus líneas, por id o por número (`FAC-2026-0001`). |
| `create_invoice` | escritura | Crea una factura `PENDING` para un cliente activo con una o más líneas (`description`, `quantity`, `unitPrice`). |
| `update_invoice` | escritura | Cambia la cabecera de una factura `PENDING`: reasignar cliente, fechas o notas. |
| `change_invoice_status` | escritura | Pasa una factura `PENDING` a `PAID` o `CANCELLED` (estados finales). Confirma con el usuario antes de marcar `PAID`. |
| `add_invoice_item` | escritura | Agrega una línea a una factura `PENDING` y recalcula totales. |
| `update_invoice_item` | escritura | Modifica una línea (por `lineNumber`) de una factura `PENDING`. |
| `remove_invoice_item` | destructiva | Quita una línea; la factura debe conservar al menos una. |
| `delete_invoice` | destructiva | Borra una factura no pagada y sus líneas. Pide confirmación al usuario antes de usarla. |
| `get_customer_billing_summary` | lectura | Resumen de un cliente: facturas por estado y montos pagados, pendientes y totales (las anuladas no suman). |

Al agregar o cambiar una tool: actualiza esta tabla y la del `README.md`, y verifica con un cliente MCP (`tools/list` y `tools/call`) que el `inputSchema` marque como obligatorios solo los parámetros que lo son.

## Servidores MCP disponibles en el proyecto

Definidos en `.mcp.json` (no se versiona porque contiene el `project_ref` de Supabase; plantilla en `.mcp.json.example`):

| Servidor | Transporte | Para qué usarlo |
|---|---|---|
| `facturacion` | HTTP `http://localhost:8090/mcp` | **Este proyecto.** Úsalo para operar clientes y facturas en lenguaje natural. Requiere la app corriendo. |
| `supabase` | HTTP (`mcp.supabase.com`, acotado a un `project_ref`) | Inspeccionar el esquema (`list_tables`), aplicar migraciones (`apply_migration`), consultas SQL puntuales (`execute_sql`) y revisar advisors de seguridad/rendimiento. Para datos de negocio prefiere las tools de `facturacion`, que aplican las reglas. |

Reglas de uso:

- Antes de cambios de esquema, inspecciona las tablas con el MCP de Supabase y aplica DDL solo con `apply_migration` (nombre en snake_case).
- No borres ni modifiques datos con SQL directo para saltarte una regla de negocio (p. ej. borrar una factura `PAID`) sin la confirmación explícita del usuario.
- Para GitHub prefiere la CLI `gh` (menos tokens); el MCP de GitHub, si está instalado, también sirve.

## Available skills

| Skill | Ubicación | Propósito |
|---|---|---|
| `spring-boot-best-practices` | `.claude/skills/spring-boot-best-practices/` | Crea proyectos Spring Boot con Spring Initializr (Java 25, Spring Boot 4.1.2+, Maven, jar, `application.properties`) y mantiene la arquitectura en capas models / dto / mappers / repositories / services (interface + impl), con DTOs `record`, Mapper explícito, excepciones de dominio y JPA + PostgreSQL bajo demanda. |

La skill es un **symlink** al original en `../2-spring-di/.claude/skills/spring-boot-best-practices/` (ruta relativa, igual que en `3-spring-api`). Editar sus archivos desde aquí modifica el original de `2-spring-di`. En un clon aislado de este repositorio el symlink queda roto: la skill vive en el repositorio del proyecto `2-spring-di`.

## Skill trigger rules

Usa `spring-boot-best-practices` cuando el usuario:

- Pida crear, agregar o modificar un entity, model, DTO, mapper, repository o service (p. ej. un recurso nuevo junto a `Customer` o `Invoice`).
- Pida agregar campos a una entidad o cambiar validaciones de un DTO.
- Pida cambiar la persistencia: Spring Data JPA, Hibernate, PostgreSQL, un `JpaRepository` o una entity `@Entity`.
- Pida crear un proyecto Spring Boot nuevo.

Reglas clave de la skill en este proyecto:

- El proyecto **ya existe** y **ya tiene** JPA + PostgreSQL: no vuelvas a ejecutar el script de Initializr; respeta el package base.
- **Excepción a la skill**: donde la skill crea un controller REST y un `@RestControllerAdvice`, aquí se crea una clase de **tools MCP** en `mcp/` (la app es solo MCP).
- Al agregar un recurso, crea el conjunto completo: entity, record DTO, mapper, repository, service + impl y tools MCP; y la migración en Supabase antes de la entidad (`ddl-auto=validate`).
- Al agregar un campo a una entity, actualiza en el mismo cambio el DTO, el mapper (`toDto`, `toEntity`, `updateEntity`), las tools afectadas y la migración.
- Inyección por constructor con campos `final`; `@Transactional(readOnly = true)` en lecturas y `@Transactional` en escrituras.

## Pendientes conocidos

- Las instrucciones del servidor (`spring.ai.mcp.server.instructions`) llegan al cliente con los acentos dañados: Spring lee `application.properties` como ISO-8859-1. Solución pendiente: escapar con `\uXXXX` o mover el texto a una configuración UTF-8.
- La búsqueda de `list_customers` distingue tildes ("Nicolás" ≠ "Nicolas"); se podría usar la extensión `unaccent` de PostgreSQL.
- Sin autenticación: necesaria antes de desplegarlo en remoto.

## Git

- Rama principal `main`, remoto `andresguzf/curso-springboot-claude-mcp` (público).
- No se versionan: `.env`, `.mcp.json`, `.claude/settings.local.json`, `target/`.
- Commits en español. Sin push ni cambios de visibilidad sin el OK del usuario.
