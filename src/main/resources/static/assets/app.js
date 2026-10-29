'use strict';

(() => {
  // ---------- Pomocné funkce ----------

  const $ = (selector, root = document) => root.querySelector(selector);

  /** Vytvoří element; text jde vždy přes textContent, aby jméno pacienta nemohlo vložit HTML. */
  function h(tag, attrs = {}, ...children) {
    const el = document.createElement(tag);
    for (const [key, value] of Object.entries(attrs || {})) {
      if (value === undefined || value === null || value === false) continue;
      if (key === 'class') el.className = value;
      else if (key === 'text') el.textContent = value;
      else if (key.startsWith('on')) el.addEventListener(key.slice(2), value);
      else if (key === 'value') el.value = value;
      else el.setAttribute(key, value === true ? '' : value);
    }
    for (const child of children.flat()) {
      if (child === null || child === undefined || child === false) continue;
      el.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return el;
  }

  const PX_PER_MIN = 1.5;
  const fmtTime = new Intl.DateTimeFormat('cs-CZ', { hour: 'numeric', minute: '2-digit' });
  const fmtDayHead = new Intl.DateTimeFormat('cs-CZ', { weekday: 'long' });
  const fmtDayShort = new Intl.DateTimeFormat('cs-CZ', { day: 'numeric', month: 'numeric' });
  const fmtDate = new Intl.DateTimeFormat('cs-CZ', { day: 'numeric', month: 'numeric', year: 'numeric' });
  const fmtDateLong = new Intl.DateTimeFormat('cs-CZ', { weekday: 'long', day: 'numeric', month: 'long' });
  const fmtDateTime = new Intl.DateTimeFormat('cs-CZ', { day: 'numeric', month: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit' });
  const fmtNumber = new Intl.NumberFormat('cs-CZ');

  const pad = (n) => String(n).padStart(2, '0');
  const isoDate = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  const isoMonth = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}`;
  const startOfDay = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const addDays = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };
  const addMinutes = (d, n) => new Date(d.getTime() + n * 60000);
  const mondayOf = (d) => addDays(startOfDay(d), -((d.getDay() + 6) % 7));
  const minutesOfDay = (d) => d.getHours() * 60 + d.getMinutes();
  const parseTime = (t) => { const [hh, mm] = t.split(':'); return Number(hh) * 60 + Number(mm); };
  const sameDay = (a, b) => isoDate(a) === isoDate(b);
  const range = (a, b) => `${fmtTime.format(a)}–${fmtTime.format(b)}`;
  const capitalize = (s) => s.charAt(0).toUpperCase() + s.slice(1);
  const phone = (p) => (p && p.length === 9 ? `${p.slice(0, 3)} ${p.slice(3, 6)} ${p.slice(6)}` : p || '');

  function debounce(fn, ms) {
    let timer;
    return (...args) => { clearTimeout(timer); timer = setTimeout(() => fn(...args), ms); };
  }

  // ---------- API ----------

  const MESSAGES = [
    ['Doctor already has an appointment', 'Lékař má v tomto čase objednaného jiného pacienta.'],
    ['Patient already has an appointment', 'Pacient má v tomto čase už jiný termín.'],
    ['office-hours', 'Termín musí celý ležet v ordinačních hodinách, kdy se objednává.'],
    ['before today', 'Do minulých dnů objednat nelze.'],
    ['absent in this time', 'Lékař je v tomto čase nepřítomen.'],
    ['multiple of 5', 'Délka termínu musí být v násobcích 5 minut.'],
    ['must be active', 'Lékař i pacient musí být aktivní.'],
    ['has not started yet', 'Termín ještě nezačal.'],
    ['before the appointment starts', 'Návštěvu lze zapsat až po začátku termínu.'],
    ['Only a BOOKED', 'Rezervace už byla dokončena, zrušena nebo přesunuta.'],
    ['Visit is already recorded', 'Návštěva už je zapsaná.'],
    ['is already recorded on this visit', 'Tento výkon už je u návštěvy zapsaný. Pro jiný počet ho stornujte a zapište znovu.'],
    ['cannot have quantity', 'U tohoto výkonu se počet nezadává.'],
    ['already voided', 'Výkon už je stornovaný.'],
    ['Insurance id is already registered', 'Pacient s tímto číslem pojištěnce už v evidenci je.'],
    ['same name, birth year and phone', 'Pacient se stejným jménem, rokem narození a telefonem už v evidenci je.'],
    ['Birth year', 'Rok narození není platný.'],
    ['Phone number', 'Telefon musí obsahovat číslice.'],
    ['upcoming appointments', 'Nejde to – existují budoucí rezervace. Nejdřív je zrušte.'],
    ['Absence must end after', 'Konec nepřítomnosti musí být po jejím začátku.'],
    ['conflicts with existing data', 'Mezitím to změnil někdo jiný (např. obsadil termín). Načtěte údaje znovu.'],
  ];
  const BY_STATUS = {
    400: 'Zkontrolujte vyplněné údaje.',
    403: 'Na tuto akci nemáte oprávnění. Obnovte stránku.',
    404: 'Záznam nebyl nalezen.',
    409: 'Akce je v rozporu s existujícími údaji.',
    422: 'Akci nejde provést.',
  };

  class ApiError extends Error {
    constructor(status, message, problem) {
      super(message);
      this.status = status;
      this.problem = problem || {};
    }
  }

  function translate(status, detail) {
    const hit = detail && MESSAGES.find(([needle]) => detail.includes(needle));
    if (hit) return hit[1];
    return BY_STATUS[status] || 'Server teď neodpovídá. Zkuste to za chvíli znovu.';
  }

  function csrfToken() {
    const cookie = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='));
    return cookie ? decodeURIComponent(cookie.split('=')[1]) : '';
  }

  async function api(path, { method = 'GET', body } = {}) {
    const headers = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (method !== 'GET') headers['X-XSRF-TOKEN'] = csrfToken();
    let res;
    try {
      res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), credentials: 'same-origin' });
    } catch {
      throw new ApiError(0, 'Nepodařilo se spojit se serverem. Zkontrolujte připojení.');
    }
    if (res.status === 401) {
      location.href = '/login.html?expired';
      throw new ApiError(401, 'Přihlášení vypršelo.');
    }
    if (!res.ok) {
      let problem = {};
      try { problem = await res.json(); } catch { /* tělo nemusí být JSON */ }
      throw new ApiError(res.status, translate(res.status, problem.detail), problem);
    }
    return res.status === 204 ? null : res.json();
  }

  // ---------- Upozornění a dialog ----------

  function toast(message, kind = 'ok') {
    const el = h('div', { class: `toast ${kind === 'error' ? 'error' : ''}`, text: message });
    $('#toasts').append(el);
    setTimeout(() => el.remove(), kind === 'error' ? 7000 : 4000);
  }

  const showError = (e) => toast(e.message || 'Něco se nepovedlo.', 'error');

  async function run(fn) {
    try { return await fn(); } catch (e) { showError(e); return undefined; }
  }

  const dialog = $('#dialog');

  function openDialog(title, body, { wide = false } = {}) {
    $('#dialog-title').textContent = title;
    $('#dialog-body').replaceChildren(body);
    dialog.classList.toggle('wide', wide);
    if (!dialog.open) dialog.showModal();
    const first = $('#dialog-body').querySelector('[autofocus], input, select, textarea, button.primary');
    if (first) first.focus();
  }

  const closeDialog = () => dialog.open && dialog.close();

  /** Chybová hláška uvnitř formuláře, aby ji uživatel viděl u toho, co vyplňuje. */
  function formError() {
    const el = h('p', { class: 'notice error', role: 'alert', hidden: true });
    return {
      el,
      show(message, ...extra) { el.replaceChildren(...[message, ...extra].filter(Boolean)); el.hidden = false; },
      clear() { el.hidden = true; },
    };
  }

  async function submitting(button, fn) {
    button.disabled = true;
    try { await fn(); } finally { button.disabled = false; }
  }

  // ---------- Stav ----------

  const STATUS = {
    BOOKED: 'Objednáno',
    COMPLETED: 'Proběhlo',
    CANCELLED: 'Zrušeno',
    NO_SHOW: 'Nedostavil se',
    RESCHEDULED: 'Přesunuto',
  };

  const state = {
    doctors: [],
    insurers: null,
    procedures: null,
    doctorId: null,
    week: mondayOf(new Date()),
    minutes: 15,
    mode: null,
    diary: null,
  };

  const doctorName = (id) => {
    const d = state.doctors.find((x) => x.id === id);
    return d ? `MUDr. ${d.firstName} ${d.lastName}` : 'Neznámý lékař';
  };

  function statusChip(a) {
    if (a.status === 'BOOKED' && a.needsRebooking) return h('span', { class: 'status rebook', text: 'K přeobjednání' });
    return h('span', { class: `status ${a.status}`, text: STATUS[a.status] });
  }

  // ---------- Navigace ----------

  const VIEWS = ['diar', 'rozpis', 'pacienti', 'preobjednat', 'vykazy', 'ruseni'];

  function route() {
    const [view, param] = location.hash.replace('#', '').split('/');
    const current = VIEWS.includes(view) ? view : 'diar';
    closeDialog();
    for (const v of VIEWS) $(`#view-${v}`).hidden = v !== current;
    document.querySelectorAll('.tabs a').forEach((a) => {
      if (a.dataset.view === current) a.setAttribute('aria-current', 'page');
      else a.removeAttribute('aria-current');
    });
    if (current === 'diar') loadDiary();
    if (current === 'rozpis') loadDay();
    if (current === 'pacienti') {
      if (param) showPatientCard(Number(param));
      else $('#patient-search').focus();
    }
    if (current === 'preobjednat') loadRebooking();
    if (current === 'vykazy' && !$('#report').hasChildNodes()) loadReport();
    if (current === 'ruseni' && !$('#stats').hasChildNodes()) loadStats();
  }

  const go = (hash) => {
    if (location.hash === hash) route();
    else location.hash = hash;
  };

  // ---------- Diář ----------

  // víc tlačítek se na tablet vedle sebe nevejde, pak je místo nich rozbalovací seznam
  const MAX_DOCTOR_BUTTONS = 4;

  function renderDoctorPicker() {
    const picker = $('#doctor-picker');
    const choose = (id) => { state.doctorId = id; saveDoctor(); renderDoctorPicker(); loadDiary(); };
    const many = state.doctors.length > MAX_DOCTOR_BUTTONS;
    picker.classList.toggle('segmented', !many);
    if (many) {
      const select = h('select', { 'aria-label': 'Lékař', onchange: (ev) => choose(Number(ev.target.value)) },
        state.doctors.map((d) => h('option', { value: d.id, text: `MUDr. ${d.lastName} ${d.firstName}` })));
      select.value = String(state.doctorId);
      picker.replaceChildren(select);
      return;
    }
    picker.replaceChildren(...state.doctors.map((d) => h('button', {
      type: 'button',
      'aria-pressed': String(d.id === state.doctorId),
      text: `MUDr. ${d.lastName}`,
      onclick: () => choose(d.id),
    })));
  }

  function saveDoctor() {
    try { localStorage.setItem('doctorId', String(state.doctorId)); } catch { /* jen pohodlí */ }
  }

  function renderWeekLabel() {
    const end = addDays(state.week, 6);
    $('#week-label').textContent = `${fmtDayShort.format(state.week)} – ${fmtDate.format(end)}`;
  }

  function renderModeBanner() {
    const banner = $('#mode-banner');
    if (!state.mode) { banner.hidden = true; return; }
    const { type, appt, patient } = state.mode;
    $('#mode-text').textContent = type === 'reschedule'
      ? `Vyberte nový termín pro ${appt.patientName} (původně ${fmtDateLong.format(new Date(appt.startAt))} ${fmtTime.format(new Date(appt.startAt))}).`
      : `Vyberte volný termín pro ${patient.lastName} ${patient.firstName}.`;
    banner.hidden = false;
  }

  async function loadDiary() {
    renderWeekLabel();
    renderModeBanner();
    const doctorId = state.doctorId;
    if (!doctorId) {
      $('#diary').replaceChildren(h('div', { class: 'diary-empty', text: 'V evidenci zatím není žádný lékař.' }));
      return;
    }
    const from = isoDate(state.week);
    const to = isoDate(addDays(state.week, 6));
    $('#diary').setAttribute('aria-busy', 'true');
    await run(async () => {
      const [hours, appts, free, absences] = await Promise.all([
        api(`/api/doctors/${doctorId}/office-hours`),
        api(`/api/appointments?doctorId=${doctorId}&from=${from}&to=${to}`),
        api(`/api/appointments/free-slots?doctorId=${doctorId}&weekOf=${from}&minutes=${state.minutes}`),
        api(`/api/doctors/${doctorId}/absences`),
      ]);
      if (doctorId !== state.doctorId || from !== isoDate(state.week)) return;
      state.diary = { hours, appts, free, absences };
      renderDiary();
    });
    $('#diary').setAttribute('aria-busy', 'false');
  }

  function positioned(el, top, height) {
    el.style.top = `${top * PX_PER_MIN}px`;
    el.style.height = `${Math.max(height * PX_PER_MIN, 18)}px`;
    if (height * PX_PER_MIN < 40) el.classList.add('compact');
    return el;
  }

  function renderDiary() {
    const { hours, appts, free, absences } = state.diary;
    const diary = $('#diary');
    if (hours.length === 0) {
      diary.style.gridTemplateColumns = '1fr';
      diary.replaceChildren(h('div', { class: 'diary-empty', text: 'Lékař nemá nastavené ordinační hodiny.' }));
      return;
    }
    const weekdays = [1, 2, 3, 4, 5].concat([6, 7].filter((d) => hours.some((x) => x.weekday === d)));
    const apptMinutes = appts.flatMap((a) => [minutesOfDay(new Date(a.startAt)), minutesOfDay(new Date(a.endAt))]);
    const first = Math.floor(Math.min(...hours.map((x) => parseTime(x.from)), ...apptMinutes) / 60) * 60;
    const last = Math.ceil(Math.max(...hours.map((x) => parseTime(x.to)), ...apptMinutes) / 60) * 60;
    const height = (last - first) * PX_PER_MIN;
    const today = new Date();

    diary.style.gridTemplateColumns = `56px repeat(${weekdays.length}, minmax(140px, 1fr))`;
    const cells = [h('div', { class: 'day-head', 'aria-hidden': 'true' })];
    for (const wd of weekdays) {
      const date = addDays(state.week, wd - 1);
      cells.push(h('div', { class: `day-head ${sameDay(date, today) ? 'today' : ''}` },
        capitalize(fmtDayHead.format(date)), h('small', { text: fmtDayShort.format(date) })));
    }

    const gutter = h('div', { class: 'gutter', 'aria-hidden': 'true' });
    gutter.style.height = `${height}px`;
    for (let m = first + 60; m < last; m += 60) {
      const label = h('span', { text: `${m / 60}:00` });
      label.style.top = `${(m - first) * PX_PER_MIN}px`;
      gutter.append(label);
    }
    cells.push(gutter);

    for (const wd of weekdays) {
      const date = addDays(state.week, wd - 1);
      const dayStart = startOfDay(date);
      const dayEnd = addDays(dayStart, 1);
      const col = h('div', { class: `day ${dayEnd <= today ? 'past' : ''}`, role: 'group', 'aria-label': capitalize(fmtDateLong.format(date)) });
      col.style.height = `${height}px`;
      for (let m = first + 60; m < last; m += 60) {
        const line = h('div', { class: 'hour-line' });
        line.style.top = `${(m - first) * PX_PER_MIN}px`;
        col.append(line);
      }
      for (const x of hours.filter((y) => y.weekday === wd)) {
        const from = parseTime(x.from);
        const to = parseTime(x.to);
        const block = x.bookable
          ? h('div', { class: 'block hours' })
          : h('div', { class: 'block offsite' }, h('strong', { text: x.location }), h('span', { class: 'reason', text: 'Zde se neobjednává' }));
        col.append(positioned(block, from - first, to - from));
      }
      for (const ab of absences) {
        const s = new Date(Math.max(new Date(ab.startAt), dayStart));
        const e = new Date(Math.min(new Date(ab.endAt), dayEnd));
        if (s >= e) continue;
        const top = Math.max(minutesOfDay(s), first);
        const bottom = e >= dayEnd ? last : Math.min(minutesOfDay(e), last);
        if (bottom <= top) continue;
        col.append(positioned(h('div', { class: 'block absent' }, 'Nepřítomnost', ab.reason ? h('span', { class: 'reason', text: ab.reason }) : null), top - first, bottom - top));
      }
      for (const w of free.filter((x) => sameDay(new Date(x.startAt), date))) {
        const s = new Date(w.startAt);
        const e = new Date(w.endAt);
        col.append(positioned(h('button', {
          type: 'button',
          class: 'block free',
          'aria-label': `Volno ${capitalize(fmtDateLong.format(s))} ${range(s, e)}`,
          onclick: () => onFreeClick(w),
        }, h('strong', { text: 'Volno' }), range(s, e)), minutesOfDay(s) - first, (e - s) / 60000));
      }
      for (const a of appts.filter((x) => sameDay(new Date(x.startAt), date))) {
        const s = new Date(a.startAt);
        const e = new Date(a.endAt);
        const rebook = a.status === 'BOOKED' && a.needsRebooking;
        col.append(positioned(h('button', {
          type: 'button',
          class: `block appt ${a.status} ${rebook ? 'rebook' : ''}`,
          'data-id': a.id,
          'aria-label': `${range(s, e)} ${a.patientName}, ${rebook ? 'k přeobjednání' : STATUS[a.status]}`,
          onclick: () => openAppointment(a),
        }, h('span', { class: 'name' }, h('span', { class: 'time', text: `${fmtTime.format(s)} ` }), a.patientName),
        a.reason ? h('span', { class: 'reason', text: a.reason }) : null), minutesOfDay(s) - first, (e - s) / 60000));
      }
      if (sameDay(date, today) && minutesOfDay(today) > first && minutesOfDay(today) < last) {
        const now = h('div', { class: 'now-line', 'aria-hidden': 'true' });
        now.style.top = `${(minutesOfDay(today) - first) * PX_PER_MIN}px`;
        col.append(now);
      }
      cells.push(col);
    }
    diary.replaceChildren(...cells);
  }

  function onFreeClick(window) {
    if (state.mode?.type === 'reschedule') openReschedule(state.mode.appt, window);
    else openBooking(window, state.mode?.type === 'book' ? state.mode.patient : null);
  }

  function clearMode() {
    state.mode = null;
    renderModeBanner();
  }

  /** Výběr začátku po 5 minutách tak, aby se termín vešel do volného okna. */
  function startSelect(window, minutes, preferred) {
    const select = h('select', { required: true });
    const start = new Date(window.startAt);
    const end = new Date(window.endAt);
    for (let t = start; addMinutes(t, minutes) <= end; t = addMinutes(t, 5)) {
      select.append(h('option', { value: t.toISOString(), text: fmtTime.format(t) }));
    }
    if ([...select.options].some((o) => o.value === preferred)) select.value = preferred;
    return select;
  }

  function durationSelect(selected) {
    const select = h('select');
    for (const m of [5, 10, 15, 20, 30, 45, 60, 90]) select.append(h('option', { value: m, text: `${m} min` }));
    select.value = String(selected);
    return select;
  }

  function patientPicker(initial) {
    let selected = initial;
    const wrap = h('div', { class: 'stack' });
    const render = () => {
      if (selected) {
        wrap.replaceChildren(h('div', { class: 'chosen' },
          h('div', {}, h('strong', { text: `${selected.lastName} ${selected.firstName}` }),
            h('div', { class: 'muted', text: `nar. ${selected.birthYear}, tel. ${phone(selected.phone)}` })),
          h('button', { type: 'button', class: 'btn ghost small', text: 'Změnit', onclick: () => { selected = null; render(); wrap.querySelector('input').focus(); } })));
        return;
      }
      const results = h('div', { class: 'picker-results', role: 'listbox' });
      const input = h('input', { type: 'search', autocomplete: 'off', placeholder: 'Příjmení nebo telefon', 'aria-label': 'Najít pacienta' });
      const search = debounce(async () => {
        const q = input.value.trim();
        if (q.length < 2) { results.replaceChildren(); return; }
        const found = await run(() => api(`/api/patients?query=${encodeURIComponent(q)}`));
        if (!found) return;
        results.replaceChildren(...(found.length ? found.map((p) => patientItem(p, () => { selected = p; render(); }))
          : [h('p', { class: 'muted empty', text: 'Nikdo takový v evidenci není.' })]));
      }, 200);
      input.addEventListener('input', search);
      wrap.replaceChildren(h('label', { class: 'field' }, h('span', { text: 'Pacient' }), input), results);
    };
    render();
    return { el: wrap, get value() { return selected; } };
  }

  function patientItem(p, onclick) {
    return h('button', { type: 'button', class: 'list-item', role: 'option', onclick },
      h('span', { class: 'title', text: `${p.lastName} ${p.firstName}` }),
      h('span', { class: 'side', text: p.insurer ? p.insurer.code : 'bez pojišťovny' }),
      h('span', { class: 'meta', text: `nar. ${p.birthYear}, tel. ${phone(p.phone)}` }));
  }

  function openBooking(window, presetPatient, title = 'Nový termín') {
    const err = formError();
    const picker = patientPicker(presetPatient);
    const duration = durationSelect(state.minutes);
    let start = startSelect(window, state.minutes);
    const startField = h('label', { class: 'field' }, h('span', { text: 'Začátek' }), start);
    duration.addEventListener('change', () => {
      const next = startSelect(window, Number(duration.value), start.value);
      start.replaceWith(next);
      start = next;
    });
    const reason = h('input', { maxlength: 200, placeholder: 'např. kontrola tlaku, očkování' });
    const submit = h('button', { type: 'submit', class: 'btn primary', text: 'Objednat' });
    const form = h('form', { class: 'stack' },
      h('p', { class: 'muted', text: `${doctorName(state.doctorId)}, ${fmtDateLong.format(new Date(window.startAt))}` }),
      picker.el,
      h('div', { class: 'row' }, startField, h('label', { class: 'field' }, h('span', { text: 'Délka' }), duration)),
      h('label', { class: 'field' }, h('span', {}, 'Důvod návštěvy ', h('span', { class: 'hint', text: '(nepovinné)' })), reason),
      err.el,
      h('div', { class: 'actions' }, submit,
        h('button', { type: 'button', class: 'btn ghost', text: 'Nový pacient', onclick: () => openPatientForm(null, (p) => openBooking(window, p, title)) }),
        h('span', { class: 'spacer' }),
        h('button', { type: 'button', class: 'btn ghost', text: 'Zavřít', onclick: closeDialog })));
    form.addEventListener('submit', (ev) => {
      ev.preventDefault();
      err.clear();
      if (!picker.value) { err.show('Vyberte pacienta.'); return; }
      if (!start.value) { err.show('Do tohoto okna se termín zvolené délky nevejde.'); return; }
      submitting(submit, async () => {
        try {
          const created = await api('/api/appointments', {
            method: 'POST',
            body: { doctorId: state.doctorId, patientId: picker.value.id, startAt: start.value, durationMinutes: Number(duration.value), reason: reason.value },
          });
          closeDialog();
          clearMode();
          toast(`Objednáno: ${created.patientName}, ${fmtDateLong.format(new Date(created.startAt))} v ${fmtTime.format(new Date(created.startAt))}.`);
          loadDiary();
        } catch (e) {
          err.show(e.message);
          if (e.status === 409) loadDiary();
        }
      });
    });
    openDialog(title, form);
  }

  function openReschedule(appt, window) {
    const err = formError();
    const originalMinutes = (new Date(appt.endAt) - new Date(appt.startAt)) / 60000;
    const duration = durationSelect(originalMinutes);
    let start = startSelect(window, originalMinutes);
    duration.addEventListener('change', () => {
      const next = startSelect(window, Number(duration.value), start.value);
      start.replaceWith(next);
      start = next;
    });
    const submit = h('button', { type: 'submit', class: 'btn primary', text: 'Přesunout termín' });
    const form = h('form', { class: 'stack' },
      h('p', {}, h('strong', { text: appt.patientName }), h('br'),
        h('span', { class: 'muted', text: `Nový termín: ${fmtDateLong.format(new Date(window.startAt))}` })),
      h('div', { class: 'row' },
        h('label', { class: 'field' }, h('span', { text: 'Začátek' }), start),
        h('label', { class: 'field' }, h('span', { text: 'Délka' }), duration)),
      err.el,
      h('div', { class: 'actions' }, submit, h('button', { type: 'button', class: 'btn ghost', text: 'Zavřít', onclick: closeDialog })));
    form.addEventListener('submit', (ev) => {
      ev.preventDefault();
      if (!start.value) { err.show('Do tohoto okna se termín zvolené délky nevejde.'); return; }
      submitting(submit, async () => {
        try {
          const moved = await api(`/api/appointments/${appt.id}/reschedule`, {
            method: 'POST', body: { startAt: start.value, durationMinutes: Number(duration.value) },
          });
          closeDialog();
          clearMode();
          toast(`Termín přesunut na ${fmtDateLong.format(new Date(moved.startAt))} v ${fmtTime.format(new Date(moved.startAt))}.`);
          loadDiary();
          refreshRebookCount();
        } catch (e) {
          err.show(e.message);
          if (e.status === 409) loadDiary();
        }
      });
    });
    openDialog('Přesunout termín', form);
  }

  function startReschedule(appt) {
    state.mode = { type: 'reschedule', appt };
    if (state.doctorId !== appt.doctorId) {
      state.doctorId = appt.doctorId;
      renderDoctorPicker();
    }
    if (new Date(appt.startAt) < state.week) state.week = mondayOf(new Date());
    closeDialog();
    go('#diar');
  }

  async function openAppointment(appt) {
    const a = await run(() => api(`/api/appointments/${appt.id}`));
    if (!a) return;
    const s = new Date(a.startAt);
    const e = new Date(a.endAt);
    const started = s <= new Date();
    const err = formError();
    const actions = h('div', { class: 'actions' });

    const done = (message) => { closeDialog(); toast(message); loadDiary(); refreshRebookCount(); };
    const act = (button, path, message) => submitting(button, async () => {
      try { await api(path, { method: 'POST' }); done(message); } catch (ex) { err.show(ex.message); }
    });

    const confirmCancel = () => {
      const yes = h('button', { type: 'button', class: 'btn danger', text: 'Ano, zrušit rezervaci' });
      yes.addEventListener('click', () => act(yes, `/api/appointments/${a.id}/cancel`, 'Rezervace zrušena, termín je opět volný.'));
      actions.replaceChildren(h('p', { text: 'Opravdu zrušit? Termín se uvolní a zrušení se započítá pacientovi do přehledu rušení.' }),
        yes, h('button', { type: 'button', class: 'btn ghost', text: 'Ne, ponechat', onclick: () => openAppointment(a) }));
    };

    const items = [];
    if (a.status === 'BOOKED') {
      if (started) items.push(h('button', { type: 'button', class: 'btn primary', text: 'Zapsat návštěvu', onclick: () => openVisit(a) }));
      items.push(h('button', { type: 'button', class: 'btn', text: 'Přesunout', onclick: () => startReschedule(a) }));
      if (started) {
        const noShow = h('button', { type: 'button', class: 'btn', text: 'Nedostavil se' });
        noShow.addEventListener('click', () => act(noShow, `/api/appointments/${a.id}/no-show`, 'Zaznamenáno: pacient se nedostavil.'));
        items.push(noShow);
      }
      items.push(h('button', { type: 'button', class: 'btn danger', text: 'Zrušit rezervaci', onclick: confirmCancel }));
    }
    if (a.status === 'COMPLETED') items.push(h('button', { type: 'button', class: 'btn primary', text: 'Otevřít návštěvu', onclick: () => openVisit(a) }));
    items.push(h('span', { class: 'spacer' }), h('a', { class: 'btn ghost', href: `#pacienti/${a.patientId}`, text: 'Karta pacienta', onclick: closeDialog }));
    actions.replaceChildren(...items);

    openDialog(a.patientName, h('div', {},
      h('dl', { class: 'detail-grid' },
        h('dt', { text: 'Termín' }), h('dd', { text: `${capitalize(fmtDateLong.format(s))}, ${range(s, e)}` }),
        h('dt', { text: 'Lékař' }), h('dd', { text: doctorName(a.doctorId) }),
        h('dt', { text: 'Telefon' }), h('dd', {}, h('a', { href: `tel:${a.patientPhone}`, text: phone(a.patientPhone) })),
        h('dt', { text: 'Důvod' }), h('dd', { text: a.reason || '–' }),
        h('dt', { text: 'Stav' }), h('dd', {}, statusChip(a)),
        a.createdBy ? [h('dt', { text: 'Objednal(a)' }), h('dd', { text: a.createdBy })] : null,
        a.updatedBy && a.status !== 'BOOKED'
          ? [h('dt', { text: 'Poslední změna' }), h('dd', { text: `${a.updatedBy}, ${fmtDateTime.format(new Date(a.updatedAt))}` })]
          : null),
      a.needsRebooking && a.status === 'BOOKED' ? h('p', { class: 'notice warn', text: 'Lékař je v tomto čase nepřítomen. Domluvte s pacientem nový termín.' }) : null,
      err.el, actions));
  }

  // ---------- Návštěva a výkony ----------

  async function openVisit(appt) {
    const visit = await run(() => (appt.status === 'COMPLETED'
      ? api(`/api/appointments/${appt.id}/visit`)
      : api(`/api/appointments/${appt.id}/visit`, { method: 'POST', body: {} })));
    if (!visit) return;
    if (!state.procedures) state.procedures = await run(() => api('/api/procedures'));
    if (!state.procedures) return;
    loadDiary();
    renderVisit(appt, visit);
  }

  function renderVisit(appt, visit) {
    const err = formError();
    const s = new Date(appt.startAt);
    const update = (v) => { visit = v; renderVisit(appt, v); };

    const note = h('textarea', { maxlength: 500, placeholder: 'Krátká poznámka k návštěvě' });
    note.value = visit.note || '';
    const saveNote = h('button', { type: 'button', class: 'btn small', text: 'Uložit poznámku' });
    saveNote.addEventListener('click', () => submitting(saveNote, async () => {
      try { await api(`/api/visits/${visit.id}/note`, { method: 'PUT', body: { note: note.value } }); toast('Poznámka uložena.'); } catch (e) { err.show(e.message); }
    }));

    const list = visit.procedures.length
      ? h('ul', { class: 'procedures' }, visit.procedures.map((p) => {
        const voidBtn = h('button', { type: 'button', class: 'btn ghost small', text: 'Stornovat', 'aria-label': `Stornovat ${p.name}` });
        voidBtn.addEventListener('click', () => submitting(voidBtn, async () => {
          try { update(await api(`/api/visits/${visit.id}/procedures/${p.id}/void`, { method: 'POST' })); } catch (e) { err.show(e.message); }
        }));
        return h('li', { class: p.voidedAt ? 'voided' : '' },
          h('strong', { text: p.code }),
          h('span', { text: p.quantity > 1 ? `${p.name} × ${p.quantity}` : p.name }),
          h('span', { class: 'muted', text: `${fmtNumber.format(p.quantity * p.pointsAtTime)} b.` }),
          voidBtn);
      }))
      : h('p', { class: 'muted', text: 'Zatím nejsou zapsané žádné výkony.' });

    const select = h('select', { 'aria-label': 'Výkon' },
      h('option', { value: '', text: 'Vyberte výkon z číselníku' }),
      state.procedures.map((p) => h('option', { value: p.id, text: `${p.code} – ${p.name} (${p.points} b.)` })));
    const qty = h('input', { type: 'number', min: 1, max: 99, value: '1', 'aria-label': 'Počet', disabled: true });
    select.addEventListener('change', () => {
      const item = state.procedures.find((p) => String(p.id) === select.value);
      qty.disabled = !item || !item.countable;
      if (qty.disabled) qty.value = '1';
    });
    const add = h('button', { type: 'submit', class: 'btn primary', text: 'Přidat výkon' });
    const addForm = h('form', { class: 'add-procedure' },
      h('label', { class: 'field' }, h('span', { text: 'Výkon' }), select),
      h('label', { class: 'field' }, h('span', { text: 'Počet' }), qty), add);
    addForm.addEventListener('submit', (ev) => {
      ev.preventDefault();
      err.clear();
      if (!select.value) { err.show('Vyberte výkon.'); return; }
      submitting(add, async () => {
        try {
          update(await api(`/api/visits/${visit.id}/procedures`, { method: 'POST', body: { procedureId: Number(select.value), quantity: Number(qty.value) } }));
        } catch (e) { err.show(e.message); }
      });
    });

    openDialog(`Návštěva: ${appt.patientName}`, h('div', { class: 'stack' },
      h('p', { class: 'muted', text: `${capitalize(fmtDateLong.format(s))}, ${fmtTime.format(s)}, ${doctorName(appt.doctorId)}` }),
      h('label', { class: 'field' }, h('span', { text: 'Poznámka' }), note),
      h('div', {}, saveNote),
      h('h3', { text: 'Provedené výkony' }),
      list,
      addForm,
      err.el,
      h('div', { class: 'total' }, h('span', { class: 'muted', text: 'Celkem' }), h('strong', { text: fmtNumber.format(visit.totalPoints) }), h('span', { text: 'bodů' })),
      h('div', { class: 'actions' }, h('span', { class: 'spacer' }), h('button', { type: 'button', class: 'btn', text: 'Hotovo', onclick: closeDialog }))), { wide: true });
  }

  // ---------- Nepřítomnost a akutní pacient ----------

  function openAbsence() {
    const err = formError();
    const today = startOfDay(new Date());
    const local = (d) => `${isoDate(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
    const from = h('input', { type: 'datetime-local', required: true, value: local(addMinutes(today, 7 * 60)) });
    const to = h('input', { type: 'datetime-local', required: true, value: local(addMinutes(today, 18 * 60)) });
    const reason = h('input', { maxlength: 200, placeholder: 'např. nemoc, dovolená, školení' });
    const submit = h('button', { type: 'submit', class: 'btn primary', text: 'Uložit nepřítomnost' });
    const form = h('form', { class: 'stack' },
      h('p', { class: 'muted', text: `${doctorName(state.doctorId)}. Do nepřítomnosti nepůjde objednávat a objednaní pacienti se objeví v seznamu K přeobjednání.` }),
      h('div', { class: 'row' },
        h('label', { class: 'field' }, h('span', { text: 'Od' }), from),
        h('label', { class: 'field' }, h('span', { text: 'Do' }), to)),
      h('label', { class: 'field' }, h('span', {}, 'Důvod ', h('span', { class: 'hint', text: '(nepovinné)' })), reason),
      err.el,
      h('div', { class: 'actions' }, submit, h('button', { type: 'button', class: 'btn ghost', text: 'Zavřít', onclick: closeDialog })));
    form.addEventListener('submit', (ev) => {
      ev.preventDefault();
      submitting(submit, async () => {
        try {
          const result = await api(`/api/doctors/${state.doctorId}/absences`, {
            method: 'POST', body: { startAt: new Date(from.value).toISOString(), endAt: new Date(to.value).toISOString(), reason: reason.value },
          });
          closeDialog();
          toast(result.flaggedAppointments
            ? `Nepřítomnost uložena. K přeobjednání: ${result.flaggedAppointments}.`
            : 'Nepřítomnost uložena. Žádný objednaný pacient není zasažen.');
          loadDiary();
          refreshRebookCount();
        } catch (e) { err.show(e.message); }
      });
    });
    openDialog('Nepřítomnost lékaře', form);
  }

  function openAcute() {
    const now = new Date();
    const start = new Date(Math.floor(now.getTime() / 300000) * 300000);
    openBooking({ startAt: start.toISOString(), endAt: addMinutes(start, 120).toISOString() }, null, 'Akutní pacient');
  }

  // ---------- Pacienti ----------

  async function loadInsurers() {
    if (!state.insurers) state.insurers = await api('/api/insurers');
    return state.insurers;
  }

  const patientSearch = debounce(async () => {
    const q = $('#patient-search').value.trim();
    const list = $('#patient-results');
    if (q.length < 2) { list.replaceChildren(); return; }
    const found = await run(() => api(`/api/patients?query=${encodeURIComponent(q)}`));
    if (!found) return;
    list.replaceChildren(...(found.length
      ? found.map((p) => h('li', {}, patientItem(p, () => go(`#pacienti/${p.id}`))))
      : [h('li', { class: 'empty' }, h('p', { text: 'Nikoho takového jsme nenašli.' }),
        h('button', { type: 'button', class: 'btn', text: 'Založit nového pacienta', onclick: () => openPatientForm(null) }))]));
  }, 200);

  async function showPatientCard(id) {
    const card = $('#patient-card');
    const data = await run(() => Promise.all([api(`/api/patients/${id}`), api(`/api/appointments/by-patient/${id}`)]));
    if (!data) return;
    const [p, history] = data;
    const now = new Date();
    const upcoming = history.filter((a) => a.status === 'BOOKED' && new Date(a.endAt) >= now).reverse();
    const past = history.filter((a) => !(a.status === 'BOOKED' && new Date(a.endAt) >= now));
    const cancelled = history.filter((a) => a.status === 'CANCELLED').length;
    const noShow = history.filter((a) => a.status === 'NO_SHOW').length;

    const historyList = (items) => h('ul', { class: 'history' }, items.map((a) => h('li', {},
      h('span', { class: 'when', text: fmtDateTime.format(new Date(a.startAt)) }),
      h('span', {}, h('button', { type: 'button', class: 'btn ghost small', text: doctorName(a.doctorId), onclick: () => openAppointment(a) }),
        a.reason ? h('span', { class: 'muted', text: ` ${a.reason}` }) : null),
      statusChip(a))));

    const book = () => {
      state.mode = { type: 'book', patient: p };
      go('#diar');
    };

    card.replaceChildren(...[
      h('div', { class: 'card-head' },
        h('div', {}, h('h2', { text: `${p.lastName} ${p.firstName}` }), h('p', { class: 'muted', text: `nar. ${p.birthYear}` })),
        h('div', { class: 'actions' },
          p.active ? h('button', { type: 'button', class: 'btn primary', text: 'Objednat', onclick: book }) : null,
          h('button', { type: 'button', class: 'btn', text: 'Upravit', onclick: () => openPatientForm(p) }))),
      p.active ? null : h('p', { class: 'notice warn', text: 'Pacient je vyřazený z evidence. Historie zůstává zachovaná.' }),
      h('dl', { class: 'detail-grid' },
        h('dt', { text: 'Telefon' }), h('dd', {}, h('a', { href: `tel:${p.phone}`, text: phone(p.phone) })),
        h('dt', { text: 'Pojišťovna' }), h('dd', { text: p.insurer ? `${p.insurer.code} – ${p.insurer.name}` : '–' }),
        h('dt', { text: 'Číslo pojištěnce' }), h('dd', { text: p.insuranceId || '–' }),
        h('dt', { text: 'Zrušil / nepřišel' }), h('dd', { text: `${cancelled}× / ${noShow}×` })),
      h('h3', { class: 'section-title', text: 'Nadcházející termíny' }),
      upcoming.length ? historyList(upcoming) : h('p', { class: 'muted', text: 'Žádné.' }),
      h('h3', { class: 'section-title', text: 'Historie' }),
      past.length ? historyList(past) : h('p', { class: 'muted', text: 'Zatím žádné návštěvy.' }),
      h('h3', { class: 'section-title', text: 'Osobní údaje' }),
      h('div', { class: 'actions' },
        h('a', { class: 'btn small', href: `/api/patients/${p.id}/export`, download: `pacient-${p.id}.json`, text: 'Stáhnout všechny údaje' }),
        h('button', { type: 'button', class: 'btn small', text: 'Kdo kartu zobrazil', onclick: () => openAccessLog(p) }),
        p.active ? h('button', { type: 'button', class: 'btn danger small', text: 'Vyřadit z evidence', onclick: () => confirmDeactivate(p) }) : null),
    ].filter(Boolean));
  }

  function confirmDeactivate(p) {
    const err = formError();
    const yes = h('button', { type: 'button', class: 'btn danger', text: 'Vyřadit' });
    yes.addEventListener('click', () => submitting(yes, async () => {
      try {
        await api(`/api/patients/${p.id}/deactivate`, { method: 'POST' });
        closeDialog();
        toast('Pacient vyřazen z evidence.');
        showPatientCard(p.id);
      } catch (e) { err.show(e.message); }
    }));
    openDialog('Vyřadit pacienta z evidence?', h('div', { class: 'stack' },
      h('p', { text: `${p.lastName} ${p.firstName} se přestane nabízet ve vyhledávání a nepůjde objednat. Zdravotnická dokumentace a historie návštěv zůstanou uložené, jak vyžaduje zákon.` }),
      err.el,
      h('div', { class: 'actions' }, yes, h('button', { type: 'button', class: 'btn ghost', text: 'Ponechat', onclick: closeDialog }))));
  }

  async function openAccessLog(p) {
    const log = await run(() => api(`/api/patients/${p.id}/access-log`));
    if (!log) return;
    const ACTION = { GET: 'Zobrazení', POST: 'Změna', PUT: 'Úprava' };
    openDialog(`Přístupy ke kartě: ${p.lastName} ${p.firstName}`, h('div', { class: 'table-wrap' },
      h('table', {},
        h('thead', {}, h('tr', {}, h('th', { text: 'Kdy' }), h('th', { text: 'Kdo' }), h('th', { text: 'Akce' }), h('th', { text: 'Část' }))),
        h('tbody', {}, log.map((e) => h('tr', {},
          h('td', { text: fmtDateTime.format(new Date(e.at)) }),
          h('td', { text: e.username }),
          h('td', { text: ACTION[e.method] || e.method }),
          h('td', { class: 'muted', text: accessPart(e.path, p.id) })))))), { wide: true });
  }

  function accessPart(path, patientId) {
    return path.replace(`/api/patients/${patientId}`, 'karta').replace(`/api/appointments/by-patient/${patientId}`, 'termíny')
      .replace(/^\/api\/appointments\/(\d+)\/visit$/, 'návštěva (rezervace $1)')
      .replace(/^\/api\/appointments\/(\d+)/, 'rezervace $1')
      .replace(/^\/api\/visits\/(\d+).*$/, 'návštěva $1');
  }

  async function openPatientForm(existing, onSaved) {
    const insurers = await run(loadInsurers);
    if (!insurers) return;
    const err = formError();
    const f = (name, label, attrs = {}) => {
      const input = h('input', { name, ...attrs });
      if (existing && existing[name] !== undefined && existing[name] !== null) input.value = existing[name];
      return [input, h('label', { class: 'field' }, h('span', { text: label }), input)];
    };
    const [first, firstField] = f('firstName', 'Jméno', { required: true, maxlength: 100, autocomplete: 'off' });
    const [last, lastField] = f('lastName', 'Příjmení', { required: true, maxlength: 100, autocomplete: 'off' });
    const [year, yearField] = f('birthYear', 'Rok narození', { required: true, type: 'number', min: 1900, max: new Date().getFullYear(), inputmode: 'numeric' });
    const [tel, telField] = f('phone', 'Telefon', { required: true, type: 'tel', maxlength: 20, autocomplete: 'off' });
    const [insId, insIdField] = f('insuranceId', 'Číslo pojištěnce', { maxlength: 20, autocomplete: 'off' });
    const insurer = h('select', { name: 'insurerId' }, h('option', { value: '', text: 'Bez pojišťovny (samoplátce)' }),
      insurers.map((i) => h('option', { value: i.id, text: `${i.code} – ${i.name}` })));
    if (existing?.insurer) insurer.value = String(existing.insurer.id);
    const submit = h('button', { type: 'submit', class: 'btn primary', text: existing ? 'Uložit změny' : 'Založit pacienta' });

    const save = (confirmDuplicate) => submitting(submit, async () => {
      err.clear();
      const body = {
        firstName: first.value, lastName: last.value, birthYear: Number(year.value), phone: tel.value,
        insuranceId: insId.value || null, insurerId: insurer.value ? Number(insurer.value) : null,
      };
      try {
        const saved = existing
          ? await api(`/api/patients/${existing.id}`, { method: 'PUT', body })
          : await api(`/api/patients${confirmDuplicate ? '?confirmDuplicate=true' : ''}`, { method: 'POST', body });
        closeDialog();
        toast(existing ? 'Změny uloženy.' : `Pacient ${saved.lastName} ${saved.firstName} založen.`);
        if (onSaved) onSaved(saved);
        else go(`#pacienti/${saved.id}`);
      } catch (e) {
        const existingId = e.problem.existingId;
        if (e.status === 409 && existingId) {
          const open = h('button', { type: 'button', class: 'btn small', text: 'Otevřít existujícího', onclick: () => { closeDialog(); go(`#pacienti/${existingId}`); } });
          const anyway = !existing && !body.insuranceId
            ? h('button', { type: 'button', class: 'btn small', text: 'Jde o jiného člověka, založit', onclick: () => save(true) })
            : null;
          err.show(h('span', { text: e.message }), h('span', { class: 'actions' }, open, anyway));
        } else {
          err.show(e.message);
        }
      }
    });

    const form = h('form', { class: 'stack' },
      h('div', { class: 'row' }, firstField, lastField),
      h('div', { class: 'row' }, yearField, telField),
      h('div', { class: 'row' }, h('label', { class: 'field' }, h('span', { text: 'Pojišťovna' }), insurer), insIdField),
      err.el,
      h('div', { class: 'actions' }, submit, h('button', { type: 'button', class: 'btn ghost', text: 'Zavřít', onclick: closeDialog })));
    form.addEventListener('submit', (ev) => { ev.preventDefault(); save(false); });
    openDialog(existing ? 'Upravit pacienta' : 'Nový pacient', form);
  }

  // ---------- K přeobjednání ----------

  async function refreshRebookCount() {
    const list = await run(() => api('/api/appointments/needs-rebooking'));
    if (!list) return;
    const badge = $('#rebook-count');
    badge.textContent = String(list.length);
    badge.hidden = list.length === 0;
    return list;
  }

  async function loadRebooking() {
    const list = await refreshRebookCount();
    if (!list) return;
    const target = $('#rebook-list');
    if (list.length === 0) {
      target.replaceChildren(h('div', { class: 'card empty' }, h('p', { text: 'Nikoho není potřeba přeobjednat.' })));
      return;
    }
    target.replaceChildren(h('div', { class: 'table-wrap' }, h('table', {},
      h('thead', {}, h('tr', {}, ['Původní termín', 'Lékař', 'Pacient', 'Telefon', 'Důvod', ''].map((t) => h('th', { text: t })))),
      h('tbody', {}, list.map((a) => h('tr', {},
        h('td', { text: fmtDateTime.format(new Date(a.startAt)) }),
        h('td', { text: doctorName(a.doctorId) }),
        h('td', {}, h('a', { href: `#pacienti/${a.patientId}`, text: a.patientName })),
        h('td', {}, h('a', { href: `tel:${a.patientPhone}`, text: phone(a.patientPhone) })),
        h('td', { text: a.reason || '–' }),
        h('td', {}, h('div', { class: 'actions' },
          h('button', { type: 'button', class: 'btn primary small', text: 'Nový termín', onclick: () => startReschedule(a) }),
          h('button', { type: 'button', class: 'btn ghost small', text: 'Detail', onclick: () => openAppointment(a) })))))))));
  }

  // ---------- Rozpis dne ----------

  async function loadDay() {
    const doctorId = Number($('#day-doctor').value);
    const day = $('#day-date').value;
    if (!doctorId || !day) return;
    const appts = await run(() => api(`/api/appointments?doctorId=${doctorId}&from=${day}&to=${day}`));
    if (!appts) return;
    const title = `${doctorName(doctorId)}, ${fmtDateLong.format(new Date(`${day}T00:00`))}`;
    const target = $('#day');
    if (appts.length === 0) {
      target.replaceChildren(h('div', { class: 'card empty' }, h('p', { text: `${title}: nikdo není objednaný.` })));
      return;
    }
    target.replaceChildren(
      h('h2', { text: title }),
      h('div', { class: 'actions' }, h('button', { type: 'button', class: 'btn', text: 'Vytisknout', onclick: () => window.print() })),
      h('div', { class: 'table-wrap' }, h('table', {},
        h('thead', {}, h('tr', {}, ['Čas', 'Pacient', 'Telefon', 'Důvod', 'Stav'].map((t) => h('th', { text: t })))),
        h('tbody', {}, appts.map((a) => h('tr', {},
          h('td', { text: range(new Date(a.startAt), new Date(a.endAt)) }),
          h('td', {}, h('a', { href: `#pacienti/${a.patientId}`, text: a.patientName })),
          h('td', {}, h('a', { href: `tel:${a.patientPhone}`, text: phone(a.patientPhone) })),
          h('td', { text: a.reason || '–' }),
          h('td', {}, statusChip(a))))))));
  }

  // ---------- Výkazy ----------

  async function loadReport() {
    const doctorId = Number($('#report-doctor').value);
    const month = $('#report-month').value;
    if (!doctorId || !month) return;
    const report = await run(() => api(`/api/reports/monthly?doctorId=${doctorId}&month=${month}`));
    if (!report) return;
    const target = $('#report');
    const [y, m] = month.split('-').map(Number);
    const monthName = new Intl.DateTimeFormat('cs-CZ', { month: 'long', year: 'numeric' }).format(new Date(y, m - 1, 1));
    if (report.lines.length === 0) {
      target.replaceChildren(h('div', { class: 'card empty' }, h('p', { text: `${doctorName(doctorId)} nemá za ${monthName} zapsané žádné výkony.` })));
      return;
    }
    target.replaceChildren(
      h('h2', { text: `${doctorName(doctorId)}, ${monthName}` }),
      h('div', { class: 'total' }, h('strong', { text: fmtNumber.format(report.totalPoints) }), h('span', { text: 'bodů celkem' })),
      h('div', { class: 'actions' },
        h('a', { class: 'btn primary', href: `/api/reports/monthly.csv?doctorId=${doctorId}&month=${month}`, text: 'Stáhnout pro pojišťovnu (CSV)' }),
        h('button', { type: 'button', class: 'btn', text: 'Vytisknout', onclick: () => window.print() })),
      h('h3', { class: 'section-title', text: 'Souhrn podle výkonu' }),
      h('div', { class: 'table-wrap' }, h('table', {},
        h('thead', {}, h('tr', {}, h('th', { text: 'Kód' }), h('th', { text: 'Výkon' }), h('th', { class: 'num', text: 'Počet' }), h('th', { class: 'num', text: 'Body' }))),
        h('tbody', {}, report.summary.map((r) => h('tr', {},
          h('td', { text: r.code }), h('td', { text: r.name }),
          h('td', { class: 'num', text: fmtNumber.format(r.quantity) }), h('td', { class: 'num', text: fmtNumber.format(r.points) })))),
        h('tfoot', {}, h('tr', {}, h('td', { text: 'Celkem' }), h('td'), h('td'), h('td', { class: 'num', text: fmtNumber.format(report.totalPoints) }))))),
      h('h3', { class: 'section-title', text: 'Jednotlivé výkony' }),
      h('div', { class: 'table-wrap' }, h('table', {},
        h('thead', {}, h('tr', {}, ['Datum', 'Pacient', 'Č. pojištěnce', 'Pojišťovna', 'Kód', 'Výkon'].map((t) => h('th', { text: t })),
          h('th', { class: 'num', text: 'Počet' }), h('th', { class: 'num', text: 'Body' }))),
        h('tbody', {}, report.lines.map((l) => h('tr', {},
          h('td', { text: fmtDate.format(new Date(l.performedAt)) }),
          h('td', { text: `${l.patientLastName} ${l.patientFirstName}` }),
          h('td', { text: l.insuranceId || '–' }),
          h('td', { text: l.insurerCode || '–' }),
          h('td', { text: l.code }), h('td', { text: l.name }),
          h('td', { class: 'num', text: l.quantity }),
          h('td', { class: 'num', text: fmtNumber.format(l.quantity * l.pointsEach) })))))));
  }

  // ---------- Rušení ----------

  async function loadStats() {
    const from = $('#stats-from').value;
    const to = $('#stats-to').value;
    if (!from || !to) return;
    const stats = await run(() => api(`/api/appointments/cancellation-stats?from=${from}&to=${to}`));
    if (!stats) return;
    const target = $('#stats');
    if (stats.length === 0) {
      target.replaceChildren(h('div', { class: 'card empty' }, h('p', { text: 'V tomto období nikdo nerušil ani nevynechal termín.' })));
      return;
    }
    target.replaceChildren(h('div', { class: 'table-wrap' }, h('table', {},
      h('thead', {}, h('tr', {}, h('th', { text: 'Pacient' }), h('th', { text: 'Telefon' }),
        h('th', { class: 'num', text: 'Zrušil' }), h('th', { class: 'num', text: 'Nepřišel' }), h('th', { class: 'num', text: 'Celkem' }))),
      h('tbody', {}, stats.map((s) => h('tr', {},
        h('td', {}, h('a', { href: `#pacienti/${s.patientId}`, text: `${s.lastName} ${s.firstName}` })),
        h('td', {}, h('a', { href: `tel:${s.phone}`, text: phone(s.phone) })),
        h('td', { class: 'num', text: s.cancelled }),
        h('td', { class: 'num', text: s.noShow }),
        h('td', { class: 'num' }, h('strong', { text: s.cancelled + s.noShow }))))))));
  }

  // ---------- Rychlé hledání v liště ----------

  function wireGlobalSearch() {
    const input = $('#global-search');
    const popover = $('#global-results');
    const close = () => { popover.hidden = true; input.setAttribute('aria-expanded', 'false'); };
    const search = debounce(async () => {
      const q = input.value.trim();
      if (q.length < 2) { close(); return; }
      const found = await run(() => api(`/api/patients?query=${encodeURIComponent(q)}`));
      if (!found) return;
      popover.replaceChildren(...(found.length
        ? found.map((p) => patientItem(p, () => { close(); input.value = ''; go(`#pacienti/${p.id}`); }))
        : [h('p', { class: 'muted empty', text: 'Nikoho takového jsme nenašli.' })]));
      popover.hidden = false;
      input.setAttribute('aria-expanded', 'true');
    }, 200);
    input.addEventListener('input', search);
    input.addEventListener('keydown', (ev) => {
      if (ev.key === 'Escape') close();
      if (ev.key === 'ArrowDown' || ev.key === 'Enter') {
        const first = popover.querySelector('button');
        if (first && !popover.hidden) { ev.preventDefault(); if (ev.key === 'Enter') first.click(); else first.focus(); }
      }
    });
    popover.addEventListener('keydown', (ev) => {
      const items = [...popover.querySelectorAll('button')];
      const i = items.indexOf(document.activeElement);
      if (ev.key === 'ArrowDown' && i < items.length - 1) { ev.preventDefault(); items[i + 1].focus(); }
      if (ev.key === 'ArrowUp') { ev.preventDefault(); (i > 0 ? items[i - 1] : input).focus(); }
      if (ev.key === 'Escape') { close(); input.focus(); }
    });
    document.addEventListener('click', (ev) => { if (!ev.target.closest('.quick-search')) close(); });
  }

  // ---------- Start ----------

  function wire() {
    window.addEventListener('hashchange', route);
    $('#week-prev').addEventListener('click', () => { state.week = addDays(state.week, -7); loadDiary(); });
    $('#week-next').addEventListener('click', () => { state.week = addDays(state.week, 7); loadDiary(); });
    $('#week-today').addEventListener('click', () => { state.week = mondayOf(new Date()); loadDiary(); });
    $('#duration').addEventListener('change', (ev) => { state.minutes = Number(ev.target.value); loadDiary(); });
    $('#mode-cancel').addEventListener('click', () => { clearMode(); });
    $('#absence-btn').addEventListener('click', openAbsence);
    $('#acute-btn').addEventListener('click', openAcute);
    $('#new-patient-btn').addEventListener('click', () => openPatientForm(null));
    $('#patient-search').addEventListener('input', patientSearch);
    $('#report-form').addEventListener('submit', (ev) => { ev.preventDefault(); loadReport(); });
    $('#day-form').addEventListener('submit', (ev) => { ev.preventDefault(); loadDay(); });
    $('#stats-form').addEventListener('submit', (ev) => { ev.preventDefault(); loadStats(); });
    $('#logout-form').addEventListener('submit', (ev) => { ev.target.querySelector('[name=_csrf]').value = csrfToken(); });
    wireGlobalSearch();
    // obnoví diář, když se recepce vrátí k oknu (jiný počítač mezitím mohl objednat)
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden && !$('#view-diar').hidden && !dialog.open) loadDiary();
    });

    const now = new Date();
    $('#report-month').value = isoMonth(now);
    $('#day-date').value = isoDate(now);
    $('#stats-from').value = `${now.getFullYear()}-01-01`;
    $('#stats-to').value = isoDate(now);
  }

  async function init() {
    wire();
    const data = await run(() => Promise.all([api('/api/me'), api('/api/doctors')]));
    if (!data) return;
    const [me, doctors] = data;
    $('#username').textContent = me.username;
    state.doctors = doctors;
    let saved = null;
    try { saved = Number(localStorage.getItem('doctorId')); } catch { /* jen pohodlí */ }
    state.doctorId = doctors.some((d) => d.id === saved) ? saved : doctors[0]?.id ?? null;
    const doctorOptions = () => doctors.map((d) => h('option', { value: d.id, text: `MUDr. ${d.firstName} ${d.lastName}` }));
    $('#report-doctor').replaceChildren(...doctorOptions());
    $('#day-doctor').replaceChildren(...doctorOptions());
    if (state.doctorId) $('#day-doctor').value = state.doctorId;
    renderDoctorPicker();
    route();
    refreshRebookCount();
  }

  init();
})();
