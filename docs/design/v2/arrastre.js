// Sello v2 · Arrastre (proposed 2026-10-28 for SELLO-041; not locked). Hand-authored fixtures,
// every figure copied from docs/testing/signed-carryover-examples.md (SC01–SC47), never computed
// here. Comida has base 100.000 and carry on from ene 2027 unless a frame says otherwise.
// Wording is ADR 0020's presentation table; "Disponible" alone and "saldo" are not used.

// ——— Shared parts ———

const yr = h => h.replace(/<small>2026<\/small>/g, "<small>2027</small>");
const spokenSigned = n => (n > 0 ? "más " : "") + spoken(n);
const signed = n => `<span class="money ${n < 0 ? "neg" : ""}" aria-label="${spokenSigned(n)}">${n > 0 ? "+" : ""}${fmt(n)}</span>`;
const delta = n => `${n > 0 ? "+" : ""}${fmt(n)}`;
const L = (label, value, { small, cls = "" } = {}) => `<div class="leader ${cls}"><span>${label}${small ? `<small>${small}</small>` : ""}</span><i class="dots" aria-hidden="true"></i><b>${value}</b></div>`;
const STAMPS = { ok: ["Al día", ""], over: ["Excedido", "loss"], done: ["Cumplido", ""] };
const stamp = k => `<span class="stamp ${STAMPS[k][1]}">${STAMPS[k][0]}</span>`;
const carryWords = k => k > 0 ? `Te sobró ${fmt(k)} de meses anteriores` : k < 0 ? `Te pasaste ${fmt(-k)} en meses anteriores` : "Empieza en 0 este mes";

// Base, carry-in, capacity and spent as receipt lines; read as one sentence.
function breakdown({ base, carry, cap, spent, words = carryWords(carry), src }) {
  const said = `Límite base ${spoken(base)}. Arrastre ${spokenSigned(carry)}, ${words}. Límite con arrastre ${spoken(cap)}.${spent !== undefined ? ` Gastado ${spoken(spent)}.` : ""}${src ? " " + src : ""}`;
  return `<div class="carry" role="group" aria-label="${said}">${L("Límite base", money(base))}${L("Arrastre", signed(carry), { small: words })}${L("Límite con arrastre", money(cap), { cls: "strong" })}${spent !== undefined ? L("Gastado", money(spent)) : ""}${src ? `<span class="src" aria-hidden="true">${src}</span>` : ""}</div>`;
}

const phone = (o, inner, { large = false, theme } = {}) => `<div class="phone"><div class="app ${large || o.scale >= 1.6 ? "large" : ""}" data-ink="${o.ink}" data-theme="${theme ?? o.theme}" style="--scale:${large ? 2 : o.scale}">${inner}</div></div>`;
const card = (title, note, inner) => `<div class="frame-card"><h3>${title}</h3><p>${note}</p>${inner}</div>`;
const crop = (o, inner) => `<div class="app" data-ink="${o.ink}" data-theme="${o.theme}" style="height:auto;display:block;padding:12px;border-radius:12px;--scale:${o.scale}">${inner}</div>`;
const toastUndo = (text, action = "Deshacer") => `<div class="toast" role="status">${icon("check")}<span>${text}</span>${action ? `<button class="undo">${action}</button>` : ""}</div>`;

// ——— Recibo ———

const CAT_ICON = { Comida: "restaurant", Transporte: "directions_bus", Ocio: "theaters", Hogar: "home" };

function cell([name, left, { cap, spent, carry, unlimited } = {}]) {
  if (unlimited) return `<button class="cell">${unlimitedCircle(CAT_ICON[name])}<span class="cell-text"><span>${name}</span><b>${money(left)}</b><small>sin límite</small></span></button>`;
  const over = left < 0, fill = over || cap <= 0 ? 100 : Math.round(100 * spent / cap);
  const said = `${name}. ${over ? "Te pasaste por " + spoken(-left) : "Quedan " + spoken(left)}${carry ? ", con arrastre" : ""}`;
  return `<button class="cell" aria-label="${said}"><span class="circle ${over ? "over" : ""}" style="--fill:${fill}%" aria-hidden="true">${icon(CAT_ICON[name])}<span class="on">${icon(CAT_ICON[name])}</span></span><span class="cell-text" aria-hidden="true"><span>${name}</span><b>${money(left)}</b>${carry ? "<small>con arrastre</small>" : ""}</span></button>`;
}
const grid = cells => `<section class="slip"><div class="section-head"><h2>Tus categorías</h2><button class="band-chip paper">${icon("add")}Nueva categoría</button></div><div class="grid">${cells.map(cell).join("")}</div></section>`;

function reciboSlip(s) {
  let h = `<section class="slip pinked" aria-label="Tu mes"><div class="answer-head"><span class="label">Restante del presupuesto</span>${stamp(s.status)}</div><div class="hero">${money(s.hero)}</div>`;
  if (s.closed) h += `<p class="closed-note">${s.closed}</p>`;
  if (s.why) h += `<p class="why">${s.why}</p>`;
  h += `<div class="tear" aria-hidden="true"></div>`;
  if (s.daily !== undefined) h += `<p class="daily-big"><b>${money(s.daily)}</b><span>por día</span></p><span class="caption">${s.days} días, incluido hoy</span>`;
  h += breakdown(s.carry);
  if (s.action) h += `<button class="text-link">${s.action}${icon("chevron_right")}</button>`;
  return h + `</section>`;
}

