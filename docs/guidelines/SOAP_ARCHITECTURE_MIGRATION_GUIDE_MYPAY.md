# MyPay4 — Guía Completa de Arquitectura SOAP para Migración

> Documento generado como referencia completa para migrar/adaptar la capa SOAP del proyecto MyPay4 v4.9.0 a otro proyecto. Contiene la estructura, patrones, endpoints, clientes, configuración e infraestructura transversal.

---

## 1. Estructura de Carpetas

```
ws/
├── iface/                → Interfaces Java (contratos de operaciones SOAP)
│   ├── PagamentiTelematiciCCPPa.java
│   ├── PagamentiTelematiciDovutiPagati.java
│   ├── PagamentiTelematiciEsito.java
│   ├── PagamentiTelematiciFlussiSPC.java
│   └── fesp/             → Interfaces del módulo FESP
│       ├── PagamentiTelematiciAvvisiDigitali.java
│       ├── PagamentiTelematiciCCP.java
│       ├── PagamentiTelematiciCCP25.java
│       ├── PagamentiTelematiciRP.java
│       └── PagamentiTelematiciRT.java
├── impl/                 → Implementaciones de negocio (@Service)
│   ├── PagamentiTelematiciCCPPaImpl.java          (2456 líneas)
│   ├── PagamentiTelematiciDovutiPagatiImpl.java   (2308 líneas)
│   ├── PagamentiTelematiciEsitoImpl.java          (148 líneas)
│   ├── PagamentiTelematiciFlussiSPCImpl.java      (131 líneas)
│   └── fesp/             → Implementaciones FESP
│       ├── PagamentiTelematiciCCPImpl.java         (664 líneas)
│       ├── PagamentiTelematiciCCP25Impl.java       (632 líneas)
│       ├── PagamentiTelematiciRTImpl.java          (490 líneas)
│       ├── PagamentiTelematiciRPImpl.java          (327 líneas)
│       ├── PagamentiTelematiciAvvisiDigitaliImpl.java (111 líneas)
│       └── mock/         → Stubs para fesp.mode=none
│           ├── PagamentiTelematiciCCPMock.java
│           ├── PagamentiTelematiciRTMock.java
│           ├── PagamentiTelematiciRPMock.java
│           └── PagamentiTelematiciAvvisiDigitaliMock.java
├── server/               → Endpoints Spring-WS (@Endpoint — reciben llamadas SOAP)
│   ├── BaseEndpoint.java
│   ├── PagamentiTelematiciCCPPaEndpoint.java
│   ├── PagamentiTelematiciDovutiPagatiEndpoint.java
│   ├── PagamentiTelematiciEsitoEndpoint.java
│   ├── PagamentiTelematiciFlussiSPCEndpoint.java
│   └── fesp/
│       ├── PagamentiTelematiciCCPEndpoint.java
│       ├── PagamentiTelematiciCCP25Endpoint.java
│       ├── PagamentiTelematiciRTEndpoint.java
│       ├── PagamentiTelematiciRPEndpoint.java
│       └── PagamentiTelematiciAvvisiDigitaliEndpoint.java
├── client/               → Clientes SOAP (invocan servicios externos)
│   ├── BaseClient.java
│   ├── PagamentiTelematiciCCPPaClient.java
│   ├── PagamentiTelematiciEsitoClient.java
│   ├── PagamentiTelematiciEsterniCCPClient.java
│   └── fesp/
│       ├── PagamentiTelematiciRPClient.java
│       ├── PagamentiTelematiciRPTClient.java
│       ├── PagamentiTelematiciAvvisiDigitaliClient.java
│       ├── PagamentiTelematiciAvvisiDigitaliServiceClient.java
│       └── mock/
│           └── PagamentiTelematiciRPTMockClient.java
├── helper/               → Utilidades de respuesta y validación
│   ├── OutcomeHelper.java
│   └── PagamentiTelematiciDovutiPagatiHelper.java
└── util/                 → Constantes, interceptores, generación WSDL
    ├── EnumUtils.java
    ├── FaultCodeConstants.java
    ├── FaultCodeChiediStatoRPT.java
    ├── FaultCodeInvioRPT.java
    ├── ManageWsFault.java
    ├── MyEndpointInterceptor.java
    ├── MySuffixBasedMessagesProvider.java
    ├── MySuffixBasedPortTypesProvider.java
    ├── MyWsdl11Definition.java
    ├── PagoPAAuthClientInterceptor.java
    ├── StatiRPT.java
    └── SumUtilis.java

config/
├── SoapWebServiceConfig.java        → Configuración server-side
└── SoapWebServiceClientConfig.java  → Configuración client-side
```

---

## 2. Modos de Operación — Propiedad `fesp.mode`

El proyecto opera en tres modos mutuamente excluyentes controlados por la propiedad `fesp.mode`:

| `fesp.mode` | Endpoints activos | Descripción |
|---|---|---|
| `local` | Endpoints FESP (`server/fesp/*`) + siempre-activos | Modo integrado: PA y FESP en el mismo JVM |
| `remote` | Endpoints PA (`CCPPa`, `Esito`) + siempre-activos | Modo separado: PA llama a FESP por SOAP remoto |
| `none` | Solo siempre-activos + mocks | FESP deshabilitado (mocks lanzan `UnsupportedOperationException`) |

**Siempre activos** (sin condición `fesp.mode`): `PagamentiTelematiciDovutiPagatiEndpoint` y `PagamentiTelematiciFlussiSPCEndpoint`.

### Matriz de activación completa

| Endpoint | `fesp.mode=local` | `fesp.mode=remote` | Siempre activo |
|---|---|---|---|
| PagamentiTelematiciDovutiPagatiEndpoint | — | — | SI |
| PagamentiTelematiciFlussiSPCEndpoint | — | — | SI |
| PagamentiTelematiciCCPPaEndpoint | — | SI | — |
| PagamentiTelematiciEsitoEndpoint | — | SI | — |
| fesp/PagamentiTelematiciCCPEndpoint | SI | — | — |
| fesp/PagamentiTelematiciCCP25Endpoint | SI | — | — |
| fesp/PagamentiTelematiciRTEndpoint | SI | — | — |
| fesp/PagamentiTelematiciRPEndpoint | SI | — | — |
| fesp/PagamentiTelematiciAvvisiDigitaliEndpoint | SI | — | — |

---

## 3. Los 5 Namespaces SOAP y los 4 Tipos de Header

### Namespaces

| # | Namespace URI | Endpoints que lo usan |
|---|---|---|
| 1 | `http://www.regione.veneto.it/pagamenti/ente/` | DovutiPagati |
| 2 | `http://www.regione.veneto.it/pagamenti/pa/` | CCPPa, Esito, FlussiSPC |
| 3 | `http://ws.pagamenti.telematici.gov/` | fesp/CCP, fesp/RT |
| 4 | `http://www.regione.veneto.it/pagamenti/nodoregionalefesp/` | fesp/RP, fesp/AvvisiDigitali |
| 5 | `http://pagopa-api.pagopa.gov.it/pa/paForNode.xsd` | fesp/CCP25 |

### Variantes de `IntestazionePPT` (SOAP Header)

Existen **4 clases distintas** con el mismo nombre `IntestazionePPT` en paquetes diferentes:

