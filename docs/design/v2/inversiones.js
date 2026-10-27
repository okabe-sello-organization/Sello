// Sello v2 · Inversiones (locked 2026-10-16). Owner picks: P1 (the Recibo slip), Gana / Pierde
// stamps, the ink “Anotar movimiento” dock, the recorded-values chart, T2 type chips in entry,
// withdrawals limited to the last recorded value, and Vale hoy starting as the first contribution. Synthetic data (COP, no symbol).
// Gain = value + withdrawals + dividends − contributions (ARCHITECTURE.md, investments);
// percentage = gain ÷ contributions, one decimal, since the first contribution. Not IRR.
//   Parqueadero  19.000.000 +       0 + 350.000 − 18.000.000 = +1.350.000 → +7,5 %
//   CDT Banco    10.746.000 +       0 +       0 − 10.000.000 =   +746.000 → +7,46 % → +7,5 %
//   Fondo         5.242.000 + 500.000 +       0 −  6.000.000 =   −258.000 → −4,3 %
//   Ecopetrol     3.050.000 +       0 + 178.000 −  3.000.000 =   +228.000 → +7,6 %
//   Total        38.038.000 + 500.000 + 528.000 − 37.000.000 = +2.066.000 → +5,58 % → +5,6 %
// Share of value: 49,9 % · 28,3 % · 13,8 % · 8,0 %.
// Total in loss: Fondo + Ecopetrol = 8.292.000 + 500.000 + 178.000 − 9.000.000 = −30.000 → −0,3 %.

const H = [
  { name: "Parqueadero", kind: "Finca raíz", icon: "home", value: 19000000, gain: 1350000, pct: "7,5", up: true, valued: "12 jun", share: 49.9 },
  { name: "CDT Banco", kind: "CDT a 360 días", icon: "account_balance", value: 10746000, gain: 746000, pct: "7,5", up: true, valued: "30 sep", share: 28.3 },
  { name: "Fondo de acciones", kind: "Fondo colectivo", icon: "insights", value: 5242000, gain: -258000, pct: "4,3", up: false, valued: "14 oct", share: 13.8 },
  { name: "Ecopetrol", kind: "Acciones", icon: "trending_up", value: 3050000, gain: 228000, pct: "7,6", up: true, valued: "9 oct", share: 8.0 },
];
const TOTAL = { value: 38038000, contributed: 37000000, withdrawn: 500000, paid: 528000, gain: 2066000, pct: "5,6", up: true, since: "mar 2025", from: "12 jun", to: "14 oct" };
const LOSS = { value: 8292000, contributed: 9000000, withdrawn: 500000, paid: 178000, gain: -30000, pct: "0,3", up: false, since: "mar 2025", from: "9 oct", to: "14 oct" };
const SHADES = [100, 68, 42, 22];
const shade = i => `color-mix(in srgb,var(--brand) ${SHADES[i]}%,var(--paper))`;

// Signed return: arrow, sign and words carry the same meaning as the colour.
const perf = (t, { words = true } = {}) => `<p class="perf ${t.up ? "gain" : "loss"}">${icon(t.up ? "trending_up" : "trending_down")}<span>${t.up ? "+" : "−"}${t.pct} %</span>${words ? `<span class="perf-gain">${t.up ? "+" : "−"}${fmt(Math.abs(t.gain))} de ${t.up ? "ganancia" : "pérdida"}</span>` : ""}</p>`;
const stamp = t => `<span class="stamp ${t.up ? "" : "loss"}">${t.up ? "Gana" : "Pierde"}</span>`;
const leader = (label, n, cls = "") => `<div class="leader"><span>${label}</span><i class="dots"></i><b class="${cls}">${n}</b></div>`;
// A quiet line, not a link (SELLO-106): each row carries its own date.
const ages = t => `<p class="caption inv-ages">${icon("event")}<span>Valores anotados entre el <b>${t.from}</b> y el <b>${t.to}</b></span></p>`;
const breakdown = t => leader("Aportaste", money(t.contributed)) + leader("Retiraste", money(t.withdrawn)) + leader("Dividendos y rentas", money(t.paid)) + leader("Ganancia", `${t.gain < 0 ? "−" : "+"}${fmt(Math.abs(t.gain))}`, t.gain < 0 ? "neg" : "pos");

// The overview: the Recibo answer slip, with Gana / Pierde as its stamp.
const heroSlip = t => `<section class="slip pinked"><div class="answer-head"><span class="label">Tus inversiones valen</span>${stamp(t)}</div><div class="hero ${t.value >= 10000000 ? "long" : ""}">${money(t.value)}</div>${perf(t)}<p class="caption inv-since">En total, desde ${t.since}</p><div class="tear" aria-hidden="true"></div>${breakdown(t)}${ages(t)}</section>`;

