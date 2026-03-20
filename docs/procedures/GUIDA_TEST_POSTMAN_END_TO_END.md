# Guida Test End-to-End con Postman — MyPay Middleware

**Versione:** 1.0  
**Data:** 18 Marzo 2026  
**Ambiente testato:** PU UAT (`api.uat.p4pa.pagopa.it`)  
**Risultato:** Test superato con successo (HTTP 200)

---

## 1. Obiettivo

Questa guida documenta come eseguire un test end-to-end reale del middleware `mypay.mypaycore`, verificando l'intero flusso:

```
Postman (simula SIL) → Middleware (localhost:8080) → PU UAT (api.uat.p4pa.pagopa.it) → pagoPA
```

Il middleware riceve la richiesta SOAP dal SIL, gestisce internamente l'autenticazione OAuth2, inoltra l'Envelope SOAP completo alla Piattaforma Unitaria, e restituisce la risposta al SIL.

---

## 2. Pre-requisiti

### 2.1 Software

| Software | Versione | Note |
|----------|----------|------|
| Java JDK | 17 | `C:\Program Files\Java\jdk-17` |
| Maven | 3.9.9 | `C:\Program Files\apache-maven\apache-maven-3.9.9` |
| PostgreSQL | qualsiasi | Database `mypay_local_copy` su `localhost:5432` |
| Postman | qualsiasi | Per l'invio delle richieste |

### 2.2 Database

PostgreSQL deve essere attivo con il seguente database:

- **Host:** `localhost:5432`
- **Database:** `mypay_local_copy`
- **User:** `admin`
- **Password:** `admin`

### 2.3 Credenziali OAuth2

Le credenziali OAuth2 sono nel file `.env` nella root del progetto (il file è gitignored).

| Parametro | Valore |
|-----------|--------|
| Client ID | `SELC_99999000013SIL_RegLomb2` |
| Client Secret | Vedi file `.env` |
| Grant Type | `client_credentials` |
| Scope | `openid` |

### 2.4 Connettività di rete

Il PC deve avere accesso a:
- `https://api.uat.p4pa.pagopa.it` (PU UAT di pagoPA)

---

## 3. Avvio del Middleware con profilo `dev`

### 3.1 Da terminale

```bash
cmd.exe /c "set JAVA_HOME=C:\Program Files\Java\jdk-17&& set PIATTAFORMA_CLIENT_SECRET=<client-secret>&& mvn spring-boot:run -f mypay.mypaycore-springboot/pom.xml -Denforcer.skip=true -Dspring-boot.run.profiles=dev"
```

> **IMPORTANTE:** Sostituire `<client-secret>` con il valore reale dal file `.env`.

### 3.2 Verifica avvio

Nei log dell'applicazione, cercare:

```
Started Application in X.XXX seconds
```

Componenti che devono risultare attivi:
- DataSource `pa` connesso a PostgreSQL
- Profilo attivo: `dev`
- Porta: `8080`

---

## 4. Importare la collection Postman

Importare in Postman il file:

```
requests/MyPay-Middleware-Dev.postman_collection.json
```

La collection contiene le seguenti cartelle:

| Cartella | Contenuto |
|----------|-----------|
| **Diagnostica** | Health check, info, token status |
| **Flusso Principale** | Richiesta SOAP `pivotSILAutorizzaImportFlussoTesoreria` |
| **Test Diretti PU** | Chiamate dirette alla PU (per debug) |

---

## 5. Esecuzione del test step-by-step

### Step 1 — Health Check

**Richiesta:**
```
GET http://localhost:8080/actuator/health
```

**Risposta attesa:**
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "piattaformaUnitaria": { "status": "UP" }
  }
}
```

**Cosa verificare:**
- `db.status` = `UP` → PostgreSQL connesso
- `piattaformaUnitaria.status` = `UP` → PU UAT raggiungibile
- `OAuthToken.status` = `DOWN` al primo avvio (normale — il token viene acquisito alla prima richiesta SOAP)

---

### Step 2 — Richiesta SOAP principale (simula il SIL)

Questa è la richiesta che il SIL invierebbe al middleware in produzione.

**Richiesta:**
```
POST http://localhost:8080/pu/sil/soap/reconciliation/PagamentiTelematiciPagatiRiconciliati
Content-Type: text/xml;charset=UTF-8
```

**Body XML:**
```xml
<soapenv:Envelope
    xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:ppt="http://www.regione.veneto.it/pagamenti/pivot/ente/ppthead"
    xmlns:ente="http://www.regione.veneto.it/pagamenti/pivot/ente/">
    <soapenv:Header>
        <ppt:intestazionePPT>
            <codIpaEnte>SELC_99999000013</codIpaEnte>
        </ppt:intestazionePPT>
    </soapenv:Header>
    <soapenv:Body>
        <ente:pivotSILAutorizzaImportFlussoTesoreria>
            <password>BERGAMO</password>
            <tipoFlusso>O</tipoFlusso>
        </ente:pivotSILAutorizzaImportFlussoTesoreria>
    </soapenv:Body>
