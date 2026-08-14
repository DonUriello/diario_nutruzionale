-- Schema iniziale del diario nutrizionale.
-- Riferimento: piano di implementazione, sezione 3.

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- ---------------------------------------------------------------
-- Catalogo dei nutrienti: è dato, non schema.
-- Contiene ordine di stampa, unità, gerarchia dei "di cui"
-- e fattore energetico per la validazione Atwater.
-- ---------------------------------------------------------------
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

-- ---------------------------------------------------------------
-- Alimenti. proprietario NULL = catalogo condiviso.
-- ---------------------------------------------------------------
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

-- ---------------------------------------------------------------
-- Diario
-- ---------------------------------------------------------------
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

-- ---------------------------------------------------------------
-- Indici
-- ---------------------------------------------------------------
CREATE INDEX ix_alimento_nome_trgm  ON alimento USING gin (nome gin_trgm_ops);
CREATE INDEX ix_alimento_proprietario ON alimento (proprietario);
CREATE UNIQUE INDEX ux_alimento_ean ON alimento (ean) WHERE ean IS NOT NULL;
CREATE INDEX ix_pasto_utente_giorno ON pasto (utente_id, giorno);
CREATE INDEX ix_voce_pasto          ON voce_pasto (pasto_id);
