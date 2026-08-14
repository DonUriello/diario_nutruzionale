# nutri — diario nutrizionale

Spring Boot 4.1 / Java 25 / PostgreSQL 18, frontend senza build step.
Copre le fasi 0 e 1 del piano: schema, catalogo nutrienti, alimenti con
versioning, diario, totali di pasto e di giornata.

## Avvio

**1. Database**

```
docker compose up -d
```

Espone Postgres sulla porta **5433** dell'host, non la 5432: così convive con
un eventuale Postgres nativo già installato. Se preferisci la 5432, cambia
`docker-compose.yml` e `application.yml` insieme.

Per verificare:

```
docker compose ps
docker compose logs db
```

**2. Applicazione**

```
./mvnw spring-boot:run
```

Al primo avvio Flyway crea lo schema e popola il catalogo nutrienti.
Nei log deve comparire `Successfully applied 3 migrations`.

Poi apri **http://localhost:8080**.

## Account

La registrazione è **su invito**: un diario alimentare non ha motivo di
accettare iscrizioni aperte. La migrazione `V003` crea due codici,
`PRIMO-INVITO` e `SECONDO-INVITO` — uno per te, uno per Giovanna.

Nella schermata di accesso scegli "Sono invitato", metti email, password
(minimo 8 caratteri) e il codice. Per aggiungerne altri:

```sql
INSERT INTO invito (codice) VALUES ('TERZO-INVITO');
```

Sessione su cookie `HttpOnly` + `SameSite=Lax`, salvata in Postgres
(Spring Session JDBC): un riavvio dell'applicazione non butta fuori nessuno.
Prima di esporre l'app su HTTPS, togli il commento a `server.servlet.session.cookie.secure`
in `application.yml`.

## Multi-account

Ogni utente vede il proprio diario e i propri alimenti. Il filtro sta
nella clausola `WHERE` di ogni query, non in un controllo successivo.

Gli alimenti con `proprietario IS NULL` sono il **catalogo condiviso**,
visibile a tutti e modificabile da nessuno via API. Per promuovere un
alimento a condiviso:

```sql
UPDATE alimento SET proprietario = NULL WHERE id = 1;
```

## Prova rapida

Dall'interfaccia: registrati, poi "+ Nuovo ingrediente" e copia
l'etichetta. Da riga di comando, con i cookie in un file:

```bash
C='-c /tmp/ck -b /tmp/ck -H Content-Type:application/json'

curl $C -X POST localhost:8080/api/auth/registrazione \
  -d '{"email":"elia@local","password":"password1","invito":"PRIMO-INVITO"}'

# fusilli Rummo, valori di etichetta per 100 g
curl $C -X POST localhost:8080/api/alimenti -d '{
  "nome":"Fusilli Rummo","marca":"Rummo","stato":"SECCO","fonte":"ETICHETTA",
  "per100":{"kcal":356,"grassi":1.6,"saturi":0.3,"carbo":69.5,
            "zuccheri":3.1,"fibre":2.9,"proteine":14.5,"sodio":4}
}'

curl $C "localhost:8080/api/alimenti?q=rumo"    # tollera gli errori di battitura
curl $C localhost:8080/api/giorni/2026-08-14    # crea i tre pasti fissi
```

Nota: le chiamate che modificano vogliono anche l'header `X-XSRF-TOKEN`
con il valore del cookie `XSRF-TOKEN`. Dal browser ci pensa `app.js`.

## Scelte che si discostano dal piano

**JdbcClient invece di JPA.** Il piano prevedeva JPA per il CRUD e SQL a mano
per le aggregazioni. Con il modello chiave-valore dei nutrienti, JPA aggiunge
mappature senza dare nulla in cambio: qui è `JdbcClient` ovunque. Se in futuro
il dominio si arricchisce di relazioni vere, JPA rientra dalla porta principale.

**Frontend senza build step.** Niente npm, niente Vite, niente Node: HTML,
CSS e JavaScript serviti da Spring da `src/main/resources/static`. Per due
utenti è la scelta giusta — un toolchain in meno da mantenere e da rompere.
Se un giorno servisse React con i tipi generati da OpenAPI, il backend è
già pronto e cambia solo cosa sta in `static/`.

**Ricerca.** Combina `ILIKE` e similarità trigram, così funziona sia sui
prefissi sia sugli errori di battitura. `unaccent` è installata ma non ancora
usata negli indici: vedi la nota nel piano sulla sua non-immutabilità.

## Cosa manca (fasi successive)

- Lookup: Open Food Facts, poi modello con ricerca web, cache, limiti per utente
- Codice a barre e foto dell'etichetta
- Ricette con peso finale e porzioni
- Test con Testcontainers: le dipendenze sono nel `pom.xml`, i test no
- Export dei dati (JSON) e cancellazione account

## Struttura

```
src/main/java/it/elia/nutri/
  comune/      sicurezza, sessione, registrazione su invito, errori RFC 9457
  nutriente/   catalogo: ordine, unità, gerarchia, fattori Atwater
  alimento/    CRUD con versioning immutabile + validazione energetica
  diario/      pasti, voci, proporzione e totali
src/main/resources/static/
  index.html   struttura
  app.css      stile
  app.js       logica, chiamate API, disegno
src/main/resources/db/migration/
  V001__schema.sql      schema completo
  V002__nutrienti.sql   catalogo nutrienti
  V003__sessioni.sql    Spring Session + inviti
```

## Note sul modello

**Le versioni.** Modificare un alimento crea una versione nuova in
`alimento_versione`; le voci di diario puntano a una versione precisa. Correggere
le proteine di un alimento non riscrive quindi i pasti già registrati.

**Il validatore.** `ValidatoreNutrienti` applica i vincoli di dominio e il
controllo di coerenza energetica secondo l'allegato XIV del reg. UE 1169/2011.
Sopra il 25% di scarto rifiuta, sopra il 10% accetta segnalando. Ricorda che nella
dichiarazione europea i carboidrati sono al netto delle fibre: con fonti
americane il controllo produce falsi positivi.