const RECIBO = {
  // SC13: feb 2027, lun 22 feb, D = 7.
  sc13: { status: "ok", hero: 130000, daily: 18550, days: 7,
    carry: { base: 150000, carry: 80000, cap: 230000, spent: 100000, words: "Comida · te sobró 80.000 de meses anteriores", src: "Comida arrastra desde ene 2027 · cifras al 22 feb" },
    cells: [["Comida", 150000, { cap: 180000, spent: 30000, carry: true }], ["Transporte", -10000, { cap: 50000, spent: 60000 }], ["Ocio", 10000, { unlimited: true }]] },
  // SC11 for Comida, with Hogar 400.000 (spent 380.000) and Transporte 50.000 (spent 40.000), both off.
  over: { status: "over", hero: -20000, daily: 0, days: 7, action: "Ajustar presupuesto",
    why: "Tu límite con arrastre es <b>400.000</b>: Comida trae <b>−150.000</b> de meses anteriores.",
    carry: { base: 550000, carry: -150000, cap: 400000, spent: 420000, words: "Comida · te pasaste 150.000 en meses anteriores", src: "Comida arrastra desde ene 2027 · cifras al 22 feb" },
    cells: [["Hogar", 20000, { cap: 400000, spent: 380000 }], ["Comida", -50000, { cap: -50000, spent: 0, carry: true }], ["Transporte", 10000, { cap: 50000, spent: 40000 }]] },
  // SC01, marzo cerrado, viewed from abril.
  closed: { status: "over", hero: -40000, closed: "Mes cerrado · gastaste 250.000 de 210.000 con arrastre.",
    carry: { base: 100000, carry: 110000, cap: 210000, spent: 250000, src: "Comida arrastra desde ene 2027 · cifras al 5 abr" },
    cells: [["Comida", -40000, { cap: 210000, spent: 250000, carry: true }]] },
  // SC25 committed: abr 2027, mar 20 abr, D = 11; 50 × floor(80.000 / 550) = 7.250.
  corrected: { status: "ok", hero: 80000, daily: 7250, days: 11,
    carry: { base: 100000, carry: -20000, cap: 80000, spent: 0, src: "Comida arrastra desde ene 2027 · cifras al 20 abr" },
    cells: [["Comida", 80000, { cap: 80000, spent: 0, carry: true }]] },
  // SC47 after catch-up: Hogar base 1.000.000, mié 10 mar, D = 22; 50 × floor(400.000 / 1.100) = 18.150.
  catchup: { status: "ok", hero: 400000, daily: 18150, days: 22,
    carry: { base: 1000000, carry: -100000, cap: 900000, spent: 500000, words: "Hogar · te pasaste 100.000 en meses anteriores", src: "Hogar arrastra desde ene 2027 · cifras al 10 mar" },
    cells: [["Hogar", 400000, { cap: 900000, spent: 500000, carry: true }]] },
};
const recibo = (o, s, { month = "Febrero", current, toast, notice, large } = {}) =>
  phone(o, yr(appShell({ month, current, body: reciboSlip(s) + grid(s.cells), tabs: 3, active: 0, notice })) + (toast ?? ""), { large });

// ——— Category detail ———

function detailBody(s) {
  const head = s.over ? "Te pasaste aquí por" : "Te quedan aquí";
  const fill = s.over ? 100 : Math.round(100 * s.carry.spent / s.carry.cap);
  let h = `<section class="slip pinked" aria-label="Comida en febrero"><div class="answer-head"><span class="label">${head}</span>${stamp(s.over ? "over" : "ok")}</div>`;
  h += `<div class="dt-hero"><span class="circle ${s.over ? "over" : ""}" style="--fill:${fill}%" aria-hidden="true">${icon("restaurant")}<span class="on">${icon("restaurant")}</span></span><div class="hero ${s.over ? "neg" : ""}">${money(Math.abs(s.hero))}</div></div>`;
  if (s.why) h += `<p class="why">${s.why}</p>`;
  h += `<div class="tear" aria-hidden="true"></div>${breakdown(s.carry)}`;
  h += `<div class="per-row"><span>Para<b>${s.days} días</b></span><span>Al día<b>${money(s.daily)}</b></span>${s.dailyNote ? `<small>${s.dailyNote}</small>` : ""}</div></section>`;
  h += `<section class="slip"><div class="section-head"><h2>Últimos gastos</h2></div>${s.expenses.map(([n, d, a]) => `<div class="leader"><span>${n}<small>${d}</small></span><i class="dots" aria-hidden="true"></i><b>${money(a)}</b></div>`).join("")}</section>`;
  return h;
}
const DETAIL = {
  // SC09: 22 feb, D = 7.
  sc09: { hero: 150000, days: 7, daily: 21400, expenses: [["Mercado", "lun 15 feb", 30000]],
    carry: { base: 100000, carry: 80000, cap: 180000, spent: 30000, src: "Viene del restante de ene 2027 · arrastras desde ene 2027" } },
  // SC17 before turning off: 10 feb, D = 19; 50 × floor(50.000 / 950) = 2.600.
  sc17: { hero: 50000, days: 19, daily: 2600, expenses: [["Mercado", "lun 8 feb", 30000]],
    carry: { base: 100000, carry: -20000, cap: 80000, spent: 30000, src: "Viene del restante de ene 2027 · arrastras desde ene 2027" } },
  // SC11: negative capacity, nothing spent, 22 feb, D = 7.
  sc11: { hero: -50000, over: true, days: 7, daily: 0, expenses: [], dailyNote: "0 por día: aún te pasas por 50.000.",
    why: "Te pasaste <b>150.000</b> en meses anteriores y tu límite base es <b>100.000</b>: febrero empezó en <b>−50.000</b>.",
    carry: { base: 100000, carry: -150000, cap: -50000, spent: 0, src: "Viene del restante de ene 2027 · arrastras desde ene 2027" } },
};
const detail = (o, s, opts = {}) => phone(o, subShell({ title: "Comida", body: detailBody(s).replace('<section class="slip"><div class="section-head"><h2>Últimos gastos</h2></div></section>', `<section class="slip"><div class="section-head"><h2>Últimos gastos</h2></div><p class="caption">Aún no hay gastos en febrero.</p></section>`), active: 0, action: ["edit_note", "Editar categoría"] }), opts);

// ——— Editar categoría and the carry card ———

