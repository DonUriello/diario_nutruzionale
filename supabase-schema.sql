-- ============================================================
--  nutri — schema completo per Supabase
--  Consolidamento di V001, V002 e V003.
--
--  Da incollare nel SQL Editor di Supabase ed eseguire una volta.
--  Se preferisci lasciar fare a Flyway, non serve: avvia con il
--  profilo supabase e le migrazioni girano da sole. Vedi la nota
--  in fondo se esegui questo file a mano.
-- ============================================================


-- ------------------------------------------------------------
--  Estensioni
--  Su Supabase pg_trgm e unaccent sono già installate nello schema
--  "extensions", che sta nel search_path del ruolo postgres:
--  queste due righe sono quindi no-op innocue.
-- ------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS pg_trgm  WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA extensions;


-- ------------------------------------------------------------
--  Catalogo dei nutrienti: è dato, non schema.
--  Contiene ordine di stampa, unità, gerarchia dei "di cui"
--  e fattore energetico per la validazione Atwater.
-- ------------------------------------------------------------
CREATE TABLE nutriente (
    codice        text        PRIMARY KEY,
    nome          text        NOT NULL,
    unita         text        NOT NULL,
    padre         text        REFERENCES nutriente(codice),
    ordine        smallint    NOT NULL UNIQUE,
    obbligatorio  boolean     NOT NULL DEFAULT false,
    kcal_per_g    numeric(4,2),
    CONSTRAINT ck_nutriente_unita CHECK (unita IN ('kcal','g','mg','ug'))
);

CREATE TABLE utente (
    id             bigserial   PRIMARY KEY,
    email          text        NOT NULL,
    password_hash  text        NOT NULL,
    fuso           text        NOT NULL DEFAULT 'Europe/Rome',
    inizio_giorno  smallint    NOT NULL DEFAULT 0,
    creato_il      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_utente_inizio CHECK (inizio_giorno BETWEEN 0 AND 12)
);
CREATE UNIQUE INDEX ux_utente_email ON utente (lower(email));


-- ------------------------------------------------------------
--  Alimenti.  proprietario NULL = catalogo condiviso.
-- ------------------------------------------------------------
CREATE TABLE alimento (
    id             bigserial   PRIMARY KEY,
    proprietario   bigint      REFERENCES utente(id) ON DELETE CASCADE,
    nome           text        NOT NULL,
    marca          text,
    ean            text,
    stato          text        NOT NULL DEFAULT 'CRUDO',
    fonte          text        NOT NULL DEFAULT 'MANUALE',
    fonte_url      text,
    verificato     boolean     NOT NULL DEFAULT false,
    versione       int         NOT NULL DEFAULT 1,
    archiviato     boolean     NOT NULL DEFAULT false,
    creato_il      timestamptz NOT NULL DEFAULT now(),
    aggiornato_il  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_alimento_stato CHECK (stato IN ('CRUDO','COTTO','SECCO')),
    CONSTRAINT ck_alimento_fonte CHECK (fonte IN
        ('MANUALE','ETICHETTA','OFF','CREA','MODELLO'))
);

-- Valori correnti, modificabili, un nutriente per riga.
CREATE TABLE alimento_nutriente (
    alimento_id  bigint        NOT NULL REFERENCES alimento(id) ON DELETE CASCADE,
    nutriente    text          NOT NULL REFERENCES nutriente(codice),
    valore       numeric(10,3) NOT NULL CHECK (valore >= 0),
    PRIMARY KEY (alimento_id, nutriente)
);

-- Versioni immutabili: scritte una volta, mai modificate.
-- La voce di diario punta qui, così correggere un alimento
-- non riscrive il passato.
CREATE TABLE alimento_versione (
    alimento_id  bigint      NOT NULL REFERENCES alimento(id) ON DELETE CASCADE,
    versione     int         NOT NULL,
    nome         text        NOT NULL,
    valori       jsonb       NOT NULL,
    valido_dal   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (alimento_id, versione)
);


-- ------------------------------------------------------------
--  Diario
-- ------------------------------------------------------------
CREATE TABLE pasto (
    id         bigserial PRIMARY KEY,
    utente_id  bigint    NOT NULL REFERENCES utente(id) ON DELETE CASCADE,
    giorno     date      NOT NULL,
    tipo       text      NOT NULL,
    nome       text,
    ordine     smallint  NOT NULL DEFAULT 0,
    CONSTRAINT ck_pasto_tipo CHECK (tipo IN
        ('COLAZIONE','PRANZO','CENA','SPUNTINO')),
    CONSTRAINT ux_pasto UNIQUE (utente_id, giorno, tipo, ordine)
);

CREATE TABLE voce_pasto (
    id           bigserial    PRIMARY KEY,
    pasto_id     bigint       NOT NULL REFERENCES pasto(id) ON DELETE CASCADE,
    alimento_id  bigint       NOT NULL,
    versione     int          NOT NULL,
    grammi       numeric(8,2) NOT NULL CHECK (grammi > 0),
    aggiunto_il  timestamptz  NOT NULL DEFAULT now(),
    FOREIGN KEY (alimento_id, versione)
        REFERENCES alimento_versione(alimento_id, versione)
);


-- ------------------------------------------------------------
--  Indici
-- ------------------------------------------------------------
CREATE INDEX ix_alimento_nome_trgm    ON alimento USING gin (nome extensions.gin_trgm_ops);
CREATE INDEX ix_alimento_proprietario ON alimento (proprietario);
CREATE UNIQUE INDEX ux_alimento_ean   ON alimento (ean) WHERE ean IS NOT NULL;
CREATE INDEX ix_pasto_utente_giorno   ON pasto (utente_id, giorno);
CREATE INDEX ix_voce_pasto            ON voce_pasto (pasto_id);


