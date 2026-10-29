// Prohlídka celé aplikace pro klienta: pomalé proklikání všech funkcí s popisky a videem.
// Spuštění: npm run prohlidka  (nejlépe nad ukázkovými daty, SPRING_PROFILES_ACTIVE=demo)
const { test, expect } = require('@playwright/test');
const { USER, PASSWORD, ADMIN, ADMIN_PASSWORD } = require('../tests/helpers');

const PAUSE = Number(process.env.PAUSE ?? 1500);
const DOCTORS = ['MUDr. Kolář', 'MUDr. Marešová', 'MUDr. Vyhlídalová'];

async function caption(page, text) {
  await page.evaluate((t) => {
    let el = document.getElementById('pw-caption');
    if (!el) {
      el = document.createElement('div');
      el.id = 'pw-caption';
      Object.assign(el.style, {
        position: 'fixed', left: '16px', bottom: '16px', zIndex: '2147483647', maxWidth: '520px',
        background: '#1c2b2d', color: '#fff', padding: '12px 18px', borderRadius: '10px',
        font: '700 17px/1.35 "Atkinson Hyperlegible", system-ui, sans-serif',
        boxShadow: '0 8px 24px rgba(0,0,0,.3)', pointerEvents: 'none',
      });
    }
    // otevřený dialog leží v horní vrstvě, popisek musí být uvnitř, jinak by zůstal pod ním
    (document.querySelector('dialog[open]') || document.body).append(el);
    el.textContent = t;
  }, text);
  await page.waitForTimeout(PAUSE);
}

const pad = (n) => String(n).padStart(2, '0');
const isoDate = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
function mondayInWeeks(weeks) {
  const d = new Date();
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7) + 7 * weeks);
  return d;
}

