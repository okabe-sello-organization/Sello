// Sello v2 · Home (locked 2026-10-15). All figures are synthetic, hand-authored fixtures
// (COP, no symbol). Financial date 2026-10-22, 10 days left including today, unless noted.

const ICONS = { Hogar: "home", Comida: "restaurant", Transporte: "directions_bus", Café: "local_cafe", Salud: "medical_services", Ocio: "theaters", Ropa: "checkroom" };
const LIMITS = { Hogar: 400000, Comida: 200000, Transporte: 100000, Café: 50000, Salud: 80000, Ocio: 100000, Ropa: 70000 }; // 1.000.000
const SPEND = {
  reserved: { Hogar: 200000, Comida: 80000, Transporte: 40000, Café: 60000, Salud: 0, Ocio: 20000, Ropa: 0 },
  paid:     { Hogar: 400000, Comida: 80000, Transporte: 40000, Café: 60000, Salud: 0, Ocio: 20000, Ropa: 0 },
  tight:    { Hogar: 300000, Comida: 200000, Transporte: 100000, Café: 90000, Salud: 60000, Ocio: 100000, Ropa: 50000 },
  over:     { Hogar: 400000, Comida: 230000, Transporte: 120000, Café: 90000, Salud: 60000, Ocio: 110000, Ropa: 70000 },
  equal:    { Hogar: 400000, Comida: 200000, Transporte: 100000, Café: 90000, Salud: 60000, Ocio: 100000, Ropa: 50000 },
  saved:    { Hogar: 200000, Comida: 128700, Transporte: 40000, Café: 60000, Salud: 0, Ocio: 20000, Ropa: 0 },
  past:     { Hogar: 400000, Comida: 210000, Transporte: 95000, Café: 70000, Salud: 40000, Ocio: 75000, Ropa: 30000 },
  early:    { Hogar: 0, Comida: 40000, Transporte: 20000, Café: 0, Salud: 0, Ocio: 0, Ropa: 0 },
};
const GOAL = { name: "Viaje a la costa", icon: "flight", pct: 57 };
// SELLO-144 (approved by the owner 2026-11-03): up to five goals in the pill's place. Starred first, then the
// rest; inside each group the higher exact completion first. ★ marks a starred goal (data only:
// the pill shows no star).
const GOALS = {
  viaje:   { name: "Viaje a la costa", icon: "flight", pct: 57, star: true },
  celular: { name: "Celular nuevo", icon: "phone_iphone", pct: 100 },
  fondo:   { name: "Fondo de emergencia", icon: "home", pct: 42 },
  bici:    { name: "Bicicleta", icon: "fitness_center", pct: 18 },
  ingles:  { name: "Curso de inglés", icon: "school", pct: 5 },
};

// Budget status stamps (owner-picked wording).
const STAMPS = {
  ok:    ["Al día", ""],           // spending below the limit
  done:  ["Cumplido", ""],         // a closed month that ended below the limit (proposal, needs confirmation)
  equal: ["Al límite", "neutral"],  // spending exactly equal to the limit
  over:  ["Excedido", "loss"],      // spending above the limit
  tight: ["Sin margen", "neutral"], // planned payments use up exactly what is left
  short: ["Revisar", "warn"],       // planned payments are more than what is left
  none:  ["Sin límite", "neutral"], // no finite limit
};
const stamp = key => `<span class="stamp ${STAMPS[key][1]}">${STAMPS[key][0]}</span>`;