</soapenv:Envelope>
```

**Risposta attesa (HTTP 200):**
```xml
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
    <SOAP-ENV:Header/>
    <SOAP-ENV:Body>
        <ns3:pivotSILAutorizzaImportFlussoTesoreriaRisposta
            xmlns:ns3="http://www.regione.veneto.it/pagamenti/pivot/ente/">
            <uploadUrl>https://api.uat.p4pa.pagopa.it/pu/fileshare/organization/11/ingestionflowfiles?ingestionFlowFileId=XXXX&amp;ingestionFlowFileType=TREASURY_OPI&amp;fileOrigin=SIL</uploadUrl>
            <authorizationToken>AUTHORIZATIONTOKEN</authorizationToken>
            <requestToken>XXXX</requestToken>
            <importPath>/IMPORTPATH</importPath>
        </ns3:pivotSILAutorizzaImportFlussoTesoreriaRisposta>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

**Cosa verificare:**
- HTTP Status: `200 OK`
- Content-Type della risposta: `text/xml`
- Il body contiene `pivotSILAutorizzaImportFlussoTesoreriaRisposta`
- I campi `uploadUrl`, `authorizationToken`, `requestToken`, `importPath` sono presenti
- Il `requestToken` è un numero (identificativo della richiesta sulla PU)

> **NOTA:** I valori `XXXX`, `AUTHORIZATIONTOKEN`, `IMPORTPATH` sono placeholder usati dall'ambiente UAT. In produzione questi conterrebbero valori reali.

---

### Step 3 — Verifica token OAuth2 in cache

Dopo la prima richiesta SOAP, il token OAuth2 è stato acquisito e memorizzato in cache.

**Richiesta:**
```
GET http://localhost:8080/actuator/health
```

**Cosa verificare:**
- `OAuthToken.status` = `UP`
- `OAuthToken.details.stato` = `"Token OAuth2 in cache valido"`

Il token ha una validità di circa 4 ore. Le richieste successive utilizzeranno il token in cache senza rinegoziarlo.

---

## 6. Flusso interno durante il test

Quando si invia la richiesta SOAP dallo Step 2, il middleware esegue internamente:

```
1. Postman invia SOAP Envelope a localhost:8080
       ↓
2. Spring WS riceve la richiesta e la instrada a ReconciliationEndpoint
       ↓
3. ReconciliationEndpoint estrae l'Envelope SOAP completo (Header + Body)
   tramite MessageContext e SoapMessage.writeTo()
       ↓
4. PiattaformaUnitariaClient.forwardSoapRequest() prepara la richiesta verso PU
       ↓
5. OAuthTokenInterceptor aggiunge automaticamente "Authorization: Bearer <token>"
       ↓
6. (Se token assente o scaduto) OAuthTokenService richiede un nuovo token:
   POST https://api.uat.p4pa.pagopa.it/pu/auth/oauth/token
     ?client_id=SELC_99999000013SIL_RegLomb2
     &client_secret=***
     &grant_type=client_credentials
     &scope=openid
       ↓
7. L'Envelope SOAP completo viene inviato alla PU con il Bearer token:
   POST https://api.uat.p4pa.pagopa.it/pu/sil/soap/reconciliation/PagamentiTelematiciPagatiRiconciliati
       ↓
8. La PU risponde con un Envelope SOAP contenente il risultato
       ↓
9. Il middleware estrae il body dalla risposta PU
       ↓
10. Spring WS re-wrappa il body in un nuovo Envelope SOAP e lo restituisce a Postman
```

---

## 7. Risoluzione problemi comuni

### 7.1 Errore di connessione al database

```
HikariPool-1 - Connection is not available
```

**Causa:** PostgreSQL non è avviato o il database `mypay_local_copy` non esiste.  
**Soluzione:** Avviare PostgreSQL e verificare che il database esista.

### 7.2 OAuthToken: DOWN — Errore di autenticazione

```
OAuthToken: DOWN - Errore nella richiesta del token OAuth2
```

**Causa possibile:** Client secret errato o non impostato.  
**Soluzione:** Verificare che la variabile `PIATTAFORMA_CLIENT_SECRET` sia impostata correttamente nel comando di avvio.

### 7.3 HTTP 404 dalla PU sull'endpoint OAuth2

