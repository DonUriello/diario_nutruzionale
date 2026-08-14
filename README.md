# diarionutrizionale

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
mvn spring-boot:run
```

Al primo avvio Flyway crea lo schema e popola il catalogo nutrienti.
Nei log deve comparire `Successfully applied 3 migrations`.

Poi apri **http://localhost:8080**.


## Wrapper Maven

Il progetto usa `mvn` di sistema, non `./mvnw`: gli script del wrapper erano
presenti senza la cartella `.mvn/wrapper` che serve a farli funzionare. Se lo
vuoi, si rigenera con:

```
mvn -N wrapper:wrapper
```

e poi il wrapper va aggiunto al repository, cartella `.mvn` compresa. Il
`Dockerfile` non ne ha bisogno: usa il `mvn` dell'immagine Maven.

## Database su Supabase

Il profilo `supabase` sostituisce il Postgres locale. Il codice non cambia:
è sempre Postgres, quindi migrazioni, `jsonb` e `pg_trgm` funzionano uguale.

**1. Prendi la stringa giusta.** Nel cruscotto Supabase, bottone *Connect*.
Ti mostra tre opzioni e per Spring Boot **serve la Session pooler**:

| Cosa | Porta | Va bene? |
|---|---|---|
| Transaction pooler | 6543 | **No.** Niente prepared statement, niente advisory lock |
| Session pooler | 5432 | **Sì.** È questa |
| Connessione diretta | 5432 | Solo se hai IPv6 |

La 6543 è la trappola: è quella che Supabase mette per prima ed è pensata per
funzioni serverless. Con JDBC fallisce due volte — il driver crea prepared
statement da solo, e Flyway prende un advisory lock di sessione per le migrazioni.
Nessuna delle due cose sopravvive al transaction mode.

**2. Configura le variabili.** Copia `.env.esempio` in `.env` e riempilo.
L'utente in session mode ha la forma `postgres.<project-ref>`, non `postgres`.

**3. Avvia.**

```
export $(cat .env | xargs)
mvn spring-boot:run -Dspring-boot.run.profiles=supabase
```

Flyway crea lo schema al primo avvio, come in locale.

### Schema a mano invece che con Flyway

`supabase-schema.sql` è il consolidamento di V001, V002 e V003 in un file
solo, da incollare nel SQL Editor di Supabase. Non è necessario — Flyway fa
lo stesso lavoro all'avvio — ma serve se preferisci vedere e creare lo schema
prima di collegare l'applicazione.

Se lo esegui a mano, aggiungi al profilo supabase:

```yaml
spring:
  flyway:
    baseline-on-migrate: true
    baseline-version: 3
```

Altrimenti Flyway trova le tabelle già lì e si ferma.

### Chiudi le tabelle verso PostgREST

Supabase espone via API REST tutto lo schema `public`, e concede i privilegi
ai ruoli `anon` e `authenticated`. La chiave `anon` è pubblica: senza
intervento, chi la conosce può leggere e scrivere il diario scavalcando il
backend Java. `supabase-schema.sql` chiude tutto in fondo al file; se invece
hai lasciato fare a Flyway, esegui quel blocco a mano una volta:

```sql
ALTER TABLE alimento ENABLE ROW LEVEL SECURITY;   -- e così per ogni tabella
REVOKE ALL ON ALL TABLES    IN SCHEMA public FROM anon, authenticated;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon, authenticated;
```

RLS attiva e nessuna policy significa: niente passa dall'API, mentre
l'applicazione continua a funzionare perché si collega come proprietaria
delle tabelle e la RLS non la tocca.

### Cose da sapere

**Le estensioni ci sono già.** Supabase pre-installa `pg_trgm` e `unaccent`
nello schema `extensions`, che è nel `search_path` del ruolo `postgres`.
Le `CREATE EXTENSION IF NOT EXISTS` di `V001` diventano quindi no-op. Se
l'indice trigram non trovasse `gin_trgm_ops`, togli il commento a `init-sqls`
in `application-supabase.yml`.

**Non serve niente altro di Supabase.** L'app parla Postgres e basta: PostgREST,
Auth, Storage e Realtime restano spenti. Di conseguenza la chiave `anon` non
serve e non va messa da nessuna parte, e RLS non entra in gioco perché nessuno
raggiunge le tabelle se non attraverso questo backend.

**Il piano gratuito si sospende** dopo circa una settimana di inattività, e va
riattivato a mano dal cruscotto. Con un diario usato ogni giorno non capita,
ma se andate in vacanza due settimane lo trovate spento.

**Il backup resta tuo.** 500 MB bastano per anni di diario, ma un piano gratuito
non è un posto dove tenere l'unica copia. Un `pg_dump` periodico verso il tuo
disco vale più di qualunque garanzia:

```
pg_dump "postgresql://postgres.<ref>:<password>@<host>:5432/postgres" \
  --no-owner --no-privileges -Fc -f nutri-$(date +%F).dump