// `left` is the remainder before reservations (R); `hero` is the Home answer (M).
const SCENES = {
  reserved: { spend: "reserved", label: "Restante del presupuesto", hero: 400000, status: "ok", daily: 40000, days: 10, reserved: 200000, payments: 1 },
  paid:     { spend: "paid", label: "Restante del presupuesto", hero: 400000, status: "ok", daily: 40000, days: 10 },
  // After recording 48.700 in Comida on the default scene: 400.000 − 48.700; per day floor(351.300 / 10) to COP 50.
  saved:    { spend: "saved", label: "Restante del presupuesto", hero: 351300, status: "ok", daily: 35100, days: 10, reserved: 200000, payments: 1, toast: "Gasto anotado: 48.700 en Comida" },
  tight:    { spend: "tight", label: "Restante del presupuesto", hero: 0, status: "tight", daily: 0, days: 10, reserved: 100000, payments: 1 },
  short:    { spend: "tight", label: "Falta para tus pagos", hero: 50000, status: "short", daily: 0, days: 10, reserved: 150000, payments: 1, left: 100000, action: "Ajustar presupuesto" },
  // RE29 (SELLO-114): already 80.000 over the limit with 200.000 reserved: Revisar, not Excedido.
  shortOver: { spend: "over", label: "Falta para tus pagos", hero: 280000, status: "short", daily: 0, days: 10, reserved: 200000, payments: 1, left: -80000, action: "Ajustar presupuesto" },
  // RE10 (SELLO-114): Sello recorded four payments on opening; the notice stays until Listo.
  recorded: { spend: "paid", label: "Restante del presupuesto", hero: 400000, status: "ok", daily: 40000, days: 10, notice: "Sello anotó 4 pagos fijos" },
  over:     { spend: "over", label: "Restante del presupuesto", hero: -80000, status: "over", daily: 0, days: 10, action: "Ajustar presupuesto" },
  equal:    { spend: "equal", label: "Restante del presupuesto", hero: 0, status: "equal", daily: 0, days: 10 },
  // September, closed: 1.000.000 − 920.000. No per-day line, no reservations, no goal.
  past:     { spend: "past", month: "Septiembre", current: "octubre", label: "Restante del presupuesto", hero: 80000, status: "done", closed: "Mes cerrado · gastaste 920.000 de 1.000.000.", mvp: true },
  // Celular nuevo reached 100 % from Metas while Viaje a la costa is the goal on Recibo.
  reachedOther: { spend: "reserved", label: "Restante del presupuesto", hero: 400000, status: "ok", daily: 40000, days: 10, reserved: 200000, payments: 1, reachedGoal: ["Celular nuevo", "phone_iphone"] },
  nolimit:  { spend: "paid", label: "Has gastado este mes", hero: 600000, status: "none", unlimited: true },
  early:    { spend: "early", month: "Noviembre", label: "Restante del presupuesto", hero: 740000, status: "ok", daily: 26400, days: 28, reserved: 200000, payments: 1, invite: true },
  // SELLO-144: the carousel. `at` is the pill on screen (0 = first; Recibo always opens there).
  one:      { spend: "reserved", label: "Restante del presupuesto", hero: 400000, status: "ok", daily: 40000, days: 10, reserved: 200000, payments: 1, goals: ["viaje"] },
  three:    { spend: "reserved", label: "Restante del presupuesto", hero: 400000, status: "ok", daily: 40000, days: 10, reserved: 200000, payments: 1, goals: ["viaje", "fondo", "bici"] },
  // Only Viaje is starred, so Celular nuevo (100 %, not yet lograda) leads the unstarred ones.
  five:     { spend: "reserved", label: "Restante del presupuesto", hero: 400000, status: "ok", daily: 40000, days: 10, reserved: 200000, payments: 1, goals: ["viaje", "celular", "fondo", "bici", "ingles"], at: 1 },
  today:    { spend: "reserved", label: "Restante del presupuesto", hero: 600000, status: "ok", daily: 60000, days: 10, mvp: true },
};
const payments = n => n === 1 ? "1 pago previsto" : `${n} pagos previstos`;