const padKeys = () => `<div class="pad" role="group" aria-label="Teclado de Sello">${["1", "2", "3", "4", "5", "6", "7", "8", "9", "000", "0"].map(k => `<button class="${k === "000" ? "k000" : ""}" aria-label="${k === "000" ? "Tres ceros" : k}">${k}</button>`).join("")}<button class="del" aria-label="Borrar">${icon("backspace")}</button></div>`;

function carryCard(c) {
  let h = `<section class="slip carry-card" aria-label="Arrastrar al mes siguiente"><h2>Arrastrar al mes siguiente</h2>`;
  if (c.on) {
    h += `<p class="state"><span>Activo desde ${c.since}</span></p>`;
    if (c.done) h += `<p class="done" role="status">${c.done}</p>`;
    h += `<div style="margin:0 0 12px">${L("Arrastre", signed(c.carry), { small: carryWords(c.carry) })}${L("Límite con arrastre", money(c.cap), { cls: "strong" })}</div>`;
    h += `<button class="outline-btn wide">Dejar de arrastrar</button>`;
  } else {
    h += `<p>Lo que te sobra o te pasas en el mes pasa al siguiente. Empieza en 0 este mes.</p>`;
    if (c.refusal) h += `<div class="refusal" role="status">${c.refusal}</div>`;
    if (c.done) h += `<p class="done" role="status">${c.done}</p>`;
    h += `<button class="outline-btn wide">Activar desde ${c.month}</button>`;
  }
  return h + `</section>`;
}

function editor({ amount = 100000, unlimited = false, scope = "Vale desde febrero en adelante. Los meses anteriores no cambian.", carry, changed = false, save = "Guardar cambios" }, { slip, toast } = {}) {
  const id = `<section class="slip form-slip"><div class="ident" style="align-items:center"><button class="pick" style="color:var(--brand)" aria-label="Ícono: Comida. Cambiar"><span class="ident-icon" style="background:var(--brand)">${icon("restaurant")}</span>Cambiar</button><div><label class="form-label">Nombre</label><div class="form-field">Comida</div></div></div><div class="form-msg"><span></span><span>6/24</span></div></section>`;
  const lim = `<section class="slip pinked"><div class="plan-label"><span class="label">Límite mensual</span><button class="chip" aria-pressed="${unlimited}">Sin límite</button></div><p class="plan-amount" style="${unlimited ? "color:var(--rule)" : ""}">${money(amount)}</p><p class="caption" style="margin:4px 0 0">${scope}</p></section>`;
  let h = `<div class="entry-top">${statusbar()}<div class="entry-bar"><button class="icon-btn" aria-label="Volver">${icon("arrow_back")}</button><b style="flex:1;font-size:1.2em;margin-left:4px">Editar categoría</b><button class="text-btn" style="border:0;background:none;color:var(--brand);font-weight:800;min-height:48px;padding:0 8px">Archivar</button></div></div>`;
  h += `<div class="entry-mid scrolled" style="padding-top:2px">${id}${lim}${carryCard(carry)}</div>`;
  h += `<div class="entry-action"><button class="primary" ${changed ? "" : "disabled"}>${save}</button></div>${padKeys()}<div class="gesture" style="background:var(--desk)"></div>`;
  if (slip) h += `<div class="scrim center">${slip}</div>`;
  return h + (toast ?? "");
}

const confirmSlip = ({ title, body = "", rows = [], go = "Confirmar", goClass = "go", changed }) => `<div class="confirm list" role="alertdialog" aria-label="${title.replace(/<[^>]+>/g, "")}"><div class="scroll">${changed ? `<div class="changed" role="status">${changed}</div>` : ""}<h2>${title}</h2>${body}${rows.length ? `<div class="ba">${rows.map(([l, v, same]) => `<div class="${same ? "same" : ""}"><span>${l}</span><b>${v}</b></div>`).join("")}</div>` : ""}</div><div class="tear" aria-hidden="true"></div><div class="confirm-actions"><button class="ghost">Cancelar</button><button class="${goClass}">${go}</button></div></div>`;

const ENABLE_SLIP = confirmSlip({ title: "¿Arrastrar Comida desde febrero?",
  body: `<p>Empieza en <b>0</b> en febrero: lo de meses anteriores no cuenta. Todo febrero cuenta desde el día 1, también los <b>30.000</b> que ya gastaste.</p><p style="margin-top:8px">Desde marzo, lo que te sobre o te pases pasa al mes siguiente. Puedes dejar de arrastrar cuando quieras.</p>`, go: "Activar" });

const REFUSALS = [
  ["Default after this month unlimited (SC46)", "<b>Para arrastrar, Comida necesita un límite mensual.</b>Febrero tiene su propio límite, pero desde marzo quedaría sin límite y el arrastre no tendría base. Quita Sin límite y guarda; luego podrás activarlo."],
  ["Unlimited this month", "<b>Comida no tiene límite este mes.</b>Ponle un límite para poder arrastrar."],
  ["Paused or never configured", "<b>Comida no tiene límite en febrero.</b>Ponle un límite para poder arrastrar."],
  ["Archived", "<b>Comida está archivada.</b>Desarchívala para poder arrastrar."],
  ["Base above the cap (CP25)", "<b>Para arrastrar, el límite puede ser hasta 999.999.999.999.</b>Baja el límite y guarda; luego podrás activarlo."],
  ["Limit edit while carrying (CP25)", "<b>No se guardó: mientras Comida arrastra, su límite puede ser hasta 999.999.999.999.</b>Tu cambio sigue aquí."],
  ["A past month made unlimited (CP13, SC22)", "<b>No se guardó: marzo no puede quedar sin límite.</b>Comida arrastraba ese mes y el arrastre quedaría con un hueco. Ponle un límite."],
];

// ——— Corrections: the list of later months ———

