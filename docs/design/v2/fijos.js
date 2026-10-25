// Sello v2 · Pagos e ingresos fijos (SELLO-114, locked 2026-10-25 on the owner's answers).
// Synthetic, hand-authored figures (COP, no symbol) following docs/testing/recurrence-examples.md.
// Financial date jue 22 oct 2026 unless a frame says otherwise.

const CATEGORIES = [["Hogar", "home"], ["Comida", "restaurant"], ["Transporte", "directions_bus"], ["Café", "local_cafe"], ["Salud", "medical_services"], ["Ocio", "theaters"], ["Gimnasio", "fitness_center"], ["Educación", "school"]];
const SOURCES = ["Salario", "Freelance", "Ingresos pasivos", "Transferencia", "Otro"];
const iconOf = name => (CATEGORIES.find(([n]) => n === name) || [, "account_balance"])[1];

// October: Arriendo (5) and Gimnasio (15) already recorded; Servicios públicos (28) still to
// come, the one payment Recibo reserves (200.000). Bus is paused; Curso de inglés ended.
const PAYMENTS = [
  { name: "Arriendo", cat: "Hogar", rule: "el 5 de cada mes", amount: 950000 },
  { name: "Gimnasio", cat: "Gimnasio", rule: "el 15 de cada mes", amount: 99900 },
  { name: "Servicios públicos", cat: "Hogar", rule: "el 28 de cada mes", next: "mié 28 oct", amount: 200000, soon: true },
  { name: "Bus", cat: "Transporte", rule: "cada semana, los sábados", amount: 20000, paused: "desde lun 12 oct" },
];
const ENDED = [{ name: "Curso de inglés", cat: "Educación", rule: "el 16 de cada mes", amount: 189000, ended: "terminó el mié 16 sep" }];
const INCOMES = [
  { name: "Freelance", cat: "Freelance", rule: "el 15 de cada mes", amount: 250000, income: true },
  { name: "Salario", cat: "Salario", rule: "el 30 de cada mes", next: "vie 30 oct", amount: 1000000, soon: true, income: true },
];

// ——— Entry with Se repite (the locked entry, one row added) ———

function amountSlip(s) {
  const income = s.mode === "income";
  const ic = income ? "" : iconOf(s.category);
  const circle = income ? "" : `<span class="circle" style="--fill:${s.category ? 100 : 0}%" aria-hidden="true">${icon(ic || "receipt_long")}<span class="on">${icon(ic || "receipt_long")}</span></span>`;
  const value = s.amount === null
    ? `<div class="amount-value placeholder">0<span class="caret"></span></div>`
    : `<div class="amount-value ${String(s.amount).length > 6 ? "mid" : ""} ${income ? "income" : ""}">${income ? "+" : ""}${money(s.amount)}<span class="caret"></span></div>`;
  return `<section class="slip pinked amount-slip"><div class="amount-meta"><span>${s.label ?? (income ? "Ingreso en pesos" : "Gasto en pesos")}</span><button class="date-pill">${icon("event")}${s.date ?? "Hoy, jue 22"}</button></div><div class="amount-row">${circle}${value}</div></section>`;
}

const repPill = s => s.repeat
  ? `<button class="note-pill filled rep-pill">${icon("autorenew")}<span>${s.repeat}</span><span class="edit">Cambiar</span></button>`
  : `<button class="note-pill rep-pill">${icon("autorenew")}<span>Se repite</span><span class="edit" style="color:var(--soft);font-weight:600">No</span></button>`;

