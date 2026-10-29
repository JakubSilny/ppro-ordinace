const { test, expect } = require('@playwright/test');
const { login, unique, USER } = require('./helpers');

// Opravy z code review ověřené přes API přihlášeného prohlížeče: souběh, validace, záznam přístupů.
// Data jdou ~30 týdnů dopředu, aby nepletla diář v prohlídce ani ostatních testech.

let api;

test.beforeEach(async ({ page, context }, testInfo) => {
  test.skip(testInfo.project.name === 'tablet', 'API stačí ověřit jednou');
  await login(page);
  const token = (await context.cookies()).find((c) => c.name === 'XSRF-TOKEN').value;
  const call = (method) => (url, data) => page.request.fetch(url, { method, data, headers: { 'X-XSRF-TOKEN': token } });
  api = { get: call('GET'), post: call('POST') };
});

async function newPatient() {
  const res = await api.post('/api/patients?confirmDuplicate=true',
    { firstName: 'Test', lastName: unique('Revize'), birthYear: 1990, phone: '777000111' });
  expect(res.status()).toBe(201);
  return (await res.json()).id;
}

/** Volné okno aspoň `minutes` dlouhé u některého lékaře v týdnu za `weeks` týdnů. */
async function freeWindow(minutes, weeks) {
  const weekOf = new Date(Date.now() + weeks * 7 * 864e5).toISOString().slice(0, 10);
  for (const d of await (await api.get('/api/doctors')).json()) {
    const slots = await (await api.get(`/api/appointments/free-slots?doctorId=${d.id}&weekOf=${weekOf}&minutes=${minutes}`)).json();
    if (slots.length) return { doctorId: d.id, start: new Date(slots[0].startAt) };
  }
  throw new Error('Žádné volné okno');
}

const plus = (date, minutes) => new Date(date.getTime() + minutes * 60_000).toISOString();

async function book(doctorId, patientId, start) {
  const res = await api.post('/api/appointments', { doctorId, patientId, startAt: plus(start, 0), durationMinutes: 15 });
  expect(res.status()).toBe(201);
  return (await res.json()).id;
}

test('dva souběžné přesuny téže rezervace: projde jen jeden', async () => {
  const { doctorId, start } = await freeWindow(45, 30 + Math.floor(Math.random() * 10));
  const id = await book(doctorId, await newPatient(), start);

  const [a, b] = await Promise.all([15, 30].map((m) => api.post(`/api/appointments/${id}/reschedule`, { startAt: plus(start, m) })));

  // druhý dostane 409 (verze se mezitím změnila) nebo 422 (načetl už RESCHEDULED)
  const [winner, loser] = [a.status(), b.status()].sort();
  expect(winner).toBe(201);
  expect([409, 422]).toContain(loser);
  const { patientId } = await (await api.get(`/api/appointments/${id}`)).json();
  const history = await (await api.get(`/api/appointments/by-patient/${patientId}`)).json();
  expect(history.filter((x) => x.rescheduledFromId === id)).toHaveLength(1);
});

test('absence současně s objednáním: rezervace v absenci je vždy k přeobjednání a autor změny sedí', async () => {
  const { doctorId, start } = await freeWindow(15, 40 + Math.floor(Math.random() * 10));
  const patientId = await newPatient();

  const [booked, absence] = await Promise.all([
    api.post('/api/appointments', { doctorId, patientId, startAt: plus(start, 0), durationMinutes: 15 }),
    api.post(`/api/doctors/${doctorId}/absences`, { startAt: plus(start, 0), endAt: plus(start, 15), reason: 'Revize souběhu' }),
  ]);

  expect(absence.status()).toBe(201);
  if (booked.status() === 201) {
    const appt = await (await api.get(`/api/appointments/${(await booked.json()).id}`)).json();
    expect(appt.needsRebooking).toBe(true);
    expect(appt.updatedBy).toBe(USER);
  } else {
    expect(booked.status()).toBe(422); // lékař už je nepřítomen
  }
});

test('příliš dlouhé jméno vrátí 400, ne zavádějící 409', async () => {
  const res = await api.post('/api/patients?confirmDuplicate=true',
    { firstName: 'Test', lastName: 'x'.repeat(101), birthYear: 1990, phone: '777000111' });
  expect(res.status()).toBe(400);
});

test('záznam přístupů pacienta obsahuje i nahlédnutí do jeho rezervace', async () => {
  const { doctorId, start } = await freeWindow(15, 50 + Math.floor(Math.random() * 10));
  const patientId = await newPatient();
  const id = await book(doctorId, patientId, start);

  expect((await api.get(`/api/appointments/${id}`)).status()).toBe(200);

  const log = await (await api.get(`/api/patients/${patientId}/access-log`)).json();
  expect(log.map((e) => e.path)).toContain(`/api/appointments/${id}`);
});
