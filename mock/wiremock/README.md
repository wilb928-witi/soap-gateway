# Mock API para validar SOAP Gateway (DEV)

Este mock cubre variantes para probar el contrato de salida SOAP del gateway:

- `success` (`true` en 2xx, `false` en errores)
- `statusCode` (HTTP real del backend, o `0` en error interno de gateway)
- `data` (JSON parseado a XML o texto escapado según `Content-Type`)
- `dataRedeable` (`true` si JSON parseable, `false` en otros casos)

## Levantar stack de validación

```bash
docker compose -f docker-compose.mock.yml up -d --build
```

Servicios:
- SOAP Gateway: `http://localhost:8080`
- WireMock API: `http://localhost:18081`

## WSDL SOAP

- `http://localhost:8080/soap/clienteService?wsdl`
- `http://localhost:8080/soap/empleadoService?wsdl`

## Selección de variante desde SOAP

En el body SOAP (header de negocio dentro de la operación):

```xml
<header>
  <channel>MOBILE</channel>
  <variant>xml-ok</variant>
</header>
```

`variant` viaja al backend como header HTTP `X-Mock-Variant`.

## Variantes soportadas

### clienteService / getCliente
- default (sin variant): `200 application/json`
- `xml-ok`: `200 application/xml`
- `text-ok`: `200 text/plain`
- `binary-ok`: `200 application/octet-stream`
- `json-malformed`: `200 application/json` inválido
- `error-400-json`: `400 application/json`
- `error-500-text`: `500 text/plain`
- `error-500-html`: `500 text/html`
- `timeout`: delay 7s (timeout gateway)
- `retry-then-ok`: 500, 500, 200 (escenario)

### clienteService / crearCliente
- default: `201 application/json`
- `error-422-json`: `422 application/json`

### clienteService / actualizarCliente
- default: `200 application/json`
- `no-content`: `204` sin body

### empleadoService / getEmpleado
- default: `200 application/json`
- `error-404-text`: `404 text/plain`

### empleadoService / crearEmpleado
- default: `201 application/xml`
- `error-409-json`: `409 application/json`

## Ejemplo SOAP getCliente

```xml
<?xml version="1.0" encoding="UTF-8"?>
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cli="http://softslim.com/gateway/clienteService">
  <soapenv:Header/>
  <soapenv:Body>
    <cli:getCliente>
      <clienteId>12345</clienteId>
      <header>
        <channel>MOBILE</channel>
        <variant>xml-ok</variant>
      </header>
    </cli:getCliente>
  </soapenv:Body>
</soapenv:Envelope>
```