// Distribution: the income split bar, one shade of the ink per investment, largest first.
const distBar = list => `<div class="split inv-split" aria-hidden="true">${list.map((h, i) => `<i style="width:${h.share}%;background:${shade(i)}"></i>`).join("")}</div>`;
const row = (h, i) => `<button class="inv-row" aria-label="${h.name}, ${spoken(h.value)}, ${h.up ? "gana" : "pierde"} ${h.pct} por ciento, valor del ${h.valued}"><span class="inv-ic" style="--c:${shade(i)}">${icon(h.icon)}</span><span><b>${h.name}</b><small>${h.kind} · valor del ${h.valued}</small></span><span class="inv-right"><b>${fmt(h.value)}</b><small class="${h.up ? "pos" : "neg"}">${icon(h.up ? "trending_up" : "trending_down")}${h.up ? "+" : "−"}${h.pct} %</small></span></button>`;
// An investment without a recorded value: no amount and no %, only what was put in. It is not
// in the bar or the total (owner decision: no warning on the overview).
const rowNoValue = h => `<button class="inv-row" aria-label="${h.name}, sin valor anotado, aportaste ${spoken(h.contributed)}"><span class="inv-ic none">${icon(h.icon)}</span><span><b>${h.name}</b><small>${h.kind} · sin valor anotado</small></span><span class="inv-right"><b class="inv-none">Sin valor</b><small>aportaste ${fmt(h.contributed)}</small></span></button>`;
const listSlip = (list, extra = []) => `<section class="slip"><h2 class="q">Dónde está, de mayor a menor</h2>${distBar(list)}${list.map(row).join("")}${extra.map(rowNoValue).join("")}</section>`;
const CRIPTO = { name: "Cripto", kind: "Criptomoneda", icon: "insights", contributed: 2000000 };

const tab = (o, body, { tall = false, bandHtml = "", dock = "Anotar movimiento" } = {}) => `<div class="phone ${tall ? "tall" : ""}"><div class="app" data-ink="${o.ink}" data-theme="${o.theme}">${appShell({ title: "Inversiones", chip: dock === "Agregar inversión" ? null : "Nueva inversión", settings: false, body, tabs: 4, active: 3, band: bandHtml, dock, dockClass: "inv" })}</div></div>`;

// Detail. Ecopetrol: aportes 2.000.000 (mar 2025) + 1.000.000 (jul 2025) = 3.000.000; dividends
// 89.000 (mar 2026) + 89.000 (sep 2026) = 178.000; recorded values below; gain +228.000 (+7,6 %).
// Fondo: aportes 6.000.000, retiro 500.000 (ago 2026), value 5.242.000; gain −258.000 (−4,3 %).
const DETAIL = {
  eco: { name: "Ecopetrol", kind: "Acciones", value: 3050000, contributed: 3000000, withdrawn: 0, paid: 178000, gain: 228000, pct: "7,6", up: true, since: "mar 2025", valued: "9 oct",
    // [month index from mar 2025, recorded value]; contributions as steps.
    values: [[0, 2000000], [4, 3010000], [9, 2900000], [13, 3120000], [19, 3050000]], steps: [[0, 2000000], [4, 3000000]],
    movs: [["value", "Valor de hoy", "vie 9 oct", "3.050.000"], ["paid", "Dividendo", "mar 15 sep", "+89.000"], ["value", "Valor de hoy", "jue 2 abr", "3.120.000"], ["paid", "Dividendo", "dom 15 mar", "+89.000"]], count: 9 },
  fondo: { name: "Fondo de acciones", kind: "Fondo colectivo", value: 5242000, contributed: 6000000, withdrawn: 500000, paid: 0, gain: -258000, pct: "4,3", up: false, since: "may 2025", valued: "14 oct",
    values: [[2, 3000000], [8, 6150000], [14, 5980000], [17, 5600000], [19, 5242000]], steps: [[2, 3000000], [8, 6000000], [17, 5500000]],
    movs: [["value", "Valor de hoy", "mié 14 oct", "5.242.000"], ["out", "Retiro", "lun 3 ago", "−500.000"], ["value", "Valor de hoy", "vie 3 jul", "5.600.000"], ["in", "Aporte", "lun 3 nov 2025", "+3.000.000"]], count: 7 },
};

// Recorded values are dots joined by a thin line (only recorded points, nothing in between is
// claimed); what was put in (aportes − retiros) is a dashed step line.
function chart(d) {
  const W = 300, Hh = 110, max = d.value < 4000000 ? 3500000 : 7000000, x = m => 8 + m * (W - 16) / 19, y = v => Hh - 6 - v * (Hh - 16) / max;
  const steps = d.steps.map(([m, v], i) => `${i ? `H${x(m).toFixed(1)}V${y(v).toFixed(1)}` : `M${x(m).toFixed(1)},${y(v).toFixed(1)}`}`).join("") + `H${x(19)}`;
  const line = d.values.map(([m, v], i) => `${i ? "L" : "M"}${x(m).toFixed(1)},${y(v).toFixed(1)}`).join("");
  const dots = d.values.map(([m, v]) => `<circle cx="${x(m).toFixed(1)}" cy="${y(v).toFixed(1)}" r="4"/>`).join("");
  return `<section class="slip"><h2 class="q">Valor anotado y lo que pusiste</h2><svg class="inv-chart" viewBox="0 0 ${W} ${Hh}" role="img" aria-label="Valores anotados de ${d.since} a octubre 2026"><path class="put" d="${steps}"/><path class="val" d="${line}"/><g class="dots">${dots}</g></svg><div class="axis"><span>mar 25</span><span class="now">oct 26</span></div><p class="legend"><span><i class="val"></i>Valor anotado</span><span><i class="put"></i>Lo que pusiste</span></p></section>`;
}
const movIcon = { value: "insights", paid: "add", in: "add", out: "arrow_back" };
const movs = d => `<section class="slip"><div class="mov-head"><h2 class="q" style="margin:0">Movimientos</h2></div>${d.movs.map(([k, label, date, n], i) => `<div class="mov-row ${k === "out" ? "out" : ""} ${k === "value" ? "val" : ""} ${i === d.movs.length - 1 ? "last" : ""}"><span class="mi">${icon(movIcon[k])}</span><span><b>${label}</b><small>${date}</small></span><b>${n}</b></div>`).join("")}<button class="text-btn">Ver los ${d.count}${icon("chevron_right")}</button></section>`;
const detailHero = d => `<section class="slip pinked"><div class="answer-head"><span class="label">Vale</span>${stamp(d)}</div><div class="gd-saved">${money(d.value)}</div>${perf(d)}<p class="caption inv-since">Valor anotado el ${d.valued} · desde ${d.since}</p><div class="tear" aria-hidden="true"></div>${breakdown(d)}<button class="outline-btn wide inv-update">${icon("insights")}Anotar el valor de hoy</button></section>`;
const detail = (o, d, tall = false) => `<div class="phone ${tall ? "tall" : ""}"><div class="app" data-ink="${o.ink}" data-theme="${o.theme}">${subShell({ title: d.name, body: detailHero(d) + chart(d) + movs(d), tabs: 4, active: 3, dock: "Anotar movimiento", dockClass: "inv", action: ["edit_note", "Editar inversión"] })}</div></div>`;

