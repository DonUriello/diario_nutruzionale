-- ============================================================
--  Rimozione degli alimenti inseriti da catalogo-crea.sql
--
--  Il selettore è: catalogo condiviso (proprietario IS NULL) con
--  fonte 'CREA'. Non tocca gli alimenti tuoi o di Giovanna, né
--  quelli arrivati da Open Food Facts.
--
--  Esegui i passi in ordine e leggi il risultato del primo prima
--  di lanciare gli altri.
-- ============================================================


-- ------------------------------------------------------------
--  PASSO 1 — Cosa sta per succedere
--
--  voce_pasto punta ad alimento_versione senza ON DELETE CASCADE:
--  è la protezione che impedisce a un pasto già registrato di
--  perdere i suoi valori. Quindi un alimento già usato nel diario
--  NON è cancellabile, e va archiviato invece che rimosso.
-- ------------------------------------------------------------
SELECT a.id,
       a.nome,
       count(vp.id) AS usi_nel_diario,
       CASE WHEN count(vp.id) = 0
            THEN 'cancellabile'
            ELSE 'in uso: verrà archiviato' END AS esito
FROM   alimento a
LEFT   JOIN voce_pasto vp ON vp.alimento_id = a.id
WHERE  a.proprietario IS NULL
  AND  a.fonte = 'CREA'
GROUP  BY a.id, a.nome
ORDER  BY count(vp.id) DESC, a.nome;


-- ------------------------------------------------------------
--  PASSO 2 — Cancella quelli mai usati
--
--  alimento_nutriente e alimento_versione hanno ON DELETE CASCADE
--  su alimento: spariscono da sole, non serve cancellarle a mano.
-- ------------------------------------------------------------
DELETE FROM alimento a
WHERE  a.proprietario IS NULL
  AND  a.fonte = 'CREA'
  AND  NOT EXISTS (
       SELECT 1 FROM voce_pasto vp WHERE vp.alimento_id = a.id
  );


-- ------------------------------------------------------------
--  PASSO 3 — Archivia quelli usati nel diario
--
--  Spariscono dalla dispensa e dalla ricerca, ma le voci di pasto
--  già registrate continuano a mostrare nome e valori corretti,
--  perché puntano alla versione immutabile.
--
--  Se il PASSO 1 ha detto che nessuno è in uso, questo non fa nulla.
-- ------------------------------------------------------------
UPDATE alimento
SET    archiviato = true
WHERE  proprietario IS NULL
  AND  fonte = 'CREA'
  AND  NOT archiviato;


-- ------------------------------------------------------------
--  PASSO 4 — Verifica
-- ------------------------------------------------------------
SELECT count(*) FILTER (WHERE NOT archiviato) AS ancora_visibili,
       count(*) FILTER (WHERE archiviato)     AS archiviati,
       count(*)                               AS totale_rimasti
FROM   alimento
WHERE  proprietario IS NULL AND fonte = 'CREA';
-- Atteso: ancora_visibili = 0


-- ============================================================
--  SE VUOI CANCELLARE TUTTO DAVVERO, DIARIO COMPRESO
--
--  Distruttivo: perdi le voci di pasto che usano questi alimenti,
--  e con esse i totali dei giorni interessati. Da usare solo se
--  quelle voci erano prove.
-- ============================================================
-- BEGIN;
--
-- DELETE FROM voce_pasto vp
-- USING  alimento a
-- WHERE  vp.alimento_id = a.id
--   AND  a.proprietario IS NULL
--   AND  a.fonte = 'CREA';
--
-- DELETE FROM alimento
-- WHERE  proprietario IS NULL AND fonte = 'CREA';
--
-- -- Controlla il risultato, poi COMMIT; oppure ROLLBACK; per annullare.
-- COMMIT;