| # | Clase FQN | Usada por |
|---|---|---|
| 1 | `it.veneto.regione.pagamenti.pa.ppthead.IntestazionePPT` | CCPPa, Esito |
| 2 | `it.veneto.regione.pagamenti.ente.ppthead.IntestazionePPT` | DovutiPagati |
| 3 | `gov.telematici.pagamenti.ws.ppthead.IntestazionePPT` | fesp/CCP, fesp/RT |
| 4 | `it.veneto.regione.pagamenti.nodoregionalefesp.ppthead.IntestazionePPT` | fesp/RP |

Adicionalmente existe `gov.telematici.pagamenti.ws.sachead.IntestazionePPT` para avisos digitales y `IntestazioneCarrelloPPT` para operaciones de carrito.

---

## 4. Patrón Arquitectónico — Cómo Se Construye un Servicio SOAP

Cada servicio SOAP sigue un patrón de **4 capas**:

### Capa 1: Interfaz (`iface/`)

Define el contrato con los tipos JAXB generados:

```java
package it.regioneveneto.mygov.payment.mypay4.ws.iface;

import it.veneto.regione.pagamenti.pa.*;
import it.veneto.regione.pagamenti.pa.ppthead.IntestazionePPT;

public interface PagamentiTelematiciEsito {
    PaaSILInviaEsitoRisposta paaSILInviaEsito(
        PaaSILInviaEsito requestBody,
        IntestazionePPT header);
}
```

### Capa 2: Implementación (`impl/`)

Contiene la lógica de negocio, anotada como `@Service` con un qualifier:

```java
@Service("PagamentiTelematiciEsitoImpl")
@Transactional(propagation = Propagation.SUPPORTS)
public class PagamentiTelematiciEsitoImpl implements PagamentiTelematiciEsito {

    @Autowired
    private EnteService enteService;
    // ... otros servicios inyectados

    @Override
    public PaaSILInviaEsitoRisposta paaSILInviaEsito(
            PaaSILInviaEsito request, IntestazionePPT header) {
        // Lógica de negocio real
    }
}
```

### Capa 3: Endpoint (`server/`)

Recibe la llamada SOAP, deserializa el header, envuelve en auditoría y delega a la implementación:

```java
@Endpoint
@ConditionalOnProperty(prefix = "fesp", name = "mode", havingValue = "remote")
@ConditionalOnWebApplication
public class PagamentiTelematiciEsitoEndpoint extends BaseEndpoint {

    public static final String NAMESPACE_URI = "http://www.regione.veneto.it/pagamenti/pa/";
    public static final String NAME = "PagamentiTelematiciEsito";

    @Autowired
    @Qualifier("PagamentiTelematiciEsitoImpl")
    private PagamentiTelematiciEsitoImpl pagamentiTelematiciEsito;

    @Autowired
    private GiornaleService giornaleCommonService;

    @LogExecution
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILInviaEsito")
    @ResponsePayload
    public PaaSILInviaEsitoRisposta paaSILInviaEsito(
            @RequestPayload PaaSILInviaEsito request,
            @SoapHeader("{http://www.regione.veneto.it/pagamenti/pa/ppthead}intestazionePPT")
            SoapHeaderElement header) {

        IntestazionePPT intestazionePPT = unmarshallHeader(header, IntestazionePPT.class);

        return giornaleCommonService.wrapRecordSoapServerEvent(
            Constants.GIORNALE_MODULO.PA,
            intestazionePPT.getIdentificativoDominio(),
            intestazionePPT.getIdentificativoUnivocoVersamento(),
            intestazionePPT.getCodiceContestoPagamento(),
            null,                                              // identificativoPSP
            null,                                              // tipoVersamento
            Constants.COMPONENTE_PA,
            Constants.GIORNALE_CATEGORIA_EVENTO.INTERNO.toString(),
            Constants.GIORNALE_TIPO_EVENTO_FESP.paaSILAttivaRP.toString(),
            intestazionePPT.getIdentificativoIntermediarioPA(),
            intestazionePPT.getIdentificativoDominio(),
            intestazionePPT.getIdentificativoStazioneIntermediarioPA(),
            null,                                              // canalePagamento
            () -> pagamentiTelematiciEsito.paaSILInviaEsito(request, intestazionePPT),
            OutcomeHelper::getOutcome
        );
    }
}
```

### Capa 4: Configuración (`SoapWebServiceConfig.java`)

Registra el WSDL, el XSD schema y el servlet dispatcher:

```java
@Configuration
@EnableWs
public class SoapWebServiceConfig extends WsConfigurerAdapter {

    // Registra el MessageDispatcherServlet en /ws/pa/ y /ws/fesp/
    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(...) {
        // ...
    }

    // Registra el WSDL definition bean
    @Bean(name = PagamentiTelematiciEsitoEndpoint.NAME)
    @ConditionalOnProperty(prefix = "fesp", name = "mode", havingValue = "remote")
    public MyWsdl11Definition pagamentiTelematiciEsitoWsdl11Definition() {
        MyWsdl11Definition wsdl = new MyWsdl11Definition();
        wsdl.setPortTypeName(PagamentiTelematiciEsitoEndpoint.NAME);
        wsdl.setTargetNamespace(PagamentiTelematiciEsitoEndpoint.NAMESPACE_URI);
        wsdl.setLocationUri("/ws/pa");
        wsdl.setSchema(pagInfRpEsito620Xsd());       // XSD schema bean
        wsdl.setRequestSuffix("");                     // Sin sufijo para requests
        wsdl.setResponseSuffix("Risposta");            // Sufijo para responses
        return wsdl;
    }
}
```

### `BaseEndpoint` — Clase base de todos los endpoints

```java
@Slf4j
public abstract class BaseEndpoint {
    protected <T> T unmarshallHeader(SoapHeaderElement header, Class<T> type) {
        if (header == null) return null;
        try {
            JAXBContext context = JAXBContext.newInstance(type);
            return (T) context.createUnmarshaller().unmarshal(header.getSource());
        } catch (Exception e) {
            throw new RuntimeException("error unmarshalling header", e);
        }
    }
}
```

---

## 5. Patrón de Clientes SOAP — Cómo Se Llaman Servicios Externos

### Jerarquía de clientes

```
WebServiceGatewaySupport (Spring-WS)
└── BaseClient (abstract)
    ├── PagamentiTelematiciCCPPaClient       (5 operaciones)
    ├── PagamentiTelematiciEsitoClient       (1 operación)
    ├── PagamentiTelematiciEsterniCCPClient   (4 operaciones)
    ├── fesp/PagamentiTelematiciRPClient      (9 operaciones, implementa interfaz RP)
    ├── fesp/PagamentiTelematiciRPTClient     (7 ops activas → Nodo PagoPA nacional)
    │   └── mock/PagamentiTelematiciRPTMockClient
    ├── fesp/PagamentiTelematiciAvvisiDigitaliClient       (1 operación)
    └── fesp/PagamentiTelematiciAvvisiDigitaliServiceClient (1 operación)
```

### `BaseClient` — Clase base de todos los clientes

Extiende `WebServiceGatewaySupport` de Spring-WS y provee:

