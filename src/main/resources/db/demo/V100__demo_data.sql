-- Ukázková data (profil demo). Vše je smyšlené. Časy se počítají od okamžiku prvního spuštění:
-- 8 týdnů historie a 2 týdny dopředu, aby šlo předvést diář, výkaz, rušení i přeobjednání.

-- Pacienti z V2 dostanou křestní jméno odpovídající rodu příjmení.
UPDATE patient
SET first_name = CASE WHEN last_name LIKE '%á'
        THEN (ARRAY['Jana', 'Eva', 'Marie', 'Hana', 'Lucie', 'Petra', 'Alena', 'Věra'])[1 + id % 8]
        ELSE (ARRAY['Jan', 'Petr', 'Karel', 'Tomáš', 'Josef', 'Martin', 'Jiří', 'Pavel'])[1 + id % 8] END
WHERE phone LIKE '777000%';

INSERT INTO patient (first_name, last_name, birth_year, phone, insurance_id, insurer_id)
SELECT v.first_name, v.last_name, v.birth_year, v.phone, v.insurance_id, i.id
FROM (VALUES
    ('Jiřina', 'Krátká', 1952, '603111201', '8100000001', '111'),
    ('Zdeněk', 'Pokorný', 1948, '603111202', '8100000002', '111'),
    ('Ludmila', 'Veselá', 1957, '603111203', '8100000003', '211'),
    ('Miroslav', 'Malý', 1963, '603111204', '8100000004', '201'),
    ('Kateřina', 'Horáková', 1984, '603111205', '8100000005', '205'),
    ('Ondřej', 'Marek', 1991, '603111206', '8100000006', '111'),
    ('Tereza', 'Pospíšilová', 1996, '603111207', '8100000007', '207'),
    ('Vladimír', 'Hájek', 1955, '603111208', '8100000008', '111'),
    ('Jaroslava', 'Jelínková', 1950, '603111209', '8100000009', '209'),
    ('Michal', 'Růžička', 1979, '603111210', '8100000010', '211'),
    ('Simona', 'Benešová', 1988, '603111211', '8100000011', '111'),
    ('František', 'Fiala', 1944, '603111212', '8100000012', '213'),
    ('Adéla', 'Sedláčková', 2001, '603111213', '8100000013', '205'),
    ('Roman', 'Doležal', 1972, '603111214', '8100000014', '111'),
    ('Zuzana', 'Zemanová', 1969, '603111215', '8100000015', '201'),
    ('Lukáš', 'Navrátil', 1993, '603111216', '8100000016', '111'),
    ('Božena', 'Čermáková', 1946, '603111217', '8100000017', '111'),
    ('David', 'Urban', 1987, '603111218', '8100000018', '207'),
    ('Monika', 'Vaňková', 1975, '603111219', '8100000019', '211'),
    ('Stanislav', 'Blažek', 1958, '603111220', '8100000020', '209'),
    ('Kristýna', 'Křížová', 1999, '603111221', '8100000021', '111'),
    ('Milan', 'Kříž', 1961, '603111222', '8100000022', '111'),
    ('Ivana', 'Musilová', 1966, '603111223', '8100000023', '205'),
    ('Radek', 'Šťastný', 1981, '603111224', '8100000024', '213'),
    ('Jitka', 'Vlčková', 1973, '603111225', '8100000025', '111'),
    ('Václav', 'Kopecký', 1953, '603111226', '8100000026', '201'),
    ('Anna', 'Holubová', 2004, '603111227', '8100000027', '111'),
    ('Filip', 'Kratochvíl', 1995, '603111228', '8100000028', '211'),
    ('Ahmed', 'Hassan', 1990, '603111229', NULL, NULL),
    ('Olena', 'Kovalenko', 1985, '603111230', NULL, NULL)
) AS v(first_name, last_name, birth_year, phone, insurance_id, insurer_code)
LEFT JOIN insurer i ON i.code = v.insurer_code;

-- Dovolená MUDr. Vyhlídalové před třemi týdny: v tu dobu se nic negeneruje.
INSERT INTO doctor_absence (doctor_id, start_at, end_at, reason)
SELECT id,
       (date_trunc('week', current_date)::date - 21)::timestamp AT TIME ZONE 'Europe/Prague',
       (date_trunc('week', current_date)::date - 16)::timestamp AT TIME ZONE 'Europe/Prague',
       'Dovolená'
FROM doctor WHERE last_name = 'Vyhlídalová';

