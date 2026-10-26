// Sello v2 · Ajustar presupuesto (locked 2026-10-26 by the owner). Hand-authored fixtures,
// the AJ examples: October 1.000.000 of limits; Excedido 1.080.000 spent, Revisar 900.000
// spent with 150.000 reserved. Financial date jue 22 oct 2026.

const ICONS = { Hogar: "home", Comida: "restaurant", Transporte: "directions_bus", Café: "local_cafe", Salud: "medical_services", Ocio: "theaters", Ropa: "checkroom", Regalos: "receipt_long" };
const LIMITS = { Hogar: 400000, Comida: 200000, Transporte: 100000, Café: 50000, Ocio: 100000, Ropa: 70000, Salud: 80000 };
const SPEND = {
  over:  { Hogar: 400000, Comida: 230000, Transporte: 120000, Café: 90000, Ocio: 110000, Ropa: 70000, Salud: 60000 },
  tight: { Hogar: 300000, Comida: 200000, Transporte: 100000, Café: 90000, Ocio: 100000, Ropa: 50000, Salud: 60000 },
};
const STAMPS = { ok: ["Al día", ""], over: ["Excedido", "loss"], short: ["Revisar", "warn"] };
const stamp = k => `<span class="stamp ${STAMPS[k][1]}">${STAMPS[k][0]}</span>`;

// A category row: circle, name, "gastaste X de Y", signed remainder, chevron; one sentence.
function row(name, spent, limit, { archived = false } = {}) {
  if (limit === null) {
    return `<button class="rank-row" aria-label="${name}. Sin límite. Gastaste ${spoken(spent)}. Ponerle límite">${unlimitedCircle(ICONS[name])}<span class="rank-main"><span>${name}</span><small class="adj-cap">sin límite</small></span>${money(spent)}${icon("chevron_right", "chev")}</button>`;
  }
  const left = limit - spent, over = left < 0, fill = over ? 100 : Math.round(100 * spent / limit);
  const caption = archived ? "Archivada · desarchívala para cambiar su límite" : `gastaste ${fmt(spent)} de ${fmt(limit)}`;
  const label = archived
    ? `${name}. Archivada. Desarchívala para cambiar su límite. Abrir categoría`
    : `${name}. ${over ? "Te pasaste por " + spoken(-left) : "Quedan " + spoken(left)}. Gastaste ${spoken(spent)} de ${spoken(limit)}. Cambiar límite`;
  return `<button class="rank-row" aria-label="${label}"><span class="circle ${over ? "over" : ""}" style="--fill:${fill}%" aria-hidden="true">${icon(ICONS[name] || "receipt_long")}<span class="on">${icon(ICONS[name] || "receipt_long")}</span></span><span class="rank-main"><span>${name}</span><small class="adj-cap">${caption}</small></span>${money(left)}${icon("chevron_right", "chev")}</button>`;
}

// Overspent first, then least room left; ties by name. Those without a limit go apart.
function rows(spend, limits, extra = "") {
  return Object.keys(spend)
    .map(n => ({ n, spent: spend[n], limit: limits[n] }))
    .sort((a, b) => (a.limit - a.spent) - (b.limit - b.spent) || a.n.localeCompare(b.n))
    .map(c => row(c.n, c.spent, c.limit)).join("") + extra;
}

