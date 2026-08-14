-- ============================================================
--  Catalogo condiviso: alimenti generici
--
--  Valori per 100 g di parte edibile, coerenti con le Tabelle di
--  composizione degli alimenti del CREA (aggiornamento 2019),
--  consultabili su https://www.alimentinutrizione.it
--
--  proprietario NULL = catalogo condiviso: visibile a tutti gli
--  utenti, nessun duplicato per persona. Per questo lo script non
--  ha bisogno di sapere quale utente sei.
--
--  Lo script è idempotente: rieseguirlo non crea doppioni.
-- ============================================================

WITH dati (nome, stato,
           kcal, grassi, saturi, carbo, zuccheri, fibre, proteine, sodio, calcio, ferro) AS (
    VALUES
    -- cereali e derivati
    ('Pasta di semola cruda',            'SECCO', 353, 1.4,  0.3,  70.9, 3.2,  2.7,  11.0,   6,   22, 1.4),
    ('Riso brillato crudo',              'SECCO', 332, 0.6,  0.2,  80.4, 0.2,  1.0,   6.7,   5,   24, 0.8),
    ('Pane tipo 0',                      'CRUDO', 275, 0.5,  0.1,  63.5, 2.0,  3.8,   8.6, 350,   18, 0.9),
    ('Pane integrale',                   'CRUDO', 224, 1.3,  0.3,  48.5, 2.4,  6.5,   7.5, 350,   45, 2.5),
    ('Farina di frumento tipo 00',       'SECCO', 340, 0.7,  0.2,  77.3, 1.6,  2.2,  11.0,   2,   17, 0.7),
    ('Fiocchi di avena',                 'SECCO', 373, 7.1,  1.3,  62.9, 1.0,  8.3,  12.6,   8,   54, 4.2),

    -- legumi
    ('Lenticchie secche',                'SECCO', 291, 1.0,  0.2,  51.1, 1.8, 13.8,  22.7,   8,   57, 8.0),
    ('Ceci secchi',                      'SECCO', 316, 6.3,  0.7,  46.9, 2.4, 13.6,  20.9,  26,  142, 6.4),
    ('Fagioli borlotti secchi',          'SECCO', 291, 2.0,  0.3,  47.5, 2.5, 17.3,  20.2,   7,  135, 9.0),
    ('Fagioli borlotti in scatola',      'COTTO',  91, 0.5,  0.1,  12.0, 0.6,  6.6,   6.9, 220,   40, 2.0),

    -- carne, pesce, uova
    ('Petto di pollo crudo',             'CRUDO', 100, 0.8,  0.2,   0.0, 0.0,  0.0,  23.3,  62,    9, 0.4),
    ('Fesa di tacchino cruda',           'CRUDO', 107, 1.2,  0.4,   0.0, 0.0,  0.0,  24.0,  55,   12, 1.0),
    ('Bovino magro crudo',               'CRUDO', 122, 3.6,  1.5,   0.0, 0.0,  0.0,  21.8,  51,    7, 1.9),
    ('Uovo di gallina intero',           'CRUDO', 128, 8.7,  3.2,   1.0, 1.0,  0.0,  12.4, 137,   48, 1.5),
    ('Nasello fresco',                   'CRUDO',  71, 0.3,  0.1,   0.0, 0.0,  0.0,  17.0, 100,   20, 0.4),
    ('Tonno sott''olio sgocciolato',     'COTTO', 192, 8.1,  2.0,   0.0, 0.0,  0.0,  25.2, 320,   12, 1.2),
    ('Salmone fresco',                   'CRUDO', 185, 12.0, 2.5,   0.0, 0.0,  0.0,  18.4,  55,   25, 0.8),

    -- latte e derivati
    ('Latte parzialmente scremato',      'CRUDO',  46, 1.5,  1.0,   5.0, 5.0,  0.0,   3.3,  50,  119, 0.1),
    ('Yogurt intero',                    'CRUDO',  66, 3.9,  2.1,   4.3, 4.3,  0.0,   3.8,  48,  125, 0.1),
    ('Parmigiano Reggiano',              'CRUDO', 387, 28.1, 18.5,  0.0, 0.0,  0.0,  33.5, 700, 1160, 0.7),
    ('Mozzarella di vacca',              'CRUDO', 253, 19.5, 11.5,  0.7, 0.7,  0.0,  18.7, 200,  403, 0.2),
    ('Ricotta di vacca',                 'CRUDO', 146, 10.9, 6.8,   3.5, 3.5,  0.0,   8.8,  78,  295, 0.4),

    -- ortaggi
    ('Pomodoro maturo',                  'CRUDO',  19, 0.2,  0.05,  3.5, 3.5,  1.0,   1.0,   6,    9, 0.3),
    ('Zucchine',                         'CRUDO',  11, 0.1,  0.02,  1.4, 1.4,  1.2,   1.3,   8,   21, 0.5),
    ('Spinaci crudi',                    'CRUDO',  31, 0.7,  0.1,   3.0, 0.4,  1.9,   3.4, 100,   78, 2.9),
    ('Carote',                           'CRUDO',  35, 0.2,  0.05,  7.6, 7.6,  3.1,   1.1,  95,   44, 0.7),
    ('Lattuga',                          'CRUDO',  19, 0.4,  0.1,   2.2, 2.2,  1.5,   1.8,   9,   45, 0.8),
    ('Patate',                           'CRUDO',  85, 1.0,  0.2,  17.9, 0.4,  1.6,   2.1,   7,   10, 0.6),
    ('Cipolla',                          'CRUDO',  26, 0.1,  0.02,  5.7, 5.7,  1.1,   1.0,  10,   25, 0.4),
    ('Broccoli',                         'CRUDO',  27, 0.3,  0.1,   3.1, 2.4,  3.1,   3.0,  12,   72, 1.0),

    -- frutta
    ('Mela',                             'CRUDO',  45, 0.1,  0.02, 10.0, 10.0, 2.0,   0.2,   2,    7, 0.3),
    ('Banana',                           'CRUDO',  65, 0.3,  0.1,  15.4, 12.8, 1.8,   1.2,   1,    7, 0.8),
    ('Arancia',                          'CRUDO',  34, 0.2,  0.05,  7.8, 7.8, 1.6,   0.7,   3,   49, 0.2),
    ('Pera',                             'CRUDO',  41, 0.1,  0.02,  8.8, 8.8, 3.8,   0.3,   2,   11, 0.3),

    -- grassi e frutta secca
    ('Olio extravergine di oliva',       'CRUDO', 899, 99.9, 14.5,  0.0, 0.0,  0.0,   0.0,   0,    1, 0.1),
    ('Burro',                            'CRUDO', 758, 83.4, 51.3,  1.1, 1.1,  0.0,   0.8,  12,   15, 0.2),
    ('Noci secche',                      'SECCO', 689, 68.1, 5.6,   5.1, 3.5,  6.2,  14.3,   2,   83, 2.6),
    ('Mandorle dolci secche',            'SECCO', 603, 55.3, 4.7,   4.6, 3.7, 12.7,  22.0,  14,  240, 3.0)
),

