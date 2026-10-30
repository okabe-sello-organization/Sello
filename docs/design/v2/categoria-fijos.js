// Sello v2 · Pagos fijos on category detail. Synthetic, hand-authored figures (COP, no
// symbol). Financial date jue 22 oct 2026, 10 days left including today, unless a frame
// says otherwise. Hogar: limit 1.500.000, Arriendo (950.000, the 5th) already recorded.

const UPCOMING = [
  { name: "Internet", when: "Mañana, vie 23 oct", amount: 90000 },
  { name: "Servicios públicos", when: "mié 28 oct", amount: 200000 },
];
const OTHERS = [
  { name: "Arriendo", rule: "el 5 de cada mes", next: "jue 5 nov", amount: 950000 },
  { name: "Aseo", rule: "cada 2 semanas", amount: 60000, paused: "desde lun 12 oct" },
];

const fxRow = (x, last, soon) => `<button class="fx-row ${x.paused ? "paused" : ""} ${last ? "last" : ""}" aria-label="${x.name}, ${spoken(x.amount)}, ${x.when ?? x.rule}. Ver pago fijo"><span class="fx-ic">${icon(x.paused ? "pause" : soon ? "event" : "autorenew")}</span><span class="fx-text"><b>${x.name}</b><small>${soon ? x.when : x.paused ? `En pausa ${x.paused}` : `${x.rule} · próximo ${x.next}`}</small></span>${icon("chevron_right", "chev")}<b>${money(x.amount)}</b></button>`;

// The hero keeps the locked detail layout; only its figures and the footnote are new.
function hero({ left, spent, limit = 1500000, stamp = ["Al día", ""], label = "Te quedan aquí", perDay, reserved, count, short, unlimited, past }) {
  const circle = unlimited ? unlimitedCircle("home") : `<span class="circle" style="--fill:${Math.min(100, Math.round(spent * 100 / limit))}%" aria-hidden="true">${icon("home")}<span class="on">${icon("home")}</span></span>`;
  const day = past ? "" : unlimited ? "" : `<div class="tear"></div><div class="cd-kv"><span><small>Para</small><b>10 días</b></span><span><small>Al día</small><b>${money(perDay)}</b></span></div>`;
  const shortLine = short ? `<p class="answer-note">${short}</p><button class="text-link">Ajustar presupuesto${icon("chevron_right")}</button>` : "";
  const foot = reserved ? `<button class="text-link cd-foot">Incluye ${fmt(reserved)} separado para ${count === 1 ? "1 pago previsto" : `${count} pagos previstos`}${icon("chevron_right")}</button>` : "";
  return `<section class="slip pinked"><div class="answer-head"><span class="label">${label}</span><span class="stamp ${stamp[1]}">${stamp[0]}</span></div><div class="cd-hero">${circle}<span class="hero">${money(left)}</span></div><div class="cd-of"><span>Gastado <b>${fmt(spent)}</b></span>${unlimited ? "" : `<span>de <b>${fmt(limit)}</b></span>`}</div>${shortLine}${day}${foot}</section>`;
}

function fijos(state, { upcoming = UPCOMING, others = OTHERS, month = "octubre" } = {}) {
  if (state === "none") return "";
  if (state === "loading") return `<section class="slip" role="status" aria-label="Cargando"><div class="skel" style="width:40%"></div><div class="skel"></div><div class="skel"></div></section>`;
  if (state === "failed") return `<section class="slip empty">${icon("error")}<h2>No pudimos leer tus pagos fijos</h2><p>Al día ya descuenta los de ${month}; esta lista no se pudo leer. Vuelve a intentarlo.</p><button class="outline-btn">Reintentar</button></section>`;
  let h = `<section class="slip"><h2 class="cd-h">Pagos fijos</h2>`;
  h += `<p class="fx-group">Por venir en ${month}</p>`;
  h += upcoming.length
    ? upcoming.map((x, i) => fxRow(x, i === upcoming.length - 1, true)).join("")
    : `<p class="answer-note" style="margin:0 2px 6px">Ya no queda nada por venir en ${month}: Al día no separa nada.</p>`;
  if (others.length) h += `<p class="fx-group">Más adelante</p>${others.map((x, i) => fxRow(x, i === others.length - 1)).join("")}`;
  return h + `<button class="text-link">Ver todos los pagos fijos${icon("chevron_right")}</button></section>`;
}