const CI = { Comida: "restaurant", Transporte: "directions_bus", Hogar: "home" };
// rows: [month, value, sub, flip] — value and sub are pre-written text, never computed.
const clGroup = (cat, rows) => `<div class="cl-cat"><span class="circle" aria-hidden="true">${icon(CI[cat])}</span>${cat}</div>${rows.map(([m, v, sub, flip]) => `<div class="cl-row" aria-label="${m}: ${v.replace("→", "a")}${sub ? ". " + sub.replace(/→/g, "a") : ""}"><span class="m">${m}</span><span class="v">${v}</span>${sub ? `<small class="${flip ? "flip" : ""}">${sub}</small>` : ""}</div>`).join("")}`;
const listSlip = ({ title, what, lead, groups, note, go, goClass = "go", changed }) => `<div class="confirm list" role="alertdialog" aria-label="${title}"><div class="scroll">${changed ? `<div class="changed" role="status">${changed}</div>` : ""}<h2>${title}</h2><p class="what">${what}</p>${lead ? `<p class="lead">${lead}</p>` : ""}${groups.map(([c, r]) => clGroup(c, r)).join("")}${note ? `<p class="caption" style="margin:10px 0 0">${note}</p>` : ""}</div><div class="tear" aria-hidden="true"></div><div class="confirm-actions"><button class="ghost">Cancelar</button><button class="${goClass}">${go}</button></div></div>`;

const ENERO = `<section class="slip"><h2 class="q">Gastos de enero 2027</h2>${L("Mercado", money(20000), { small: "mié 13 ene" })}<p class="caption" style="margin:8px 0 0">Toca un gasto para corregirlo. Para borrarlo, deslízalo o mantenlo presionado.</p></section>`;
const expensesScreen = (o, slip, opts = {}) => phone(o, yr(subShell({ title: "Gastos de Comida", body: ENERO, active: 0, month: "Enero", current: "abril", dock: null })) + `<div class="scrim center">${slip}</div>`, opts);

const SC25 = { title: "¿Borrar este gasto?", what: "Mercado · <b>20.000</b> · mié 13 ene 2027", lead: "Esto cambia el límite con arrastre de 3 meses", go: "Borrar", goClass: "del",
  groups: [["Comida", [["feb 2027", "180.000 → 200.000", "Restante 110.000 → 130.000"], ["mar 2027", "210.000 → 230.000", "Restante −40.000 → −20.000 · sigue Excedido"], ["abr 2027", "60.000 → 80.000", "Restante 60.000 → 80.000"]]]],
  note: "Los límites base no cambian. Se guarda todo junto o nada." };

// ——— Catch-up ———

const recordedList = (lines, title) => `<section class="slip"><h2 class="q">Sello anotó esto</h2><p class="answer-note" style="margin:0 0 6px">Cada uno en su fecha, desde la última vez que abriste Sello. Revisa que estén bien; toca uno para corregirlo o borrarlo.</p><button class="fx-row last" aria-label="Arriendo, ${spoken(lines.amount)}, vie 5 feb. Editar"><span class="fx-ic">${icon("home")}</span><span class="fx-text"><b>Arriendo</b><small>Hogar · vie 5 feb</small></span>${icon("chevron_right", "chev")}<b>${money(lines.amount)}</b></button></section><section class="slip"><h2 class="q">${title}</h2>${clGroup("Hogar", lines.rows)}</section>`;

// ——— Resumen ———

const COLS = [["Sep", 140000], ["Oct", 160000], ["Nov", 120000], ["Dic", 150000], ["Ene", 110000], ["Feb", 100000, true]];
function monthChart() {
  const max = 250000, cap = 230000;
  const cols = COLS.map(([m, n, now]) => `<button class="col ${now ? "now partial sel" : ""}" aria-label="${m}${now ? ", en curso hasta el 22" : ""}: gastado ${spoken(n)}${now ? ". Límite base 150.000 pesos, arrastre más 80.000 pesos, límite con arrastre 230.000 pesos" : ""}">${now || n === 160000 ? `<span class="v">${Math.round(n / 1000)} mil</span>` : ""}<span class="b" style="height:${100 * n / max}%"></span></button>`).join("");
  const axis = COLS.map(([m, , now]) => `<span class="${now ? "now" : ""}">${now ? m + "*" : m}</span>`).join("");
  return `<section class="slip"><h2 class="q">Mes a mes</h2><p class="chart-pick"><span>Un mes normal</span><b>${money(136000)}</b></p><p class="basis">Promedio de 5 meses completos</p>
    <div style="margin-top:12px"><span class="caption">Febrero, hasta hoy</span><p class="chart-pick"><span>Gastado</span><b>${money(100000)}</b></p>${breakdown({ base: 150000, carry: 80000, cap: 230000, words: "Comida · te sobró 80.000 de meses anteriores" })}</div>
    <div class="chart" role="group" aria-label="Gastos por mes">${cols}<div class="limit-line" style="bottom:${100 * cap / max}%"><span>Límite con arrastre 230 mil</span></div></div><div class="axis">${axis}</div><p class="chart-note">* Febrero va hasta el 22; no se proyecta. La línea es el límite con arrastre de febrero. Toca una columna para ver su valor exacto.</p></section>`;
}
const resumenIncome = `<section class="slip income-slip"><h2 class="q">¿A dónde fue lo que entró?</h2>${L("Entró en febrero", money(200000))}${L("Gastos", money(100000))}${L("Ingresos menos gastos", money(100000), { cls: "strong" })}</section>`;
const incomeBody = `<section class="slip pinked"><span class="label">Entró en febrero</span><div class="hero">${money(200000)}</div><div class="tear" aria-hidden="true"></div><div class="split" aria-hidden="true"><i class="assigned" style="width:75%"></i><i class="free" style="width:25%"></i></div>${L("En presupuestos de categorías", money(150000))}${L("Sin asignar", money(50000))}<p class="why">Tus presupuestos cuentan su límite base. El arrastre no es ingreso ni gasto, así que no cambia lo que queda sin asignar.</p></section>`;