function middle(s, extra = "", scrolled = false) {
  const income = s.mode === "income";
  const chips = income
    ? SOURCES.map(n => `<button class="chip" aria-pressed="${n === s.category}">${n}</button>`).join("")
    : CATEGORIES.map(([n, ic]) => `<button class="chip" aria-pressed="${n === s.category}">${icon(ic)}${n}</button>`).join("");
  let h = `<h3>${income ? "Origen" : "Categoría"}</h3><div class="chips">${chips}</div>`;
  h += s.note ? `<button class="note-pill filled">${icon("edit_note")}<span>${s.note}</span><span class="edit">Editar</span></button>` : `<button class="note-pill">${icon("edit_note")}<span>Agregar nota</span></button>`;
  if (!s.noRepeat) h += repPill(s);
  return `<div class="entry-mid ${scrolled ? "scrolled" : ""}">${h}${extra}</div>`;
}

const pad = () => `<div class="pad" role="group" aria-label="Teclado de Sello">${["1", "2", "3", "4", "5", "6", "7", "8", "9", "000", "0"].map(k => `<button class="${k === "000" ? "k000" : ""}" aria-label="${k === "000" ? "Tres ceros" : k}">${k}</button>`).join("")}<button class="del" aria-label="Borrar (mantén para borrar todo)">${icon("backspace")}</button></div>`;

function entry(s, { sheet, choice, title, seg = true, action, extra = "", scrolled = false } = {}) {
  const income = s.mode === "income";
  const head = seg
    ? `<div class="seg" role="group" aria-label="Tipo"><button aria-pressed="${!income}">Gasto</button><button aria-pressed="${income}">Ingreso</button></div>`
    : `<h1 class="sub-title" style="margin:0;color:var(--ink)">${title}</h1>`;
  const label = action ?? (s.repeat ? (income ? "Guardar ingreso fijo" : "Guardar pago fijo") : (income ? "Anotar ingreso" : "Anotar gasto"));
  const ready = s.amount !== null && s.category;
  let h = `<div class="entry-top">${statusbar()}<div class="entry-bar"><button class="icon-btn" aria-label="Cerrar">${icon("close")}</button>${head}<span class="spacer"></span></div></div>`;
  h += amountSlip(s) + middle(s, extra, scrolled) + `<div class="entry-action"><button class="primary" ${ready && !s.unchanged ? "" : "disabled"}>${s.repeat || !seg ? icon("autorenew") : icon("add")}${label}</button></div>` + pad() + `<div class="gesture" style="background:var(--desk)"></div>`;
  if (sheet) h += `<div class="scrim">${sheet}</div>`;
  if (choice) h += `<div class="scrim center">${choice}</div>`;
  return h;
}

// The rule sheet: four kinds, the day or interval, and an optional end. "Próximo" says when
// the first one is recorded; the start is the entry's date, today or later.
function ruleSheet(kind, { days = [22], n = 1, end = "Nunca", next = "hoy, jue 22 oct", note } = {}) {
  const kinds = [["month", "Cada mes"], ["twice", "Dos veces al mes"], ["weeks", "Cada semana o más"], ["days", "Cada cierto número de días"]];
  let body = `<div class="chips" role="radiogroup" aria-label="Cada cuánto">${kinds.map(([k, t]) => `<button class="chip" aria-pressed="${k === kind}">${t}</button>`).join("")}</div>`;
  if (kind === "month" || kind === "twice") {
    body += `<span class="form-label">${kind === "month" ? "El día" : "Los días (elige dos)"}</span><div class="day-grid">${Array.from({ length: 31 }, (_, i) => i + 1).map(d => `<button aria-pressed="${days.includes(d)}" aria-label="Día ${d}">${d}</button>`).join("")}</div>`;
    if (days.some(d => d > 28)) body += `<p class="caption" style="margin:0 2px 6px">En los meses más cortos, el último día del mes.</p>`;
  } else {
    const unit = kind === "weeks" ? (n === 1 ? "semana" : "semanas") : (n === 1 ? "día" : "días");
    body += `<span class="form-label">Cada</span><div class="stepper"><button aria-label="Menos">−</button><b>${n}</b><button aria-label="Más">+</button><span>${unit}, desde el día que elegiste</span></div>`;
  }
  body += `<span class="form-label">Termina</span><div class="chips" role="radiogroup" aria-label="Termina"><button class="chip" aria-pressed="${end === "Nunca"}">Nunca</button><button class="chip" aria-pressed="${end !== "Nunca"}">${icon("event_busy")}${end === "Nunca" ? "En una fecha" : end}</button></div>`;
  body += `<p class="rule-next">${icon("autorenew")}<span>Próximo: <b>${next}</b>. ${note ?? "Se anota solo en su fecha."}</span></p>`;
  body += `<div class="rule-actions"><button class="ghost">Quitar</button><button class="go">Listo</button></div>`;
  return `<div class="composer rule-sheet" role="dialog" aria-label="Se repite"><h2>Se repite</h2>${body}</div>`;
}

