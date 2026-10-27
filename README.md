# Ordinace Na Vyhlídce

Objednávací a evidenční systém pro soukromou praxi praktických lékařů. Recepce v něm vede diář lékařů, objednává pacienty do volných termínů, zapisuje návštěvy a provedené výkony a na konci měsíce stáhne výkaz pro pojišťovnu.

Semestrální projekt PPRO (UHK, ZS 2026/2027), zadání A. Všechna data v systému jsou syntetická.

## Co systém umí

- **Diář lékaře po týdnech.** Ukazuje ordinační hodiny, objednané pacienty, nepřítomnosti a volná okna pro zvolenou délku termínu. Kliknutím na volné okno se pacient rovnou objedná.
- **Rezervace.** Zrušení (termín se uvolní), „nedostavil se“ a přesun na jiný termín (původní rezervace zůstává v historii) se řeší přímo v diáři.
- **Akutní pacient.** Objednání na aktuální čas, pokud je lékař v ordinaci.
- **Pacienti.** Hledání podle příjmení (bez ohledu na diakritiku) nebo telefonu, karta s historií, hlídání duplicit a vyřazení z evidence místo smazání.
- **Návštěvy a výkony.** Výkony z číselníku, u počitatelných výkonů s počtem. Body se při zápisu zmrazí a chybný zápis se stornuje, nemaže.
- **Nepřítomnost lékaře.** Dotčené rezervace se označí a objeví se v seznamu **K přeobjednání**.
- **Měsíční výkaz** pro lékaře: souhrn podle výkonu, jednotlivé výkony, součet bodů, export do CSV pro Excel a tisk.
- **Přehled rušení.** Kdo ruší nebo nechodí ve zvoleném období.
- **Ochrana osobních údajů.** Přihlášení, záznam přístupů ke kartě pacienta a export všech údajů pacienta (viz níže).

Rozhraní je česky, ovládá se myší, klávesnicí i dotykem a funguje i na tabletu.

## Spuštění

Potřebujete Docker (s Docker Compose).

```bash
cp .env.example .env
# v .env nastavte vlastní hesla: DB_PASSWORD a účty v APP_USERS
docker compose up --build -d
```

Aplikace poběží na **http://localhost:8080**. Přihlašovací jména a hesla jsou ta, která jste nastavili v `APP_USERS` (formát `jmeno:heslo,jmeno2:heslo2`). Bez nich aplikace odmítne nastartovat.

Databáze PostgreSQL je dostupná jen z tohoto počítače (`127.0.0.1:5432`). Schéma i výchozí data (číselníky, lékaři, 100 syntetických pacientů) založí migrace Flyway při prvním startu.

S profilem `demo` (výchozí v `.env.example`) se navíc založí ukázkový provoz: 30 dalších pacientů, asi 8 týdnů proběhlých návštěv s výkony, zrušené a vynechané termíny (paní Krátká ruší opakovaně), objednaní pacienti na 2 týdny dopředu, dovolená a školení lékaře s rezervacemi k přeobjednání. Časy se počítají od prvního spuštění. Pro ostrý provoz proměnnou `SPRING_PROFILES_ACTIVE` smažte.

| Proměnná | Význam |
|---|---|
| `DB_NAME`, `DB_USER`, `DB_PASSWORD` | databáze |
| `APP_USERS` | účty personálu, `jmeno:heslo` oddělené čárkou |
| `COOKIE_SECURE` | `true`, pokud aplikace běží za HTTPS proxy |
| `SPRING_PROFILES_ACTIVE` | `demo` = ukázková data (jen pro předvedení) |

Pro provoz mimo jeden počítač patří aplikace za reverzní proxy s HTTPS (např. Caddy nebo nginx) a `COOKIE_SECURE=true`.

## Testy

```bash
./mvnw verify                    # jednotkové testy + integrační testy proti PostgreSQL (Testcontainers, potřebuje Docker)
```

Prohlížečové testy (Playwright) běží proti spuštěné aplikaci:

```bash
APP_USERS=e2e:e2e-password-123 docker compose up --build -d
cd e2e && npm ci && npx playwright install chromium && npx playwright test
```

Obojí spouští GitHub Actions při každém pushi (`.github/workflows/ci.yml`). E2E testy běží ve dvou profilech: desktop a tablet.

## Dokumentace