CREATE TEMP TABLE demo_reason_proc (reason TEXT, code TEXT, quantity INT) ON COMMIT DROP;
INSERT INTO demo_reason_proc VALUES
    ('Kontrola tlaku', '09115', 1),
    ('Akutní respirační infekt', '09115', 1),
    ('Odběr krve', '09123', 2),
    ('Odběr krve', '09119', 1),
    ('Recept – chronická medikace', '09119', 1),
    ('Kontrola po léčbě', '09115', 1),
    ('Bolest zad', '09115', 1),
    ('Očkování proti chřipce', '09215', 1),
    ('Očkování proti chřipce', '09119', 1),
    ('Kontrola cukrovky', '09115', 1),
    ('Kontrola cukrovky', '09123', 1),
    ('Preventivní prohlídka', '09127', 1),
    ('Preventivní prohlídka', '09123', 3),
    ('Preventivní prohlídka', '09513', 1),
    ('EKG před operací', '09513', 1),
    ('EKG před operací', '09115', 1);

-- Ordinační hodiny rozdělené na půlhodinové bloky; každý blok je prázdný, má jeden nebo dva
-- 15minutové termíny, nebo jeden 30minutový. Budoucnost je zaplněná méně, ať je kam objednávat.
CREATE TEMP TABLE demo_cell ON COMMIT DROP AS
SELECT o.doctor_id, t AS ts,
       ((hashtext(o.doctor_id || ':' || t) % 100) + 100) % 100 AS h,
       CASE WHEN d::date > current_date THEN 55 ELSE 25 END AS lo
FROM office_hours o
JOIN generate_series(current_date - 56, current_date + 13, interval '1 day') AS d
    ON o.weekday = extract(isodow FROM d)
CROSS JOIN LATERAL generate_series(d::date + o.from_time, d::date + o.to_time - interval '30 minutes',
    interval '30 minutes') AS t
WHERE o.bookable;

CREATE TEMP TABLE demo_appt ON COMMIT DROP AS
SELECT c.doctor_id,
       s.local_start AT TIME ZONE 'Europe/Prague' AS start_at,
       (s.local_start + s.minutes * interval '1 minute') AT TIME ZONE 'Europe/Prague' AS end_at,
       s.minutes
FROM demo_cell c
CROSS JOIN LATERAL (VALUES
    (c.ts, 15, c.h BETWEEN c.lo AND 84),
    (c.ts + interval '15 minutes', 15, c.h BETWEEN c.lo + 30 AND 84),
    (c.ts, 30, c.h >= 85)
) AS s(local_start, minutes, used)
WHERE s.used;

DELETE FROM demo_appt d WHERE EXISTS (
    SELECT 1 FROM doctor_absence a
    WHERE a.doctor_id = d.doctor_id AND a.start_at < d.end_at AND a.end_at > d.start_at);

ALTER TABLE demo_appt ADD COLUMN rn INT, ADD COLUMN s INT, ADD COLUMN patient_id BIGINT,
    ADD COLUMN status TEXT, ADD COLUMN reason TEXT;
UPDATE demo_appt d SET rn = x.rn
FROM (SELECT ctid, row_number() OVER (ORDER BY start_at, doctor_id) AS rn FROM demo_appt) x
WHERE d.ctid = x.ctid;
UPDATE demo_appt SET s = ((hashtext('s' || rn) % 100) + 100) % 100;

-- Pacienti se střídají s krokem 37: souběžné termíny (sousední rn) tak nikdy nedostanou stejného
-- pacienta, protože počet pacientů není násobkem 37.
CREATE TEMP TABLE demo_patient ON COMMIT DROP AS
SELECT id, (row_number() OVER (ORDER BY id) - 1)::INT AS idx
FROM patient WHERE active AND last_name <> 'Krátká';
UPDATE demo_appt d SET patient_id = p.id
FROM demo_patient p
WHERE p.idx = (d.rn * 37) % (SELECT count(*) FROM demo_patient);

UPDATE demo_appt SET
    status = CASE
        WHEN end_at <= now() THEN CASE WHEN s < 7 THEN 'CANCELLED' WHEN s < 11 THEN 'NO_SHOW' ELSE 'COMPLETED' END
        WHEN s < 4 THEN 'CANCELLED'
        ELSE 'BOOKED' END,
    reason = CASE
        WHEN minutes = 30 THEN (ARRAY['Preventivní prohlídka', 'EKG před operací'])[1 + s % 2]
        WHEN s % 10 = 9 THEN NULL
        ELSE (ARRAY['Kontrola tlaku', 'Akutní respirační infekt', 'Odběr krve', 'Recept – chronická medikace',
                    'Kontrola po léčbě', 'Bolest zad', 'Očkování proti chřipce', 'Kontrola cukrovky'])[1 + s % 8]
        END;