// Anotar movimiento: the locked entry screen; the investment chips replace categories and one plain
// fact sits under them (no live preview, as on entry). The type is a row of chips above the
// investments (owner pick T2; four tabs in the top bar were too many).
const TYPES = [["in", "Aporte", "add", "Pones dinero en la inversión."], ["value", "Valor de hoy", "insights", "Cuánto vale ahora. Reemplaza el valor anterior."], ["out", "Retiro", "arrow_back", "Sacas dinero de la inversión."], ["paid", "Dividendo o renta", "add", "Te pagó sin que vendieras."]];
const NAMES = [["Parqueadero", "home"], ["CDT Banco", "account_balance"], ["Fondo de acciones", "insights"], ["Ecopetrol", "trending_up"], ["Cripto", "insights"]];
// Retiro is limited to the last recorded value (5.242.000 for Fondo, 14 oct): taking out more would
// leave a negative value. If it is worth more today, the person records the value first.
const MOVE = {
  aporte:  { type: "in", amount: 500000, inv: "Ecopetrol", fact: "Llevas <b>3.000.000</b> aportados en Ecopetrol." },
  valor:   { type: "value", amount: 3120000, inv: "Ecopetrol", fact: "Último valor: <b>3.050.000</b>, anotado el 9 oct. El nuevo lo reemplaza; no se suma." },
  retiro:  { type: "out", amount: 500000, inv: "Fondo de acciones", fact: "Vale <b>5.242.000</b> según el valor del 14 oct." },
  over:    { type: "out", amount: 6000000, inv: "Fondo de acciones", over: 5242000, fact: "Vale <b>5.242.000</b> según el valor del 14 oct. ¿Vale más hoy?", link: "Anotar el valor de hoy" },
  divid:   { type: "paid", amount: 89000, inv: "Ecopetrol", fact: "Llevas <b>178.000</b> en dividendos y rentas de Ecopetrol." },
  fromTab: { type: "in", amount: null, inv: null },
};
const LABEL = { in: "Aporte", value: "Valor de hoy", out: "Retiro", paid: "Dividendo o renta" };
const SIGN = { in: "+", value: "", out: "−", paid: "+" };
const ACTION = { in: "Anotar aporte", value: "Anotar valor", out: "Anotar retiro", paid: "Anotar dividendo" };
const pad = () => `<div class="pad" role="group" aria-label="Teclado de Sello">${["1", "2", "3", "4", "5", "6", "7", "8", "9", "000", "0"].map(k => `<button class="${k === "000" ? "k000" : ""}">${k}</button>`).join("")}<button class="del" aria-label="Borrar">${icon("backspace")}</button></div>`;
function moveForm(m) {
  const ic = (NAMES.find(n => n[0] === m.inv) ?? [, "trending_up"])[1];
  const label = m.over ? `<span class="limit-msg" role="status">Más de lo que vale (${fmt(m.over)})</span>` : `<span>${LABEL[m.type]} en pesos</span>`;
  const value = m.amount === null ? `<div class="amount-value placeholder">0<span class="caret"></span></div>` : `<div class="amount-value ${String(m.amount).length > 6 ? "mid" : ""}">${SIGN[m.type]}${money(m.amount)}<span class="caret"></span></div>`;
  const chips = NAMES.map(([n, i]) => `<button class="chip" aria-pressed="${n === m.inv}">${icon(i)}${n}</button>`).join("");
  const typeChips = `<h3>Tipo</h3><div class="chips one-row">${TYPES.map(([k, t, i]) => `<button class="chip" aria-pressed="${k === m.type}">${t}</button>`).join("")}</div>`;
  const fact = m.fact ? `<p class="goal-fact">${m.fact}${m.link ? ` <button class="text-btn inline">${m.link}</button>` : ""}</p>` : "";
  const ok = m.amount && m.inv && !m.over;
  return `<div class="entry-top">${statusbar()}<div class="entry-bar"><button class="icon-btn" aria-label="Cerrar">${icon("close")}</button><b style="flex:1;font-size:1.2em;margin-left:4px">Movimiento</b></div></div><section class="slip pinked amount-slip"><div class="amount-meta">${label}<button class="date-pill">${icon("event")}Hoy, vie 16</button></div><div class="amount-row"><span class="circle" style="--fill:${m.inv ? 100 : 0}%;--c:var(--ink)" aria-hidden="true">${icon(ic)}<span class="on">${icon(ic)}</span></span>${value}</div></section><div class="entry-mid">${typeChips}<h3>Inversión</h3><div class="chips one-row">${chips}</div>${fact}<button class="note-pill">${icon("edit_note")}<span>Agregar nota</span></button></div><div class="entry-action"><button class="primary inv" ${ok ? "" : "disabled"}>${ACTION[m.type]}</button></div>${pad()}<div class="gesture" style="background:var(--desk)"></div>`;
}

