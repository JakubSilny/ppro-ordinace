# Ordinace Na Vyhlídce (PPRO Zadání A)

Rezervační a ordinační systém pro praktické lékaře (UHK, ZS 2026/2027).

## Spuštění aplikace

Pro spuštění aplikace a PostgreSQL databáze stačí jediný příkaz (předem vytvořte `.env` z `.env.example`):

```bash
cp .env.example .env
docker compose up --build -d
```

- Aplikace a rozhraní recepce: **[http://localhost:8080](http://localhost:8080)**
- PostgreSQL databáze: `localhost:5432` (uživatel: `ordinace`, heslo: `ordinace`, db: `ordinace`)

### Spuštění testů

```bash
./mvnw verify
```

---

## Architektura a technologie

- **Java 21**, **Spring Boot 3.4** (Web, Data JPA, Validation)
- **Třívrstvá architektura**: Controller (`api`) → Service (`service`) → Repository (`repository`)
- **PostgreSQL 16** v Dockeru s verzovanými Flyway migracemi (`V1__init_schema.sql`, `V2__seed_data.sql`)
- **PostgreSQL GiST Exclusion Constraints** (`btree_gist`): ochrana proti překryvům termínů lékaře i pacienta přímo na úrovni DB jádra
- **Testy**: JUnit 5, AssertJ, Mockito a Testcontainers

---

## Oddíl Rozhodnutí (řešení otevřených bodů)

- **K1 Délka termínu**: Vlastní délka v násobcích 5 minut (výchozí 15 min), termín musí celý ležet uvnitř jednoho bookable bloku ordinačních hodin; kolize hlídá databázový exclusion constraint u lékaře i pacienta.
- **K2 Kdo zakládá**: Rezervace zakládá recepce; rezervační logika je plně zapouzdřena v servisní vrstvě nezávisle na prezentačním rozhraní.
- **K3 Zrušená rezervace**: Zrušení nastaví stav `CANCELLED`, řádek v historii zůstává, ale termín se okamžitě uvolní v kalendáři i volných slotech.
- **K4 / K4a Identifikátor pojištěnce**: Číslo pojištěnce (`insurance_id`) je nepovinné a unikátní pokud je vyplněno (duplicita vrací 409 Conflict). U pacientů bez čísla (samoplátci, cizinci) se hlídá shoda jména, roku narození a telefonu s možností potvrzení duplicity recepcí.
- **K5 Nemoc a absence lékaře**: Zadáním absence lékaře dostanou všechny dotčené rezervace ve stavu `BOOKED` příznak `needs_rebooking = true` a objeví se ve výstražném seznamu pro recepci.
- **P1 Zápis návštěvy**: Návštěvu a výkony lze zapsat až po začátku rezervace (ověřováno přes `Clock`), zápis nastaví stav rezervace na `COMPLETED`.
- **P2 Vazba návštěvy a rezervace**: Návštěva má pevnou vazbu 1:1 na rezervaci; akutní pacient představuje rezervaci na aktuální čas.
- **P3 Stavy zrušení**: Rozlišuje se `CANCELLED` (zrušeno předem) a `NO_SHOW` (pacient se nedostavil po začátku termínu).
- **P5 Přesun termínu**: Přesun probíhá v jedné transakci – původní rezervace přejde do stavu `RESCHEDULED`, nová na ni odkazuje přes `rescheduled_from_id`.
- **P6 Souběh pacienta**: Pacient nesmí mít překrývající se aktivní rezervace ani u různých lékařů (vynuceno databází i aplikační kontrolou).
- **P7 Rezervace do minulosti**: Rezervace smí začínat od začátku dnešního dne (`Europe/Prague`), starší termíny jsou blokovány.
- **P8, P9 Ordinační hodiny**: Týdenní šablona ordinačních hodin s místem a příznakem `bookable`. Úterní výjezdní pobočka Poliklinika Jih je nastavena jako nebookovatelná.
- **P10 Celistvost termínu**: Celá rezervace musí ležet uvnitř jednoho spojitého ordinačního intervalu.
- **P11, P12 Absence lékaře**: Do termínu nahlášené absence nelze rezervovat žádný termín.
- **P13 Pojišťovny**: Číselník 7 oficiálních českých zdravotních pojišťoven.
- **P14, P15 Vyhledávání pacientů**: Vyhledávání podle začátku příjmení bez ohledu na velikost písmen a bez diakritiky (`unaccent`), telefon vyhledáván po očištění na číslice.
- **P16 Mazání**: Pacienti ani lékaři se nemažou, pouze deaktivují; deaktivace pacienta s budoucí aktivní rezervací je odmítnuta.
- **P17, P18, P19 Výkony na návštěvě**: Vazba M:N nese počet (`quantity >= 1`) a zmražené body v době zápisu (`points_at_time`), výkon lze stornovat přes `voided_at`.
- **N1 Uživatelské rozhraní**: Jednostránková přehledná webová aplikace pro recepci s velkými tlačítky, okamžitým našeptávačem pacientů, výběrem volných slotů a přehledem k přeobjednání.