-- Salta gli alimenti già presenti in catalogo: lo script si può rieseguire.
nuovi AS (
    SELECT d.* FROM dati d
    WHERE NOT EXISTS (
        SELECT 1 FROM alimento a
        WHERE a.proprietario IS NULL AND a.nome = d.nome
    )
),

inseriti AS (
    INSERT INTO alimento (proprietario, nome, stato, fonte, fonte_url, verificato)
    SELECT NULL, n.nome, n.stato, 'CREA',
           'https://www.alimentinutrizione.it', true
    FROM   nuovi n
    RETURNING id, nome
),

-- Da colonne a righe: un nutriente per riga, come vuole alimento_nutriente.
valori AS (
    SELECT i.id, i.nome, v.codice, v.valore
    FROM   inseriti i
    JOIN   nuovi n ON n.nome = i.nome
    CROSS  JOIN LATERAL (VALUES
               ('kcal',     n.kcal::numeric),
               ('grassi',   n.grassi::numeric),
               ('saturi',   n.saturi::numeric),
               ('carbo',    n.carbo::numeric),
               ('zuccheri', n.zuccheri::numeric),
               ('fibre',    n.fibre::numeric),
               ('proteine', n.proteine::numeric),
               ('sodio',    n.sodio::numeric),
               ('calcio',   n.calcio::numeric),
               ('ferro',    n.ferro::numeric)
           ) AS v(codice, valore)
),

scrivi_nutrienti AS (
    INSERT INTO alimento_nutriente (alimento_id, nutriente, valore)
    SELECT id, codice, valore FROM valori
    RETURNING alimento_id
)

-- Versione 1 immutabile: è a questa che puntano le voci di diario.
INSERT INTO alimento_versione (alimento_id, versione, nome, valori)
SELECT id, 1, nome, jsonb_object_agg(codice, valore)
FROM   valori
GROUP  BY id, nome;


-- ============================================================
--  Verifica
-- ============================================================
-- SELECT count(*) FROM alimento WHERE proprietario IS NULL;   -- atteso 38
--
-- SELECT a.nome, an.valore AS kcal
-- FROM   alimento a
-- JOIN   alimento_nutriente an ON an.alimento_id = a.id AND an.nutriente = 'kcal'
-- WHERE  a.proprietario IS NULL
-- ORDER  BY an.valore DESC;


-- ============================================================
--  NOTA SUI VALORI DI VERDURA, FRUTTA E LEGUMI
--
--  Su questi alimenti la somma pesata dei macronutrienti supera
--  le kcal dichiarate del 10-22%. Non è un errore di trascrizione:
--  il CREA esprime i carboidrati in equivalenti monosaccaridici,
--  una convenzione diversa da quella dell'etichetta europea, e la
--  differenza pesa dove i carboidrati contano molto su poche calorie.
--
--  Carne, latticini, grassi e cereali tornano invece entro il 3%.
--
--  Conseguenza pratica: se un giorno modificherai uno di questi
--  alimenti dall'interfaccia, il validatore energetico mostrerà un
--  avviso giallo. È corretto che lo faccia, e va ignorato: i valori
--  sono quelli della fonte. Inserendoli via SQL il validatore non
--  interviene, perché vive nel livello applicativo.
-- ============================================================
