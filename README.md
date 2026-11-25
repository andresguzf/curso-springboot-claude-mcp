# Facturación MCP con Spring Boot

Servidor **MCP** (Model Context Protocol) de facturación construido con **Spring Boot 4** y **Spring AI**. Te permite administrar **clientes**, **facturas** y sus **líneas** conversando en lenguaje natural con un agente de IA como **Claude Code** o **Codex**:

> *"Crea una factura para Camila Rojas con 10 horas de consultoría a $5.000."*
> *"¿Qué facturas tiene pendientes Bruno González?"*
> *"Marca como pagada la FAC-2026-0002."*

El agente traduce lo que pides a llamadas a las **tools** del servidor, y el servidor aplica las reglas de negocio (cálculo de IVA, estados, validaciones) sobre una base de datos PostgreSQL en [Supabase](https://supabase.com).

Proyecto del curso de Spring Boot con Claude Code.

## ¿Qué es MCP?

MCP es un protocolo abierto que permite a un agente de IA usar herramientas externas. Esta aplicación es un **servidor MCP**: no tiene interfaz web ni API REST; expone 15 tools en el endpoint `http://localhost:8090/mcp` (transporte *Streamable HTTP*) y el agente (el **cliente MCP**) las descubre y las usa por ti.

## Stack

- Java 25
- Spring Boot 4.1.3 (Spring Web MVC, Validation, DevTools)
- Spring AI 2.0.1 — MCP Server (`spring-ai-starter-mcp-server-webmvc`)
- Spring Data JPA + Hibernate 7
- PostgreSQL en Supabase
- Maven (con Maven Wrapper)

## Requisitos

- JDK 25
- Una base de datos PostgreSQL (este proyecto usa Supabase) con las tablas `customers`, `invoices` e `invoice_items` (ver [Base de datos](#base-de-datos))
- Un cliente MCP: [Claude Code](https://claude.com/claude-code), Codex u otro compatible con Streamable HTTP

## Puesta en marcha

1. Clona el repositorio:

   ```bash
   git clone https://github.com/andresguzf/curso-springboot-claude-mcp.git
   cd curso-springboot-claude-mcp
   ```

2. Crea tu archivo de variables de entorno y complétalo con los datos de tu base de datos (en Supabase: **Connect → JDBC → Session pooler**):

   ```bash
   cp .env.example .env
   ```

   | Variable | Descripción | Por defecto |
   |---|---|---|
   | `DB_URL` | URL JDBC de PostgreSQL | `jdbc:postgresql://localhost:5432/mcp` |
   | `DB_USERNAME` | Usuario (en Supabase: `postgres.<ref-del-proyecto>`) | `postgres` |
   | `DB_PASSWORD` | Contraseña | `postgres` |
   | `SERVER_PORT` | Puerto del servidor MCP (opcional) | `8090` |
   | `DB_POOL_SIZE` | Conexiones máximas a la base de datos (opcional) | `5` |

   El archivo `.env` no se sube a git.

3. Genera el JAR y levanta el servidor (desde la raíz del proyecto, para que lea el `.env`):

   ```bash
   ./mvnw clean package -DskipTests
   java -jar target/5-mcp-0.0.1-SNAPSHOT.jar
   ```

   Cuando veas `Started Application` el servidor está listo en `http://localhost:8090/mcp`. Para desarrollo también puedes usar `./mvnw spring-boot:run`.

## Conectar el agente de IA

El servidor tiene que estar corriendo mientras lo uses.

### Claude Code

Opción 1, con el comando:

```bash
claude mcp add --transport http facturacion http://localhost:8090/mcp
```

Opción 2, por proyecto: copia la plantilla `.mcp.json.example` como `.mcp.json` (incluye también el MCP de Supabase; reemplaza `<ref-de-tu-proyecto>` o quita esa entrada). Al abrir Claude Code en la carpeta te pedirá aprobar el servidor.

Comprueba la conexión con `claude mcp list` (debe decir `facturacion … ✔ Connected`) o con `/mcp` dentro de una sesión.

### Codex

```bash
codex mcp add facturacion --url http://localhost:8090/mcp
```

## Tools disponibles

| Tool | Qué hace |
|---|---|
| `list_customers` | Lista clientes; busca por nombre, apellido, email o ciudad y filtra por activos/inactivos |
| `get_customer` | Muestra un cliente por su id |
| `create_customer` | Crea un cliente |
| `update_customer` | Modifica solo los datos indicados de un cliente; también lo desactiva o reactiva |
| `delete_customer` | Borra un cliente (solo si no tiene facturas) |
| `list_invoices` | Lista facturas con sus líneas; filtra por cliente y estado |
| `get_invoice` | Muestra una factura por id o por número |
| `create_invoice` | Crea una factura con una o más líneas |
| `update_invoice` | Reasigna la factura a otro cliente o cambia fechas y notas |
| `change_invoice_status` | Marca una factura como pagada o la anula |
| `add_invoice_item` | Agrega una línea a una factura |
| `update_invoice_item` | Modifica una línea |
| `remove_invoice_item` | Quita una línea |
| `delete_invoice` | Borra una factura (no pagada) |
| `get_customer_billing_summary` | Resumen de facturación de un cliente: facturas por estado y montos |

## Reglas de negocio

- El **subtotal**, el **IVA (19 %)** y el **total** los calcula el sistema a partir de las líneas.
- Cada factura tiene al menos una línea.
- El número se genera solo con el formato `FAC-2026-0001` si no indicas uno.
- Una factura nace **pendiente** (`PENDING`) y puede pasar a **pagada** (`PAID`) o **anulada** (`CANCELLED`); esos dos estados son finales.
- Solo las facturas pendientes se pueden editar, y las pagadas no se pueden borrar.
- No se pueden crear facturas para clientes desactivados.
- Un cliente con facturas no se borra: se desactiva.
- El email de cada cliente es único.

## Ejemplos de uso

Escríbele al agente con naturalidad:

- *"Lista los clientes desactivados."*
- *"Busca un cliente que se llame Nicolás."*
- *"Crea el cliente Ana Soto con email ana.soto@ejemplo.com, de Valparaíso."*
- *"Créale una factura con 2 monitores a $500 y un teclado a $180, que venza el 31 de diciembre."*
- *"Agrega a la FAC-2026-0004 una línea de 3 horas de soporte a $5.000."*
- *"Anula la factura FAC-2026-0002."*
- *"Dame el resumen de facturación de Bruno González."*

Los agentes piden confirmación antes de borrar datos o de marcar una factura como pagada.

## Base de datos

| Tabla | Contenido |
|---|---|
| `customers` | Clientes (compartida con el proyecto de la API REST del curso) |
| `invoices` | Facturas: número, cliente, fechas, montos, estado y notas |
| `invoice_items` | Líneas de cada factura: descripción, cantidad, precio unitario y total de la línea (calculado por PostgreSQL) |

El esquema lo administra la base de datos con migraciones; Hibernate solo **valida** que las entidades coincidan (`ddl-auto=validate`) y nunca modifica las tablas.

## Estructura del proyecto

```
src/main/java/com/andres/course/claude/springboot/mcp/app/
├── models/        # entidades JPA
├── dto/           # records de entrada y salida
├── mappers/       # conversión entidad <-> dto
├── repositories/  # Spring Data JPA
├── services/      # lógica y reglas de negocio
├── mcp/           # tools MCP (la "puerta de entrada" de la aplicación)
└── exceptions/    # errores de dominio
```

## Seguridad

El servidor **no tiene autenticación** y puede crear, modificar y borrar datos, por eso escucha solo en `127.0.0.1`. No lo expongas a internet sin agregar antes autenticación (por ejemplo, un token *Bearer*).

## Para agentes de IA

La guía técnica completa para agentes (arquitectura, convenciones, skills y servidores MCP del proyecto) está en [AGENTS.md](AGENTS.md).