function body(s) {
  if (s.loading) return `<div role="status" aria-label="Cargando"><section class="slip"><div class="skel" style="width:45%"></div><div class="skel big"></div><div class="skel"></div></section><section class="slip"><div class="skel" style="width:35%"></div><div class="skel"></div><div class="skel"></div></section></div>`;
  if (s.failed) return `<section class="slip empty">${icon("error")}<h2>No pudimos leer tu presupuesto</h2><p>Sello no muestra una cifra que no pudo leer. Vuelve a intentarlo.</p><button class="outline-btn">Reintentar</button></section>`;
  const limits = { ...LIMITS, ...(s.limits || {}) };
  const total = Object.values(limits).reduce((a, b) => a + b, 0);
  const spent = Object.values(SPEND[s.spend]).reduce((a, b) => a + b, 0);
  let h = s.stale ? `<div class="stale" role="status">Mostramos tus últimos datos leídos.<button class="text-link">Reintentar</button></div>` : "";
  h += `<section class="slip pinked" aria-label="Tu presupuesto de octubre"><div class="answer-head"><span class="label">${s.label}</span>${stamp(s.status)}</div><div class="hero">${money(s.hero)}</div>`;
  if (s.sentence) h += `<p class="answer-note">${s.sentence}</p>`;
  h += `<div class="tear" aria-hidden="true"></div>`;
  h += `<div class="leader"><span>Límite de octubre</span><i class="dots"></i><b>${money(total)}</b></div>`;
  h += `<div class="leader"><span>Gastado</span><i class="dots"></i><b>${money(spent)}</b></div>`;
  if (s.reserved) h += `<button class="footnote">${icon("event")}<span>Incluye <b>${money(s.reserved)}</b> separado para 1 pago previsto</span>${icon("chevron_right", "chev")}</button>`;
  h += `</section>`;
  h += `<section class="slip"><h2 class="q">Tus categorías</h2><p class="caption" style="margin:0">Toca una para cambiar su límite de octubre.</p><div class="rank">${rows(SPEND[s.spend], limits, s.extraRows || "")}</div>${s.open ? `<p class="open-head">Sin límite</p><div class="rank">${s.open}</div>` : ""}</section>`;
  return h;
}

// ——— The limit sheet, over the Excedido screen ———

const padKeys = () => `<div class="pad" role="group" aria-label="Teclado de Sello">${["1", "2", "3", "4", "5", "6", "7", "8", "9", "000", "0"].map(k => `<button class="${k === "000" ? "k000" : ""}">${k}</button>`).join("")}<button class="del" aria-label="Borrar">${icon("backspace")}</button></div>`;

function sheet({ scope = "month", amount, chip, fact, unknown }) {
  const month = scope === "month";
  const scopeLine = month
    ? "Solo cambia octubre. Los meses siguientes no cambian."
    : "Desde octubre, hasta que lo vuelvas a cambiar. Los meses anteriores no cambian.";
  const chips = [["spent", "Lo gastado", 90000], ["month", "Cubre el mes", 130000]]
    .map(([k, t, n]) => `<button class="chip" aria-pressed="${chip === k}">${t} · ${fmt(n)}</button>`).join("");
  const label = unknown ? "Comprobar si se guardó" : month ? "Guardar solo octubre" : "Guardar desde octubre";
  let h = `<div class="composer limit-sheet" role="dialog" aria-label="Límite de Café">`;
  h += `<div class="ls-head"><span class="circle" aria-hidden="true">${icon("local_cafe")}</span><span class="ls-title"><b>Límite de Café</b><small>Gastaste 90.000 de 50.000 en octubre</small></span><button class="icon-btn" aria-label="Cerrar">${icon("close")}</button></div>`;
  h += `<div class="ls-body" ${unknown ? 'style="opacity:.55"' : ""}>`;
  h += `<div class="seg" role="group" aria-label="Para qué meses"><button aria-pressed="${month}">Solo octubre</button><button aria-pressed="${!month}">Desde octubre</button></div>`;
  h += `<p class="ls-scope">${scopeLine}</p>`;
  h += `<div class="ls-amount"><div class="amount-meta"><span>Límite en pesos</span><span>antes ${fmt(50000)}</span></div><div class="amount-value">${money(amount)}<span class="caret"></span></div></div>`;
  h += `<div class="chips">${chips}</div>`;
  h += `<p class="ls-fact">${icon("receipt_long")}<span>${fact}</span></p></div>`;
  if (unknown) h += `<div class="ls-body"><div class="ls-unknown"><b>Aún no sabemos si se guardó.</b>Compruébalo antes de volver a intentarlo.</div></div>`;
  h += `<div class="entry-action"><button class="primary">${label}</button></div>`;
  h += padKeys().replace('class="pad"', `class="pad ${unknown ? "locked" : ""}"`);
  return h + `</div>`;
}