// Agregar / editar inversión, made alike with Nueva meta and Nueva categoría (owner, 2026-10-26):
// the icon circle opens the shared 35 icons (money first); Pesos | Dólares is a segmented switch;
// the figures are typed on the Sello pad, always under the form, and the one it types into is
// edged in ink. "Vale hoy: lo mismo que aportas · Otro valor" sits at the first slip's foot.
const pickInv = ic => ic
  ? `<button class="pick" style="color:var(--ink)" aria-label="Ícono. Cambiar"><span class="ident-icon inv">${icon(ic)}</span>Cambiar</button>`
  : `<button class="pick" style="color:var(--ink)" aria-label="Elegir ícono"><span class="ident-icon empty pick-empty">${icon("add")}</span>Elegir ícono</button>`;
const segInv = usd => `<div class="seg" role="group" aria-label="Moneda" style="margin-top:12px;max-width:none"><button aria-pressed="${!usd}">Pesos</button><button aria-pressed="${usd}">Dólares</button></div>`;
const identInv = (f, usd = false) => `<section class="slip form-slip inv-ident"><div class="ident" style="align-items:center">${pickInv(f.icon)}<div><label class="form-label">Nombre</label><div class="form-field ${f.name ? "" : "placeholder"}">${f.name || "CDT, fondo, acciones…"}</div></div></div><div class="form-msg"><span></span><span>${[...f.name].length}/40</span></div><label class="form-label">Descripción <small>(opcional)</small></label><div class="form-field ${f.kind ? "" : "placeholder"}">${f.kind || "Ej. CDT a 360 días"}</div>${f.edit ? "" : segInv(usd)}${usd ? `<p class="goal-fact">Lo que anotes de esta inversión queda en dólares. No se puede cambiar después.</p>` : ""}</section>`;
// A figure on the pad: edged in ink while the pad types into it; the others step back.
const figure = (label, value, active, extra = "", date = true) => `<section class="slip pinked" style="${active ? "box-shadow:inset 0 0 0 2px var(--ink)" : ""}"><div class="plan-label"><span class="label">${label}</span>${date ? `<button class="chip">${icon("event")}Hoy, vie 16</button>` : ""}</div><p class="plan-amount ${value ? "" : "placeholder"}" style="${active ? "" : "opacity:.55"}">${value || "0"}${active ? `<span class="caret"></span>` : ""}</p>${extra}</section>`;
const valeLine = other => `<p class="goal-fact" style="display:flex;gap:8px;align-items:center">${icon("insights")}<span style="flex:1">${other ? "Vale hoy: otro valor, abajo" : "Vale hoy: lo mismo que aportas"}</span><b style="color:var(--brand)">${other ? "Igual al aporte" : "Otro valor"}</b></p>`;
function invForm(f, { confirm = false } = {}) {
  const ident = identInv(f);
  const first = f.edit ? "" : figure("Primer aporte", f.amount ? money(f.amount) : "", !f.other, valeLine(!!f.other)) + (f.other ? figure("Vale hoy", money(f.other), true, `<p class="goal-fact">Lo que valía antes de anotarla en Sello.</p>`, false) : "");
  const danger = f.edit ? `<section class="slip danger"><h2>Borrar esta inversión</h2><p>Se borran la inversión y sus <b>9 movimientos</b>. Esto no se puede deshacer.</p><button class="outline-btn loss wide">Borrar inversión</button></section>` : "";
  const ok = f.name && f.icon && (f.edit || f.amount);
  const missing = !f.icon ? "Elige un ícono." : !f.name ? "Escribe un nombre." : (!f.edit && !f.amount) ? "Escribe el primer aporte." : "";
  const confirmSlip = confirm ? `<div class="scrim center"><div class="confirm" role="alertdialog" aria-label="Borrar inversión"><h2>¿Borrar ${f.name}?</h2><p>Se borran la inversión y sus <b>9 movimientos</b>. Esto no se puede deshacer.</p><div class="tear" aria-hidden="true"></div><div class="confirm-actions"><button class="ghost">Cancelar</button><button class="del">Borrar</button></div></div></div>` : "";
  return `<div class="entry-top">${statusbar()}<div class="entry-bar"><button class="icon-btn" aria-label="Cerrar">${icon("close")}</button><b style="flex:1;font-size:1.2em;margin-left:4px">${f.edit ? "Editar inversión" : "Nueva inversión"}</b></div></div><div class="entry-mid" style="padding-top:2px">${ident}${first}${danger}</div><div class="entry-action">${missing ? `<p class="caption" style="margin:0 2px 6px">${missing}</p>` : ""}<button class="primary inv" ${ok ? "" : "disabled"}>${f.edit ? "Guardar cambios" : "Crear inversión"}</button></div>${f.edit ? "" : pad()}<div class="gesture" style="background:var(--desk)"></div>${confirmSlip}`;
}
const phone = (o, inner, tall = false) => `<div class="phone ${tall ? "tall" : ""}"><div class="app" data-ink="${o.ink}" data-theme="${o.theme}">${inner}</div></div>`;

const card = (title, note, inner) => `<div class="frame-card"><h3>${title}</h3><p>${note}</p>${inner}</div>`;