1. **Fix del SoapAction header**: Extrae dinámicamente el nombre del método desde la stack trace y lo setea como SOAPAction
2. **Marshalling dinámico del header SOAP**: Usa reflexión para encontrar la `ObjectFactory` JAXB del paquete del header, instanciarla, copiar propiedades y marshalizar al SOAP header

```java
public abstract class BaseClient extends WebServiceGatewaySupport {

    protected WebServiceMessageCallback getMessageCallback(Object header) {
        return message -> {
            // 1. Fix SoapAction
            Arrays.stream(Thread.currentThread().getStackTrace())
                .filter(/* encuentra el método del subclase */)
                .findFirst()
                .ifPresent(ste -> {
                    String methodName = /* extrae nombre limpio */;
                    ((SoapMessage)message).setSoapAction(methodName);
                });

            // 2. Marshal SOAP Header
            if (header == null) return;
            SoapHeader soapHeader = ((SoapMessage) message).getSoapHeader();
            Class objFactoryClass = Class.forName(
                header.getClass().getPackageName() + ".ObjectFactory");
            Method createMethod = objFactoryClass.getMethod(
                "create" + StringUtils.capitalize(header.getClass().getSimpleName()));
            Object headerObj = createMethod.invoke(
                objFactoryClass.getConstructor().newInstance());
            BeanUtils.copyProperties(headerObj, header);
            JAXBContext context = JAXBContext.newInstance(header.getClass());
            Marshaller marshaller = context.createMarshaller();
            marshaller.marshal(headerObj, soapHeader.getResult());
        };
    }
}
```

### Patrón de invocación de un cliente

```java
public class PagamentiTelematiciEsitoClient extends BaseClient {

    public PaaSILInviaEsitoRisposta paaSILInviaEsito(
            PaaSILInviaEsito request, IntestazionePPT header, String wsUrl) {

        systemBlockService.blockByOperationName("pa.client.paaSILInviaEsito");

        return giornaleCommonService.wrapRecordSoapClientEvent(
            Constants.GIORNALE_MODULO.FESP,
            header.getIdentificativoDominio(),
            header.getIdentificativoUnivocoVersamento(),
            header.getCodiceContestoPagamento(),
            Constants.EMPTY,
            Constants.EMPTY,
            Constants.COMPONENTE_FESP,
            Constants.GIORNALE_CATEGORIA_EVENTO.INTERNO.toString(),
            Constants.GIORNALE_TIPO_EVENTO_FESP.paaSILInviaEsito.toString(),
            header.getIdentificativoIntermediarioPA(),
            header.getIdentificativoDominio(),
            header.getIdentificativoStazioneIntermediarioPA(),
            null,
            () -> (PaaSILInviaEsitoRisposta) getWebServiceTemplate()
                .marshalSendAndReceive(wsUrl, request, getMessageCallback(header)),
            OutcomeHelper::getOutcome
        );
    }
}
```

### Dos estrategias de URL en clientes

| Tipo | Patrón | Clientes |
|---|---|---|
| **URL explícita** | `marshalSendAndReceive(wsUrl, request, callback)` | CCPPaClient, EsitoClient, EsterniCCPClient |
| **URL por defecto** | `marshalSendAndReceive(request, callback)` | RPClient, RPTClient, AvvisiDigitaliClient |

### Configuración de clientes (`SoapWebServiceClientConfig.java`)

```java
@Configuration
public class SoapWebServiceClientConfig {

    @Bean
    @ConditionalOnProperty(prefix = "fesp", name = "mode", havingValue = "local")
    public PagamentiTelematiciCCPPaClient pagamentiTelematiciCCPPaClient() {
        var client = new PagamentiTelematiciCCPPaClient(giornaleService, idIntPA, idStazPA);
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("it.veneto.regione.pagamenti.pa");
        client.setMarshaller(marshaller);
        client.setUnmarshaller(marshaller);
        client.setInterceptors(new ClientInterceptor[]{ myEndpointInterceptor });
        return client;
    }
}
```

---

## 6. Patrón Local/Remoto — Decisión por Ente

Las implementaciones FESP (`CCP`, `CCP25`, `RT`) verifican si el ente tiene configurada una URL externa:

```java
// Dentro de PagamentiTelematiciCCPImpl.paaVerificaRPT():
Ente ente = enteService.getEnteByCodFiscale(header.getIdentificativoDominio());

if (StringUtils.isNotBlank(ente.getDeUrlEsterniAttiva())) {
    // REMOTO: llama via SOAP client al PA endpoint externo
    response = pagamentiTelematiciCCPPaClient
        .paaSILVerificaRP(request, header, ente.getDeUrlEsterniAttiva());
} else {
    // LOCAL: llama directamente al Spring bean en el mismo JVM
    response = pagamentiTelematiciCCPPa
        .paaSILVerificaRP(request, header);
}
```

Esto es configurable **por ente** (entidad/organización).

---

## 7. Catálogo Completo de Endpoints Servidor (40 operaciones)

### 7.1 PagamentiTelematiciDovutiPagatiEndpoint — 16 operaciones

- **Namespace:** `http://www.regione.veneto.it/pagamenti/ente/`
- **Condición:** Siempre activo
- **Impl:** `PagamentiTelematiciDovutiPagatiImpl` — qualifier: `"PagamentiTelematiciDovutiPagatiImpl"`
- **Header SOAP:** `{http://www.regione.veneto.it/pagamenti/ente/ppthead}intestazionePPT`
- **Nota:** Usa `SystemBlockService` en cada operación. Algunas operaciones usan `Holder<>` (patrón JAX-WS para respuestas multi-valor).

| # | `localPart` (operación SOAP) | Return Type | Header? | SystemBlock |
|---|---|---|---|---|
| 1 | `paaSILImportaDovuto` | `PaaSILImportaDovutoRisposta` | Sí | `pa.paaSILImportaDovuto` |
| 2 | `paaSILAutorizzaImportFlusso` | `PaaSILAutorizzaImportFlussoRisposta` | Sí | `pa.paaSILAutorizzaImportFlusso` |
| 3 | `paaSILChiediEsitoCarrelloDovuti` | `PaaSILChiediEsitoCarrelloDovutiRisposta` | No | `pa.paaSILChiediEsitoCarrelloDovuti` |
| 4 | `paaSILChiediPagati` | `PaaSILChiediPagatiRisposta` | No | `pa.paaSILChiediPagati` |
| 5 | `paaSILChiediPagatiConRicevuta` | `PaaSILChiediPagatiConRicevutaRisposta` | No | `pa.paaSILChiediPagatiConRicevuta` |
| 6 | `paaSILChiediPosizioniAperte` | `PaaSILChiediPosizioniAperteRisposta` | No | `pa.paaSILChiediPosizioniAperte` |
| 7 | `paaSILChiediStatoExportFlusso` | `PaaSILChiediStatoExportFlussoRisposta` | Sí | `pa.paaSILChiediStatoExportFlusso` |
| 8 | `paaSILChiediStatoImportFlusso` | `PaaSILChiediStatoImportFlussoRisposta` | Sí | `pa.paaSILChiediStatoImportFlusso` |
| 9 | `paaSILChiediStoricoPagamenti` | `PaaSILChiediStoricoPagamentiRisposta` | No | `pa.paaSILChiediStoricoPagamenti` |
| 10 | `paaSILInviaDovuti` | `PaaSILInviaDovutiRisposta` | Sí | `pa.paaSILInviaDovuti` |
| 11 | `paaSILInviaCarrelloDovuti` | `PaaSILInviaCarrelloDovutiRisposta` | Sí | `pa.paaSILInviaCarrelloDovuti` |
| 12 | `paaSILPrenotaExportFlusso` | `PaaSILPrenotaExportFlussoRisposta` | Sí | `pa.paaSILPrenotaExportFlusso` |
| 13 | `paaSILPrenotaExportFlussoIncrementaleConRicevuta` | `PaaSILPrenotaExportFlussoIncrementaleConRicevutaRisposta` | Sí | `pa.paaSILPrenotaExportFlussoIncrementaleConRicevuta` |
| 14 | `paaSILRegistraPagamento` | `PaaSILRegistraPagamentoRisposta` | No | `pa.paaSILRegistraPagamento` |
| 15 | `paaSILVerificaAvviso` | `PaaSILVerificaAvvisoRisposta` | Sí | `pa.paaSILVerificaAvviso` |
| 16 | `paaSILRecuperaAvviso` | `PaaSILRecuperaAvvisoRisposta` | Sí | `pa.paaSILRecuperaAvviso` |