const EXCEDIDO = { spend: "over", label: "Restante del presupuesto", hero: -80000, status: "over" };
const OPEN = row("Regalos", 10000, null);
const ARCHIVED = row("Viejo", 0, 30000, { archived: true });

const SCREENS = [
  ["Excedido", "Overspent categories first, each with its own signed remainder. Categories without a limit are listed apart because their spending counts too; an archived one says why it cannot be changed here and opens its editor.",
    { ...EXCEDIDO, open: OPEN, extraRows: ARCHIVED }],
  ["Revisar · payments are more than what is left", "The reserved payments stay as Recibo's footnote and open Pagos previstos; categories never show them.",
    { spend: "tight", label: "Falta para tus pagos", hero: 50000, status: "short", sentence: "Quedan 100.000 y tus pagos previstos suman 150.000.", reserved: 150000 }],
  ["Back from the sheet", "Café's limit for October went from 50.000 to 150.000. The screen re-reads the month: the stamp and the order change, and the notice names the period.",
    { spend: "over", label: "Restante del presupuesto", hero: 20000, status: "ok", limits: { Café: 150000 } }, "Límite de Café para octubre: 150.000"],
  ["Stale", "The last good read, labelled; never shown as current.", { ...EXCEDIDO, stale: true }],
  ["Loading", "", { loading: true }],
  ["Failed read", "Never zero or an empty list.", { failed: true }],
];

const SHEETS = [
  ["Typed 150.000", "Solo octubre is chosen. The line under the chips is the editor's “pasaría a” total plus what the month would have left (owner-approved, 2026-10-26).",
    { amount: 150000, fact: "Tu límite total de octubre pasaría a <b>1.100.000</b>. Te quedarían <b>20.000</b>." }],
  ["Chip · Cubre el mes", "Covers the whole overrun from this category: 50.000 + 80.000 (AJ04). Chips come from the domain, never the screen.",
    { amount: 130000, chip: "month", fact: "Tu límite total de octubre pasaría a <b>1.080.000</b>. Quedarías justo en el límite." }],
  ["Chip · Lo gastado", "Covers Café alone; the line stays honest that the month is still over (AJ06).",
    { amount: 90000, chip: "spent", fact: "Tu límite total de octubre pasaría a <b>1.040.000</b>. Aún te pasarías por <b>40.000</b>." }],
  ["Desde octubre", "A new default from October on, which also replaces October's own limit (AJ03). The button names the scope before saving (ADR 0001 D04).",
    { scope: "from", amount: 150000, fact: "Tu límite total de octubre pasaría a <b>1.100.000</b>. Te quedarían <b>20.000</b>." }],
  ["Unknown outcome", "As in entry: everything locks and the only action asks again with the same operation. Nothing is sent twice.",
    { amount: 150000, unknown: true, fact: "Tu límite total de octubre pasaría a <b>1.100.000</b>. Te quedarían <b>20.000</b>." }],
];

const frame = (o, inner, extra = "") => `<div class="phone"><div class="app" data-ink="${o.ink}" data-theme="${o.theme}" style="--scale:1">${subShell({ title: "Ajustar presupuesto", body: inner, active: 0, dock: null })}${extra}</div></div>`;
const card = (title, note, inner) => `<div class="frame-card"><h3>${title}</h3><p>${note}</p>${inner}</div>`;
const toast = text => `<div class="toast" role="status" style="bottom:110px">${icon("check")}${text}</div>`;

function render() {
  const o = reviewOptions(["ink", "theme"]);
  document.getElementById("screen").innerHTML = SCREENS
    .map(([title, note, s, notice]) => card(title, note, frame(o, body(s), notice ? toast(notice) : ""))).join("");
  document.getElementById("sheet").innerHTML = SHEETS
    .map(([title, note, s]) => card(title, note, frame(o, body(EXCEDIDO), `<div class="scrim">${sheet(s)}</div>`))).join("");
}
document.querySelectorAll(".controls select").forEach(s => s.addEventListener("change", render));
render();