-- ------------------------------------------------------------
--  Sessioni (Spring Session JDBC)
-- ------------------------------------------------------------
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID            char(36)     NOT NULL,
    SESSION_ID            char(36)     NOT NULL,
    CREATION_TIME         bigint       NOT NULL,
    LAST_ACCESS_TIME      bigint       NOT NULL,
    MAX_INACTIVE_INTERVAL int          NOT NULL,
    EXPIRY_TIME           bigint       NOT NULL,
    PRINCIPAL_NAME        varchar(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID char(36)     NOT NULL,
    ATTRIBUTE_NAME     varchar(200) NOT NULL,
    ATTRIBUTE_BYTES    bytea        NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK
        PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK
        FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);


-- ------------------------------------------------------------
--  Inviti: la registrazione è chiusa.
-- ------------------------------------------------------------
CREATE TABLE invito (
    codice     text        PRIMARY KEY,
    creato_il  timestamptz NOT NULL DEFAULT now(),
    usato_da   bigint      REFERENCES utente(id) ON DELETE SET NULL,
    usato_il   timestamptz
);


-- ============================================================
--  Dati di riferimento
-- ============================================================

-- Ordine e nomi secondo la dichiarazione nutrizionale del reg. UE 1169/2011.
-- kcal_per_g: fattori dell'allegato XIV, usati per il controllo Atwater.
-- obbligatorio: true se il regolamento ne impone la dichiarazione.
INSERT INTO nutriente (codice, nome, unita, padre, ordine, obbligatorio, kcal_per_g) VALUES
    ('kcal',     'Energia',                     'kcal', NULL,     10, true,  NULL),
    ('grassi',   'Grassi',                      'g',    NULL,     20, true,  9.00),
    ('saturi',   'di cui acidi grassi saturi',  'g',    'grassi', 30, true,  NULL),
    ('carbo',    'Carboidrati',                 'g',    NULL,     40, true,  4.00),
    ('zuccheri', 'di cui zuccheri',             'g',    'carbo',  50, true,  NULL),
    ('fibre',    'Fibre',                       'g',    NULL,     60, false, 2.00),
    ('proteine', 'Proteine',                    'g',    NULL,     70, true,  4.00),
    ('sodio',    'Sodio',                       'mg',   NULL,     80, true,  NULL),
    ('calcio',   'Calcio',                      'mg',   NULL,     90, false, NULL),
    ('ferro',    'Ferro',                       'mg',   NULL,    100, false, NULL);

INSERT INTO invito (codice) VALUES ('PRIMO-INVITO'), ('SECONDO-INVITO');


-- ============================================================
--  Chiusura verso l'API di Supabase
--
--  IMPORTANTE, e non è un dettaglio formale.
--
--  Supabase espone automaticamente via PostgREST tutte le tabelle
--  dello schema "public", e concede per impostazione predefinita i
--  privilegi ai ruoli anon e authenticated. La chiave anon è pubblica
--  per progetto: senza queste righe, chiunque la conosca potrebbe
--  leggere e scrivere il diario passando dall'API REST, scavalcando
--  del tutto il backend Java e la sua autenticazione.
--
--  Qui l'API di Supabase non serve: l'unico client è l'applicazione
--  Spring, che si collega come proprietario delle tabelle e non è
--  soggetta alla RLS. Attivare la RLS senza definire alcuna policy
--  significa quindi: tutto negato via API, tutto normale via backend.
-- ============================================================
ALTER TABLE nutriente                 ENABLE ROW LEVEL SECURITY;
ALTER TABLE utente                    ENABLE ROW LEVEL SECURITY;
ALTER TABLE alimento                  ENABLE ROW LEVEL SECURITY;
ALTER TABLE alimento_nutriente        ENABLE ROW LEVEL SECURITY;
ALTER TABLE alimento_versione         ENABLE ROW LEVEL SECURITY;
ALTER TABLE pasto                     ENABLE ROW LEVEL SECURITY;
ALTER TABLE voce_pasto                ENABLE ROW LEVEL SECURITY;
ALTER TABLE invito                    ENABLE ROW LEVEL SECURITY;
ALTER TABLE SPRING_SESSION            ENABLE ROW LEVEL SECURITY;
ALTER TABLE SPRING_SESSION_ATTRIBUTES ENABLE ROW LEVEL SECURITY;

-- Cintura oltre alle bretelle: togliamo anche i privilegi.
REVOKE ALL ON ALL TABLES    IN SCHEMA public FROM anon, authenticated;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon, authenticated;


-- ============================================================
--  SE HAI ESEGUITO QUESTO FILE A MANO
--
--  Flyway al primo avvio troverebbe le tabelle già presenti e si
--  fermerebbe, perché non esiste la sua flyway_schema_history.
--  Aggiungi al profilo supabase, in application-supabase.yml:
--
--      spring:
--        flyway:
--          baseline-on-migrate: true
--          baseline-version: 3
--
--  Così Flyway considera V001, V002 e V003 già applicate e da lì in
--  poi esegue soltanto le migrazioni nuove (V004 e successive).
--
--  Se invece lasci fare tutto a Flyway, non eseguire questo file e
--  non toccare la configurazione.
-- ============================================================