### 7.2 PagamentiTelematiciCCPPaEndpoint — 4 operaciones

- **Namespace:** `http://www.regione.veneto.it/pagamenti/pa/`
- **Condición:** `fesp.mode=remote`
- **Impl:** `PagamentiTelematiciCCPPaImpl` — qualifier: `"PagamentiTelematiciCCPPaImpl"`
- **Header SOAP:** `{http://www.regione.veneto.it/pagamenti/pa/ppthead}intestazionePPT`

| # | `localPart` | Return Type | Header? | Protocolo |
|---|---|---|---|---|
| 1 | `paaSILAttivaRP` | `PaaSILAttivaRPRisposta` | Sí | Legacy SIL |
| 2 | `paaSILVerificaRP` | `PaaSILVerificaRPRisposta` | Sí | Legacy SIL |
| 3 | `paVerifyPaymentNotice` | `PaVerifyPaymentNoticeRisposta` | No | SANP 2.4 |
| 4 | `paGetPayment` | `PaGetPaymentRisposta` | No | SANP 2.4 |

### 7.3 PagamentiTelematiciEsitoEndpoint — 1 operación

- **Namespace:** `http://www.regione.veneto.it/pagamenti/pa/`
- **Condición:** `fesp.mode=remote`
- **Impl:** `PagamentiTelematiciEsitoImpl` — qualifier: `"PagamentiTelematiciEsitoImpl"`

| # | `localPart` | Return Type | Header? |
|---|---|---|---|
| 1 | `paaSILInviaEsito` | `PaaSILInviaEsitoRisposta` | Sí |

### 7.4 PagamentiTelematiciFlussiSPCEndpoint — 2 operaciones

- **Namespace:** `http://www.regione.veneto.it/pagamenti/pa/`
- **Condición:** Siempre activo
- **Impl:** `PagamentiTelematiciFlussiSPCImpl` — qualifier: `"PagamentiTelematiciFlussiSPCImpl"`

| # | `localPart` | Return Type | Header? |
|---|---|---|---|
| 1 | `paaSILChiediFlussoSPC` | `PaaSILChiediFlussoSPCRisposta` | No |
| 2 | `paaSILChiediElencoFlussiSPC` | `PaaSILChiediElencoFlussiSPCRisposta` | No |

### 7.5 fesp/PagamentiTelematiciCCPEndpoint — 2 operaciones

- **Namespace:** `http://ws.pagamenti.telematici.gov/`
- **Condición:** `fesp.mode=local`
- **Impl:** `PagamentiTelematiciCCPImpl` — qualifier: `"PagamentiTelematiciCCPFespImpl"`
- **Header SOAP:** `{http://ws.pagamenti.telematici.gov/ppthead}intestazionePPT`
- **Nota:** Trackea elapsed time con `GiornaleElapsedService`.

| # | `localPart` | Return Type | Header? | Elapsed |
|---|---|---|---|---|
| 1 | `paaVerificaRPT` | `PaaVerificaRPTRisposta` | Sí | `paaVerificaRPT` |
| 2 | `paaAttivaRPT` | `PaaAttivaRPTRisposta` | Sí | `paaAttivaRPT` |

### 7.6 fesp/PagamentiTelematiciCCP25Endpoint — 5 operaciones

- **Namespace:** `http://pagopa-api.pagopa.gov.it/pa/paForNode.xsd`
- **Condición:** `fesp.mode=local`
- **Impl:** `PagamentiTelematiciCCP25Impl` — qualifier: `"PagamentiTelematiciCCP25FespImpl"`
- **Nota:** Sin SOAP headers. Usa `@Transactional(transactionManager = "tmFesp")` en algunas operaciones.

| # | `localPart` | Return Type | Transactional? | Elapsed |
|---|---|---|---|---|
| 1 | `paVerifyPaymentNoticeReq` | `PaVerifyPaymentNoticeRes` | Sí (`tmFesp`, REQUIRED) | `paVerifyPaymentNotic` |
| 2 | `paGetPaymentReq` | `PaGetPaymentRes` | Sí (`tmFesp`, REQUIRED) | `paGetPayment` |
| 3 | `paSendRTReq` | `PaSendRTRes` | No | `paSendRT` |
| 4 | `paSendRTV2Request` | `PaSendRTV2Response` | No | `paSendRTV2` |
| 5 | `paGetPaymentV2Request` | `PaGetPaymentV2Response` | Sí (`tmFesp`, REQUIRED) | `paGetPaymentV2` |

### 7.7 fesp/PagamentiTelematiciRTEndpoint — 1 operación

- **Namespace:** `http://ws.pagamenti.telematici.gov/`
- **Condición:** `fesp.mode=local`
- **Impl:** `PagamentiTelematiciRTImpl` — qualifier: `"PagamentiTelematiciRTFespImpl"` (inyectado sin `@Qualifier`)

| # | `localPart` | Return Type | Header? | Elapsed |
|---|---|---|---|---|
| 1 | `paaInviaRT` | `PaaInviaRTRisposta` | Sí | `paaInviaRT` |

### 7.8 fesp/PagamentiTelematiciRPEndpoint — 8 operaciones

- **Namespace:** `http://www.regione.veneto.it/pagamenti/nodoregionalefesp/`
- **Condición:** `fesp.mode=local`
- **Impl:** `PagamentiTelematiciRPImpl` — qualifier: `"PagamentiTelematiciRPImpl"`
- **Header SOAP:** `{http://www.regione.veneto.it/pagamenti/nodoregionalefesp/ppthead}intestazionePPT` (solo `nodoSILInviaRP`)

| # | `localPart` | Return Type | Header? | `@LogExecution`? |
|---|---|---|---|---|
| 1 | `chiediFlussoSPC` | `ChiediFlussoSPCRisposta` | No | No |
| 2 | `chiediFlussoSPCPage` | `ChiediFlussoSPCPageRisposta` | No | No |
| 3 | `chiediListaFlussiSPC` | `ChiediListaFlussiSPCRisposta` | No | No |
| 4 | `nodoSILChiediCopiaEsito` | `NodoSILChiediCopiaEsitoRisposta` | No | No |
| 5 | `nodoSILInviaRP` | `NodoSILInviaRPRisposta` | Sí | No |
| 6 | `nodoSILChiediIUV` | `NodoSILChiediIUVRisposta` | No | Sí |
| 7 | `nodoSILInviaCarrelloRP` | `NodoSILInviaCarrelloRPRisposta` | No | Sí |
| 8 | `nodoSILRichiediRT` | `NodoSILRichiediRTRisposta` | No | Sí |