Technická dokumentace je v `docs/dokumentace.pdf`. Její zdroj `docs/dokumentace.md` se převede příkazem:

```bash
cd docs && pandoc dokumentace.md -o dokumentace.pdf --pdf-engine=xelatex -V mainfont="Helvetica Neue" -V monofont=Menlo -V geometry:margin=2.5cm --toc
```

## Architektura

- **Java 21, Spring Boot 3.4** (Web, Data JPA, Validation, Security), **PostgreSQL 16**, migrace **Flyway**.
- Balíčky podle funkcí (`patient`, `doctor`, `appointment`, `visit`, `audit`, `gdpr`, `security`), uvnitř vrstvy `api` → `service` → `repository`. Controller nikdy nesahá na databázi.
- Pravidla, která musí platit vždy, hlídá i databáze. Dvě rezervace se u lékaře ani u pacienta nepřekryjí (exclusion constraint `btree_gist`). Totéž platí pro unikátní číslo pojištěnce a pro jeden aktivní zápis výkonu na návštěvu. Při souběžném objednání tak uspěje jen jeden požadavek, i kdyby aplikační kontrola proběhla u obou zároveň.
- Webové rozhraní je statická stránka (HTML, CSS, JavaScript bez frameworku) nad REST API. Písmo Atkinson Hyperlegible je hostované spolu s aplikací.

### REST API (výběr)

| Metoda a cesta | Účel |
|---|---|
| `GET /api/appointments?doctorId&from&to` | diář lékaře |
| `GET /api/appointments/free-slots?doctorId&weekOf&minutes` | volná okna v týdnu |
| `POST /api/appointments` | nová rezervace |
| `POST /api/appointments/{id}/cancel`, `/no-show`, `/reschedule` | změny rezervace |
| `POST /api/appointments/{id}/visit` | zápis návštěvy (jen po začátku termínu) |
| `POST /api/visits/{id}/procedures`, `…/{itemId}/void` | výkon na návštěvě, storno |
| `GET /api/reports/monthly?doctorId&month`, `/monthly.csv` | měsíční výkaz |
| `GET /api/appointments/cancellation-stats?from&to` | kdo ruší |
| `GET /api/patients?query`, `POST /api/patients` | hledání a zakládání pacientů |
| `GET /api/patients/{id}/export`, `/access-log` | údaje pacienta a záznam přístupů |
| `POST /api/doctors/{id}/absences` | nepřítomnost lékaře |

Chyby se vrací jako `application/problem+json`: 404 (neexistuje), 409 (kolize, duplicita), 422 (porušené pravidlo, např. mimo ordinační hodiny).

## Ochrana osobních údajů

Systém zpracovává zdravotní údaje (zvláštní kategorie podle čl. 9 GDPR), proto:

- **Přístup jen po přihlášení.** Odhlášení po 30 minutách nečinnosti, cookie `HttpOnly` a `SameSite=Strict`, ochrana proti CSRF.
- **Záznam přístupů.** Každé volání API s osobními údaji se zapíše do tabulky `audit_log` (kdo, kdy, co). Hledané výrazy se neukládají. Na kartě pacienta je vidět, kdo ji zobrazil.
- **Právo na přístup a přenositelnost (čl. 15 a 20).** Tlačítkem „Stáhnout všechny údaje“ se stáhne JSON se všemi údaji pacienta, rezervacemi a návštěvami.
- **Výmaz.** Zdravotnickou dokumentaci je nutné uchovávat po zákonem stanovenou dobu (vyhláška č. 98/2012 Sb.), proto se pacient nemaže, jen vyřadí z evidence.
- **Minimalizace.** Evidují se jen údaje nutné pro objednání a vykázání péče (bez rodného čísla a adresy).
- **Technická opatření.** Content-Security-Policy bez inline skriptů, databáze dostupná jen lokálně, kontejner běží pod neprivilegovaným uživatelem, hesla jen v proměnných prostředí. Žádné externí zdroje, takže prohlížeč nekomunikuje s nikým dalším.

## Rozhodnutí