const latest = `<section class="slip"><div class="cd-row"><h2 class="cd-h">Últimos gastos</h2><button class="text-link">Ver los 3</button></div><div class="leader"><span>Arriendo<small>lun 5 oct</small></span><i class="dots"></i><b>950.000</b></div></section>`;

const phone = (o, inner, { theme } = {}) => `<div class="phone"><div class="app" data-ink="${o.ink}" data-theme="${theme ?? o.theme}">${inner}</div></div>`;
const detail = (o, body, opts = {}) => phone(o, subShell({ title: `Hogar<small class="cd-sub">${opts.month ?? "Octubre"} 2026</small>`, body, active: 0, action: ["edit_note", "Editar categoría"], current: opts.current, month: opts.month }), opts);
const card = (title, note, inner) => `<div class="frame-card"><h3>${title}</h3><p>${note}</p>${inner}</div>`;

function render() {
  const o = reviewOptions(["ink", "theme"]);
  const set = (id, h) => document.getElementById(id).innerHTML = h;
  // 1.500.000 − 950.000 = 550.000 left; 90.000 + 200.000 = 290.000 reserved; margin 260.000.
  const base = hero({ left: 260000, spent: 950000, perDay: 26000, reserved: 290000, count: 2 });
  set("main",
    card("Two payments still to come", "Before: Te quedan 550.000, Al día 55.000. Now, as on Recibo, the hero is what the payments leave (550.000 − 290.000 = 260.000) and Al día is 260.000 ÷ 10 = 26.000. The footnote scrolls to Pagos fijos.", detail(o, base + fijos() + latest)) +
    card("Before (today’s app)", "Same data: the rent-free figures promise 55.000 a day that the two payments will take.", detail(o, hero({ left: 550000, spent: 950000, perDay: 55000 }) + latest)) +
    card("Nothing left to come this month", "Internet and Servicios públicos already recorded (spent 1.240.000). Nothing reserved: no footnote, Al día from what is left (260.000 ÷ 10).", detail(o, hero({ left: 260000, spent: 1240000, perDay: 26000 }) + fijos(null, { upcoming: [] }))));

  set("standing",
    card("Sin margen", "Spent 1.210.000: the 290.000 left is exactly what the payments take. Big number 0, Al día 0.", detail(o, hero({ left: 0, spent: 1210000, perDay: 0, reserved: 290000, count: 2, stamp: ["Sin margen", "neutral"] }) + fijos())) +
    card("Revisar", "Spent 1.300.000: 200.000 left, 290.000 to come. Label Falta para tus pagos, shortfall 90.000, Al día 0, and Recibo’s sentence.", detail(o, hero({ left: 90000, spent: 1300000, perDay: 0, reserved: 290000, count: 2, stamp: ["Revisar", "warn"], label: "Falta para tus pagos", short: "Quedan 200.000 y tus pagos previstos suman 290.000." }) + fijos())) +
    card("Sin límite", "No limit, so no per-day figure, as today. The list still shows what is coming.", detail(o, hero({ left: 950000, spent: 950000, stamp: ["Sin límite", "neutral"], label: "Has gastado este mes", unlimited: true }) + fijos())));

  set("states",
    card("No fixed payments here", "The slip is left out; the screen is today’s.", detail(o, hero({ left: 550000, spent: 950000, perDay: 55000 }) + latest)) +
    card("A past month", "September is closed: nothing is to come, nothing is reserved and the slip is left out.", detail(o, hero({ left: 50000, spent: 1450000, past: true }) + latest, { month: "Septiembre", current: "octubre" })) +
    card("Loading", "", detail(o, base + fijos("loading"))) +
    card("List failed to read", "The hero still comes from the month’s own read, which includes the payments; only the list failed.", detail(o, base + fijos("failed"))) +
    card("Dark", "", detail(o, base + fijos(), { theme: "dark" })));
}
document.querySelectorAll(".controls select").forEach(s => s.addEventListener("change", render));
render();
