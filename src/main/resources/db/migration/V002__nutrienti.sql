-- Catalogo nutrienti. Dato di riferimento: si aggiunge per migrazione,
-- così ogni nuovo nutriente lascia traccia versionata.
--
-- L'ordine segue la dichiarazione nutrizionale del reg. UE 1169/2011.
-- kcal_per_g: fattori dell'allegato XIV, usati per il controllo Atwater.
-- obbligatorio: true se il regolamento ne impone la dichiarazione.

INSERT INTO nutriente (codice, nome, unita, padre, ordine, obbligatorio, kcal_per_g) VALUES
    ('kcal',     'Energia',                          'kcal', NULL,     10, true,  NULL),
    ('grassi',   'Grassi',                           'g',    NULL,     20, true,  9.00),
    ('saturi',   'di cui acidi grassi saturi',       'g',    'grassi', 30, true,  NULL),
    ('carbo',    'Carboidrati',                      'g',    NULL,     40, true,  4.00),
    ('zuccheri', 'di cui zuccheri',                  'g',    'carbo',  50, true,  NULL),
    ('fibre',    'Fibre',                            'g',    NULL,     60, false, 2.00),
    ('proteine', 'Proteine',                         'g',    NULL,     70, true,  4.00),
    ('sodio',    'Sodio',                            'mg',   NULL,     80, true,  NULL),
    ('calcio',   'Calcio',                           'mg',   NULL,     90, false, NULL),
    ('ferro',    'Ferro',                            'mg',   NULL,    100, false, NULL);
