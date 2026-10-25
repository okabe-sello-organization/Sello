// Sello v2 · Income (locked 2026-10-15). Synthetic, hand-authored figures (COP, no symbol).
// Allocation is category budgets only (ADR 0012 BG4); expected income stays apart (BG3).

const INCOMES = [
  ["Salario", "jue 1 oct", "", 1600000],
  ["Freelance", "mié 14 oct", "Diseño de logo", 250000],
  ["Transferencia", "lun 19 oct", "Devolución del viaje", 150000],
]; // 2.000.000
const SEP_INCOMES = [["Salario", "mar 1 sep", "", 1600000], ["Freelance", "lun 15 sep", "Página web", 400000]]; // 2.000.000
const BUDGETS = 1000000; // sum of finite category limits for October

// Scenes: recorded income I, category budgets A, optional expected income F (future recurrence).
const SCENES = {
  normal:   { recorded: 2000000, budgets: BUDGETS },
  over:     { recorded: 2000000, budgets: 2800000 },
  planned:  { recorded: 2000000, budgets: 2800000, expected: [["Salario", "vie 30 oct", 1000000]] },
  past:     { recorded: 2000000, budgets: 1000000, month: "septiembre", closed: true },
};

const leaderLine = (label, n, cls = "") => `<div class="leader ${cls} ${n < 0 ? "neg" : ""}"><span>${label}</span><i class="dots" aria-hidden="true"></i><b>${money(n)}</b></div>`;
function splitBar(assigned, total) {
  if (assigned >= total) return `<div class="split" aria-hidden="true"><i class="assigned" style="width:100%"></i></div>`;
  const a = 100 * assigned / total;
  return `<div class="split" aria-hidden="true"><i class="assigned" style="width:${a}%"></i><i class="free" style="width:${100 - a}%"></i></div><p class="legend"><span><i></i>En presupuestos</span><span><i class="free"></i>Sin asignar</span></p>`;
}

// I1: lead with what came in; the split and the unassigned amount sit under the tear line.
function heroI1(s) {
  const free = s.recorded - s.budgets;
  return `<section class="slip pinked"><h2 class="q">Entró en ${s.month ?? "octubre"}</h2><p class="answer">${money(s.recorded)}</p><div class="tear" aria-hidden="true"></div>${splitBar(s.budgets, s.recorded)}${leaderLine("En presupuestos de categorías", s.budgets)}${leaderLine("Sin asignar", free)}${free < 0 ? `<p class="basis">Tus presupuestos suman ${fmt(s.budgets)}; anotaste ${fmt(s.recorded)}.</p>` : ""}</section>`;
}
const nudge = free => free > 0
  ? `<section class="sug sug-tint"><div class="sug-head"><span class="sug-icon">${icon("insights")}</span><b>Una sugerencia</b><button class="icon-btn" aria-label="Descartar sugerencia">${icon("close")}</button></div><p class="sug-fact">${fmt(free)} de lo que entró aún no tiene un presupuesto.</p><p class="sug-advice">Podrías darle un destino: ahorro, ocio o una reserva, con su propia categoría.</p><button class="text-link">Asignar a una categoría${icon("chevron_right")}</button></section>`
  : "";

// Expected income stays in a dashed "Plan del mes" box, written as receipt lines.
function plan(s) {
  if (!s.expected) return "";
  const f = s.expected.reduce((a, x) => a + x[2], 0);
  const items = s.expected.map(([src, date, n]) => `<span class="exp-chip">${icon("event")}${src} · ${date} · <b>${money(n)}</b></span>`).join("");
  return `<section class="plan-slip"><div class="plan-head"><span class="plan-tag">Plan del mes</span><span class="caption">Incluye lo que esperas recibir</span></div>${leaderLine("Anotado", s.recorded)}${leaderLine("Esperado", f, "plan")}<div class="plan-total">${leaderLine("Planeado", s.recorded + f)}</div>${leaderLine("Sin asignar en el plan", s.recorded + f - s.budgets, "strong")}<div class="exp-list">${items}</div><p class="plan-foot">Lo esperado se anota solo en su fecha.</p></section>`;
}

function history(list = INCOMES) {
  const rows = list.map(([src, date, note, n], i) => `<button class="inc-row ${i === list.length - 1 ? "last" : ""}"><span><b>${src}</b><small>${date}${note ? ` · ${note}` : ""}</small></span><b>${money(n)}</b>${icon("chevron_right", "chev")}</button>`).join("");
  return `<section class="slip"><h2 class="q">Lo que entró</h2>${rows}<div class="total-line"><span>Total</span>${money(2000000)}</div></section>`;
}

function body(o, { scene = "normal", state } = {}) {
  if (state === "loading") return `<div role="status" aria-label="Cargando"><section class="slip"><div class="skel" style="width:40%"></div><div class="skel big"></div><div class="skel" style="width:70%"></div></section><section class="slip"><div class="skel"></div><div class="skel"></div><div class="skel"></div></section></div>`;
  if (state === "failed") return `<section class="slip empty">${icon("error")}<h2>No pudimos leer tus ingresos</h2><p>Tus registros siguen guardados. Vuelve a intentarlo.</p><button class="outline-btn">Reintentar</button></section>`;
  if (state === "empty") return `<section class="slip empty">${icon("account_balance")}<h2>Aún no anotas ingresos en octubre</h2><p>Sello funciona sin ingresos. Si los anotas, verás cuánto tiene un presupuesto y cuánto no.</p><button class="outline-btn">Anotar ingreso</button></section>`;
  const s = SCENES[scene];
  const hero = heroI1(s);
  return `${hero}${plan(s)}${s.closed ? "" : nudge(s.recorded - s.budgets)}${history(s.closed ? SEP_INCOMES : INCOMES)}`;
}

const frame = (o, html, { tall = false, current } = {}) => `<div class="phone ${tall ? "tall" : ""}"><div class="app" data-ink="${o.ink}" data-theme="${o.theme}">${subShell({ title: current ? "Ingresos de septiembre" : "Ingresos de octubre", body: html, dock: "Anotar ingreso", month: "Septiembre", current })}</div></div>`;
const card = (title, note, inner) => `<div class="frame-card"><h3>${title}</h3><p>${note}</p>${inner}</div>`;

function render() {
  const o = reviewOptions(["ink", "theme"]);
  const set = (id, h) => document.getElementById(id).innerHTML = h;
  set("whole", card("Whole screen, scrolled", "Hero → suggestion (only while something is unassigned) → this month’s income, each one editable. The dock records income here.", frame(o, body(o), { tall: true })));
  set("future",
    card("Budgets above recorded income", "Today’s scope. Negative unassigned in red with the two facts behind it; no suggestion, no blame.", frame(o, body(o, { scene: "over" }), { tall: true })) +
    card("With expected income · future", "When recurring income exists: a dashed “Plan del mes” keeps the expected salary apart. Recorded stays −800.000; the plan leaves 200.000 unassigned.", frame(o, body(o, { scene: "planned" }), { tall: true })));
  set("states",
    card("No income yet", "The app works without income; Recibo never depends on it.", frame(o, body(o, { state: "empty" }))) +
    card("Loading", "Skeleton slips.", frame(o, body(o, { state: "loading" }))) +
    card("Failed read", "Never zero income or an empty list.", frame(o, body(o, { state: "failed" }))) +
    card("A past month", "Same ribbon as Recibo and Resumen. No suggestion for a closed month.", frame(o, body(o, { scene: "past" }), { current: "octubre" })));
}
document.querySelectorAll(".controls select").forEach(s => s.addEventListener("change", render));
render();