### 7.9 fesp/PagamentiTelematiciAvvisiDigitaliEndpoint — 1 operación

- **Namespace:** `http://www.regione.veneto.it/pagamenti/nodoregionalefesp/`
- **Condición:** `fesp.mode=local`
- **Impl:** `PagamentiTelematiciAvvisiDigitaliImpl` — qualifier: `"PagamentiTelematiciAvvisiDigitaliImpl"`

| # | `localPart` | Return Type | Header? |
|---|---|---|---|
| 1 | `nodoSILInviaAvvisoDigitale` | `NodoSILInviaAvvisoDigitaleRisposta` | Sí |

---

## 8. Catálogo Completo de Clientes SOAP (28 operaciones)

### 8.1 PagamentiTelematiciCCPPaClient — 5 operaciones

- **Condición:** `fesp.mode=local`
- **URL:** Explícita por parámetro `wsUrl`
- **Interceptor:** `myEndpointInterceptor`

| # | Método | Return Type | Header? | Giornale Módulo |
|---|---|---|---|---|
| 1 | `paaSILVerificaRP` | `PaaSILVerificaRPRisposta` | Sí | FESP |
| 2 | `paaSILAttivaRP` | `PaaSILAttivaRPRisposta` | Sí | FESP |
| 3 | `paVerifyPaymentNotice` | `PaVerifyPaymentNoticeRisposta` | No | FESP |
| 4 | `paGetPayment` | `PaGetPaymentRisposta` | No | FESP |
| 5 | `paSendRT` | `PaSendRTRisposta` | No | FESP |

### 8.2 PagamentiTelematiciEsitoClient — 1 operación

- **Condición:** `fesp.mode=local`
- **URL:** Explícita por parámetro `wsUrl`

| # | Método | Return Type | Header? | SystemBlock |
|---|---|---|---|---|
| 1 | `paaSILInviaEsito` | `PaaSILInviaEsitoRisposta` | Sí | `pa.client.paaSILInviaEsito` |

### 8.3 PagamentiTelematiciEsterniCCPClient — 4 operaciones

- **Condición:** Sin condición (siempre disponible)
- **URL:** Explícita por parámetro `wsUrl`
- **Nota:** Usa módulo `PA` (no FESP) y categoría `INTERFACCIA`

| # | Método | Return Type | Header? | SystemBlock |
|---|---|---|---|---|
| 1 | `paaSILVerificaEsterna` | `PaaSILVerificaEsternaRisposta` | Sí | `pa.client.paaSILVerificaEsterna` |
| 2 | `paaSILAttivaEsterna` | `PaaSILAttivaEsternaRisposta` | Sí | `pa.client.paaSILAttivaEsterna` |
| 3 | `paExternalVerifyPaymentNotice` | `PaExternalVerifyPaymentNoticeRes` | No | `pa.client.paExternalVerifyPaymentNotice` |
| 4 | `paExternalGetPayment` | `PaExternalGetPaymentRes` | No | `pa.client.paExternalGetPayment` |

### 8.4 fesp/PagamentiTelematiciRPClient — 9 operaciones

- **Condición:** `fesp.mode=remote`
- **URL:** URL por defecto del `WebServiceTemplate`
- **Implementa:** interfaz `PagamentiTelematiciRP`
- **Interceptor:** `myEndpointInterceptor`

| # | Método | Return Type | Header? |
|---|---|---|---|
| 1 | `chiediFlussoSPC` | `ChiediFlussoSPCRisposta` | No |
| 2 | `chiediFlussoSPCPage` | `ChiediFlussoSPCPageRisposta` | No |
| 3 | `chiediListaFlussiSPC` | `ChiediListaFlussiSPCRisposta` | No |
| 4 | `nodoSILChiediCopiaEsito` | `NodoSILChiediCopiaEsitoRisposta` | No |
| 5 | `nodoSILInviaRP` | `NodoSILInviaRPRisposta` | Sí |
| 6 | `nodoSILChiediIUV` | `NodoSILChiediIUVRisposta` | No |
| 7 | `nodoSILChiediCCP` | `NodoSILChiediCCPRisposta` | No |
| 8 | `nodoSILInviaCarrelloRP` | `NodoSILInviaCarrelloRPRisposta` | No |
| 9 | `nodoSILRichiediRT` | `NodoSILRichiediRTRisposta` | No |

### 8.5 fesp/PagamentiTelematiciRPTClient — 7 operaciones activas

- **Condición:** `fesp.mode=local`
- **URL:** URL por defecto del `WebServiceTemplate`
- **Interceptores:** `myEndpointInterceptor` + `pagoPAAuthClientInterceptor`
- **SSL:** Configurado con certificado cliente y proxy HTTP
- **Mock:** `PagamentiTelematiciRPTMockClient` cuando `fesp.mockPagoPa=true`

| # | Método | Return Type | Header? | SystemBlock |
|---|---|---|---|---|
| 1 | `nodoInviaRPT` | `NodoInviaRPTRisposta` | Sí (IntestazionePPT) | `fesp.client.nodoInviaRPT` |
| 2 | `nodoInviaCarrelloRPT` | `NodoInviaCarrelloRPTRisposta` | Sí (IntestazioneCarrelloPPT) | `fesp.client.nodoInviaCarrelloRPT` |
| 3 | `nodoChiediListaPendentiRPT` | `NodoChiediListaPendentiRPTRisposta` | No | `fesp.client.nodoChiediListaPendentiRPT` |
| 4 | `nodoChiediCopiaRT` | `NodoChiediCopiaRTRisposta` | No | `fesp.client.nodoChiediCopiaRT` |
| 5 | `nodoChiediElencoFlussiRendicontazione` | `NodoChiediElencoFlussiRendicontazioneRisposta` | No | `fesp.client.nodoChiediElencoFlussiRendicontazione` |
| 6 | `nodoChiediStatoRPT` | `NodoChiediStatoRPTRisposta` | No | `fesp.client.nodoChiediStatoRPT` |
| 7 | `nodoChiediFlussoRendicontazione` | `NodoChiediFlussoRendicontazioneRisposta` | No | `fesp.client.nodoChiediFlussoRendicontazione` |

Operaciones **deshabilitadas** (lanzan `UnsupportedOperationException`): `nodoInviaRichiestaStorno`, `nodoChiediElencoQuadraturePA`, `nodoChiediInformativaPSP`, `nodoChiediSceltaWISP`, `nodoInviaRispostaRevoca`.

### 8.6 fesp/PagamentiTelematiciAvvisiDigitaliClient — 1 operación

- **Condición:** `fesp.mode=remote`
- **Implementa:** interfaz `PagamentiTelematiciAvvisiDigitali`

| # | Método | Return Type | Header? |
|---|---|---|---|
| 1 | `nodoSILInviaAvvisoDigitale` | `NodoSILInviaAvvisoDigitaleRisposta` | Sí |