function answer(s) {
  const long = fmt(s.hero).length > 8;
  let h = `<section class="slip pinked" aria-label="Tu mes"><div class="answer-head"><span class="label">${s.label}</span>${stamp(s.status)}</div><div class="hero ${long ? "long" : ""}">${money(s.hero)}</div>`;
  if (s.closed) h += `<p class="closed-note">${s.closed}</p>`;
  if (s.status === "short") h += s.left < 0
    ? `<p class="answer-note">Pasaste tu límite por ${fmt(-s.left)} y tus pagos previstos suman ${fmt(s.reserved)}.</p>`
    : `<p class="answer-note">Quedan ${fmt(s.left)} y tus pagos previstos suman ${fmt(s.reserved)}.</p>`;
  if (s.daily !== undefined || s.reserved) {
    h += `<div class="tear" aria-hidden="true"></div>`;
    if (s.daily !== undefined) h += `<p class="daily-big"><b>${money(s.daily)}</b><span>por día</span></p><span class="caption">${s.days} días, incluido hoy</span>`;
    if (s.reserved) h += `<button class="footnote">${icon("event")}<span>Incluye <b>${money(s.reserved)}</b> separado para ${payments(s.payments)}</span>${icon("chevron_right", "chev")}</button>`;
  }
  if (s.action) h += `<button class="text-link">${s.action}${icon("chevron_right")}</button>`;
  return h + `</section>`;
}

// Goal notch: the pill itself is the progress bar. At 100 % not yet lograda it turns green
// with a round ✓ (Marcar como lograda) in place of the +.
function pill(g = GOAL, place = "", tag = "section") {
  const done = g.pct >= 100;
  const label = `${g.name}, ${g.pct} por ciento${place}. Ver meta`;
  const round = done
    ? `<button class="gw-round" aria-label="Marcar ${g.name} como lograda">${icon("check")}</button>`
    : `<button class="gw-round" aria-label="Aportar a ${g.name}">${icon("add")}</button>`;
  return `<${tag} class="gw-fill ${done ? "reached" : ""}" style="--p:${Math.min(g.pct, 100)}%"><button class="gw-fill-main" aria-label="${label}">${icon(g.icon)}<span>${g.name}</span><b>${g.pct} %</b></button>${round}</${tag}>`;
}

// SELLO-144: one goal is today's pill exactly. Two to five: the same slot swipes sideways and
// loops; dots just under it show the place. No peek of the next pill (owner, 2026-11-03).
function goal(s = {}) {
  const ids = s.goals ?? null;
  if (!ids || ids.length === 1) return pill(ids ? GOALS[ids[0]] : GOAL);
  const at = s.at ?? 0, n = ids.length;
  const here = GOALS[ids[at]];
  const place = `, meta ${at + 1} de ${n}`;
  const dots = `<div class="gw-dots" aria-hidden="true">${ids.map((_, i) => `<i class="${i === at ? "on" : ""}"></i>`).join("")}</div>`;
  return `<section class="gw-carousel" aria-roledescription="carrusel" aria-label="Tus metas">${pill(here, place, "div")}${dots}</section>`;
}

function categoryGrid(s) {
  const cells = Object.entries(SPEND[s.spend]).map(([name, spent]) => {
    const unlimited = !!s.unlimited, limit = LIMITS[name], left = unlimited ? spent : limit - spent;
    const over = !unlimited && left < 0, fill = unlimited ? 0 : over ? 100 : Math.round(100 * spent / limit);
    const circle = unlimited
      ? unlimitedCircle(ICONS[name])
      : `<span class="circle ${over ? "over" : ""}" style="--fill:${fill}%">${icon(ICONS[name])}<span class="on">${icon(ICONS[name])}</span></span>`;
    return `<button class="cell">${circle}<span class="cell-text"><span>${name}</span><b>${money(left)}</b>${unlimited ? "<small>sin límite</small>" : ""}</span></button>`;
  }).join("");
  return `<section class="slip"><div class="section-head"><h2>Tus categorías</h2><button class="band-chip paper">${icon("add")}Nueva categoría</button></div><div class="grid">${cells}</div></section>`;
}

function homeBody(s) {
  let b = answer(s);
  if (s.invite) b += `<button class="invite">${icon("insights")}<span>Ver cómo cerraste octubre</span>${icon("chevron_right", "chev")}</button>`;
  if (!s.mvp) b += goal(s);
  if (s.reachedGoal) b += `<section class="reached" aria-label="Meta alcanzada">${icon("check")}<span><b>${s.reachedGoal[0]}</b><small>Llegó al 100 %</small></span><button>Marcar como lograda</button></section>`;
  return b + categoryGrid(s);
}