// ── Section 6 · Dólares y la tasa (SELLO-106, locked 2026-10-25 by owner delegation) ──────────
// Rules: docs/testing/investment-examples.md. A USD investment keeps every figure in USD; only the
// overview converts, once per sum, at the current rate (IN28):
//   Acciones EE. UU.: aporte USD 1.000,00, value USD 1.050,50 → +USD 50,50 → 5,05 % → +5,1 %.
//   At 4.150,25 (20 oct): V 1.050,50 → 4.359.838; C 1.000,00 → 4.150.250.
//   Total V 38.038.000 + 4.359.838 = 42.397.838; C 41.150.250; W 500.000; D 528.000;
//   G +2.275.588 → 5,53 % → +5,5 %.
const usd = cents => `USD ${fmt(Math.trunc(cents / 100))},${String(Math.abs(cents) % 100).padStart(2, "0")}`;
const spokenUsd = cents => `${fmt(Math.trunc(cents / 100))} dólares con ${Math.abs(cents) % 100} centavos`;
const usdMoney = cents => `<span class="money" aria-label="${spokenUsd(cents)}">${usd(cents)}</span>`;
const RATE = { text: "4.150,25", date: "20 oct", spoken: "4.150 pesos con 25 centavos" };
const TOTAL_USD = { value: 42397838, contributed: 41150250, withdrawn: 500000, paid: 528000, gain: 2275588, pct: "5,5", up: true, since: "mar 2025", from: "12 jun", to: "14 oct" };
const USD_ROW = { name: "Acciones EE. UU.", kind: "ETF · USD", icon: "trending_up", cents: 105050, pct: "5,1", up: true, valued: "16 oct" };
// The rate line: a Footnote (E18) that opens Dólar. The valuation dates become a quiet line.
const rateLine = () => `<button class="footnote">${icon("autorenew")}<span>Dólar a <b>${RATE.text}</b> · tasa del ${RATE.date}</span>${icon("chevron_right", "chev")}</button>`;
const heroSlipUsd = t => `<section class="slip pinked"><div class="answer-head"><span class="label">Tus inversiones valen</span>${stamp(t)}</div><div class="hero long">${money(t.value)}</div>${perf(t)}<p class="caption inv-since">En total, desde ${t.since}</p><div class="tear" aria-hidden="true"></div>${breakdown(t)}${ages(t)}${rateLine()}</section>`;
const usdRow = (h, i) => `<button class="inv-row" aria-label="${h.name}, ${spokenUsd(h.cents)}, gana ${h.pct} por ciento en dólares, valor del ${h.valued}"><span class="inv-ic" style="--c:${shade(i)}">${icon(h.icon)}</span><span><b>${h.name}</b><small>${h.kind} · valor del ${h.valued}</small></span><span class="inv-right"><b>${usd(h.cents)}</b><small class="pos">${icon("trending_up")}+${h.pct} %</small></span></button>`;
// Shares by value in pesos: 19.000.000, 10.746.000, 5.242.000, 4.359.838, 3.050.000 of 42.397.838.
const listSlipUsd = () => `<section class="slip"><h2 class="q">Dónde está, de mayor a menor</h2><div class="split inv-split" aria-hidden="true">${[44.8, 25.3, 12.4, 10.3, 7.2].map((w, i) => `<i style="width:${w}%;background:${shade(i % 4)}"></i>`).join("")}</div>${[H[0], H[1], H[2]].map(row).join("")}${usdRow(USD_ROW, 3)}${row(H[3], 0)}</section>`;

// The pad: for USD the 000 key becomes the decimal comma (at most two decimals).
const padUsd = () => `<div class="pad" role="group" aria-label="Teclado de Sello">${["1", "2", "3", "4", "5", "6", "7", "8", "9", ",", "0"].map(k => `<button class="${k === "," ? "k000" : ""}" ${k === "," ? 'aria-label="Coma decimal"' : ""}>${k}</button>`).join("")}<button class="del" aria-label="Borrar">${icon("backspace")}</button></div>`;

// Nueva inversión with a currency: Pesos / Dólares chips under the description. Dólares with no
// rate yet asks for it in the same form, before Crear inversión (IN33).
function invFormUsd({ rate = false, amount = 100000 } = {}) {
  const ident = identInv({ name: "Acciones EE. UU.", kind: "ETF", icon: "trending_up" }, true);
  const first = figure("Primer aporte en dólares", usdMoney(amount), rate, valeLine(false));
  const rateSlip = rate
    ? `<p class="goal-fact">${icon("autorenew")} Dólar a <b>${RATE.text}</b>, tasa del ${RATE.date}: solo para el total en pesos.</p>`
    : figure("¿A cuánto está el dólar?", "", true, `<p class="goal-fact">Pesos por 1 dólar. Sello no la busca: la anotas tú, y sirve para sumar tus dólares al total.</p>`);
  return `<div class="entry-top">${statusbar()}<div class="entry-bar"><button class="icon-btn" aria-label="Cerrar">${icon("close")}</button><b style="flex:1;font-size:1.2em;margin-left:4px">Nueva inversión</b></div></div><div class="entry-mid" style="padding-top:2px">${ident}${first}${rateSlip}</div><div class="entry-action">${rate ? "" : `<p class="caption" style="margin:0 2px 6px">Anota a cuánto está el dólar.</p>`}<button class="primary inv" ${rate ? "" : "disabled"}>Crear inversión</button></div>${padUsd()}<div class="gesture" style="background:var(--desk)"></div>`;
}