### 8.7 fesp/PagamentiTelematiciAvvisiDigitaliServiceClient — 1 operación

- **Condición:** `fesp.mode=local`
- **Nota:** Usa types del namespace `gov.telematici.pagamenti.ws.*` (Nodo PagoPA nacional)

| # | Método | Return Type | Header? | SystemBlock |
|---|---|---|---|---|
| 1 | `nodoInviaAvvisoDigitale` | `NodoInviaAvvisoDigitaleRisposta` | Sí (sachead) | `fesp.client.nodoInviaAvvisoDigitale` |

---

## 9. Infraestructura Transversal

### 9.1 GiornaleService — Auditoría de llamadas SOAP

Todos los endpoints y clientes envuelven sus llamadas en estos métodos genéricos:

```java
// Para endpoints que RECIBEN llamadas
public <T> T wrapRecordSoapServerEvent(
    Constants.GIORNALE_MODULO module,
    String identificativoDominio,
    String identificativoUnivocoVersamento,
    String codiceContestoPagamento,
    String identificativoPrestatoreServiziPagamento,
    String tipoVersamento,
    String componente,                    // COMPONENTE_PA o COMPONENTE_FESP
    String categoriaEvento,               // "INTERNO" o "INTERFACCIA"
    String tipoEvento,                    // nombre del evento
    String identificativoFruitore,
    String identificativoErogatore,
    String identificativoStazioneIntermediarioPa,
    String canalePagamento,
    Supplier<T> responseSupplier,         // lambda con la llamada real
    Function<T, String> esitoFunction)    // siempre OutcomeHelper::getOutcome

// Para clientes que ENVÍAN llamadas
public <T> T wrapRecordSoapClientEvent(
    /* misma firma que wrapRecordSoapServerEvent */
)
```

### 9.2 MyEndpointInterceptor — Logging de mensajes XML

Implementa `EndpointInterceptor` (server) + `ClientInterceptor` (client). Serializa el XML SOAP completo al Giornale:

- **Server**: request = `recordSoapEventFirst(REQ)`, response = `recordSoapEventLast(RES)`
- **Client**: request = `recordSoapEventLast(REQ)`, response = `recordSoapEventFirst(RES)`

### 9.3 PagoPAAuthClientInterceptor — Autenticación PagoPA

Agrega el header HTTP de subscription key solo a llamadas dirigidas al Nodo PagoPA:

```
Propiedades:
  pagopa.subscription.name    → nombre del header HTTP
  pagopa.subscription.value   → API key
  ws.pagamentiTelematiciRPT.remoteurl → URL a matchear
```

### 9.4 SystemBlockService — Bloqueo de operaciones

Pre-check antes de cada operación: `systemBlockService.blockByOperationName("pa.paaSILImportaDovuto")`. Permite bloquear operaciones específicas dinámicamente sin redespliegue.

### 9.5 OutcomeHelper — Extracción de resultado

30+ métodos sobrecargados `getOutcome(TipoRespuesta)` que inspeccionan cada tipo de respuesta SOAP y devuelven `"OK"` o `"KO"` basándose en la presencia de un `FaultBean`. Dos patrones:

- **Single-level**: respuesta tiene `getFault()` directamente
- **Two-level**: respuesta tiene un wrapper que primero se desenvuelve (ej: `PaaVerificaRPTRisposta` → `EsitoVerificaRPT` → `getFault()`)

### 9.6 ManageWsFault — Construcción genérica de faults

`@FunctionalInterface` parametrizada que permite construir respuestas de error tipadas:

```java
@FunctionalInterface
public interface ManageWsFault<Response> {
    Response apply(String faultCode, String faultString, String faultDescr, Throwable error);
    default Response apply(String faultCode, String faultString) {
        return apply(faultCode, faultString, null, null);
    }
}
```

### 9.7 WSDL personalizado

Tres clases personalizan la generación automática de WSDL por Spring-WS:

- **`MyWsdl11Definition`**: Reemplaza `DefaultWsdl11Definition`, conecta providers personalizados
- **`MySuffixBasedMessagesProvider`**: Permite request elements **sin sufijo** (empty request suffix)
- **`MySuffixBasedPortTypesProvider`**: Personaliza derivación de nombres de operación, elimina operaciones sin output

---

## 10. Interfaces Completas (`iface/`)

### PagamentiTelematiciDovutiPagati (16 métodos)

```java
// Namespace de tipos: it.veneto.regione.pagamenti.ente.*
// Header: it.veneto.regione.pagamenti.ente.ppthead.IntestazionePPT
// Nota: 4 métodos retornan void con Holder<> (patrón JAX-WS)

PaaSILImportaDovutoRisposta paaSILImportaDovuto(PaaSILImportaDovuto, IntestazionePPT);
PaaSILAutorizzaImportFlussoRisposta paaSILAutorizzaImportFlusso(PaaSILAutorizzaImportFlusso, IntestazionePPT);
void paaSILChiediEsitoCarrelloDovuti(String codIpaEnte, String password, String idSessionCarrello,
    Holder<FaultBean> fault, Holder<ListaCarrelli> listaCarrelli);
void paaSILChiediPagati(String codIpaEnte, String password, String idSession,
    Holder<FaultBean> fault, Holder<DataHandler> pagati);
void paaSILChiediPagatiConRicevuta(String codIpaEnte, String password, String idSession,
    String iuv, String iud, Holder<FaultBean> fault, Holder<DataHandler> pagati,
    Holder<String> tipoFirma, Holder<DataHandler> rt);
PaaSILChiediPosizioniAperteRisposta paaSILChiediPosizioniAperte(PaaSILChiediPosizioniAperte);
PaaSILChiediStatoExportFlussoRisposta paaSILChiediStatoExportFlusso(PaaSILChiediStatoExportFlusso, IntestazionePPT);
PaaSILChiediStatoImportFlussoRisposta paaSILChiediStatoImportFlusso(PaaSILChiediStatoImportFlusso, IntestazionePPT);
PaaSILChiediStoricoPagamentiRisposta paaSILChiediStoricoPagamenti(PaaSILChiediStoricoPagamenti);
PaaSILInviaCarrelloDovutiRisposta paaSILInviaCarrelloDovuti(PaaSILInviaCarrelloDovuti, IntestazionePPT);
PaaSILInviaDovutiRisposta paaSILInviaDovuti(PaaSILInviaDovuti, IntestazionePPT);
PaaSILPrenotaExportFlussoRisposta paaSILPrenotaExportFlusso(PaaSILPrenotaExportFlusso, IntestazionePPT);
PaaSILPrenotaExportFlussoIncrementaleConRicevutaRisposta paaSILPrenotaExportFlussoIncrementaleConRicevuta(..., IntestazionePPT);
void paaSILRegistraPagamento(String codIpaEnte, String password, String iuv, String ccp,
    BigDecimal importo, XMLGregorianCalendar dataEsito, Integer indice, String iur,
    String tipoIstituto, String codiceIstituto, String denominazione, String idFlusso,
    Holder<FaultBean> fault, Holder<String> esito);
PaaSILRecuperaAvvisoRisposta paaSILRecuperaAvviso(PaaSILRecuperaAvviso, IntestazionePPT);
PaaSILVerificaAvvisoRisposta paaSILVerificaAvviso(PaaSILVerificaAvviso, IntestazionePPT);
```