// ——— The list ———

function fxRow(x, last) {
  const ic = x.income ? (x.cat === "Salario" ? "account_balance" : "edit_note") : iconOf(x.cat);
  const when = x.paused ? `${x.rule} · en pausa ${x.paused}` : x.ended ? `${x.rule} · ${x.ended}` : x.next ? `${x.rule} · próximo ${x.next}` : x.rule;
  const stamp = x.paused ? `<span class="stamp small neutral">En pausa</span>` : `<span></span>`;
  const amount = x.income ? `+${fmt(x.amount)}` : fmt(x.amount);
  return `<button class="fx-row ${x.soon ? "soon" : ""} ${x.income ? "income" : ""} ${x.paused || x.ended ? "paused" : ""} ${last ? "last" : ""}" aria-label="${x.name}, ${spoken(x.amount)}, ${when}"><span class="fx-ic">${icon(ic)}</span><span class="fx-text"><b>${x.name}</b><small>${x.income ? "" : x.cat + " · "}${when}</small></span>${stamp}<b>${amount}</b></button>`;
}

// What the month holds, as a plan: never a recorded total (dashed, soft for what is to come).
function monthPlan(rows, income) {
  const active = rows.filter(x => !x.paused && !x.ended);
  const done = active.filter(x => !x.soon), soon = active.filter(x => x.soon);
  const sum = l => l.reduce((a, x) => a + x.amount, 0);
  const word = income ? "Ya entraron" : "Ya se anotaron";
  return `<section class="plan-slip"><div class="plan-head"><span class="plan-tag">Previsto en octubre</span><span class="caption">${active.length} ${income ? "ingresos" : "pagos"} activos</span></div><div class="leader"><span>${word} (${done.length})</span><i class="dots"></i><b>${money(sum(done))}</b></div><div class="leader plan"><span>Faltan (${soon.length})</span><i class="dots"></i><b>${money(sum(soon))}</b></div><div class="plan-total"><div class="leader"><span>En el mes</span><i class="dots"></i><b>${money(sum(active))}</b></div></div></section>`;
}

function listBody(kind = "pagos", state) {
  const income = kind === "ingresos";
  const seg = `<div class="seg" role="group" aria-label="Tipo" style="margin:0 0 12px"><button aria-pressed="${!income}">Pagos</button><button aria-pressed="${income}">Ingresos</button></div>`;
  if (state === "loading") return seg + `<div role="status" aria-label="Cargando"><section class="slip"><div class="skel" style="width:45%"></div><div class="skel"></div><div class="skel" style="width:60%"></div></section><section class="slip"><div class="skel"></div><div class="skel"></div><div class="skel"></div></section></div>`;
  if (state === "failed") return seg + `<section class="slip empty">${icon("error")}<h2>No pudimos leer tus pagos fijos</h2><p>Siguen guardados y Sello no anotó nada de más. Vuelve a intentarlo.</p><button class="outline-btn">Reintentar</button></section>`;
  if (state === "empty") return seg + `<section class="slip empty">${icon("autorenew")}<h2>Nada se repite todavía</h2><p>Cuando anotes el arriendo o el salario, marca <b>Se repite</b> y Sello lo anotará solo en su fecha.</p><button class="outline-btn">Anotar algo que se repite</button></section>`;
  const rows = income ? INCOMES : PAYMENTS;
  let h = seg + monthPlan(rows, income) + `<section class="slip"><h2 class="q" style="margin-bottom:4px">${income ? "Tus ingresos fijos" : "Tus pagos fijos"}</h2>${rows.map((x, i) => fxRow(x, i === rows.length - 1)).join("")}`;
  if (!income) h += `<p class="fx-group">Terminados</p>${ENDED.map((x, i) => fxRow(x, i === ENDED.length - 1)).join("")}`;
  return h + `</section><p class="caption" style="margin:4px 4px 0">Se anotan solos, cada uno en su fecha, cuando abres Sello.</p>`;
}