function adjRow(name, cap, spent, caption) {
  const left = cap - spent, over = left < 0, fill = over || cap <= 0 ? 100 : Math.round(100 * spent / cap);
  return `<button class="rank-row" aria-label="${name}. ${over ? "Te pasaste por " + spoken(-left) : "Quedan " + spoken(left)}. ${caption}. Cambiar límite"><span class="circle ${over ? "over" : ""}" style="--fill:${fill}%" aria-hidden="true">${icon(CAT_ICON[name])}<span class="on">${icon(CAT_ICON[name])}</span></span><span class="rank-main"><span>${name}</span><small class="adj-cap">${caption}</small></span>${money(left)}${icon("chevron_right", "chev")}</button>`;
}
const adjustBody = `<section class="slip pinked"><div class="answer-head"><span class="label">Restante del presupuesto</span>${stamp("over")}</div><div class="hero">${money(-20000)}</div><p class="why">Tu límite con arrastre es <b>400.000</b>: Comida trae <b>−150.000</b> de meses anteriores.</p><div class="tear" aria-hidden="true"></div>${L("Límite con arrastre de febrero", money(400000), { small: "base 550.000 · arrastre −150.000" })}${L("Gastado", money(420000))}</section><section class="slip"><h2 class="q">Tus categorías</h2><p class="caption" style="margin:0">Toca una para cambiar su límite de febrero.</p><div class="rank">${adjRow("Comida", -50000, 0, "gastaste 0 · límite con arrastre −50.000")}${adjRow("Transporte", 50000, 40000, "gastaste 40.000 de 50.000")}${adjRow("Hogar", 400000, 380000, "gastaste 380.000 de 400.000")}</div></section>`;
const limitSheet = `<div class="composer limit-sheet" role="dialog" aria-label="Límite base de Comida"><div class="ls-head"><span class="circle" aria-hidden="true">${icon("restaurant")}</span><span class="ls-title"><b>Límite base de Comida</b><small>Arrastre −150.000 · gastaste 0 en febrero</small></span><button class="icon-btn" aria-label="Cerrar">${icon("close")}</button></div><div class="ls-body"><div class="seg" role="group" aria-label="Para qué meses"><button aria-pressed="true">Solo febrero</button><button aria-pressed="false">Desde febrero</button></div><p class="ls-scope">Solo cambia febrero. Los meses siguientes no cambian.</p><div class="ls-amount"><div class="amount-meta"><span>Límite base en pesos</span><span>antes 100.000</span></div><div class="amount-value">${money(150000)}<span class="caret"></span></div></div><div class="chips"><button class="chip" aria-pressed="true">Lo gastado · 150.000</button><button class="chip" aria-pressed="false">Cubre el mes · 120.000</button></div><p class="ls-fact">${icon("receipt_long")}<span>Tu límite con arrastre de febrero pasaría a <b>450.000</b>. Te quedarían <b>30.000</b>.</span></p></div><div class="entry-action"><button class="primary">Guardar solo febrero</button></div>${padKeys()}</div>`;

// ——— Render ———

const params = new URLSearchParams(location.search);
["ink", "theme", "scale"].forEach(id => { if (params.get(id)) document.getElementById(id).value = params.get(id); });
if (params.get("only")) document.querySelectorAll(".section>h2, .section>.frames, .lede").forEach(e => { if (e.id !== params.get("only") && e.nextElementSibling?.id !== params.get("only")) e.style.display = "none"; });