### PagamentiTelematiciCCPPa (2 métodos)

```java
// Namespace de tipos: it.veneto.regione.pagamenti.pa.*
// Header: it.veneto.regione.pagamenti.pa.ppthead.IntestazionePPT

PaaSILVerificaRPRisposta paaSILVerificaRP(PaaSILVerificaRP, IntestazionePPT);
PaaSILAttivaRPRisposta paaSILAttivaRP(PaaSILAttivaRP, IntestazionePPT);
```

### PagamentiTelematiciEsito (1 método)

```java
// Namespace de tipos: it.veneto.regione.pagamenti.pa.*
PaaSILInviaEsitoRisposta paaSILInviaEsito(PaaSILInviaEsito, IntestazionePPT);
```

### PagamentiTelematiciFlussiSPC (2 métodos)

```java
// Namespace de tipos: it.veneto.regione.pagamenti.pa.*
PaaSILChiediFlussoSPCRisposta paaSILChiediFlussoSPC(PaaSILChiediFlussoSPC);
PaaSILChiediElencoFlussiSPCRisposta paaSILChiediElencoFlussiSPC(PaaSILChiediElencoFlussiSPC);
```

### fesp/PagamentiTelematiciCCP (2 métodos)

```java
// Namespace de tipos: gov.telematici.pagamenti.ws.*
// Header: gov.telematici.pagamenti.ws.ppthead.IntestazionePPT
PaaVerificaRPTRisposta paaVerificaRPT(PaaVerificaRPT, IntestazionePPT);
PaaAttivaRPTRisposta paaAttivaRPT(PaaAttivaRPT, IntestazionePPT);
```

### fesp/PagamentiTelematiciCCP25 (5 métodos)

```java
// Namespace de tipos: it.gov.pagopa.pagopa_api.pa.pafornode.*
// Sin header SOAP
PaVerifyPaymentNoticeRes paVerifyPaymentNotice(PaVerifyPaymentNoticeReq);
PaGetPaymentRes paGetPayment(PaGetPaymentReq);
PaSendRTRes paSendRT(PaSendRTReq);
PaGetPaymentV2Response paGetPaymentV2(PaGetPaymentV2Request);
PaSendRTV2Response paSendRTV2(PaSendRTV2Request);
```

### fesp/PagamentiTelematiciRT (1 método)

```java
// Namespace de tipos: gov.telematici.pagamenti.ws.*
PaaInviaRTRisposta paaInviaRT(PaaInviaRT, IntestazionePPT);
```

### fesp/PagamentiTelematiciRP (9 métodos)

```java
// Namespace de tipos: it.veneto.regione.pagamenti.nodoregionalefesp.nodoregionaleperpa.*
// Header: it.veneto.regione.pagamenti.nodoregionalefesp.ppthead.IntestazionePPT (solo nodoSILInviaRP)
ChiediFlussoSPCRisposta chiediFlussoSPC(ChiediFlussoSPC);
ChiediFlussoSPCPageRisposta chiediFlussoSPCPage(ChiediFlussoSPCPage);
ChiediListaFlussiSPCRisposta chiediListaFlussiSPC(ChiediListaFlussiSPC);
NodoSILChiediCopiaEsitoRisposta nodoSILChiediCopiaEsito(NodoSILChiediCopiaEsito);
NodoSILInviaRPRisposta nodoSILInviaRP(NodoSILInviaRP, IntestazionePPT);
NodoSILChiediIUVRisposta nodoSILChiediIUV(NodoSILChiediIUV);
NodoSILChiediCCPRisposta nodoSILChiediCCP(NodoSILChiediCCP);
NodoSILInviaCarrelloRPRisposta nodoSILInviaCarrelloRP(NodoSILInviaCarrelloRP);
NodoSILRichiediRTRisposta nodoSILRichiediRT(NodoSILRichiediRT);
```

### fesp/PagamentiTelematiciAvvisiDigitali (1 método)

```java
// Namespace de tipos: it.veneto.regione.pagamenti.nodoregionalefesp.*
// Header: it.veneto.regione.pagamenti.nodoregionalefesp.IntestazionePPT
NodoSILInviaAvvisoDigitaleRisposta nodoSILInviaAvvisoDigitale(NodoSILInviaAvvisoDigitale, IntestazionePPT);
```

---

## 11. Paquetes JAXB Generados (módulo `mypay4-be-generated`)

Los tipos Request/Response/Header provienen de clases JAXB generadas desde XSD/WSDL:

| Paquete | Contenido |
|---|---|
| `it.veneto.regione.pagamenti.pa.*` | Tipos PA (PaaSIL*, FaultBean, etc.) |
| `it.veneto.regione.pagamenti.pa.ppthead` | Header IntestazionePPT para PA |
| `it.veneto.regione.pagamenti.ente.*` | Tipos Ente (DovutiPagati) |
| `it.veneto.regione.pagamenti.ente.ppthead` | Header IntestazionePPT para Ente |
| `gov.telematici.pagamenti.ws.*` | Tipos PagoPA nacional (old SANP) |
| `gov.telematici.pagamenti.ws.ppthead` | Header IntestazionePPT para NdP |
| `gov.telematici.pagamenti.ws.sachead` | Header IntestazionePPT para avisos SAC |
| `gov.telematici.pagamenti.ws.nodospcpernodoregionale` | Tipos NodoSPC-per-NodoRegionale |
| `it.gov.pagopa.pagopa_api.pa.pafornode.*` | Tipos PagoPA v2.5 (nuevo modelo) |
| `it.veneto.regione.pagamenti.nodoregionalefesp.*` | Tipos FESP generales |
| `it.veneto.regione.pagamenti.nodoregionalefesp.nodoregionaleperpa.*` | Tipos FESP-to-PA |
| `it.veneto.regione.pagamenti.nodoregionalefesp.ppthead` | Header IntestazionePPT para FESP |
| `it.veneto.regione.pagamenti.nodoregionalefesp.nodoregionaleperpamsd` | Tipos MSD |
| `it.veneto.regione.schemas._2012.pagamenti.ente.*` | Schemas regionales de pago |
| `it.gov.digitpa.schemas._2011.pagamenti` | Schemas DigitPA legacy |
| `eu.easybridge.bridge` | Integración EasyBridge |

---

## 12. Configuración de Servidor SOAP (`SoapWebServiceConfig.java`)

### URLs expuestas

- PA endpoints: `http://host:{port}/ws/pa/{NombreEndpoint}`
- FESP endpoints: `http://host:{port}/ws/fesp/{NombreEndpoint}`
- WSDL disponible en: `http://host:{port}/ws/pa/{NombreEndpoint}?wsdl`

### Estructura de la configuración