-- „Paní Krátká letos nepřišla pětkrát“: několik jejích termínů zrušených nebo vynechaných, pan Pokorný občas ruší.
UPDATE demo_appt SET patient_id = (SELECT id FROM patient WHERE last_name = 'Krátká'),
    status = CASE WHEN rn % 2 = 0 THEN 'NO_SHOW' ELSE 'CANCELLED' END
WHERE rn % 97 = 0 AND end_at <= now();
UPDATE demo_appt SET patient_id = (SELECT id FROM patient WHERE last_name = 'Pokorný'), status = 'CANCELLED'
WHERE rn % 151 = 5 AND end_at <= now();

-- Nekolidovat s tím, co už v databázi je (např. termíny zadané ručně před zapnutím dema).
DELETE FROM demo_appt d WHERE EXISTS (
    SELECT 1 FROM appointment a
    WHERE a.status IN ('BOOKED', 'COMPLETED', 'NO_SHOW') AND d.status IN ('BOOKED', 'COMPLETED', 'NO_SHOW')
      AND (a.doctor_id = d.doctor_id OR a.patient_id = d.patient_id)
      AND a.start_at < d.end_at AND a.end_at > d.start_at);

INSERT INTO appointment (doctor_id, patient_id, start_at, end_at, status, reason, created_at, updated_at)
SELECT doctor_id, patient_id, start_at, end_at, status, reason,
       LEAST(start_at - (1 + s % 20) * interval '1 day', now()),
       CASE WHEN status = 'BOOKED' THEN LEAST(start_at - (1 + s % 20) * interval '1 day', now())
            ELSE LEAST(start_at + interval '20 minutes', now()) END
FROM demo_appt;

INSERT INTO visit (appointment_id, note, created_at)
SELECT a.id,
       CASE a.reason
           WHEN 'Kontrola tlaku' THEN 'TK ' || (125 + a.id % 30) || '/' || (78 + a.id % 15) || ', medikace beze změny'
           WHEN 'Akutní respirační infekt' THEN 'Klidový režim, kontrola při zhoršení'
           WHEN 'Očkování proti chřipce' THEN 'Bez reakce po očkování'
           WHEN 'Preventivní prohlídka' THEN 'Bez patologického nálezu'
           ELSE NULL END,
       a.start_at + interval '10 minutes'
FROM appointment a
WHERE a.status = 'COMPLETED' AND NOT EXISTS (SELECT 1 FROM visit v WHERE v.appointment_id = a.id);

INSERT INTO visit_procedure (visit_id, procedure_id, quantity, points_at_time)
SELECT v.id, pi.id, rp.quantity, pi.points
FROM visit v
JOIN appointment a ON a.id = v.appointment_id
JOIN demo_reason_proc rp ON rp.reason = a.reason
JOIN procedure_item pi ON pi.code = rp.code
WHERE NOT EXISTS (SELECT 1 FROM visit_procedure x WHERE x.visit_id = v.id);

-- Návštěvy bez důvodu dostanou aspoň kontrolní vyšetření.
INSERT INTO visit_procedure (visit_id, procedure_id, quantity, points_at_time)
SELECT v.id, pi.id, 1, pi.points
FROM visit v
JOIN procedure_item pi ON pi.code = '09115'
WHERE NOT EXISTS (SELECT 1 FROM visit_procedure x WHERE x.visit_id = v.id);

-- Školení MUDr. Koláře příští středu: objednaní pacienti čekají na přeobjednání.
INSERT INTO doctor_absence (doctor_id, start_at, end_at, reason)
SELECT id,
       (date_trunc('week', current_date)::date + 9)::timestamp AT TIME ZONE 'Europe/Prague',
       (date_trunc('week', current_date)::date + 10)::timestamp AT TIME ZONE 'Europe/Prague',
       'Odborné školení'
FROM doctor WHERE last_name = 'Kolář';

UPDATE appointment a SET needs_rebooking = TRUE, updated_at = now()
FROM doctor_absence ab
WHERE ab.reason = 'Odborné školení' AND a.doctor_id = ab.doctor_id AND a.status = 'BOOKED'
  AND a.start_at < ab.end_at AND a.end_at > ab.start_at;