test('Prohlídka aplikace Ordinace Na Vyhlídce', async ({ page }) => {
  test.setTimeout(15 * 60_000);
  const dialog = page.getByRole('dialog');
  const lastToast = page.locator('.toast').last();
  const patient = {
    first: 'Martin',
    last: 'Procházka',
    year: '1978',
    phone: `7${String(Date.now()).slice(-8)}`,
  };
  // rezervace hledáme podle ID z odpovědi serveru – v diáři může být stejné jméno z dřívější prohlídky
  let apptId;
  const ourAppt = () => page.locator(`button.block.appt[data-id="${apptId}"]`);
  const remember = (pattern, method = 'POST') => page.waitForResponse((r) => pattern.test(new URL(r.url()).pathname)
      && r.request().method() === method).then(async (r) => {
    if (r.status() < 300) apptId = (await r.json()).id;
    return r.status();
  });
  // po přepnutí týdne nebo lékaře počkat na nové načtení diáře, jinak by klik trefil starý týden
  const settled = () => expect(page.locator('#diary')).toHaveAttribute('aria-busy', 'false');
  const pickDoctor = async (name) => { await page.locator('#doctor-picker').getByRole('button', { name }).click(); await settled(); };
  const week = async (name) => { await page.getByRole('button', { name }).click(); await settled(); };
  const nextWeek = () => week('Další týden');
  const searchPatient = async (q) => {
    await dialog.getByLabel('Najít pacienta').pressSequentially(q, { delay: 90 });
    await dialog.locator('.list-item').first().click();
  };

  await test.step('Přihlášení', async () => {
    await page.goto('/');
    await caption(page, 'Do systému se dostane jen přihlášený personál – jsou v něm zdravotní údaje.');
    await page.getByLabel('Uživatelské jméno').fill(USER);
    await page.getByLabel('Heslo').fill('spatne-heslo');
    await page.getByRole('button', { name: 'Přihlásit se' }).click();
    await expect(page.getByText('Nesprávné jméno nebo heslo.')).toBeVisible();
    await caption(page, 'Se špatným heslem neprojde nikdo.');
    // jiné jméno při každém běhu: zámek drží 5 minut a prohlídka se může pouštět opakovaně
    const guesser = `host${Date.now() % 10000}`;
    await caption(page, 'Kdo zkouší hesla naslepo, toho systém po pěti pokusech zastaví.');
    for (let i = 0; i < 5; i++) {
      await page.getByLabel('Uživatelské jméno').fill(guesser);
      await page.getByLabel('Heslo').fill(`heslo${i}`);
      await page.getByRole('button', { name: 'Přihlásit se' }).click();
      await expect(page.getByText('Nesprávné jméno nebo heslo.')).toBeVisible();
    }
    await page.getByLabel('Uživatelské jméno').fill(guesser);
    await page.getByLabel('Heslo').fill('heslo5');
    await page.getByRole('button', { name: 'Přihlásit se' }).click();
    await expect(page.getByText(/Příliš mnoho špatných pokusů/)).toBeVisible();
    await caption(page, 'Jméno je teď 5 minut zablokované, i se správným heslem. Každý pokus se zapíše.');
    await page.getByLabel('Uživatelské jméno').fill(USER);
    await page.getByLabel('Heslo').pressSequentially(PASSWORD, { delay: 40 });
    await page.getByRole('button', { name: 'Přihlásit se' }).click();
    await expect(page.locator('#diary .day').first()).toBeVisible();
    await settled();
  });

  await test.step('Diář lékařů', async () => {
    await caption(page, 'Týdenní diář: ordinační hodiny, objednaní pacienti a zeleně volná okna.');
    for (const d of DOCTORS) {
      await pickDoctor(d);
      await caption(page, `Každý lékař má jiné dny a časy – ${d}.`);
    }
    await caption(page, 'Úterní odpoledne na pobočce je vidět, ale objednat se tam nedá (šrafované).');
    await nextWeek();
    await caption(page, 'Listování po týdnech.');
    await week('Tento týden');
    await page.locator('#duration').selectOption('30');
    await settled();
    await caption(page, 'Volná okna se přepočítají podle délky termínu, tady 30 minut.');
    await page.locator('#duration').selectOption('15');
  });

  await test.step('Objednání nového pacienta', async () => {
    await pickDoctor('MUDr. Vyhlídalová');
    await nextWeek();
    await caption(page, 'Pacient volá a chce termín. Kliknu na volné okno…');
    await page.locator('button.block.free').first().click();
    await caption(page, 'Pacient u nás ještě není – založím ho rovnou z objednání.');
    await dialog.getByRole('button', { name: 'Nový pacient' }).click();
    await dialog.getByLabel('Jméno', { exact: true }).pressSequentially(patient.first, { delay: 60 });
    await dialog.getByLabel('Příjmení').pressSequentially(patient.last, { delay: 60 });
    await dialog.getByLabel('Rok narození').fill(patient.year);
    await dialog.getByLabel('Telefon').fill(`+420 ${patient.phone}`);
    await dialog.getByLabel('Pojišťovna').selectOption({ index: 1 });
    await caption(page, 'Telefon může být s předvolbou i mezerami, uloží se jen číslice.');
    await dialog.getByRole('button', { name: 'Založit pacienta' }).click();
    await caption(page, 'Pacient je vybraný, zbývá čas, délka a důvod.');
    const start = dialog.getByLabel('Začátek');
    const options = await start.locator('option').count();
    await start.selectOption({ index: Math.min(2, options - 1) });
    await dialog.getByLabel('Důvod návštěvy').pressSequentially('Kontrola tlaku', { delay: 50 });
    const created = remember(/^\/api\/appointments$/);
    await dialog.getByRole('button', { name: 'Objednat' }).click();
    expect(await created).toBe(201);
    await expect(lastToast).toContainText('Objednáno');
    await caption(page, 'Hotovo – termín je v diáři. Dva pacienti na stejný čas nejdou ani omylem.');
  });

  await test.step('Přesun termínu', async () => {
    await ourAppt().click();
    await caption(page, 'Detail rezervace: telefon na jedno kliknutí, důvod a možné akce.');
    await dialog.getByRole('button', { name: 'Přesunout' }).click();
    await expect(page.locator('#mode-banner')).toBeVisible();
    await caption(page, 'Pacient chce jiný den. Diář je v režimu výběru nového termínu.');
    await nextWeek();
    await page.locator('button.block.free').first().click();
    const moved = remember(/\/reschedule$/);
    await dialog.getByRole('button', { name: 'Přesunout termín' }).click();
    expect(await moved).toBe(201);
    await expect(lastToast).toContainText('Termín přesunut');
    await caption(page, 'Přesunuto. Původní rezervace zůstává v historii.');
  });

  await test.step('Zrušení rezervace', async () => {
    await ourAppt().click();
    await dialog.getByRole('button', { name: 'Zrušit rezervaci' }).click();
    await caption(page, 'Před zrušením se systém zeptá. Zrušení se pacientovi započítá do statistiky.');
    await dialog.getByRole('button', { name: 'Ano, zrušit rezervaci' }).click();
    await expect(lastToast).toContainText('Rezervace zrušena');
    await caption(page, 'Termín zmizel z diáře a je znovu volný.');
  });

  await test.step('Akutní pacient a zápis návštěvy', async () => {
    await week('Tento týden');
    await caption(page, 'Akutní pacient: objednání na teď, když je lékař v ordinaci.');
    let booked = false;
    for (const d of DOCTORS) {
      await pickDoctor(d);
      await page.locator('#acute-btn').click();
      await searchPatient(patient.phone);
      const response = remember(/^\/api\/appointments$/);
      await dialog.getByRole('button', { name: 'Objednat' }).click();
      if ((await response) === 201) { booked = true; break; }
      await caption(page, `${d} teď neordinuje – systém mimo ordinační hodiny objednat nedovolí.`);
      await page.keyboard.press('Escape');
    }

    if (booked) {
      await ourAppt().click();
      await dialog.getByRole('button', { name: 'Zapsat návštěvu' }).click();
    } else {
      await caption(page, 'Teď nikdo neordinuje, ukážu tedy návštěvu z minulého týdne.');
      await week('Předchozí týden');
      const done = page.locator('button.block.appt.COMPLETED').first();
      if (!(await done.count())) return;
      await done.click();
      await dialog.getByRole('button', { name: 'Otevřít návštěvu' }).click();
    }
    await expect(dialog.getByText('Provedené výkony')).toBeVisible();
    await caption(page, 'Záznam o návštěvě: výkony z číselníku, body se sčítají samy.');
    const procedure = dialog.getByLabel('Výkon', { exact: true });
    const add = async (code, quantity) => {
      await procedure.selectOption(await procedure.locator('option', { hasText: code }).getAttribute('value'));
      if (quantity) await dialog.getByLabel('Počet').fill(String(quantity));
      await dialog.getByRole('button', { name: 'Přidat výkon' }).click();
      await page.waitForTimeout(PAUSE / 2);
    };
    await add('09513');
    await caption(page, 'U očkování se zadává i počet – například tři aplikace.');
    await add('09215', 3);
    await add('09513');
    await caption(page, 'Stejný výkon dvakrát zapsat nejde, systém upozorní.');
    const voidButton = dialog.getByRole('button', { name: /Stornovat EKG/ });
    if (await voidButton.count()) {
      await voidButton.first().click();
      await caption(page, 'Chybný výkon se nemaže, ale stornuje – historie zůstává.');
    }
    await dialog.getByLabel('Poznámka').fill('TK 132/84, bez obtíží');
    await dialog.getByRole('button', { name: 'Uložit poznámku' }).click();
    await caption(page, 'Poznámka uložena. Výkon nejde zapsat k návštěvě, která ještě nezačala.');
    await dialog.getByRole('button', { name: 'Hotovo' }).click();
  });

  await test.step('Rozpis dne pro lékaře', async () => {
    await page.getByRole('link', { name: 'Rozpis' }).click();
    await caption(page, 'Ráno si lékař nebo recepce vytiskne rozpis dne: kdo přijde, kdy a proč.');
    await page.locator('#day-doctor').selectOption({ label: 'MUDr. Marie Vyhlídalová' });
    // první den v příštích dvou týdnech, kdy je někdo objednaný
    const day = new Date();
    for (let i = 0; i < 14; i++) {
      await page.locator('#day-date').fill(isoDate(day));
      await page.getByRole('button', { name: 'Zobrazit' }).click();
      if (await page.locator('#day table').waitFor({ timeout: 1500 }).then(() => true, () => false)) break;
      day.setDate(day.getDate() + 1);
    }
    await caption(page, 'Telefon na jedno kliknutí, jméno vede na kartu pacienta.');
    await caption(page, 'Tlačítkem „Vytisknout“ vyjde jen tabulka, bez menu a tlačítek.');
    await page.getByRole('link', { name: 'Diář' }).click();
    await settled();
  });

  await test.step('Nemoc lékaře a přeobjednání', async () => {
    await pickDoctor('MUDr. Vyhlídalová');
    await page.locator('#absence-btn').click();
    const monday = isoDate(mondayInWeeks(1));
    await dialog.getByLabel('Od', { exact: true }).fill(`${monday}T08:00`);
    await dialog.getByLabel('Do', { exact: true }).fill(`${monday}T12:00`);
    await dialog.getByLabel(/^Důvod/).fill('Nemoc');
    await caption(page, 'Lékařka příští pondělí dopoledne onemocní. Zadám nepřítomnost…');
    await dialog.getByRole('button', { name: 'Uložit nepřítomnost' }).click();
    await expect(lastToast).toContainText('Nepřítomnost uložena');
    await caption(page, 'Do nepřítomnosti už nejde objednat a dotčení pacienti se označí.');
    await nextWeek();
    await caption(page, 'V diáři je nepřítomnost vyšrafovaná, zasažené termíny svítí oranžově.');

    await page.getByRole('link', { name: /K přeobjednání/ }).click();
    await caption(page, 'Seznam lidí k obvolání. Počet svítí i v menu.');
    const rebook = page.getByRole('button', { name: 'Nový termín' }).first();
    if (await rebook.count()) {
      await rebook.click();
      await settled();
      await caption(page, 'Domluvím nový termín a vyberu ho rovnou v diáři.');
      for (let i = 0; i < 3; i++) await nextWeek();
      await page.locator('button.block.free').first().click();
      await dialog.getByRole('button', { name: 'Přesunout termín' }).click();
      await caption(page, 'Pacient je přeobjednaný a ze seznamu zmizel.');
      if (await dialog.isVisible()) await page.keyboard.press('Escape');
    }
  });

  await test.step('Kartotéka pacientů', async () => {
    await page.getByRole('link', { name: 'Pacienti' }).click();
    await caption(page, 'Hledání podle příjmení – i bez háčků a čárek.');
    await page.getByLabel('Příjmení nebo telefon').pressSequentially('kratk', { delay: 120 });
    let first = page.locator('#patient-results .list-item').first();
    if (!(await first.waitFor({ timeout: 3000 }).then(() => true, () => false))) {
      await page.getByLabel('Příjmení nebo telefon').fill('');
      await page.getByLabel('Příjmení nebo telefon').pressSequentially('nov', { delay: 120 });
      first = page.locator('#patient-results .list-item').first();
    }
    await first.click();
    await caption(page, 'Karta pacienta: kontakt, pojišťovna, nadcházející termíny, historie a kolikrát zrušil.');
    await page.mouse.wheel(0, 500);
    await caption(page, 'Historie se nikdy nemaže – lze doložit, co se kdy dělalo.');

    await page.getByRole('button', { name: 'Kdo kartu zobrazil' }).click();
    await caption(page, 'GDPR: každé nahlédnutí do karty se zaznamená.');
    await caption(page, 'Záznam nejde upravit ani smazat – nedovolí to ani databáze.');
    await page.keyboard.press('Escape');
    const download = page.waitForEvent('download');
    await page.getByRole('link', { name: 'Stáhnout všechny údaje' }).click();
    await caption(page, `GDPR: pacient může dostat všechny své údaje – ${(await download).suggestedFilename()}.`);

    await caption(page, 'Rychlé hledání v horní liště funguje odkudkoli, třeba podle telefonu.');
    await page.getByLabel('Najít pacienta').pressSequentially(patient.phone.slice(0, 6), { delay: 120 });
    await page.locator('#global-results .list-item', { hasText: patient.last }).first().click();
    await page.locator('#patient-card .history li', { hasText: 'Zrušeno' }).first().getByRole('button').click();
    await expect(dialog.getByText('Poslední změna')).toBeVisible();
    await caption(page, 'U každé rezervace je vidět, kdo ji objednal a kdo ji naposledy změnil nebo zrušil.');
    await page.keyboard.press('Escape');
    await page.getByRole('button', { name: 'Kdo kartu zobrazil' }).click();
    await expect(dialog.getByRole('cell', { name: /^rezervace \d+/ }).first()).toBeVisible();
    await caption(page, 'Do záznamu přístupů se počítá i otevření rezervace nebo návštěvy pacienta, nejen karta.');
    await page.keyboard.press('Escape');

    await page.getByRole('button', { name: 'Nový pacient' }).click();
    await dialog.getByLabel('Jméno', { exact: true }).fill(patient.first);
    await dialog.getByLabel('Příjmení').fill(patient.last);
    await dialog.getByLabel('Rok narození').fill(patient.year);
    await dialog.getByLabel('Telefon').fill(patient.phone);
    await dialog.getByRole('button', { name: 'Založit pacienta' }).click();
    await expect(dialog.getByText(/už v evidenci je/)).toBeVisible();
    await caption(page, 'Stejného člověka nejde omylem založit dvakrát – systém nabídne existující kartu.');
    await dialog.getByRole('button', { name: 'Otevřít existujícího' }).click();

    await page.getByRole('button', { name: 'Upravit' }).click();
    await dialog.getByLabel('Telefon').fill(`6${patient.phone.slice(1)}`);
    await caption(page, 'Úprava údajů, třeba nového telefonu.');
    await dialog.getByRole('button', { name: 'Uložit změny' }).click();
    await expect(lastToast).toContainText('Změny uloženy');

    await page.getByRole('button', { name: 'Vyřadit z evidence' }).click();
    await caption(page, 'Pacient, který odešel, se nemaže – jen vyřadí. Dokumentace zůstává.');
    await dialog.getByRole('button', { name: 'Vyřadit' }).click();
    await expect(page.getByText('Pacient je vyřazený z evidence.')).toBeVisible();
  });

  await test.step('Měsíční výkaz', async () => {
    await page.getByRole('link', { name: 'Výkazy' }).click();
    const lastMonth = new Date();
    lastMonth.setDate(1);
    lastMonth.setMonth(lastMonth.getMonth() - 1);
    await page.locator('#report-doctor').selectOption({ index: 1 });
    await page.locator('#report-month').fill(`${lastMonth.getFullYear()}-${pad(lastMonth.getMonth() + 1)}`);
    await page.getByRole('button', { name: 'Zobrazit' }).click();
    await caption(page, 'Měsíční výkaz: souhrn výkonů a součet bodů – místo celého podvečera přepisování.');
    await page.mouse.wheel(0, 600);
    await caption(page, 'Pod souhrnem jsou jednotlivé výkony s číslem pojištěnce.');
    const csv = page.getByRole('link', { name: /Stáhnout pro pojišťovnu/ });
    if (await csv.count()) {
      const download = page.waitForEvent('download');
      await csv.click();
      await caption(page, `Export pro pojišťovnu do Excelu: ${(await download).suggestedFilename()}.`);
    }
  });

  await test.step('Kdo ruší a nechodí', async () => {
    await page.getByRole('link', { name: 'Rušení' }).click();
    await caption(page, 'Přehled, kdo ruší nebo nechodí – „paní Krátká letos nepřišla pětkrát“.');
    const top = page.locator('#stats tbody a').first();
    if (await top.count()) {
      await top.click();
      await caption(page, 'Z přehledu jedním klikem na kartu pacienta.');
    }
  });

  await test.step('Tablet', async () => {
    await page.getByRole('link', { name: 'Diář' }).click();
    await page.setViewportSize({ width: 820, height: 1180 });
    await caption(page, 'Stejné rozhraní funguje i na tabletu.');
    await page.setViewportSize({ width: 1440, height: 900 });
  });

  await test.step('Správce: záznam přístupů', async () => {
    await page.getByRole('button', { name: 'Odhlásit' }).click();
    await caption(page, 'Správce (Super Admin) má vše jako recepce a navíc vidí, kdo co v systému dělal.');
    await page.getByLabel('Uživatelské jméno').fill(ADMIN);
    await page.getByLabel('Heslo').pressSequentially(ADMIN_PASSWORD, { delay: 40 });
    await page.getByRole('button', { name: 'Přihlásit se' }).click();
    await page.getByRole('link', { name: 'Záznam přístupů' }).click();
    await expect(page.locator('#audit table')).toBeVisible();
    await caption(page, 'Činnosti všech uživatelů za den: přihlášení, nahlédnutí do karet, rezervace, výkazy…');
    await caption(page, 'Zablokované pokusy o přihlášení jsou tu taky. Obsah karet se do záznamu neukládá.');
    await page.getByLabel('Zobrazení').selectOption('api');
    await expect(page.locator('#audit th', { hasText: 'Stav' })).toBeVisible();
    await caption(page, 'API log: technický pohled s cestou a HTTP stavem, včetně odmítnutých požadavků.');
  });

  await test.step('Odhlášení', async () => {
    await page.getByRole('button', { name: 'Odhlásit' }).click();
    await expect(page.getByText('Byli jste odhlášeni.')).toBeVisible();
    await caption(page, 'Odhlášeno. Po 30 minutách nečinnosti systém odhlásí sám.');
  });
});