- **K1 Délka termínu.** Rezervace má vlastní délku v násobcích 5 minut (výchozí 15 min) a musí celá ležet v jednom intervalu ordinačních hodin, kde se objednává. Pevná mřížka by nepasovala na prohlídky, které trvají déle.
- **K2 Kdo zakládá rezervaci.** Jen recepce, po přihlášení. Pravidla jsou v servisní vrstvě nezávisle na rozhraní, takže se dá později doplnit online objednávání bez jejich přepisu.
- **K3 Zrušená rezervace.** Zrušení nastaví stav `CANCELLED` a rezervace zmizí z diáře, ale v databázi zůstane. Ze zrušených se počítá přehled rušení.
- **K4/K4a Totožnost pacienta.** Číslo pojištěnce je nepovinné, ale unikátní, takže duplicita vrátí 409 s odkazem na existující kartu. Bez čísla (cizinec, samoplátce) se hlídá shoda jména, roku narození a telefonu a recepce může vědomě založit jiného člověka se stejnými údaji.
- **K5 Nemoc lékaře.** Zadaná nepřítomnost blokuje objednávání a dotčené rezervace dostanou příznak „k přeobjednání“. Volání pacientům zůstává na recepci, systém nic hromadně neruší ani nepřesouvá.
- **P1 Zápis návštěvy.** Návštěvu lze zapsat až po začátku termínu a rezervace tím přejde do stavu `COMPLETED`.
- **P2 Návštěva bez rezervace.** Návštěva je vždy vázaná na rezervaci. Akutní pacient dostane rezervaci na aktuální čas (tlačítko „Akutní pacient“).
- **P3 Druhy zrušení.** `CANCELLED` (pacient se omluvil) a `NO_SHOW` (nepřišel, lze až po začátku termínu) se evidují zvlášť.
- **P4 Kdo ruší opakovaně.** Bez pevného prahu: přehled za zvolené období řazený podle počtu.
- **P5 Přesun.** V jedné transakci se původní rezervace přepne na `RESCHEDULED` a nová na ni odkazuje (`rescheduled_from_id`).
- **P6 Souběh u pacienta.** Pacient nemůže mít dvě překrývající se rezervace ani u různých lékařů (aplikace i DB).
- **P7 Minulost.** Objednat lze od začátku dnešního dne (Europe/Prague), dřív ne.
- **P8/P9 Ordinační hodiny.** Týdenní šablona s místem a příznakem, zda se tam objednává. Úterní pobočka je vidět v diáři, ale objednat do ní nejde.
- **P10 Celistvost.** Rezervace nesmí přesahovat přes polední pauzu ani konec ordinační doby.
- **P11/P12 Absence.** Nepřítomnost je časový interval. Příznak k přeobjednání dostanou jen objednané (`BOOKED`) rezervace a zůstává jim i po případném odstranění absence, dokud je recepce nevyřeší.
- **P13 Pojišťovny.** Pevný číselník sedmi českých zdravotních pojišťoven.
- **P14/P15 Hledání.** Podle začátku příjmení bez diakritiky a velikosti písmen, nebo podle části telefonu. Telefon se ukládá jen jako číslice bez předvolby +420.
- **P16 Mazání.** Pacienti ani lékaři se nemažou, jen vyřadí. Vyřadit nejde, dokud mají budoucí rezervace.
- **P17–P19 Výkony.** Vazba návštěva–výkon (M:N) nese počet a body platné v době zápisu, takže změna ceníku neovlivní minulé výkazy. Jeden výkon je na návštěvě jednou, opakování se zapisuje počtem.
- **P20 Oprava výkonu.** Stornem (`voided_at`), řádek zůstává.
- **P21 Historie změn.** Rezervace a pacienti mají `created_at`/`updated_at` a stavy. Navíc se kvůli GDPR zaznamenává každý přístup k osobním údajům (`audit_log`).
- **P22 Výkaz.** Jen z dokončených návštěv a nestornovaných výkonů. Souhrn podle kódu, jednotlivé řádky s číslem pojištěnce a export do CSV.
- **P23 Volné termíny.** Pro lékaře, týden a délku termínu se počítají souvislá volná okna (ordinační hodiny minus rezervace, nepřítomnosti a uplynulý čas).
- **N1 Rozhraní.** Jedna stránka pro recepci s velkými ovládacími prvky a písmem pro snadné čtení, rychlým hledáním pacienta v horní liště a podporou tabletu.
- **T2 Přihlášení.** Původně bez přihlášení, kvůli zdravotním údajům doplněno přihlášení personálu (účty z proměnné prostředí) a záznam přístupů. Role nerozlišujeme, protože recepce i lékaři potřebují stejné funkce.