// Anotar movimiento on a USD investment: label "en dólares", the amount with cents, the comma key.
function moveFormUsd() {
  const chips = [["Acciones EE. UU.", "trending_up"], ...NAMES.slice(0, 3)].map(([n, i]) => `<button class="chip" aria-pressed="${n === "Acciones EE. UU."}">${icon(i)}${n}</button>`).join("");
  const typeChips = `<h3>Tipo</h3><div class="chips one-row">${TYPES.map(([k, t]) => `<button class="chip" aria-pressed="${k === "in"}">${t}</button>`).join("")}</div>`;
  return `<div class="entry-top">${statusbar()}<div class="entry-bar"><button class="icon-btn" aria-label="Cerrar">${icon("close")}</button><b style="flex:1;font-size:1.2em;margin-left:4px">Movimiento</b></div></div><section class="slip pinked amount-slip"><div class="amount-meta"><span>Aporte en dólares</span><button class="date-pill">${icon("event")}Hoy, vie 16</button></div><div class="amount-row"><span class="circle" style="--fill:100%;--c:var(--ink)" aria-hidden="true">${icon("trending_up")}<span class="on">${icon("trending_up")}</span></span><div class="amount-value">+USD 250,5<span class="caret"></span></div></div></section><div class="entry-mid">${typeChips}<h3>Inversión</h3><div class="chips one-row">${chips}</div><p class="goal-fact">Llevas <b>USD 1.000,00</b> aportados en Acciones EE. UU.</p><button class="note-pill">${icon("edit_note")}<span>Agregar nota</span></button></div><div class="entry-action"><button class="primary inv">Anotar aporte</button></div>${padUsd()}<div class="gesture" style="background:var(--desk)"></div>`;
}

// Dólar: reached from Configuración (Tus datos) and from the overview's rate line. The current rate
// on a pinked slip, the history under it, newest date first; a rate is never deleted (rule 11).
const HISTORY = [["20 oct 2026", "4.150,25"], ["2 oct 2026", "4.010,00"], ["15 sep 2026", "3.960,50"]];
const rateBody = ({ history = HISTORY } = {}) => `<section class="slip pinked"><div class="answer-head"><span class="label">1 dólar vale</span></div><div class="hero" aria-label="${RATE.spoken}">${RATE.text}</div><p class="caption inv-since">pesos · tasa del ${RATE.date}</p><div class="tear" aria-hidden="true"></div><p class="goal-fact">Sello no busca la tasa: la anotas tú. Solo convierte el total de Inversiones; cada inversión en dólares sigue en dólares.</p><button class="outline-btn wide inv-update">${icon("autorenew")}Cambiar la tasa</button></section><section class="slip"><h2 class="q">Tasas anteriores</h2>${history.slice(1).map(([d, r]) => leader(d, r)).join("")}</section>`;
const rateScreen = (o, body, opts = {}) => phoneX(o, subShell({ title: "Dólar", body, tabs: 4, active: 3, dock: "", dockClass: "inv" }), opts);
function rateEntry() {
  return `<div class="entry-top">${statusbar()}<div class="entry-bar"><button class="icon-btn" aria-label="Cerrar">${icon("close")}</button><b style="flex:1;font-size:1.2em;margin-left:4px">Tasa del dólar</b></div></div><section class="slip pinked amount-slip"><div class="amount-meta"><span>Pesos por 1 dólar</span><button class="date-pill">${icon("event")}Hoy, dom 25</button></div><div class="amount-row"><span class="circle" style="--fill:100%;--c:var(--ink)" aria-hidden="true">${icon("autorenew")}<span class="on">${icon("autorenew")}</span></span><div class="amount-value">4.180,7<span class="caret"></span></div></div></section><div class="entry-mid"><p class="goal-fact">La de hoy es <b>4.150,25</b>, del 20 oct. La nueva la reemplaza desde su fecha; la anterior queda en la lista.</p></div><div class="entry-action"><button class="primary inv">Guardar tasa</button></div>${padUsd()}<div class="gesture" style="background:var(--desk)"></div>`;
}
const settingsRows = `<section class="slip"><h2 class="q">Tus datos</h2><button class="setting-row">${icon("autorenew")}<span><b>Pagos e ingresos fijos</b><small>4 pagos, 2 ingresos</small></span>${icon("chevron_right", "chev")}</button><button class="setting-row">${icon("account_balance")}<span><b>Dólar</b><small>4.150,25 · tasa del 20 oct</small></span>${icon("chevron_right", "chev")}</button><button class="setting-row">${icon("content_paste")}<span><b>Respaldo</b><small>hace 6 días</small></span>${icon("chevron_right", "chev")}</button></section>`;

// Frames with large text (200 %) or a fixed theme, as the E18 page does.
const phoneX = (o, inner, { large = false, theme, tall = false } = {}) => `<div class="phone ${tall ? "tall" : ""}"><div class="app ${large ? "large" : ""}" data-ink="${o.ink}" data-theme="${theme ?? o.theme}" style="--scale:${large ? 2 : 1}">${inner}</div></div>`;
const tabX = (o, body, opts = {}) => phoneX(o, appShell({ title: "Inversiones", chip: "Nueva inversión", settings: false, body, tabs: 4, active: 3, dock: opts.dock ?? "Anotar movimiento", dockClass: "inv" }), opts);