```

**La latenza cambia.** In locale una query è mezzo millisecondo, verso
Francoforte sono decine. Il diario fa poche query per schermata, quindi non
si nota; ma è la ragione per cui il pool è configurato a 5 connessioni e non
a 50, e per cui conviene scegliere la regione europea più vicina.


## Esercizio su Render

L'errore `failed to read dockerfile` significa che Render costruisce con
Docker ma non trova il `Dockerfile`. Ora c'è, nella radice del progetto.

**Se il repository ha il progetto in una sottocartella**, indicala in Render:
Settings → Root Directory. Il `Dockerfile` deve stare lì dentro, non sopra.

### Configurazione

| Voce | Valore |
|---|---|
| Runtime | Docker |
| Region | Frankfurt |
| Health Check Path | `/actuator/health` |

Variabili d'ambiente (Settings → Environment):

```
SPRING_PROFILES_ACTIVE = supabase
SUPABASE_URL           = jdbc:postgresql://aws-1-eu-west-3.pooler.supabase.com:5432/postgres?sslmode=require
SUPABASE_USER          = postgres.<project-ref>
SUPABASE_PASSWORD      = <la tua>
```

C'è anche `render.yaml` se preferisci il blueprint. Le credenziali restano
comunque da inserire a mano: `sync: false` serve proprio a tenerle fuori dal
repository.

### Cose che cambiano rispetto al locale

**La porta la assegna Render.** `application.yml` legge `${PORT:8080}`:
in locale resta 8080, in esercizio ascolta dove gli dicono. Un'applicazione
che ignora `PORT` viene dichiarata morta dopo qualche minuto di health check
falliti, con un messaggio che parla di porte e non di configurazione.

**Il cookie diventa Secure.** Il profilo `supabase` attiva
`server.servlet.session.cookie.secure` e `forward-headers-strategy: native`.
Il secondo serve perché Render termina TLS davanti: senza, Spring crede di
essere in HTTP e sbaglia gli URL assoluti e i redirect.

**Il piano gratuito si addormenta** dopo un quarto d'ora di inattività, e la
prima richiesta dopo il risveglio paga il tempo di avvio della JVM: parliamo
di quasi un minuto. Sommato al fatto che anche Supabase si sospende dopo una
settimana, l'apertura del lunedì mattina può essere lenta.

**I test non vengono compilati.** Il `Dockerfile` usa `-Dmaven.test.skip=true`
e non `-DskipTests`: il secondo salta l'esecuzione ma compila comunque i
sorgenti di test, quindi un file di test con una dipendenza mancante fa
fallire il build di produzione. Se Spring Initializr ti ha lasciato un
`TestcontainersConfiguration.java` e non hai le relative dipendenze,
cancellalo o aggiungile (vedi la sezione Test).

**Il build è più lento la prima volta.** Il `Dockerfile` è in due stadi:
compila con Maven, poi copia il JAR su un'immagine con la sola JRE. La cache
sul `pom.xml` fa sì che le ricompilazioni successive non riscarichino le
dipendenze.

Volendo si può suddividere il JAR in strati, così un rebuild dopo una modifica
al codice ricostruisce qualche centinaio di kilobyte invece dell'immagine
intera. Non è nel `Dockerfile` perché la sintassi di `jarmode` è cambiata fra
le versioni di Spring Boot ed è facile sbagliarla; se la vuoi, parti dalla
documentazione corrente di Spring Boot sulle immagini Docker, sezione
"Layering Docker Images". Per un progetto che ridistribuisci ogni tanto il
guadagno è modesto.

### Se preferisci senza Docker

Render sa costruire progetti Java anche da solo, e per un'applicazione così
è del tutto ragionevole. Runtime Java, poi:

```
Build Command:  mvn clean package -DskipTests
Start Command:  java -jar target/diarionutrizionale-0.0.1-SNAPSHOT.jar
```

Meno controllo sull'immagine, ma un file in meno da mantenere.

## Ricerca prodotti (Open Food Facts)

Nella scheda del nuovo ingrediente c'è un campo di ricerca che interroga
Open Food Facts: banca dati collaborativa di prodotti alimentari, gratuita,
senza chiave API.

Scrivi il nome (`fusilli rummo`) e scegli fra i risultati, oppure digita il
**codice a barre** — se il campo contiene solo 8, 12 o 13 cifre viene
riconosciuto come EAN e il prodotto è applicato subito, senza lista.

I valori atterrano nel form con un avviso e **non vengono salvati**: sei tu a
premere Salva. Se la scheda del produttore è incompleta l'avviso diventa
giallo e ti dice quanti campi restano da compilare a mano.

### Cose che il client normalizza

- **kJ contro kcal.** Il campo `energy` di Open Food Facts è in kilojoule;
  le calorie stanno in `energy-kcal_100g`. Leggere quello sbagliato dà valori
  quadruplicati.
- **Minerali in grammi.** Calcio e ferro arrivano in grammi, non milligrammi:
  il ferro è `0.0014`, non `1.4`. Senza conversione lo scarto è di tre ordini
  di grandezza.
- **Sale invece di sodio.** Se manca il sodio si ricava dal sale, fattore 400.
- **Codice inesistente.** Restituiscono 200 con `status: 0`, quindi il codice
  HTTP non basta a capire se il prodotto c'è.

### Configurazione

In `application.yml`:

```yaml
off:
  base-url: https://world.openfoodfacts.org
  user-agent: DiarioNutrizionale/0.1 (uso personale)
