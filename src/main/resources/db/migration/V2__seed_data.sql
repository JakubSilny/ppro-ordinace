INSERT INTO insurer (code, name) VALUES
    ('111', 'Všeobecná zdravotní pojišťovna ČR'),
    ('201', 'Vojenská zdravotní pojišťovna ČR'),
    ('205', 'Česká průmyslová zdravotní pojišťovna'),
    ('207', 'Oborová zdravotní pojišťovna'),
    ('209', 'Zaměstnanecká pojišťovna Škoda'),
    ('211', 'Zdravotní pojišťovna ministerstva vnitra ČR'),
    ('213', 'Revírní bratrská pokladna');

INSERT INTO doctor (first_name, last_name) VALUES
    ('Marie', 'Vyhlídalová'),
    ('Petr', 'Kolář'),
    ('Eva', 'Marešová');

-- 1 = pondělí ... 7 = neděle; úterní pobočka je nebookovatelná
INSERT INTO office_hours (doctor_id, weekday, from_time, to_time, location, bookable)
SELECT d.id, w.day, h.f, h.t,
       CASE WHEN w.day = 2 THEN 'Poliklinika Jih' ELSE 'Na Vyhlídce' END,
       w.day <> 2
FROM doctor d
CROSS JOIN generate_series(1, 5) AS w(day)
CROSS JOIN (VALUES (TIME '08:00', TIME '12:00'), (TIME '13:00', TIME '16:00')) AS h(f, t)
WHERE d.last_name = 'Vyhlídalová';

INSERT INTO office_hours (doctor_id, weekday, from_time, to_time, location, bookable)
SELECT d.id, w.day, h.f, h.t, 'Na Vyhlídce', TRUE
FROM doctor d
CROSS JOIN (VALUES (1), (3), (5)) AS w(day)
CROSS JOIN (VALUES (TIME '07:00', TIME '11:00'), (TIME '12:00', TIME '15:00')) AS h(f, t)
WHERE d.last_name = 'Kolář';

INSERT INTO office_hours (doctor_id, weekday, from_time, to_time, location, bookable)
SELECT d.id, w.day, TIME '09:00', TIME '17:00', 'Na Vyhlídce', TRUE
FROM doctor d
CROSS JOIN (VALUES (2), (4)) AS w(day)
WHERE d.last_name = 'Marešová';

INSERT INTO procedure_item (code, name, points, countable) VALUES
    ('09115', 'Kontrolní vyšetření', 180, FALSE),
    ('09119', 'Minimální kontakt s pojištěncem', 60, TRUE),
    ('09127', 'Preventivní prohlídka', 330, FALSE),
    ('09123', 'Odběr biologického materiálu', 30, TRUE),
    ('09513', 'EKG vyšetření', 150, FALSE),
    ('09215', 'Očkování', 50, TRUE);

-- syntetičtí pacienti; každý desátý bez čísla pojištěnce (cizinec, samoplátce)
INSERT INTO patient (first_name, last_name, birth_year, phone, insurance_id, insurer_id)
SELECT (ARRAY['Jan', 'Jana', 'Petr', 'Eva', 'Karel', 'Marie', 'Tomáš', 'Hana', 'Josef', 'Lucie'])[1 + i % 10],
       (ARRAY['Novák', 'Svobodová', 'Černý', 'Procházková', 'Dvořák', 'Horáček', 'Řezníček', 'Šimková', 'Žák', 'Kučerová'])[1 + (i / 10) % 10],
       1940 + (i * 7) % 65,
       '777' || lpad(i::text, 6, '0'),
       CASE WHEN i % 10 = 0 THEN NULL ELSE '70' || lpad(i::text, 8, '0') END,
       CASE WHEN i % 10 = 0 THEN NULL ELSE (SELECT id FROM insurer ORDER BY id OFFSET (i % 7) LIMIT 1) END
FROM generate_series(1, 100) AS g(i);