```java
@Configuration
@EnableWs
public class SoapWebServiceConfig extends WsConfigurerAdapter {

    // 1. MessageDispatcherServlet con mappings /ws/pa/* y /ws/fesp/*
    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet() { ... }

    // 2. Interceptor de auditoría
    @Override
    public void addInterceptors(List<EndpointInterceptor> interceptors) {
        interceptors.add(myEndpointInterceptor);
    }

    // 3. XSD Schema beans
    @Bean public XsdSchema digitpaXsd() { ... }
    @Bean public XsdSchema pagamXsd() { ... }
    @Bean public XsdSchemaCollection pagamHeadXsd() { ... }
    @Bean public XsdSchema paForNodeXsd() { ... }

    // 4. WSDL definition beans (uno por endpoint)
    @Bean(name = PagamentiTelematiciCCPPaEndpoint.NAME)
    @ConditionalOnProperty(prefix = "fesp", name = "mode", havingValue = "remote")
    public MyWsdl11Definition pagamentiTelematiciCCPPaWsdl() {
        MyWsdl11Definition wsdl = new MyWsdl11Definition();
        wsdl.setPortTypeName(PagamentiTelematiciCCPPaEndpoint.NAME);
        wsdl.setTargetNamespace(PagamentiTelematiciCCPPaEndpoint.NAMESPACE_URI);
        wsdl.setLocationUri("/ws/pa");
        wsdl.setSchema(pagInfRpEsito620Xsd());
        wsdl.setRequestSuffix("");
        wsdl.setResponseSuffix("Risposta");
        return wsdl;
    }
    // ... otros beans similares
}
```

---

## 13. Propiedades de Configuración Relevantes

```properties
# Modo FESP
fesp.mode=local|remote|none

# Identificadores del intermediario
nodoRegionaleFesp.identificativoIntermediarioPA=...
nodoRegionaleFesp.identificativoStazioneIntermediarioPA=...

# PSP por defecto
pa.pspDefaultIdentificativoPsp=...
pa.pspDefaultIdentificativoCanale=...

# URL del Nodo PagoPA
ws.pagamentiTelematiciRPT.remoteurl=...

# Autenticación PagoPA
pagopa.subscription.name=...
pagopa.subscription.value=...

# GPD
pa.gpd.enabled=true|false
pa.gpd.preload=true|false

# SEND/PND (notifiche)
pa.pnd.codTipoDovuto=...
pa.pnd.codTassonomico=...

# Mock
fesp.mockPagoPa=true|false

# URL mock
nodoSILInviaRP.url=...

# Versión RP
pa.deRpVersioneOggetto=...
```

---

## 14. Seguridad

- Los paths `/ws/**` están **excluidos** de la autenticación JWT de Spring Security (definido en `MyPay4AbstractSecurityConfig.getSecurityWhitelist()`)
- La autenticación SOAP se hace **a nivel de aplicación**: cada operación valida `password` del `IntestazionePPT` o del request body contra la configuración del ente
- La comunicación con el Nodo PagoPA usa **certificados SSL/mTLS** + **API subscription key** (via `PagoPAAuthClientInterceptor`)

---

## 15. Dependencias Maven

```xml
<!-- Spring Web Services -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web-services</artifactId>
</dependency>

<!-- WSDL -->
<dependency>
    <groupId>wsdl4j</groupId>
    <artifactId>wsdl4j</artifactId>
</dependency>

<!-- XSD -->
<dependency>
    <groupId>org.apache.ws.xmlschema</groupId>
    <artifactId>xmlschema-core</artifactId>
</dependency>

<!-- JAXB -->
<dependency>
    <groupId>javax.xml.bind</groupId>
    <artifactId>jaxb-api</artifactId>
</dependency>

<!-- JAX-WS -->
<dependency>
    <groupId>com.sun.xml.ws</groupId>
    <artifactId>jaxws-ri</artifactId>
    <type>pom</type>
</dependency>

<!-- XMLBeans -->
<dependency>
    <groupId>org.apache.xmlbeans</groupId>
    <artifactId>xmlbeans</artifactId>
</dependency>
```

---

## 16. Utilidades (`ws/util/`)

### Fault Codes

| Clase | Propósito |
|---|---|
| `FaultCodeConstants` | Registro central de **todos** los códigos de error PAA (208 líneas de constantes) |
| `FaultCodeInvioRPT` | Enum de 33 códigos PPT para `nodoInviaRPT` |
| `FaultCodeChiediStatoRPT` | Enum de 12 códigos PPT para `chiediStatoRPT` |

### Enums de Dominio

| Clase | Propósito |
|---|---|
| `EnumUtils.StCodiceEsitoPagamento` | Códigos de resultado de pago: 0=pagado, 1=no pagado, 2=parcial, 3=scaduto, 4=rechazado |
| `EnumUtils.StTipoBollo` | Tipo de bollo digital: "01" |
| `EnumUtils.StFirmaRicevuta` | Tipos de firma de recibo: "0", "1", "3", "4" |
| `StatiRPT` | 15 estados del ciclo de vida RPT (desde `RPT_RICEVUTA_NODO` hasta `RT_ERRORE_INVIO_A_PA`) |

### Helpers

| Clase | Propósito |
|---|---|
| `OutcomeHelper` | 30+ métodos `getOutcome()` sobrecargados para extraer OK/KO de cada tipo de respuesta |
| `PagamentiTelematiciDovutiPagatiHelper` | Validación de requests + construcción de documentos XML `Pagati`/`PagatiConRicevuta` |
| `ManageWsFault<R>` | `@FunctionalInterface` para construir respuestas de error tipadas |
| `SumUtilis` | Suma de importes monetarios en formato string |

### WSDL y generación

| Clase | Propósito |
|---|---|
| `MyWsdl11Definition` | Reemplazo de `DefaultWsdl11Definition` con providers personalizados |
| `MySuffixBasedMessagesProvider` | Permite request elements sin sufijo |
| `MySuffixBasedPortTypesProvider` | Personaliza derivación de nombres de operación |

---

## 17. Resumen Ejecutivo para Migración

Para replicar esta arquitectura SOAP en otro proyecto se necesita:

1. **Tipos JAXB** (módulo `generated`): 20+ paquetes de clases generadas desde XSD. Si los XSD/WSDL son los mismos, regenerar con `jaxb2-maven-plugin` o copiar las clases.

2. **Configuración de servidor** (`SoapWebServiceConfig`): `@EnableWs`, `MessageDispatcherServlet`, beans de `MyWsdl11Definition` por endpoint, XSD schemas.

3. **Configuración de clientes** (`SoapWebServiceClientConfig`): `Jaxb2Marshaller` con context paths, beans de clientes con interceptores, SSL/proxy para Nodo PagoPA.

4. **Patrón de 4 capas por servicio**: `iface` → `impl` → `server/@Endpoint` / `client`.

5. **Clases base**: `BaseEndpoint` (unmarshall headers) + `BaseClient` (marshalling headers + SoapAction fix).

6. **Interceptores**: `MyEndpointInterceptor` (auditoría bidireccional) + `PagoPAAuthClientInterceptor` (API key PagoPA).

7. **Utilidades**: `OutcomeHelper`, `FaultCodeConstants`, `ManageWsFault`, `MyWsdl11Definition` + providers.

8. **Patrón local/remoto**: La decisión por ente (`ente.getDeUrlEsterniAttiva()`) de llamar local bean vs SOAP remoto.

9. **GiornaleService**: Auditoría completa con `wrapRecordSoapServerEvent` / `wrapRecordSoapClientEvent`.

10. **Seguridad**: Excluir `/ws/**` de JWT, validar password en SOAP headers a nivel aplicación, configurar mTLS + subscription key para Nodo PagoPA.