```

Lo `User-Agent` è una loro condizione d'uso: chiedono di identificare
l'applicazione, nella forma `NomeApp/Versione (contatto)`. Mettici un
riferimento vero se un giorno l'uso diventa intenso.

I dati sono sotto licenza ODbL. Per uso personale non comporta obblighi;
se un giorno ridistribuisci il database, sì.

### Cosa manca ancora

- **Cache.** Ogni ricerca è una chiamata. Open Food Facts è gratuito e non
  pone limiti per un uso ragionevole, quindi non è urgente; una tabella
  `lookup_cache` sul nome normalizzato lo diventerebbe con più utenti.
- **Scanner del codice a barre dalla fotocamera.** Su Android Chrome basta
  `BarcodeDetector`, nativo e senza dipendenze; su iPhone non esiste e serve
  una libreria JavaScript che decodifica dal flusso video. Per ora il codice
  si digita.
- **Il modello come ultima risorsa**, per gli alimenti generici che su Open
  Food Facts non ci sono. Per quelli c'è già il catalogo CREA.

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

**Niente Jackson nei repository.** Spring Boot 4 adotta Jackson 3, che ha
rinominato i package da `com.fasterxml.jackson` a `tools.jackson`. Invece di
inseguire i nomi nuovi, la serializzazione del `jsonb` la fa Postgres: in
scrittura la stringa JSON si compone a mano (chiavi e valori sono vincolati
dal catalogo, non c'è nulla da sfuggire), in lettura `jsonb_each_text` esplode
la mappa e la proporzione sui grammi è già nella query. Jackson resta dove
serve davvero, cioè nel livello web, gestito da Spring.

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
src/main/java/pj/eu/diarionutrizionale/
  comune/      sicurezza, sessione, registrazione su invito, errori RFC 9457
  nutriente/   catalogo: ordine, unità, gerarchia, fattori Atwater
  alimento/    CRUD con versioning immutabile + validazione energetica
  diario/      pasti, voci, proporzione e totali
Dockerfile                   immagine per Render (3 stadi, con layering)
render.yaml                  blueprint Render (facoltativo)
supabase-schema.sql          schema consolidato per il SQL Editor
src/main/resources/
  application.yml            profilo locale (Docker)
  application-supabase.yml   profilo Supabase
src/main/resources/static/
  index.html   struttura
  app.css      stile
  app.js       logica, chiamate API, disegno
src/main/resources/db/migration/
  V001__schema.sql      schema completo
  V002__nutrienti.sql   catalogo nutrienti
  V003__sessioni.sql    Spring Session + inviti
```

## Test

Nel progetto non ce ne sono ancora. Quando li scrivi, servono Testcontainers
con un Postgres vero: su questo schema i test con H2 sono peggio che inutili,
perché mancano `jsonb`, `pg_trgm` e `jsonb_each_text`, cioè esattamente le
query che contano.

Dalla versione 2.x il BOM è obbligatorio e gli artefatti dei moduli hanno il
prefisso `testcontainers-` (prima erano `org.testcontainers:postgresql`).
Aggiungi al `pom.xml`:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-bom</artifactId>
            <version>2.0.5</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

e fra le dipendenze:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

Controlla la versione corrente del BOM su Maven Central prima di fissarla.

Le tre famiglie di test che valgono più delle altre: le aggregazioni con
numeri calcolati a penna, l'isolamento fra utenti (A non deve vedere i dati
di B, per ogni endpoint), e il validatore energetico sui casi limite come
l'olio, dove le fibre sono zero e i grassi quasi cento.

## Note sul modello

**Le versioni.** Modificare un alimento crea una versione nuova in
`alimento_versione`; le voci di diario puntano a una versione precisa. Correggere
le proteine di un alimento non riscrive quindi i pasti già registrati.

**Il validatore.** `ValidatoreNutrienti` applica i vincoli di dominio e il
controllo di coerenza energetica secondo l'allegato XIV del reg. UE 1169/2011.
Sopra il 25% di scarto rifiuta, sopra il 10% accetta segnalando. Ricorda che nella
dichiarazione europea i carboidrati sono al netto delle fibre: con fonti
americane il controllo produce falsi positivi.