// IN35: investments exist, none has a value: no number, stamp or bar; the rows say so.
const noValues = () => `<section class="slip pinked"><div class="answer-head"><span class="label">Tus inversiones valen</span></div><p class="goal-fact" style="margin-top:8px">Todavía no hay valores anotados. Anota cuánto vale cada una para ver el total.</p></section><section class="slip"><h2 class="q">Tus inversiones</h2>${rowNoValue(CRIPTO)}${rowNoValue({ name: "Acciones EE. UU.", kind: "ETF · USD", icon: "trending_up", contributed: 0 }).replaceAll("aportaste 0 pesos", "aportaste 1.000 dólares").replaceAll("aportaste 0", "aportaste USD 1.000,00")}</section>`;
// Six investments: the four shades repeat in order, largest first; rows carry the exact values.
const SIX = [...H, { name: "Bonos", kind: "TES", icon: "account_balance", value: 2400000, gain: 120000, pct: "5,3", up: true, valued: "1 oct" }, { name: "Local", kind: "Arriendo", icon: "shopping_cart", value: 1900000, gain: 40000, pct: "2,2", up: true, valued: "30 sep" }];
const sixSlip = () => `<section class="slip"><h2 class="q">Dónde está, de mayor a menor</h2><div class="split inv-split" aria-hidden="true">${[45.0, 25.4, 12.4, 7.2, 5.7, 4.5].map((w, i) => `<i style="width:${w}%;background:${shade(i % 4)}"></i>`).join("")}</div>${SIX.map((h, i) => row(h, i % 4)).join("")}</section>`;
// IN15: gain exactly 0: no stamp, neutral 0,0 %.
const evenSlip = `<section class="slip pinked"><div class="answer-head"><span class="label">Vale</span></div><div class="gd-saved">${money(1000000)}</div><p class="perf"><span>0,0 %</span><span class="perf-gain">sin ganancia ni pérdida</span></p><p class="caption inv-since">Valor anotado el 16 oct · desde oct 2026</p><div class="tear" aria-hidden="true"></div>${leader("Aportaste", money(1000000))}${leader("Ganancia", "0")}</section>`;
const rateEmpty = `<section class="slip empty">${icon("account_balance")}<h2>Todavía no hay tasa</h2><p>La anotas cuando tengas una inversión en dólares. Sello no la busca en internet.</p><button class="outline-btn">Anotar la tasa</button></section>`;
const rateLoading = `<div role="status" aria-label="Cargando"><section class="slip"><div class="skel" style="width:40%"></div><div class="skel big"></div><div class="skel" style="width:60%"></div></section></div>`;
const rateFailed = `<section class="slip empty">${icon("error")}<h2>No pudimos leer la tasa</h2><p>Tus tasas siguen guardadas. Vuelve a intentarlo.</p><button class="outline-btn">Reintentar</button></section>`;