function special(kind) {
  if (kind === "loading") return `<div role="status" aria-label="Cargando"><section class="slip"><div class="skel" style="width:45%"></div><div class="skel big"></div><div class="skel" style="width:60%"></div></section><section class="slip"><div class="skel" style="width:35%"></div><div class="skel"></div><div class="skel"></div></section></div>`;
  if (kind === "empty") return `<section class="slip empty">${icon("receipt_long")}<h2>Dale forma a tu mes</h2><p>Crea una categoría para lo que importa: la casa, la comida, el ocio o tu ahorro.</p><button class="outline-btn">Crear categoría</button></section>`;
  if (kind === "failed") return `<section class="slip empty">${icon("error")}<h2>No pudimos leer tu mes</h2><p>Tus registros siguen guardados. Vuelve a intentarlo.</p><button class="outline-btn">Reintentar</button></section>`;
  if (kind === "stale") return `<div class="stale" role="status">Mostramos tus últimos datos leídos.<button class="text-link">Reintentar</button></div>` + homeBody(SCENES.reserved);
}

// SELLO-144, approved by the owner 2026-11-03.
const CAROUSEL = [
  { title: "1 goal · today's pill, no dots", scene: "one" },
  { title: "3 goals · first pill, dots under it", scene: "three" },
  { title: "5 goals · on the 2nd, a 100 % goal not yet lograda", scene: "five" },
];

const STATES = [
  { title: "Default · a payment is reserved", scene: "reserved" },
  { title: "Reserved payment recorded", scene: "paid" },
  { title: "After saving · brief confirmation", scene: "saved" },
  { title: "Sin margen · payments use up what is left", scene: "tight" },
  { title: "Revisar · payments are more than what is left", scene: "short" },
  { title: "Revisar · already over, payments still reserved (RE29, added 2026-10-25)", scene: "shortOver" },
  { title: "Excedido · over the limit", scene: "over" },
  { title: "Al límite · spending equals the limit", scene: "equal" },
  { title: "Sin límite · no finite limits", scene: "nolimit" },
  { title: "Early month · review invitation", scene: "early" },
  { title: "A past month · September", scene: "past" },
  { title: "Another goal reached 100 %", scene: "reachedOther" },
  { title: "After opening · Sello recorded payments (added 2026-10-25)", scene: "recorded" },
  { title: "Today's scope · no Metas, no recurrence", scene: "today", tabs: 2 },
  { title: "First use", special: "empty" },
  { title: "Loading", special: "loading" },
  { title: "Failed read", special: "failed" },
  { title: "Stale data", special: "stale" },
  { title: "Large text · 200%", scene: "reserved", large: true },
];

function render() {
  const o = Object.fromEntries(["ink", "theme", "scale", "tabs"].map(id => [id, document.getElementById(id).value]));
  const carousel = document.getElementById("carousel");
  if (carousel) carousel.innerHTML = frames(CAROUSEL, o);
  document.getElementById("states").innerHTML = frames(STATES, o);
}

function frames(list, o) {
  return list.map(d => {
    const s = d.scene ? SCENES[d.scene] : null;
    const body = d.special ? special(d.special) : homeBody(s);
    const scale = d.large ? 2 : Number(o.scale);
    return `<div class="frame-card"><h3>${d.title}</h3><div class="phone"><div class="app ${scale >= 1.6 ? "large" : ""}" data-ink="${o.ink}" data-theme="${o.theme}" style="--scale:${scale}">${appShell({ month: s?.month, body, tabs: d.tabs ?? Number(o.tabs), toast: s?.toast, notice: s?.notice, active: 0, current: s?.current })}</div></div></div>`;
  }).join("");
}
document.querySelectorAll(".controls select").forEach(s => s.addEventListener("change", render));
render();
