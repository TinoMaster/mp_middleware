1. Project Overview

Questo progetto ha lo scopo di sviluppare un middleware di integrazione tra i Sistemi Informativi Locali (SIL) degli enti e la Piattaforma Unitaria collegata a pagoPA.

Il middleware fungerà da layer di comunicazione e orchestrazione, permettendo ai sistemi SIL di interagire con i servizi della piattaforma attraverso un unico punto di accesso.

Il sistema dovrà:

ricevere richieste dai sistemi SIL

autenticarsi verso la Piattaforma Unitaria

invocare i servizi esposti dalla piattaforma

restituire la risposta al sistema chiamante

In questa fase iniziale il progetto si concentra sulla costruzione delle fondamenta tecniche del middleware.

2. High Level Architecture

Il middleware si colloca tra i sistemi locali e la piattaforma di pagamento.

Flusso generale:

SIL
  │
  │ SOAP Request
  ▼
Middleware
  │
  │ OAuth2 Authentication
  ▼
Piattaforma Unitaria
  │
  ▼
pagoPA

Il middleware ha tre responsabilità principali:

Ricezione richieste dai SIL

Gestione autenticazione verso la Piattaforma Unitaria

Forward delle richieste e gestione delle risposte

3. Actors
SIL (Sistemi Informativi Locali)

Sistemi applicativi degli enti che inviano richieste al middleware.

Responsabilità:

inviare richieste SOAP

ricevere le risposte dal middleware

Middleware (questo progetto)

Sistema di integrazione che gestisce:

autenticazione

orchestrazione delle chiamate

comunicazione con la piattaforma

Piattaforma Unitaria

Sistema esterno collegato a pagoPA che espone servizi per la gestione dei pagamenti e della riconciliazione.

4. Main Functional Flow

Il flusso operativo principale è il seguente.

Step 1 — Richiesta dal SIL

Il sistema SIL invia una richiesta SOAP al middleware.

Endpoint esempio:

POST
/pu/sil/soap/reconciliation/PagamentiTelematiciPagatiRiconciliati

Header principali:

Content-Type: text/xml
Authorization: Bearer <token>

Body SOAP (semplificato):

<soapenv:Envelope>
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

Il middleware deve:

ricevere la richiesta

validare la struttura

preparare la chiamata verso la piattaforma.

In questa fase la logica di business non è prioritaria.

5. Authentication Flow

Il middleware deve autenticarsi verso la Piattaforma Unitaria usando OAuth2 Client Credentials Flow.

Endpoint di login:

POST
https://api.uat.p4pa.pagopa.it/pu/auth/oauth/token

Parametri:

client_id
client_secret
grant_type=client_credentials
scope=openid

Esempio:

client_id=SELC_99999000013SIL_RegLomb2
client_secret=xxxxx
grant_type=client_credentials
scope=openid

Risposta attesa:

access_token
token_type
expires_in

Il token restituito è un JWT utilizzato per autenticare le richieste successive.

6. Token Management

Il middleware deve implementare una gestione del token efficiente.

Funzionalità richieste:

Richiesta token

Il middleware richiede il token alla piattaforma quando necessario.

Caching token

Il token deve essere salvato temporaneamente per evitare richieste di login inutili.

Token refresh

Se il token è scaduto:

il middleware deve richiederne uno nuovo.

7. Middleware Responsibilities

Il middleware deve implementare le seguenti capacità.

1 Ricezione richiesta SOAP

Esporre endpoint che possano essere chiamati dai SIL.

2 Gestione autenticazione

Gestire il login verso la Piattaforma Unitaria tramite OAuth2.

3 Chiamata ai servizi della piattaforma

Utilizzare il token ottenuto per invocare gli endpoint della piattaforma.

Header richiesto:

Authorization: Bearer <access_token>
4 Gestione risposta

Ricevere la risposta dalla piattaforma e restituirla al SIL.

8. Required Components

Per costruire la base del progetto il sistema deve includere almeno i seguenti componenti.

Config Module

Gestione configurazioni:

URL piattaforma

credenziali OAuth

configurazioni ambienti (dev / test / uat)

Authentication Module

Responsabile di:

chiamata endpoint OAuth

gestione token

caching token

SOAP Endpoint Module

Espone gli endpoint utilizzati dai sistemi SIL.

Platform Client Module

Client HTTP responsabile di invocare la Piattaforma Unitaria.

Logging Module

Logging di:

richieste

risposte

errori

chiamate esterne

9. Phase 1 Development Scope

In questa fase l'obiettivo è costruire le fondamenta del middleware.

L'agente deve quindi:

creare la struttura base del progetto

implementare il sistema di autenticazione OAuth2

creare un endpoint SOAP di esempio

implementare il client verso la piattaforma

gestire il token di accesso

La logica di business verrà sviluppata nelle fasi successive.

10. Expected Outcome

Al termine della fase iniziale il sistema dovrà essere in grado di:

ricevere una richiesta dal SIL

ottenere un token OAuth dalla Piattaforma Unitaria

inviare una richiesta autenticata alla piattaforma

restituire la risposta al SIL