// ——— Changing a schedule: the entry layout in edit mode ———

const ARRIENDO = { mode: "expense", amount: 950000, category: "Hogar", note: "Arriendo", repeat: "Cada mes, el 5 · termina: nunca", label: "Pago fijo", date: "Desde lun 5 oct" };

function scheduleExtras({ paused, archived } = {}) {
  let h = `<p class="goal-fact" style="margin:14px 2px 0">Los cambios aplican desde mañana. Lo que ya se anotó no cambia.</p>`;
  if (paused) {
    h += `<section class="slip" style="margin:12px 0 0"><div class="answer-head"><span class="label">${archived ? "En pausa porque Gimnasio está archivada" : "En pausa desde lun 12 oct"}</span><span class="stamp neutral">En pausa</span></div><p class="answer-note">Mientras está en pausa no se anota ni se separa nada. Al reanudar, sigue desde ese día; lo de la pausa no se anota.</p><button class="outline-btn" style="margin-top:10px;width:100%">${icon("play_arrow")}Reanudar</button></section>`;
  } else {
    h += `<button class="note-pill" style="margin-top:12px">${icon("pause")}<span>Pausar</span><span class="edit" style="color:var(--soft);font-weight:600">Próximo: jue 5 nov</span></button>`;
  }
  h += `<section class="slip danger" style="margin:12px 0 4px"><h2>Borrar este pago fijo</h2><p>Deja de anotarse. Lo que ya se anotó se queda: <b>2 gastos</b>.</p><button class="danger-link">Borrar</button></section>`;
  return h;
}

const confirmDelete = `<div class="confirm" role="dialog" aria-label="Borrar pago fijo"><h2>¿Borrar Arriendo?</h2><p>Sello deja de anotarlo desde hoy. <b>Los gastos ya anotados no se borran.</b></p><div class="tear" aria-hidden="true"></div><div class="confirm-actions"><button class="ghost">Cancelar</button><button class="del">Borrar</button></div></div>`;

// ——— Editing a recorded entry: Solo este / Este y los siguientes ———

const choice = (kind = "gasto") => `<div class="confirm" role="dialog" aria-label="Cambiar también los siguientes"><h2>¿Cambiar también los siguientes?</h2><p>Este ${kind} lo anotó <b>Arriendo</b>, que se repite el 5 de cada mes. Los siguientes cambian desde mañana; los ya anotados no.</p><div class="tear" aria-hidden="true"></div><div class="confirm-actions"><button class="ghost">Solo este</button><button class="go">Este y los siguientes</button></div><button class="back-link">Volver a editar</button></div>`;

function editRecorded(s, { choiceOpen } = {}) {
  return entry(s, { seg: false, title: "Editar gasto", action: "Guardar cambios", choice: choiceOpen ? choice() : undefined });
}

// ——— Upcoming payments ———