function render() {
  const o = { ...reviewOptions(["ink", "theme"]), scale: Number(document.getElementById("scale").value) };
  const set = (id, h) => document.getElementById(id).innerHTML = h;
  const ed = (s, opts = {}, ph = {}) => phone(o, editor(s, opts), ph);
  const onFeb = { on: true, since: "feb 2027", carry: 0, cap: 100000 };
  const onSc17 = { on: true, since: "ene 2027", carry: -20000, cap: 80000 };
  const onSc18 = { on: true, since: "ene 2027", carry: 80000, cap: 180000 };

  set("enable",
    card("Off · the card in Editar categoría", "Its own card under Límite mensual, like Metas' “¿Ya la lograste?”: a separate decision with its own command and receipt (CP28), never saved with the form. Shown only when editing an existing category; new ones start off (CP1). While the form has unsaved changes the button waits and says “Guarda o descarta tus cambios para cambiar el arrastre.”", ed({ carry: { month: "febrero" } })) +
    card("Turning on asks once (SC07)", "17 feb, 30.000 already spent from the 1st. The slip names the start month, that carry starts at 0 and that the whole month counts from day 1 (CP2). Activar sends EnableCarry; nothing turns on by default.", ed({ carry: { month: "febrero" } }, { slip: ENABLE_SLIP })) +
    card("On · after the receipt", "Back from the confirmed receipt the card shows the start month and this month's arrastre and capacity, and says what happened in one line, read out when it appears. With arrastre 0, Dejar de arrastrar asks nothing (SC18).", ed({ carry: { ...onFeb, done: "Comida arrastra desde febrero." } })) +
    card("Refused · default after this month unlimited (CP3, SC46)", "Febrero has its own 100.000 but the default is Sin límite, so marzo would have no base. Activar sends EnableCarry; its refusal comes back in this sentence, under the help, and nothing turns on (ruling 2: no read of the default history before the tap).", ed({ amount: 0, unlimited: true, scope: "Sin límite desde febrero. Febrero tiene su propio límite: 100.000.", carry: { month: "febrero", refusal: REFUSALS[0][1] } })) +
    card("Every refusal, in words", "One sentence per refusal of CarryEnableRefusal and the limit edits a chain refuses; each says what to do. AlreadyOn is never shown: the screen re-reads and shows the card on.", crop(o, REFUSALS.map(([k, t]) => `<p class="caption" style="margin:8px 2px 4px;font-weight:700">${k}</p><div class="refusal">${t}</div>`).join(""))) +
    card("Large text · 200%", "The card wraps; the button stays full width.", ed({ carry: { month: "febrero" } }, {}, { large: true })));

  set("recibo",
    card("With carry · Al día (SC13)", "Only when a category carries this month. The answer slip keeps its order; under the per-day line, receipt lines give base, arrastre, capacity and spent, then the chain's start and the day the figures were read (CP27). Comida's cell says “con arrastre”; Transporte (off) is judged on its own base.", recibo(o, RECIBO.sc13)) +
    card("Excedido from an earlier deficit (CP14, SC11)", "The month's bases alone would leave 130.000. Comida starts at −50.000 with nothing spent, so the overall month is Excedido by 20.000; one sentence says why. 0 por día stays, next to the signed −20.000 (CP15).", recibo(o, RECIBO.over)) +
    card("A closed month (SC01, marzo)", "The past-month line names the capacity, and the slip says the closed remainder is next month's arrastre.", recibo(o, RECIBO.closed, { month: "Marzo", current: "abril" })) +
    card("Large text · 200%", "Scrolled to the breakdown: each amount moves under its label instead of being cut.", recibo(o, RECIBO.sc13, { large: true })));

  set("detail",
    card("Surplus carried (SC09)", "The hero is Restante del presupuesto against the capacity. Under the tear line: base, signed arrastre with its words, capacity and spent, then the month it comes from and the chain's start. The day figures use the capacity (CP19).", detail(o, DETAIL.sc09)) +
    card("Deficit carried (SC17)", "−20.000 is shown, not folded into a smaller limit: the base stays 100.000 (ticket pitfall).", detail(o, DETAIL.sc17)) +
    card("Negative capacity (SC11)", "Capacity −50.000 with nothing spent: Excedido by the exact 50.000, a sentence explains it, and Al día 0 says the deficit is still there (CP14, CP15).", detail(o, DETAIL.sc11)) +
    card("Large text · 200%", "SC11, scrolled to the breakdown. The signed figures stay whole.", detail(o, DETAIL.sc11, { large: true })));

  set("drop",
    card("Turning off a deficit (SC17)", "ADR 0020's wording. The slip gives the exact arrastre and this month's capacity and remaining before and after (CP10). Cancelar changes nothing; the command carries the confirmed −20.000.", ed({ carry: onSc17 }, { slip: confirmSlip({ title: "¿Dejar de arrastrar −20.000?", body: "<p>Este mes el límite vuelve a ser el base. No se borra ningún gasto.</p>", rows: [["Límite con arrastre", "80.000 → 100.000"], ["Restante del presupuesto", "50.000 → 70.000"], ["Límite base", "100.000, no cambia", true]], go: "Dejar de arrastrar" }) })) +
    card("Turning off a surplus (SC18)", "Dropping a surplus lowers this month: the slip says so in the same rows.", ed({ carry: onSc18 }, { slip: confirmSlip({ title: "¿Dejar de arrastrar +80.000?", body: "<p>Este mes el límite vuelve a ser el base. No se borra ningún gasto.</p>", rows: [["Límite con arrastre", "180.000 → 100.000"], ["Restante del presupuesto", "150.000 → 70.000"], ["Límite base", "100.000, no cambia", true]], go: "Dejar de arrastrar" }) })) +
    card("Archiving (SC20)", "After the existing archive slip, ArchiveCategory answers with the balance it would drop; this slip shows it with this month's before and after, and Archivar sends it confirmed. Unarchiving never turns carry back on (CP11).", ed({ carry: onSc17 }, { slip: confirmSlip({ title: "¿Dejar de arrastrar −20.000?", body: "<p>Al archivar Comida, este mes el límite vuelve a ser el base. No se borra ningún gasto.</p>", rows: [["Límite con arrastre", "80.000 → 100.000"], ["Restante del presupuesto", "50.000 → 70.000"], ["Límite base", "100.000, no cambia", true]], go: "Archivar" }) })) +
    card("Making it unlimited (SC21)", "Saving Sin límite while carrying asks first; the button names what is saved.", ed({ amount: 100000, unlimited: true, changed: true, scope: "Sin límite desde febrero. Los meses anteriores no cambian.", carry: onSc18 }, { slip: confirmSlip({ title: "¿Dejar de arrastrar +80.000?", body: "<p>Sin límite, Comida no puede arrastrar: desde febrero queda sin límite. No se borra ningún gasto.</p>", rows: [["Límite con arrastre", "180.000 → sin límite"], ["Restante del presupuesto", "150.000 → sin límite"]], go: "Guardar sin límite" }) })) +
    card("The arrastre moved while confirming", "A 5.000 expense of enero was recorded meanwhile, so the confirmed −20.000 no longer matches: nothing changed, and the slip comes back with the new figures (CP10).", ed({ carry: { on: true, since: "ene 2027", carry: -25000, cap: 75000 } }, { slip: confirmSlip({ changed: "El arrastre cambió mientras confirmabas: era −20.000. Nada se guardó; revisa las cifras nuevas.", title: "¿Dejar de arrastrar −25.000?", body: "<p>Este mes el límite vuelve a ser el base. No se borra ningún gasto.</p>", rows: [["Límite con arrastre", "75.000 → 100.000"], ["Restante del presupuesto", "45.000 → 70.000"]], go: "Dejar de arrastrar" }) })) +
    card("Arrastre 0: nothing to confirm (SC18)", "Turning off with 0 (or in the start month) asks nothing; the card says it once the receipt is back.", ed({ carry: { month: "febrero", done: "Comida dejó de arrastrar." } })) +
    card("Large text · 200%", "The slip scrolls between its title and its buttons.", ed({ carry: onSc17 }, { slip: confirmSlip({ title: "¿Dejar de arrastrar −20.000?", body: "<p>Este mes el límite vuelve a ser el base. No se borra ningún gasto.</p>", rows: [["Límite con arrastre", "80.000 → 100.000"], ["Restante del presupuesto", "50.000 → 70.000"], ["Límite base", "100.000, no cambia", true]], go: "Dejar de arrastrar" }) }, { large: true })));

  set("correct",
    card("Deleting an old expense (SC25)", "Today 20 abr 2027. Deleting a closed chain month's expense now asks first (it used to delete with Deshacer): each later month, its capacity with the change, its remaining and any verdict that flips (CP20, CP21).", expensesScreen(o, listSlip(SC25))) +
    card("A move touches two chains (SC26)", "Grouped by category. The month of the expense gives its own remaining; later months their capacity. The overall total does not move, and the note says so.", expensesScreen(o, listSlip({ title: "¿Guardar el cambio?", what: "Mercado · <b>70.000</b> · feb 2027 · pasa de Comida a Transporte", lead: "Esto cambia el límite con arrastre de 4 meses", go: "Guardar",
      groups: [["Comida", [["feb 2027", "mes del cambio", "Restante 110.000 → 180.000"], ["mar 2027", "210.000 → 280.000", "Restante −40.000 → 30.000 · Excedido → Cumplido", true], ["abr 2027", "60.000 → 130.000", "Restante 60.000 → 130.000"]]],
        ["Transporte", [["feb 2027", "mes del cambio", "Restante 90.000 → 20.000"], ["mar 2027", "140.000 → 70.000", "Restante 140.000 → 70.000"], ["abr 2027", "190.000 → 120.000", "Restante 190.000 → 120.000"]]]],
      note: "Tu límite total con arrastre no cambia: lo que sube en una categoría baja en la otra." }))) +
    card("A dropped balance changes (SC32)", "Carry was turned off on 5 abr with −40.000. The list names that balance as it would now read; abril itself is not listed (CP22).", expensesScreen(o, listSlip({ ...SC25, lead: "Esto cambia el límite con arrastre de 2 meses",
      groups: [["Comida", [["feb 2027", "180.000 → 200.000", "Restante 110.000 → 130.000"], ["mar 2027", "210.000 → 230.000", "Restante −40.000 → −20.000 · sigue Excedido"], ["abr 2027", "−40.000 → −20.000", "Lo que dejaste de arrastrar"]]]],
      note: "Abril no cambia: su límite ya es el base. Los límites base no cambian." }))) +
    card("Something changed before saving (SC34)", "Example: a 10.000 expense of febrero landed meanwhile. Saving is refused with nothing changed and the list comes back with the new figures.", expensesScreen(o, listSlip({ ...SC25, changed: "Tus datos cambiaron mientras revisabas. Nada se guardó; esta es la lista nueva.",
      groups: [["Comida", [["feb 2027", "180.000 → 200.000", "Restante 100.000 → 120.000"], ["mar 2027", "200.000 → 220.000", "Restante −50.000 → −30.000 · sigue Excedido"], ["abr 2027", "50.000 → 70.000", "Restante 50.000 → 70.000"]]]] }))) +
    card("After saving (SC25)", "One revision: Recibo re-reads and abril's capacity is 80.000 everywhere. The toast says how many months changed; Deshacer is a correction too and asks first.", recibo(o, RECIBO.corrected, { month: "Abril", toast: toastUndo("Gasto borrado · cambió el límite con arrastre de 3 meses") })) +
    card("Deshacer asks first", "Undoing restores the expense, so it lists the same months back (CP21).", phone(o, yr(appShell({ month: "Abril", body: reciboSlip(RECIBO.corrected) + grid(RECIBO.corrected.cells), active: 0 })) + `<div class="scrim center">${listSlip({ title: "¿Deshacer el borrado?", what: "Mercado · <b>20.000</b> · mié 13 ene 2027 vuelve a enero", lead: "Esto cambia el límite con arrastre de 3 meses", go: "Deshacer",
      groups: [["Comida", [["feb 2027", "200.000 → 180.000", "Restante 130.000 → 110.000"], ["mar 2027", "230.000 → 210.000", "Restante −20.000 → −40.000 · sigue Excedido"], ["abr 2027", "80.000 → 60.000", "Restante 80.000 → 60.000"]]]] })}</div>`)) +
    card("A past month's limit (SC28)", "Editing enero's own limit lists the months after it; their bases stay 100.000. A month in the current period needs no list (SC31).", phone(o, editor({ amount: 120000, changed: true, scope: "Cambias solo el límite de enero 2027. Los demás meses y el límite habitual no cambian.", carry: { on: true, since: "ene 2027", carry: -40000, cap: 60000 }, save: "Guardar límite de enero" }, { slip: listSlip({ title: "¿Guardar el límite de enero?", what: "Límite base de enero 2027: 100.000 → <b>120.000</b>", lead: "Esto cambia el límite con arrastre de 3 meses", go: "Guardar",
      groups: [["Comida", [["ene 2027", "mes del cambio", "Restante 80.000 → 100.000"], ["feb 2027", "180.000 → 200.000", "Restante 110.000 → 130.000"], ["mar 2027", "210.000 → 230.000", "Restante −40.000 → −20.000 · sigue Excedido"], ["abr 2027", "60.000 → 80.000", "Restante 60.000 → 80.000"]]]],
      note: "Los límites de febrero, marzo y abril siguen en 100.000." }) }))) +
    card("Large text · 200%", "The list scrolls; Cancelar and Borrar stay.", expensesScreen(o, listSlip(SC25), { large: true })));

  const sc47 = { amount: 300000, rows: [["feb 2027", "Restante 200.000 → −100.000", "ahora Excedido", true], ["mar 2027", "1.200.000 → 900.000", "límite con arrastre"]] };
  const sc37 = { amount: 200000, rows: [["feb 2027", "Restante 200.000 → 0", "sigue Cumplido"], ["mar 2027", "1.200.000 → 1.000.000", "límite con arrastre"]] };
  const CATCH = "Al registrar tus pagos pendientes cambió el límite con arrastre";
  set("catchup",
    card("The notice on Recibo (SC47)", "The locked bar names the carry change too, so it is never silent (CP18). The headline did not move: 400.000 before and after, because the payment was reserved until recorded.", recibo(o, RECIBO.catchup, { month: "Marzo", notice: "Sello anotó 1 pago fijo y cambió tu límite con arrastre" })) +
    card("Lo que Sello anotó · a verdict flips (SC47)", "Under the recorded payment, ADR 0020's lines: the month of the payment with its remaining and new verdict, then each later month's capacity before and after.", phone(o, yr(subShell({ title: "Lo que Sello anotó", body: recordedList(sc47, CATCH), active: 0, dock: "Listo", dockClass: "plain", month: "Marzo" })))) +
    card("Lo que Sello anotó · no flip (SC37)", "ADR 0020's own example: mar 1.200.000 → 1.000.000.", phone(o, yr(subShell({ title: "Lo que Sello anotó", body: recordedList(sc37, CATCH), active: 0, dock: "Listo", dockClass: "plain", month: "Marzo" })))) +
    card("Deleting it from the notice (SC47)", "Deleting a recorded payment is a correction: its list comes first (CP21). Its lines leave the notice once deleted.", phone(o, yr(subShell({ title: "Lo que Sello anotó", body: recordedList(sc47, CATCH), active: 0, dock: "Listo", dockClass: "plain", month: "Marzo" })) + `<div class="scrim center">${listSlip({ title: "¿Borrar este gasto?", what: "Arriendo · <b>300.000</b> · vie 5 feb 2027 · lo anotó Sello", lead: "Esto cambia el límite con arrastre de 1 mes", go: "Borrar", goClass: "del",
      groups: [["Hogar", [["feb 2027", "mes del cambio", "Restante −100.000 → 200.000 · Excedido → Cumplido", true], ["mar 2027", "900.000 → 1.200.000", "Restante 400.000 → 700.000"]]]] })}</div>`)));

  const failedOne = (h, p, btn) => `<section class="slip empty">${icon("error")}<h2>${h}</h2><p>${p}</p><button class="outline-btn">${btn}</button></section>`;
  set("unavailable",
    card("Recibo · carry cannot be calculated", "A typed carry failure (CP8, CP23) is never shown as 0 or as the base alone. It names the category (ruling 2) and offers the one repair that works: a chain month without a base opens the editor on that month. A read that failed offers Reintentar; a damaged chain says a restore repairs it.", phone(o, yr(appShell({ month: "Marzo", body: failedOne("No pudimos calcular el arrastre de Comida", "A feb 2027 de Comida le falta un límite, y el arrastre lo necesita. Ponle un límite a ese mes para seguir; tus registros siguen guardados.", "Ponerle límite a feb 2027"), active: 0 })))) +
    card("Category detail · the same repair", "Every month from the broken one fails, its expenses included, so the slip points to what can still be opened: the editor on the month without a base.", phone(o, subShell({ title: "Comida", active: 0, action: ["edit_note", "Editar categoría"], body: failedOne("No pudimos calcular el arrastre de Comida", "A feb 2027 de Comida le falta un límite, y el arrastre lo necesita. Ponle un límite a ese mes para seguir; tus registros siguen guardados.", "Ponerle límite a feb 2027") }))) +
    card("A save refused (CarryUnavailable)", "When a change would leave a month uncomputable, the command refuses with nothing changed. Entry, the editor and the limit sheet say it in their usual message line and keep the draft.", crop(o, `<p class="save-line">No se guardó: con este cambio Sello no podría calcular el arrastre. Tu borrador sigue aquí.</p><button class="primary">Anotar gasto</button>`)));

  set("resumen",
    card("Mes a mes (CP19)", "The dashed line is febrero's capacity, labelled Límite con arrastre. The selected column gives spent and its exact base, arrastre and capacity, also in the column's spoken value. Spending averages are unchanged; the month in progress is never projected.", phone(o, yr(appShell({ month: "Febrero", body: monthChart() + resumenIncome, tabs: 3, active: 1 })))) +
    card("Ingresos · Sin asignar (CP17, SC14)", "Base limits only: 200.000 − 150.000 = 50.000. One line says why carry does not count, since Recibo's remaining (130.000) does include it (CP16).", phone(o, yr(subShell({ title: "Ingresos de febrero", body: incomeBody, active: 1, dock: "Anotar ingreso", dockClass: "plain", month: "Febrero" })))) +
    card("Ajustar presupuesto from carry", "Reached from the Excedido slip above. The header gives capacity with its base and arrastre; Comida's row names its capacity instead of “de 100.000”.", phone(o, subShell({ title: "Ajustar presupuesto", body: adjustBody, active: 0, dock: null }))) +
    card("The limit sheet edits the base", "Its title says Límite base; the line under the chips gives the capacity and what is left. Chips come from the domain: Lo gastado sets the base that covers Comida (0 −), Cubre el mes the overall 20.000.", phone(o, subShell({ title: "Ajustar presupuesto", body: adjustBody, active: 0, dock: null }) + `<div class="scrim">${limitSheet}</div>`)));
}
// Frames marked "scrolled" show the bottom of the scrolling middle (the carry card).
// Large-text frames of Recibo and detail scroll their content to the breakdown.
const scrollFrames = () => {
  document.querySelectorAll(".entry-mid.scrolled").forEach(e => { e.scrollTop = e.scrollHeight; });
  document.querySelectorAll(".app.large .content").forEach(c => { const k = c.querySelector(".carry"); if (k) c.scrollTop = k.getBoundingClientRect().top - c.getBoundingClientRect().top - 24; });
};
document.querySelectorAll(".controls select").forEach(s => s.addEventListener("change", () => { render(); scrollFrames(); }));
render();
scrollFrames();