**Causa:** I parametri OAuth2 vengono inviati come body `x-www-form-urlencoded` invece che come query string.  
**Soluzione:** Questo bug è stato corretto in `OAuthTokenService.java`. Verificare di avere la versione aggiornata.

### 7.4 SOAP Fault: Namespace non riconosciuto

```xml
<SOAP-ENV:Fault>
    <faultcode>SOAP-ENV:Server</faultcode>
    <faultstring>Errore nell'elaborazione della richiesta</faultstring>
</SOAP-ENV:Fault>
```

**Causa possibile:** La richiesta SOAP usa i namespace sbagliati (lombardia invece di veneto).  
**Soluzione:** Verificare che i namespace siano:
- Header: `http://www.regione.veneto.it/pagamenti/pivot/ente/ppthead`
- Body: `http://www.regione.veneto.it/pagamenti/pivot/ente/`

### 7.5 HTTP 401/403 dal middleware

**Causa:** La sicurezza JWT di SpringLine2 sta bloccando la richiesta.  
**Soluzione:** Verificare che il profilo attivo sia `dev`. Il profilo `dev` disabilita la sicurezza JWT sugli endpoint SOAP (`application-dev.yml` sovrascrive `spl.security.authentication.jwt.enabled: false`).

### 7.6 Timeout o connessione rifiutata verso la PU

**Causa:** Problemi di rete o firewall verso `api.uat.p4pa.pagopa.it`.  
**Soluzione:** Verificare la connettività con:
```bash
curl -v https://api.uat.p4pa.pagopa.it/pu/auth/oauth/token
```

---

## 8. Report del test eseguito il 18 Marzo 2026

### Ambiente

| Parametro | Valore |
|-----------|--------|
| Data | 18 Marzo 2026 |
| Profilo Spring | `dev` |
| Middleware | `localhost:8080` |
| PU Target | `https://api.uat.p4pa.pagopa.it` |
| Database | PostgreSQL `localhost:5432/mypay_local_copy` |
| Client ID | `SELC_99999000013SIL_RegLomb2` |

### Risultati

| Step | Descrizione | Risultato |
|------|-------------|-----------|
| 1 | Health check middleware | OK — db: UP, piattaformaUnitaria: UP |
| 2 | Richiesta SOAP principale | OK — HTTP 200, risposta valida con `uploadUrl`, `authorizationToken`, `requestToken`, `importPath` |
| 3 | Token OAuth2 in cache | OK — OAuthToken: UP, token valido (~4 ore) |

### Risposta reale ricevuta dalla PU

```xml
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
  <SOAP-ENV:Header/>
  <SOAP-ENV:Body>
    <ns3:pivotSILAutorizzaImportFlussoTesoreriaRisposta
        xmlns:ns3="http://www.regione.veneto.it/pagamenti/pivot/ente/">
      <uploadUrl>https://api.uat.p4pa.pagopa.it/pu/fileshare/organization/11/ingestionflowfiles?ingestionFlowFileId=5575&amp;ingestionFlowFileType=TREASURY_OPI&amp;fileOrigin=SIL</uploadUrl>
      <authorizationToken>AUTHORIZATIONTOKEN</authorizationToken>
      <requestToken>5575</requestToken>
      <importPath>/IMPORTPATH</importPath>
    </ns3:pivotSILAutorizzaImportFlussoTesoreriaRisposta>
  </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

### Bug trovati e risolti durante il test

| # | Bug | Correzione | File modificato |
|---|-----|-----------|-----------------|
| 1 | OAuth2 endpoint richiede query string, non form body | Parametri inviati come `?client_id=...&client_secret=...` nella URL | `OAuthTokenService.java` |
| 2 | Sicurezza JWT SpringLine2 bloccava endpoint SOAP | JWT disabilitato nel profilo dev | `application-dev.yml` |
| 3 | Namespace SOAP errati (lombardia vs veneto) | Corretti in tutti i file (endpoint, mock, fault resolver, collection Postman) | Multipli |
| 4 | Middleware inoltrava solo il body, non l'Envelope completo | Iniezione `MessageContext` per estrarre l'Envelope completo | `ReconciliationEndpoint.java` |

### Conclusione

Il test end-to-end ha confermato che il middleware è in grado di:
1. Ricevere richieste SOAP dai SIL senza richiedere autenticazione JWT
2. Acquisire autonomamente un token OAuth2 dalla PU UAT
3. Inoltrare l'Envelope SOAP completo (Header + Body) alla PU con il Bearer token
4. Ricevere la risposta dalla PU e restituirla al SIL in formato SOAP corretto
5. Memorizzare il token in cache per le richieste successive

Il flusso principale `pivotSILAutorizzaImportFlussoTesoreria` è **pienamente operativo** in ambiente UAT.