function upcomingBody(state, two = false) {
  if (state === "loading") return `<div role="status" aria-label="Cargando"><section class="slip"><div class="skel" style="width:45%"></div><div class="skel big"></div><div class="skel"></div></section></div>`;
  if (state === "failed") return `<section class="slip empty">${icon("error")}<h2>No pudimos leer tus pagos previstos</h2><p>Recibo no muestra una cifra que no pudo leer. Vuelve a intentarlo.</p><button class="outline-btn">Reintentar</button></section>`;
  if (state === "empty") return `<section class="slip empty">${icon("check")}<h2>Ya no queda nada previsto en octubre</h2><p>Los pagos de este mes ya se anotaron. Recibo ya no separa nada.</p><button class="outline-btn">Ver pagos fijos</button></section>`;
  const items = two
    ? [["Suscripciones", "Ocio", "dom 25 oct", 120000], ["Seguro", "Salud", "vie 30 oct", 80000]]
    : [["Servicios públicos", "Hogar", "mié 28 oct", 200000]];
  const total = items.reduce((a, x) => a + x[3], 0);
  const rows = items.map(([n, c, d, a], i) => `<button class="fx-row ${i === items.length - 1 ? "last" : ""}" aria-label="${n}, ${spoken(a)}, ${d}. Ver pago fijo"><span class="fx-ic">${icon(iconOf(c))}</span><span class="fx-text"><b>${n}</b><small>${c} · ${d}</small></span><span></span><b>${money(a)}</b></button>`).join("");
  return `<section class="slip pinked"><h2 class="q">Separado en octubre</h2><p class="answer">${money(total)}</p><span class="caption">${items.length === 1 ? "1 pago previsto" : `${items.length} pagos previstos`}, hasta el sáb 31 oct</span><div class="tear" aria-hidden="true"></div>${rows}</section><p class="caption" style="margin:4px 4px 0">Recibo los separa de lo que te queda hasta que se anotan, cada uno en su fecha. Toca uno para ver su pago fijo.</p><button class="text-link">Ver todos los pagos fijos${icon("chevron_right")}</button>`;
}

// ——— What Sello recorded (RE10: opened sáb 24 oct, last catch-up sáb 3 oct) ———

const RECORDED = [["Arriendo", "Hogar", "lun 5 oct", 950000], ["Bus", "Transporte", "sáb 10 oct", 20000], ["Bus", "Transporte", "sáb 17 oct", 20000], ["Bus", "Transporte", "Hoy, sáb 24 oct", 20000]];

function recordedBody(state, list = RECORDED, income) {
  if (state === "loading") return `<div role="status" aria-label="Cargando"><section class="slip"><div class="skel" style="width:55%"></div><div class="skel"></div><div class="skel"></div></section></div>`;
  if (state === "failed") return `<section class="slip empty">${icon("error")}<h2>No pudimos leer lo que Sello anotó</h2><p>Lo anotado sigue guardado en tu mes. Vuelve a intentarlo.</p><button class="outline-btn">Reintentar</button></section>`;
  const rows = list.map(([n, c, d, a, inc], i) => `<button class="fx-row ${inc ? "income" : ""} ${i === list.length - 1 ? "last" : ""}" aria-label="${n}, ${spoken(a)}, ${d}. Editar"><span class="fx-ic">${icon(inc ? "account_balance" : iconOf(c))}</span><span class="fx-text"><b>${n}</b><small>${inc ? "Ingreso" : c} · ${d}</small></span>${icon("chevron_right", "chev")}<b>${inc ? "+" + fmt(a) : fmt(a)}</b></button>`).join("");
  return `<section class="slip"><h2 class="q">Sello anotó ${list.length === 1 ? "esto" : `estos ${list.length}`}</h2><p class="answer-note" style="margin:0 0 6px">Cada uno en su fecha, desde la última vez que abriste Sello. Revisa que estén bien; toca uno para corregirlo o borrarlo.</p>${rows}</section>`;
}

// ——— Frames ———