function render() {
  const o = reviewOptions(["ink", "theme"]);
  const set = (id, h) => document.getElementById(id).innerHTML = h;
  set("overview",
    card("Overview", "Value as the big number with a Gana / Pierde stamp, the signed return with arrow and pesos, then what produced it under the tear. A quiet line says when the values were recorded (a link until SELLO-106). Then one bar for where the money is and a row per investment.", tab(o, heroSlip(TOTAL) + listSlip(H))) +
    card("Whole screen", "Rows show each value’s date, so an old valuation (Parqueadero, 12 jun) is visible without opening it.", tab(o, heroSlip(TOTAL) + listSlip(H), { tall: true })));
  set("detail",
    card("Gaining, with dividends", "Same slip language: value, stamp, signed return, the breakdown and “Anotar el valor de hoy”. The chart shows only recorded values as dots; the dashed line is what was put in.", detail(o, DETAIL.eco)) +
    card("Losing, after a withdrawal", "−4,3 % in red with a down arrow and “de pérdida”, stated plainly. The withdrawal lowers what was put in, so it is not shown as a loss.", detail(o, DETAIL.fondo)) +
    card("Detail, whole screen", "Movements: Valor de hoy, Dividendo, Aporte, Retiro. Anotar movimiento opens the entry screen below.", detail(o, DETAIL.eco, true)));
  set("states",
    card("Total in loss", "Pierde, −0,3 %, −30.000 de pérdida. Factual, no advice.", tab(o, heroSlip(LOSS) + listSlip([{ ...H[2], share: 63.2 }, { ...H[3], share: 36.8 }]))) +
    card("An investment without a value", "No message on the overview. Cripto’s row says Sin valor and what was put in; it stays out of the bar and the total until a value is recorded, never counted as 0.", tab(o, heroSlip(TOTAL) + listSlip(H, [CRIPTO]), { tall: true })) +
    card("No investments", "Welcoming, no invented chart. The dock becomes Agregar inversión, as Metas does with Crear una meta.", tab(o, `<section class="slip pinked empty">${icon("trending_up")}<h2>Anota lo que tienes invertido</h2><p>Un CDT, un fondo, acciones o un local. Anotas cuánto pusiste y, de vez en cuando, cuánto vale. Sello no se conecta a ningún banco.</p></section>`, { dock: "Agregar inversión" })) +
    card("Loading", "Skeleton slips.", tab(o, `<div role="status" aria-label="Cargando"><section class="slip"><div class="skel" style="width:50%"></div><div class="skel big"></div><div class="skel" style="width:70%"></div></section><section class="slip"><div class="skel" style="width:60%"></div><div class="skel"></div><div class="skel"></div></section></div>`)) +
    card("Failed read", "Never shown as zero or as no investments.", tab(o, `<section class="slip empty">${icon("error")}<h2>No pudimos leer tus inversiones</h2><p>Tus registros siguen guardados. Vuelve a intentarlo.</p><button class="outline-btn">Reintentar</button></section>`)));
  set("move",
    card("Aporte, from the investment", "Opened from Ecopetrol: it is already chosen. One row of type chips, then the investments in one row. The fact says where it stands.", phone(o, moveForm(MOVE.aporte))) +
    card("Valor de hoy", "No sign: a value replaces the last one, it is not added. “Anotar el valor de hoy” in the detail opens it here.", phone(o, moveForm(MOVE.valor))) +
    card("Retiro", "Minus sign, neutral button.", phone(o, moveForm(MOVE.retiro))) +
    card("More than it is worth", "A withdrawal is limited to the last recorded value; the label becomes the message and the button waits. If it is worth more today, Anotar el valor de hoy first.", phone(o, moveForm(MOVE.over))) +
    card("Dividendo o renta", "Money the investment paid out. It counts in the gain, not in the value.", phone(o, moveForm(MOVE.divid))) +
    card("From the tab’s dock", "No investment chosen yet, so the button waits. Same order as the list.", phone(o, moveForm(MOVE.fromTab))));
  set("create",
    card("Nueva inversión", "The icon circle beside the name, the optional description and Pesos | Dólares; the first contribution as the big number on the pad, with its date in the corner and Vale hoy at its foot.", phone(o, invForm({ name: "CDT Banco", kind: "CDT a 360 días", icon: "account_balance", amount: 10000000 }), true)) +
    card("Otro valor", "If it is already worth something else (an investment from before Sello), Otro valor adds a Vale hoy slip; the pad types into the one edged in ink.", phone(o, invForm({ name: "Parqueadero", kind: "Finca raíz", icon: "home", amount: 18000000, other: 19000000 }), true)) +
    card("Editar inversión", "Name, description and icon only; movements are edited from the list. Deleting is its own card with the confirm slip.", phone(o, invForm({ name: "Ecopetrol", kind: "Acciones", icon: "trending_up", edit: true }), true)) +
    card("Deleting asks once more", "Same confirm slip as goals.", phone(o, invForm({ name: "Ecopetrol", kind: "Acciones", icon: "trending_up", edit: true }, { confirm: true }))));
  set("usd",
    card("Nueva inversión en dólares", "Pesos | Dólares under the description, fixed once created. With no rate yet the form asks for it below the first contribution, a figure with its own date, and Crear inversión waits (IN33). The pad's 000 key becomes the decimal comma for dollars.", phoneX(o, invFormUsd(), { tall: true })) +
    card("With a rate", "Once a rate exists, one quiet line says which and that it only serves the total.", phoneX(o, invFormUsd({ rate: true }), { tall: true })) +
    card("Aporte en dólares", "Everything about a USD investment stays in dollars, with cents. The comma key replaces 000.", phoneX(o, moveFormUsd())) +
    card("Overview with dollars", "The total converts at the current rate (IN28) and the slip says which, as a footnote that opens Dólar. The valuation dates become a quiet line, not a link: each row carries its own date. The USD row stays in dollars; the bar uses its value in pesos.", tabX(o, heroSlipUsd(TOTAL_USD) + listSlipUsd(), { tall: true })) +
    card("Dólar", "From Configuración or the overview's rate line. The current rate on a pinked slip, the earlier ones below, newest date first. A rate is never deleted; a new one replaces it from its date.", rateScreen(o, rateBody())) +
    card("Cambiar la tasa", "The entry layout: pesos per dollar with the comma key, a date today or earlier. 1,00 to 100.000,00, two decimals at most.", phoneX(o, rateEntry())) +
    card("Configuración · Tus datos", "A Dólar row with the current rate, between Pagos e ingresos fijos and Respaldo.", `<div class="app cmp-stage" data-ink="${o.ink}" data-theme="${o.theme}">${settingsRows}</div>`));
  set("more",
    card("No values yet", "IN35: investments exist but none has a value. No number, stamp, return or bar; the rows say Sin valor.", tabX(o, noValues())) +
    card("Six investments", "The four shades repeat in order, largest first, with a 2dp gap between segments; the rows carry the exact values.", tabX(o, heroSlip(TOTAL) + sixSlip(), { tall: true })) +
    card("Neither gain nor loss", "IN15: no stamp; 0,0 % in neutral ink.", phoneX(o, subShell({ title: "Fondo nuevo", body: evenSlip, tabs: 4, active: 3, dock: "Anotar movimiento", dockClass: "inv" }))) +
    card("Dólar · no rate", "Reached from Configuración before any USD investment.", rateScreen(o, rateEmpty)) +
    card("Dólar · loading", "", rateScreen(o, rateLoading)) +
    card("Dólar · failed read", "Never shown as no rate.", rateScreen(o, rateFailed)) +
    card("Large text · 200%", "The overview with dollars at 200 %. The mock only scales; in the app a receipt line's value and a row's amount move under their label when they do not fit, never clipped.", tabX(o, heroSlipUsd(TOTAL_USD) + listSlipUsd(), { large: true, tall: true })) +
    card("Large text · 200% · rate", "The middle scrolls; the amount, the button and the pad stay put, as in the locked entry.", phoneX(o, rateEntry(), { large: true })) +
    card("Dark", "", tabX(o, heroSlipUsd(TOTAL_USD) + listSlipUsd(), { theme: "dark", tall: true })) +
    card("Dark · Dólar", "", rateScreen(o, rateBody(), { theme: "dark" })));
}
document.querySelectorAll(".controls select").forEach(s => s.addEventListener("change", render));
render();