const phone = (o, inner, { large = false, theme } = {}) => `<div class="phone"><div class="app ${large ? "large" : ""}" data-ink="${o.ink}" data-theme="${theme ?? o.theme}" style="--scale:${large ? 2 : 1}">${inner}</div></div>`;
const sub = (o, title, body, opts = {}) => phone(o, subShell({ title, body, active: opts.active ?? 1, dock: opts.dock ?? null, dockClass: opts.dock ? "plain" : "", action: opts.action }), opts);
const card = (title, note, inner) => `<div class="frame-card"><h3>${title}</h3><p>${note}</p>${inner}</div>`;
const crop = (o, inner) => `<div class="app cmp-stage" data-ink="${o.ink}" data-theme="${o.theme}">${inner}</div>`;

function render() {
  const o = reviewOptions(["ink", "theme"]);
  const set = (id, h) => document.getElementById(id).innerHTML = h;
  const ready = { mode: "expense", amount: 950000, category: "Hogar", note: "Arriendo" };
  const on = { ...ready, repeat: "Cada mes, el 5 · desde jue 5 nov", date: "jue 5 nov" };

  set("create",
    card("Ready to save · Se repite off", "The locked entry with one more row under the note. Off by default: Anotar records one expense, as today.", phone(o, entry(ready))) +
    card("Se repite · each month", "Tapping the row opens the rule. The day starts as the entry’s date; Próximo says when Sello records the first one.", phone(o, entry(ready, { sheet: ruleSheet("month", { days: [5], next: "jue 5 nov" }) }))) +
    card("Twice a month · 15 and 30", "The person picks both days (SELLO-096). Past day 28, shorter months use their last day.", phone(o, entry({ mode: "income", amount: 1000000, category: "Salario" }, { sheet: ruleSheet("twice", { days: [15, 30], next: "vie 30 oct" }) }))) +
    card("Every N weeks", "A stepper, 1–52 weeks (or 1–365 days), counted from the entry’s date.", phone(o, entry({ mode: "expense", amount: 20000, category: "Transporte", note: "Bus" }, { sheet: ruleSheet("weeks", { n: 1, next: "sáb 24 oct" }) }))) +
    card("Se repite on · future start", "The date may be later than today only while Se repite is on, never earlier. The button says what saving does: it keeps a schedule, and Sello records each one on its date.", phone(o, entry(on))) +
    card("Starting today", "If the first one is today, Sello records it right after saving; the notice on Recibo says so (section 6).", phone(o, entry({ ...ready, repeat: "Cada mes, el 22 · desde hoy" }, { sheet: ruleSheet("month", { days: [22], next: "hoy, jue 22 oct", note: "Se anota en cuanto guardes." }) }))) +
    card("Saved", "Recibo’s brief confirmation, as for any save. Nothing is recorded until the date.", phone(o, appShell({ body: `<section class="slip pinked"><div class="answer-head"><span class="label">Restante del presupuesto</span><span class="stamp">Al día</span></div><div class="hero">${money(400000)}</div></section>`, toast: "Pago fijo guardado: Arriendo, el 5 de cada mes" }))) +
    card("Large text · 200%", "The middle scrolls; amount, the button and the pad stay put, as in the locked entry.", phone(o, entry(on), { large: true })) +
    card("Dark", "Same parts in the dark theme.", phone(o, entry(ready, { sheet: ruleSheet("month", { days: [5], next: "jue 5 nov" }) }), { theme: "dark" })));

  set("list",
    card("Pagos · default", "Gastos and ingresos apart. A dashed plan says what October holds — what already went through and what is still to come (soft) — never a recorded total. Rows by day; paused and ended ones are dimmed.", sub(o, "Pagos e ingresos fijos", listBody(), { action: ["add", "Agregar pago fijo"] })) +
    card("Ingresos", "The same for income. Salario still to come is soft, as on Plan del mes.", sub(o, "Pagos e ingresos fijos", listBody("ingresos"), { action: ["add", "Agregar ingreso fijo"] })) +
    card("Empty", "Nothing repeats yet. The button opens Anotar with Se repite already on.", sub(o, "Pagos e ingresos fijos", listBody("pagos", "empty"), { action: ["add", "Agregar pago fijo"] })) +
    card("Loading", "Skeleton slips.", sub(o, "Pagos e ingresos fijos", listBody("pagos", "loading"))) +
    card("Failed read", "Never an empty list.", sub(o, "Pagos e ingresos fijos", listBody("pagos", "failed"))) +
    card("Large text · 200%", "Rows wrap; the amount moves under the name.", sub(o, "Pagos e ingresos fijos", listBody(), { large: true, action: ["add", "Agregar pago fijo"] })) +
    card("Dark", "", sub(o, "Pagos e ingresos fijos", listBody(), { theme: "dark", action: ["add", "Agregar pago fijo"] })));

  set("edit",
    card("Changing Arriendo", "Tapping a row opens the entry layout for the schedule: amount, category, note and Se repite. Changes apply from tomorrow; Guardar waits for a change.", phone(o, entry({ ...ARRIENDO, unchanged: true }, { seg: false, title: "Pago fijo", action: "Guardar cambios", scrolled: true, extra: scheduleExtras() }))) +
    card("Paused", "En pausa, and Reanudar. Nothing paused is recorded or reserved, and the pause is not caught up later (RE21).", phone(o, entry({ ...ARRIENDO, unchanged: true, repeat: "Cada mes, el 5 · en pausa" }, { seg: false, title: "Pago fijo", action: "Guardar cambios", scrolled: true, extra: scheduleExtras({ paused: true }) }))) +
    card("Paused by archiving its category", "Archiving a category pauses its payments and says why; unarchiving does not resume them (RE23).", phone(o, entry({ mode: "expense", amount: 99900, category: "Gimnasio", note: "Gimnasio", repeat: "Cada mes, el 15 · en pausa", label: "Pago fijo", date: "Desde jue 15 oct", unchanged: true }, { seg: false, title: "Pago fijo", action: "Guardar cambios", scrolled: true, extra: scheduleExtras({ paused: true, archived: true }) }))) +
    card("Deleting", "A confirm slip. What was recorded stays as ordinary expenses (RE24).", phone(o, entry({ ...ARRIENDO, unchanged: true }, { seg: false, title: "Pago fijo", action: "Guardar cambios", scrolled: true, extra: scheduleExtras(), choice: confirmDelete }))) +
    card("Large text · 200%", "", phone(o, entry({ ...ARRIENDO }, { seg: false, title: "Pago fijo", action: "Guardar cambios", scrolled: true, extra: scheduleExtras() }), { large: true })) +
    card("Dark", "", phone(o, entry({ ...ARRIENDO }, { seg: false, title: "Pago fijo", action: "Guardar cambios", scrolled: true, extra: scheduleExtras({ paused: true }) }), { theme: "dark" })));

  const recordedArriendo = { mode: "expense", amount: 1000000, category: "Hogar", note: "Arriendo", noRepeat: true, label: "Gasto en pesos", date: "lun 5 oct" };
  set("occurrence",
    card("Saving a change", "The entry a schedule recorded is edited like any other. On saving a new amount, category or note, Sello asks whether the schedule changes too. A date change alone asks nothing.", phone(o, editRecorded(recordedArriendo, { choiceOpen: true }))) +
    card("Large text · 200%", "", phone(o, editRecorded(recordedArriendo, { choiceOpen: true }), { large: true })) +
    card("Dark", "", phone(o, editRecorded(recordedArriendo, { choiceOpen: true }), { theme: "dark" })));

  set("upcoming",
    card("One payment reserved", "Opened from “Incluye 200.000 separado para 1 pago previsto ›”. Each row opens its schedule.", sub(o, "Pagos previstos", upcomingBody(), { active: 0 })) +
    card("Two payments (RE30)", "", sub(o, "Pagos previstos", upcomingBody(null, true), { active: 0 })) +
    card("Nothing left", "All recorded while the list was open.", sub(o, "Pagos previstos", upcomingBody("empty"), { active: 0 })) +
    card("Loading", "", sub(o, "Pagos previstos", upcomingBody("loading"), { active: 0 })) +
    card("Failed read", "", sub(o, "Pagos previstos", upcomingBody("failed"), { active: 0 })) +
    card("Large text · 200%", "", sub(o, "Pagos previstos", upcomingBody(), { active: 0, large: true })) +
    card("Dark", "", sub(o, "Pagos previstos", upcomingBody(), { active: 0, theme: "dark" })));

  const recibo = `<section class="slip pinked"><div class="answer-head"><span class="label">Restante del presupuesto</span><span class="stamp">Al día</span></div><div class="hero">${money(390000)}</div><div class="tear"></div><p class="daily-big"><b>${money(48750)}</b><span>por día</span></p><span class="caption">8 días, incluido hoy</span></section>`;
  set("notice",
    card("Recibo after opening", "A bar above Anotar says what Sello recorded since you last checked. It stays, across openings, until you press Listo on the list.", phone(o, appShell({ body: recibo, notice: "Sello anotó 4 pagos fijos" }))) +
    card("The list", "Each one opens its edit. Listo dismisses the bar; back keeps it.", sub(o, "Lo que Sello anotó", recordedBody(), { active: 0, dock: "Listo" })) +
    card("Payments and incomes", "", sub(o, "Lo que Sello anotó", recordedBody(null, [["Arriendo", "Hogar", "lun 5 oct", 950000], ["Freelance", "", "jue 15 oct", 250000, true], ["Gimnasio", "Gimnasio", "jue 15 oct", 99900]]), { active: 0, dock: "Listo" })) +
    card("One deleted from its edit", "It leaves the list; the others stay (RE39). When none is left the bar goes away.", sub(o, "Lo que Sello anotó", recordedBody(null, RECORDED.slice(0, 3)), { active: 0, dock: "Listo" })) +
    card("Loading", "", sub(o, "Lo que Sello anotó", recordedBody("loading"), { active: 0, dock: "Listo" })) +
    card("Failed read", "", sub(o, "Lo que Sello anotó", recordedBody("failed"), { active: 0, dock: "Listo" })) +
    card("Large text · 200%", "", sub(o, "Lo que Sello anotó", recordedBody(), { active: 0, dock: "Listo", large: true })) +
    card("Dark", "", phone(o, appShell({ body: recibo, notice: "Sello anotó 4 pagos fijos" }), { theme: "dark" })));

  const settings = `<section class="slip"><h2 class="q">Tus datos</h2><button class="setting-row">${icon("autorenew")}<span><b>Pagos e ingresos fijos</b><small>4 pagos, 2 ingresos</small></span>${icon("chevron_right", "chev")}</button><button class="setting-row">${icon("content_paste")}<span><b>Respaldo</b><small>hace 6 días</small></span>${icon("chevron_right", "chev")}</button></section>`;
  const resumen = `<section class="slip income-slip"><h2 class="q">Ingresos de octubre</h2><div class="leader"><span>Ingresos anotados</span><i class="dots"></i><b>${money(2000000)}</b></div><div class="leader"><span>Ingresos menos gastos</span><i class="dots"></i><b>${money(1600000)}</b></div><button class="text-link">Ver ingresos y su destino${icon("chevron_right")}</button><button class="text-link">Pagos e ingresos fijos${icon("chevron_right")}</button></section>`;
  set("reach",
    card("Configuración · Tus datos", "A row with the counts, above Respaldo.", crop(o, settings)) +
    card("Resumen · income slip", "A second link under Ver ingresos y su destino.", crop(o, resumen)));
}
// Frames marked "scrolled" show the bottom of the entry's scrolling middle.
const scrollFrames = () => document.querySelectorAll(".entry-mid.scrolled").forEach(e => { e.scrollTop = e.scrollHeight; });
document.querySelectorAll(".controls select").forEach(s => s.addEventListener("change", () => { render(); scrollFrames(); }));
render();
scrollFrames();
