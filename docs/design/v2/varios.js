// Sello v2 · Anotar varios (SELLO-126, pending the owner's approval). The bulk receipt in the language
// of the locked entry sheet: ✕ and the switch, a pinked receipt where the amount slip would be, the
// chips and note of the highlighted line, the action, the Sello pad. Four receipts share one drawing:
// expenses, incomes, the movements of one goal and the movements of one investment (COP or USD).
// Synthetic figures; every total below is worked by hand. The text keyboard is an inert illustration.

const TODAY = "Hoy, jue 22";
const CATEGORIES = [["Hogar", "home"], ["Comida", "restaurant"], ["Transporte", "directions_bus"], ["Café", "local_cafe"], ["Salud", "medical_services"], ["Ocio", "theaters"], ["Ropa", "checkroom"], ["Mascotas", "pets"], ["Educación", "school"], ["Gimnasio", "fitness_center"]];
const SOURCES = ["Salario", "Freelance", "Ingresos pasivos", "Transferencia", "Otro"];
const GOAL_TYPES = [["in", "Aportar"], ["out", "Retirar"]];
const INV_TYPES = [["in", "Aporte"], ["value", "Valor de hoy"], ["out", "Retiro"], ["paid", "Dividendo o renta"]];
const usdText = c => `USD ${fmt(Math.trunc(c / 100))},${String(c % 100).padStart(2, "0")}`;

// attr: the field a line needs before it can be saved. btn1 / btnN: the action's words.
const KIND = {
  expense: { attr: "cat", seg: true, label: "Categoría", missing: "Elige una categoría", what: ["categoría", "categorías"], btn1: "Anotar gasto", btnN: "gastos", btnCls: "", chips: CATEGORIES.map(([n, ic]) => [n, n, ic]), total: "Total" },
  income: { attr: "src", seg: true, label: "Origen", missing: "Elige un origen", what: ["origen", "orígenes"], btn1: "Anotar ingreso", btnN: "ingresos", btnCls: "", chips: SOURCES.map(n => [n, n]), total: "Total" },
  goal: { attr: "type", dated: true, label: "Tipo", missing: "Elige Aportar o Retirar", what: ["tipo", "tipos"], btn1: "Anotar movimiento", btnN: "movimientos", btnCls: "goal", bar: "Aportar varios", sub: "Viaje a la costa", chips: GOAL_TYPES.map(([k, t]) => [k, t]), one: true, total: "Total" },
  invest: { attr: "type", dated: true, label: "Tipo", missing: "Elige un tipo", what: ["tipo", "tipos"], btn1: "Anotar movimiento", btnN: "movimientos", btnCls: "inv", bar: "Varios movimientos", sub: "Ecopetrol", chips: INV_TYPES.map(([k, t]) => [k, t]), one: true, total: "Aportado" },
  usd: { attr: "type", dated: true, label: "Tipo", missing: "Elige un tipo", what: ["tipo", "tipos"], btn1: "Anotar movimiento", btnN: "movimientos", btnCls: "inv", bar: "Varios movimientos", sub: "Acciones EE. UU. · en dólares", chips: INV_TYPES.map(([k, t]) => [k, t]), one: true, total: "Aportado", usd: true },
};
const TYPE_LOOK = { // [mark class, icon, title]
  goal: { in: ["gain", "add", "Aporte"], out: ["out", "arrow_back", "Retiro"] },
  invest: { in: ["gain", "add", "Aporte"], value: ["val", "insights", "Valor de hoy"], out: ["out", "arrow_back", "Retiro"], paid: ["gain", "add", "Dividendo o renta"] },
};
TYPE_LOOK.usd = TYPE_LOOK.invest;

// ---------- Lines ----------
const num = (k, a) => k === "usd" ? usdText(a) : fmt(a);
function sign(k, l) {
  if (k === "income") return "+";
  if (k === "goal") return l.type === "in" ? "+" : l.type === "out" ? "−" : "";
  if (k === "invest" || k === "usd") return l.type === "in" || l.type === "paid" ? "+" : l.type === "out" ? "−" : "";
  return "";
}
function amount(k, l, hl) {
  const caret = hl ? `<span class="caret"></span>` : "";
  if (l.a == null) return `<span class="rc-amt ph">0${caret}</span>`;
  const gain = k === "income" || (k === "goal" && l.type === "in");
  return `<span class="rc-amt ${gain ? "gain" : ""}">${sign(k, l)}${num(k, l.a)}${caret}</span>`;
}
function parts(k, l) {
  const C = KIND[k], has = l[C.attr] != null && l[C.attr] !== "";
  const reason = l.bad || (l.a != null && !has ? C.missing : "");
  let p = { markCls: "none", mark: "?", title: "", soft: false, sub: l.note || "", reason };
  if (k === "expense") {
    const c = CATEGORIES.find(([n]) => n === l.cat);
    if (c) p = { ...p, markCls: "cat", mark: icon(c[1]), title: c[0] };
    else p = { ...p, title: l.note || "Sin categoría", soft: !l.note };
  } else if (k === "income") {
    if (has) p = { ...p, markCls: "gain", mark: icon("add"), title: l.src === "Otro" ? (l.srcName || "Otro") : l.src, sub: [l.src === "Otro" && l.srcName ? "Otro" : "", l.note].filter(Boolean).join(" · ") };
    else p = { ...p, title: l.note || "Sin origen", soft: !l.note };
  } else {
    const t = TYPE_LOOK[k][l.type];
    if (t) p = { ...p, markCls: t[0], mark: icon(t[1]), title: t[2] };
    else p = { ...p, title: "Sin tipo", soft: true };
  }
  if (C.dated && !l.hl) p.sub = [l.date || TODAY, l.note].filter(Boolean).join(" · ");
  if (C.dated && l.hl) p.sub = l.note || "";
  if (reason) p.sub = reason;
  return p;
}
function line(k, l, i, n, show, swipe) {
  const C = KIND[k], p = parts(k, l), hl = show && l.hl;
  const pill = hl && C.dated ? `<button class="date-pill">${icon("event")}${l.date || TODAY}</button>` : "";
  const label = `Renglón ${i + 1} de ${n}. ${p.title}${p.reason ? `, ${p.reason}` : ""}`;
  const body = `<div class="rc-line ${hl ? "hl" : ""} ${p.reason ? "bad" : ""}" role="button" tabindex="0" aria-label="${label}" ${swipe ? `style="transform:translateX(-${swipe}%)"` : ""}><span class="rc-mark ${p.markCls}" aria-hidden="true">${p.mark}</span><span class="rc-txt"><b class="${p.soft ? "soft" : ""}">${p.title}</b>${p.sub ? `<small class="${p.reason ? "bad" : ""}">${p.sub}</small>` : ""}</span>${amount(k, l, hl)}${pill}</div>`;
  return `<div class="rc-row">${swipe ? `<div class="rc-del" aria-hidden="true">${icon("close")}${swipe > 40 ? "Suelta para quitar" : "Quitar"}</div>` : ""}${body}</div>`;
}

// ---------- The receipt ----------
function totalHtml(k, t) {
  if (k === "income") return t > 0 ? `<b class="gain">+${fmt(t)}</b>` : `<b>0</b>`;
  if (k === "goal") return t > 0 ? `<b class="gain">+${fmt(t)}</b>` : t < 0 ? `<b>−${fmt(-t)}</b>` : `<b>0</b>`;
  if (k === "usd") return `<b>${t < 0 ? "−" : ""}${usdText(Math.abs(t))}</b>`;
  return `<b>${t < 0 ? "−" : ""}${fmt(Math.abs(t))}</b>`;
}
function head(k, S, lines) {
  const C = KIND[k], n = lines.length;
  const missing = lines.filter(l => l.a != null && !(l[C.attr] != null && l[C.attr] !== "")).length;
  const refused = lines.filter(l => l.bad).length;
  let left;
  if (S.limit) left = `<span class="bad">Máximo 30 por recibo</span>`;
  else if (missing) left = `<span class="bad">${icon("error")}${missing === 1 ? `Falta 1 ${C.what[0]}` : `Faltan ${missing} ${C.what[1]}`}</span>`;
  else if (refused) left = `<span class="bad">${icon("error")}Revisa ${refused === 1 ? "1 renglón" : `${refused} renglones`}</span>`;
  else if (C.seg) left = `<span class="t"><b>Anotar varios</b><small>${n} ${n === 1 ? "renglón" : "renglones"}</small></span>`;
  else left = `<span class="t"><b>${n} ${n === 1 ? "renglón" : "renglones"}</b></span>`;
  const date = C.seg ? `<button class="date-pill" ${S.dis}>${icon("event")}${S.date || TODAY}</button>` : "";
  return `<div class="rc-head">${left}${date}</div>`;
}
function receipt(k, S, lines, show) {
  const C = KIND[k];
  const rows = lines.map((l, i) => line(k, l, i, lines.length, show, S.swipe && S.swipe.i === i ? S.swipe.pct : 0)).join("");
  const fades = S.scroll === "end" ? "fade-top" : S.scroll === "mid" ? "fade-top fade-bottom" : S.scroll === "start" ? "fade-bottom" : "";
  const empty = lines[Math.max(0, lines.findIndex(l => l.hl))].a == null;
  const addDis = S.dis || S.limit || empty ? "disabled" : "";
  return `<section class="slip pinked rc" aria-label="Recibo">${head(k, S, lines)}<div class="rc-lines ${fades}" ${S.scroll === "end" ? 'data-end="1"' : ""}>${rows}</div><div class="tear" aria-hidden="true"></div><div class="rc-foot"><button class="text-btn" ${addDis}>${icon("add")}Otro renglón</button><span class="rc-total"><span>${C.total}</span>${totalHtml(k, S.total ?? 0)}</span></div></section>`;
}

// ---------- Chips, note, action, pad ----------
const chip = (key, text, ic, on, dis) => `<button class="chip" aria-pressed="${on}" ${dis}>${ic ? icon(ic) : ""}${text}</button>`;
function mid(k, S, lines, hi) {
  const C = KIND[k], l = lines[hi], dis = S.dis;
  const h3 = `<h3>${C.label}${lines.length > 1 ? ` · renglón ${hi + 1}` : ""}</h3>`;
  if (S.read === "loading") return `<div class="entry-mid rc-mid" role="status" aria-label="Cargando">${h3}<div class="chips"><span class="skel" style="width:90px;height:36px"></span><span class="skel" style="width:110px;height:36px"></span><span class="skel" style="width:80px;height:36px"></span><span class="skel" style="width:100px;height:36px"></span></div></div>`;
  if (S.read === "failed") return `<div class="entry-mid rc-mid">${h3}<p class="read-fail" role="status">${icon("error")}<span>No pudimos leer tus categorías. Tus renglones siguen aquí.</span></p><button class="outline-btn" style="margin-top:6px">Reintentar</button></div>`;
  const chips = C.chips.map(([key, text, ic]) => chip(key, text, ic, key === l[C.attr], dis)).join("");
  let h = `${h3}<div class="chips ${C.one ? "one-row" : ""}">${chips}</div>`;
  if (k === "income" && l.src === "Otro" && l.srcName) h += `<button class="note-pill filled" ${dis}>${icon("account_balance")}<span>${l.srcName}</span><span class="edit">Cambiar</span></button>`;
  h += l.note ? `<button class="note-pill filled" ${dis}>${icon("edit_note")}<span>${l.note}</span><span class="edit">Editar</span></button>` : `<button class="note-pill" ${dis}>${icon("edit_note")}<span>Agregar nota</span></button>`;
  return `<div class="entry-mid rc-mid">${h}</div>`;
}
function action(k, S, lines) {
  const C = KIND[k], n = lines.filter(l => l.a != null).length;
  const label = n <= 1 ? C.btn1 : `Anotar ${n} ${C.btnN}`;
  const blocked = lines.some(l => l.a != null && ((l[C.attr] == null || l[C.attr] === "") || l.bad));
  const ok = n > 0 && !blocked, cls = `primary ${C.btnCls}`;
  if (S.save === "busy") return `<div class="entry-action"><button class="${cls}" disabled aria-busy="true"><span class="spinner" aria-hidden="true"></span>Guardando…</button></div>`;
  if (S.save === "unknown") return `<div class="entry-action"><p class="save-msg warn" role="status">${icon("error")}Aún no sabemos si se guardó. Compruébalo antes de volver a anotar.</p><button class="${cls}">Comprobar si se guardó</button></div>`;
  if (S.save === "rejected") return `<div class="entry-action"><p class="save-msg loss" role="status">${icon("error")}No se guardó. Tu borrador sigue aquí.</p><button class="${cls}" ${ok ? "" : "disabled"}>${icon("add")}${label}</button></div>`;
  return `<div class="entry-action"><button class="${cls}" ${ok ? "" : "disabled"}>${icon("add")}${label}</button></div>`;
}
const padKeys = usd => `<div class="pad {L}" role="group" aria-label="Teclado de Sello">${["1", "2", "3", "4", "5", "6", "7", "8", "9", usd ? "," : "000", "0"].map(key => `<button class="${key === "000" || key === "," ? "k000" : ""}" ${key === "," ? 'aria-label="Coma decimal"' : key === "000" ? 'aria-label="Tres ceros"' : ""} {D}>${key}</button>`).join("")}<button class="del" aria-label="Borrar (mantén para borrar todo)" {D}>${icon("backspace")}</button></div>`;
const pad = (usd, locked) => padKeys(usd).replaceAll("{L}", locked ? "locked" : "").replaceAll("{D}", locked ? "disabled" : "");

// ---------- Overlays ----------
const imeText = () => `<div class="ime" aria-hidden="true"><div class="ime-tools"><span>☺</span><span>Español</span><span>⌄</span></div>${["qwertyuiop", "asdfghjklñ", "zxcvbnm"].map(r => `<div class="ime-row">${[...r].map(key => `<span>${key}</span>`).join("")}</div>`).join("")}<div class="ime-space">Español</div></div>`;
function composer({ text, ctx, icn }) {
  const count = [...text].length;
  return `<div class="scrim"><div class="composer" role="dialog" aria-label="Nota"><div class="composer-head"><p class="note-context">${icon(icn)}${ctx}</p><button class="text-link">Cancelar</button></div><div class="composer-row"><div class="text-box">${text}<span class="caret"></span></div><button class="gw-round" aria-label="Listo">${icon("check")}</button></div><div class="field-foot"><span>Opcional</span><span>${count}/60</span></div></div>${imeText()}</div>`;
}
const confirmSlip = ({ title, body, ghost, go, danger }) => `<div class="scrim center"><div class="confirm" role="alertdialog" aria-label="${title}"><h2>${title}</h2><p>${body}</p><div class="tear" aria-hidden="true"></div><div class="confirm-actions"><button class="ghost">${ghost}</button><button class="${danger ? "del" : "go"}">${go}</button></div></div></div>`;
const toast = (text, undo) => `<div class="toast low" role="status">${icon("check")}<span>${text}</span>${undo ? `<button class="undo">Deshacer</button>` : ""}</div>`;

// ---------- The sheet ----------
// S: lines, total, save (busy | rejected | unknown), limit, swipe {i, pct}, scroll (start | mid | end),
//    composer, confirm, toast, read (loading | failed), date. A line's `hl` marks the one the pad types into.
function sheet(k, S) {
  const C = KIND[k], lines = S.lines;
  const locked = S.save === "busy" || S.save === "unknown";
  S = { ...S, dis: locked ? "disabled" : "" };
  let hi = lines.findIndex(l => l.hl);
  if (hi < 0) hi = lines.length - 1;
  const show = !locked;
  const closeDis = S.save === "busy" ? "disabled" : "";
  const barMid = C.seg
    ? `<div class="seg" role="group" aria-label="Tipo"><button aria-pressed="${k === "expense"}" ${S.dis}>Gasto</button><button aria-pressed="${k === "income"}" ${S.dis}>Ingreso</button></div>`
    : `<div class="bar-title"><b>${C.bar}</b><small>${C.sub}</small></div>`;
  const top = `<div class="entry-top">${statusbar()}<div class="entry-bar"><button class="icon-btn" aria-label="Cerrar" ${closeDis}>${icon("close")}</button>${barMid}${C.seg ? `<span class="spacer"></span>` : ""}</div></div>`;
  let h = top + receipt(k, S, lines, show) + mid(k, S, lines, hi) + action(k, S, lines) + pad(C.usd, locked) + `<div class="gesture" style="background:var(--desk)"></div>`;
  if (S.composer) h += composer(S.composer);
  if (S.confirm) h += confirmSlip(S.confirm);
  if (S.toast) h += S.toast;
  return h;
}

// ---------- Synthetic receipts (every total worked by hand) ----------
const hl = l => ({ ...l, hl: true });
// Expenses: 18.400 + 75.100 + 32.500 = 126.000
const E3 = [{ cat: "Transporte", a: 18400, note: "Uber" }, { cat: "Salud", a: 75100, note: "Farmacia Pasteur" }, { cat: "Comida", a: 32500, note: "Carulla" }];
const withHl = (list, i) => list.map((l, j) => j === i ? hl(l) : l);
// 30 lines of 4.500 = 135.000
const E30 = Array.from({ length: 30 }, (_, i) => ({ cat: i % 2 ? "Comida" : "Café", a: 4500, note: i % 2 ? "" : "Tinto" }));
// Incomes: 450.000 + 120.000 + 850.000 = 1.420.000
const I3 = [{ src: "Freelance", a: 450000, note: "Página web" }, { src: "Transferencia", a: 120000, note: "Devolución de Laura" }, { src: "Otro", srcName: "Venta de la bicicleta", a: 850000 }];
// 30 lines of 50.000 = 1.500.000
const I30 = Array.from({ length: 30 }, (_, i) => ({ src: i % 2 ? "Salario" : "Freelance", a: 50000 }));
// Goal: +100.000 +80.000 −50.000 = +130.000
const G3 = [{ type: "in", a: 100000, date: "lun 12 oct" }, { type: "in", a: 80000, date: "mié 14 oct", note: "Regalo de mamá" }, { type: "out", a: 50000, date: "vie 16 oct", note: "Arreglo del carro" }];
// 30 aportes of 20.000 = 600.000
const G30 = Array.from({ length: 30 }, (_, i) => ({ type: "in", a: 20000, date: ["lun 5 oct", "mar 6 oct", "mié 7 oct"][i % 3] }));
// Investment: aportes − retiros = 500.000 (the dividend and the valuation do not add up with it)
const V3 = [{ type: "in", a: 500000, date: "sáb 3 oct" }, { type: "paid", a: 89000, date: "mar 29 sep" }, { type: "value", a: 3600000, date: "hoy, jue 22" }];
// 30 aportes of 100.000 = 3.000.000
const V30 = Array.from({ length: 30 }, (_, i) => ({ type: "in", a: 100000, date: ["lun 5 oct", "mar 6 oct", "mié 7 oct"][i % 3] }));
// USD, in cents: 250,50 + 300,00 = 550,50 aportado; the dividend of 12,40 does not add up with it
const U3 = [{ type: "in", a: 25050, date: "lun 12 oct" }, { type: "in", a: 30000, date: "lun 5 oct" }, { type: "paid", a: 1240, date: "vie 16 oct", note: "Dividendo de octubre" }];

const SC = {
  expense: {
    opening: { lines: [hl({ a: null })], total: 0 },
    three: { lines: withHl(E3, 2), total: 126000 },
    missing: { lines: [E3[0], hl({ cat: null, a: 75100, note: "Farmacia Pasteur" }), E3[2]], total: 126000 },
    swipe: { lines: E3, total: 126000, swipe: { i: 1, pct: 55 } },
    removed: { lines: [E3[0], hl(E3[2])], total: 50900, toast: toast("Quitaste el renglón de Salud", true) },
    limit: { lines: withHl(E30, 29), total: 135000, limit: true, scroll: "end" },
    note: { lines: withHl(E3, 1), total: 126000, composer: { text: "Farmacia Pasteur", ctx: "Renglón 2 · <b>Salud</b> · <b>75.100</b>", icn: "medical_services" } },
    busy: { lines: E3, total: 126000, save: "busy" },
    rejected: { lines: withHl(E3, 2), total: 126000, save: "rejected" },
    refused: { lines: [E3[0], hl({ ...E3[1], bad: "Esta categoría está archivada" }), E3[2]], total: 126000, save: "rejected" },
    unknown: { lines: E3, total: 126000, save: "unknown" },
    discard: { lines: withHl(E3, 2), total: 126000, confirm: { title: "¿Descartar 3 renglones?", body: "Se borra lo que escribiste en este recibo. Esto no se puede deshacer.", ghost: "Seguir anotando", go: "Descartar", danger: true } },
    loading: { lines: withHl(E3, 2), total: 126000, read: "loading" },
    failedRead: { lines: withHl(E3, 2), total: 126000, read: "failed" },
    fromEntry: { lines: [E3[0], hl({ cat: "Transporte", a: null })], total: 18400 },
    fromCategory: { lines: [hl({ cat: "Transporte", a: null })], total: 0 },
    switching: { lines: withHl(E3, 2), total: 126000, confirm: { title: "¿Pasar a ingresos?", body: "Tus <b>3 renglones</b> conservan monto y nota. Tendrás que elegir el origen de cada uno.", ghost: "Seguir con gastos", go: "Pasar a ingreso" } },
  },
  income: {
    opening: { lines: [hl({ a: null })], total: 0 },
    three: { lines: withHl(I3, 2), total: 1420000 },
    missing: { lines: [I3[0], hl({ src: null, a: 120000, note: "Devolución de Laura" }), I3[2]], total: 1420000 },
    swipe: { lines: I3, total: 1420000, swipe: { i: 1, pct: 55 } },
    limit: { lines: withHl(I30, 29), total: 1500000, limit: true, scroll: "end" },
    otro: { lines: [I3[0], I3[1], hl({ src: "Otro", a: 850000 })], total: 1420000, composer: { text: "Venta de la bicicleta", ctx: "Renglón 3 · Origen <b>Otro</b> · nombre", icn: "account_balance" } },
    busy: { lines: I3, total: 1420000, save: "busy" },
    rejected: { lines: withHl(I3, 2), total: 1420000, save: "rejected" },
    unknown: { lines: I3, total: 1420000, save: "unknown" },
    switched: { lines: [hl({ src: null, a: 18400, note: "Uber" }), { src: null, a: 75100, note: "Farmacia Pasteur" }, { src: null, a: 32500, note: "Carulla" }], total: 126000 },
  },
  goal: {
    opening: { lines: [hl({ type: null, a: null })], total: 0 },
    three: { lines: withHl(G3, 2), total: 130000 },
    missing: { lines: [G3[0], hl({ type: null, a: 80000, date: "mié 14 oct", note: "Regalo de mamá" }), G3[2]], total: 50000 },
    swipe: { lines: G3, total: 130000, swipe: { i: 1, pct: 55 } },
    limit: { lines: withHl(G30, 29), total: 600000, limit: true, scroll: "end" },
    busy: { lines: G3, total: 130000, save: "busy" },
    rejected: { lines: withHl(G3, 2), total: 130000, save: "rejected" },
    refused: { lines: [G3[0], G3[1], hl({ type: "out", a: 2000000, date: "vie 16 oct", bad: "Solo hay 1.890.000 disponibles" })], total: -1820000, save: "rejected" },
    unknown: { lines: G3, total: 130000, save: "unknown" },
  },
  invest: {
    opening: { lines: [hl({ type: null, a: null })], total: 0 },
    three: { lines: withHl(V3, 2), total: 500000 },
    missing: { lines: [V3[0], hl({ type: null, a: 89000, date: "mar 29 sep" }), V3[2]], total: 500000 },
    swipe: { lines: V3, total: 500000, swipe: { i: 1, pct: 55 } },
    limit: { lines: withHl(V30, 29), total: 3000000, limit: true, scroll: "end" },
    busy: { lines: V3, total: 500000, save: "busy" },
    rejected: { lines: withHl(V3, 2), total: 500000, save: "rejected" },
    refused: { lines: [V3[0], hl({ type: "out", a: 4000000, date: "hoy, jue 22", bad: "Más de lo que vale (3.600.000)" }), V3[2]], total: -3500000, save: "rejected" },
    unknown: { lines: V3, total: 500000, save: "unknown" },
  },
  usd: {
    opening: { lines: [hl({ type: null, a: null })], total: 0 },
    three: { lines: withHl(U3, 2), total: 55050 },
    missing: { lines: [U3[0], hl({ type: null, a: 30000, date: "lun 5 oct" }), U3[2]], total: 25050 },
  },
};

// ---------- Entry points ----------
const phone = (o, inner) => `<div class="phone"><div class="app" data-ink="${o.ink}" data-theme="${o.theme}">${inner}</div></div>`;
const card = (title, note, inner) => `<div class="frame-card"><h3>${title}</h3><p>${note}</p>${inner}</div>`;

// The Anotar sheet with the new Varios pill where the bar had an empty slot.
function anotar() {
  const cat = ["Transporte", "directions_bus"];
  const bar = `<div class="entry-top">${statusbar()}<div class="entry-bar"><button class="icon-btn" aria-label="Cerrar">${icon("close")}</button><div class="seg" role="group" aria-label="Tipo"><button aria-pressed="true">Gasto</button><button aria-pressed="false">Ingreso</button></div><button class="varios-btn" aria-label="Anotar varios">${icon("receipt_long")}Varios</button></div></div>`;
  const slip = `<section class="slip pinked amount-slip"><div class="amount-meta"><span>Gasto en pesos</span><button class="date-pill">${icon("event")}${TODAY}</button></div><div class="amount-row"><span class="circle" style="--fill:100%" aria-hidden="true">${icon(cat[1])}<span class="on">${icon(cat[1])}</span></span><div class="amount-value">${money(18400)}<span class="caret"></span></div></div></section>`;
  const chips = CATEGORIES.map(([n, ic]) => `<button class="chip" aria-pressed="${n === cat[0]}">${icon(ic)}${n}</button>`).join("") + `<button class="chip add">${icon("add")}Nueva</button>`;
  const midHtml = `<div class="entry-mid"><h3>Categoría</h3><div class="chips">${chips}</div><button class="note-pill filled">${icon("edit_note")}<span>Uber</span><span class="edit">Editar</span></button></div>`;
  return bar + slip + midHtml + `<div class="entry-action"><button class="primary">${icon("add")}Anotar gasto</button></div>` + pad(false, false) + `<div class="gesture" style="background:var(--desk)"></div>`;
}

const foot = (label, right) => `<div class="origin-foot"><button class="text-btn">${icon("receipt_long")}${label}</button>${right}</div>`;
const verLos = n => `<button class="text-btn">Ver los ${n}${icon("chevron_right")}</button>`;
const movRow = (cls, ic, label, date, n, last) => `<div class="mov-row ${cls} ${last ? "last" : ""}"><span class="mi">${icon(ic)}</span><span><b>${label}</b><small>${date}</small></span><b>${n}</b></div>`;

function categoryDetail() {
  const body = `<section class="slip pinked"><div class="answer-head"><span class="label">Te quedan en Transporte</span></div><div class="hero">81.600</div><p class="answer-note">de 200.000 · llevas 118.400</p></section><section class="slip"><h2 class="q">Últimos gastos</h2>${[["Bus", "mié 21 oct", "2.800"], ["Gasolina", "mar 20 oct", "95.000"], ["Uber", "lun 19 oct", "20.600"]].map(([n, d, a], i) => `<div class="inc-row ${i === 2 ? "last" : ""}" style="grid-template-columns:1fr auto"><span><b>${n}</b><small>${d}</small></span><b>${a}</b></div>`).join("")}${foot("Anotar varios", verLos(13))}</section>`;
  return subShell({ title: "Transporte", body, tabs: 3, active: 0, dock: "Anotar aquí" });
}
function goalDetail(after) {
  const saved = after ? 1840000 : 1710000, pct = after ? 61 : 57, left = after ? 1160000 : 1290000;
  const rows = after
    ? [movRow("out", "arrow_back", "Retiro", "vie 16 oct · Arreglo del carro", "−50.000"), movRow("", "add", "Aporte", "mié 14 oct · Regalo de mamá", "+80.000"), movRow("", "add", "Aporte", "lun 12 oct", "+100.000"), movRow("", "add", "Aporte", "vie 2 oct", "+350.000", true)]
    : [movRow("", "add", "Aporte", "vie 2 oct", "+350.000"), movRow("", "add", "Aporte", "mié 2 sep", "+350.000"), movRow("", "add", "Aporte", "sáb 2 ago", "+300.000", true)];
  const body = `<section class="slip pinked"><div class="gd-head"><span class="label">Ya guardaste</span><b>${pct} %</b></div><div class="gd-saved">${money(saved)}</div><div class="gd-sub"><span>de 3.000.000</span><span>faltan ${fmt(left)}</span></div><span class="v1-track" aria-hidden="true"><i style="width:${pct}%"></i></span></section><section class="slip"><div class="mov-head"><h2 class="q" style="margin:0">Movimientos</h2><button class="text-btn">Retirar dinero</button></div>${rows.join("")}${foot("Aportar varios", verLos(after ? 9 : 6))}</section>`;
  return subShell({ title: "Viaje a la costa", body, active: 2, dock: "Aportar a esta meta", dockClass: "goal", action: ["edit_note", "Editar meta"] });
}
function investDetail(after) {
  const rows = after
    ? [movRow("val", "insights", "Valor de hoy", "jue 22 oct", "3.600.000"), movRow("", "add", "Aporte", "sáb 3 oct", "500.000"), movRow("val", "insights", "Valor de hoy", "mié 9 oct", "3.050.000"), movRow("", "add", "Dividendo o renta", "mar 29 sep", "89.000", true)]
    : [movRow("val", "insights", "Valor de hoy", "mié 9 oct", "3.050.000"), movRow("", "add", "Aporte", "lun 14 sep", "500.000"), movRow("", "add", "Aporte", "lun 3 ago", "500.000", true)];
  const body = `<section class="slip pinked"><div class="answer-head"><span class="label">Vale</span></div><div class="hero">${after ? "3.600.000" : "3.050.000"}</div><p class="answer-note">valor del ${after ? "22" : "9"} oct</p></section><section class="slip"><div class="mov-head"><h2 class="q" style="margin:0">Movimientos</h2></div>${rows.join("")}${foot("Varios movimientos", verLos(after ? 12 : 9))}</section>`;
  return subShell({ title: "Ecopetrol", body, tabs: 4, active: 3, dock: "Anotar movimiento", dockClass: "inv" });
}
function incomeList(after) {
  const items = after
    ? [["Otro · Venta de la bicicleta", "jue 22 oct", "", 850000], ["Freelance", "jue 22 oct", "Página web", 450000], ["Transferencia", "jue 22 oct", "Devolución de Laura", 120000], ["Transferencia", "lun 19 oct", "Devolución del viaje", 150000]]
    : [["Freelance", "mié 14 oct", "Diseño de logo", 250000], ["Transferencia", "lun 19 oct", "Devolución del viaje", 150000], ["Salario", "jue 1 oct", "", 1600000]];
  const rows = items.map(([s, d, n, a], i) => `<button class="inc-row ${i === items.length - 1 ? "last" : ""}"><span><b>${s}</b><small>${d}${n ? ` · ${n}` : ""}</small></span><b>${money(a)}</b>${icon("chevron_right", "chev")}</button>`).join("");
  const body = `<section class="slip pinked"><h2 class="q">Entró en octubre</h2><p class="answer">${money(after ? 3420000 : 2000000)}</p></section><section class="slip"><h2 class="q">Lo que entró</h2>${rows}<div class="total-line"><span>Total</span>${money(after ? 3420000 : 2000000)}</div>${foot("Anotar varios", "")}</section>`;
  return subShell({ title: "Ingresos de octubre", body, dock: "Anotar ingreso" });
}
// Recibo after three expenses: Comida 400.000 − 32.500, Transporte 200.000 − 18.400, Salud 200.000 − 75.100, Hogar untouched.
function recibo() {
  const cells = [["Comida", "restaurant", 367500, 8], ["Transporte", "directions_bus", 181600, 9], ["Salud", "medical_services", 124900, 38], ["Hogar", "home", 600000, 0]]
    .map(([n, ic, left, fill]) => `<button class="cell"><span class="circle" style="--fill:${fill}%">${icon(ic)}<span class="on">${icon(ic)}</span></span><span class="cell-text"><span>${n}</span><b>${money(left)}</b></span></button>`).join("");
  const body = `<section class="slip pinked"><div class="answer-head"><span class="label">Te quedan</span><span class="stamp">Al día</span></div><div class="hero">1.274.000</div><p class="answer-note">en octubre</p></section><section class="slip"><div class="section-head"><h2>Tus categorías</h2></div><div class="grid">${cells}</div></section>`;
  return appShell({ body, tabs: 3, active: 0, toast: "3 gastos anotados: 126.000" });
}
const withToast = (inner, text) => inner + `<div class="toast" role="status">${icon("check")}<span>${text}</span></div>`;

// ---------- Choices the ticket left open ----------
const OPEN = [
  "<b>Switching Gasto / Ingreso with typed lines:</b> the lines keep their amounts and notes, lose their category or source, and the switch asks first (a confirm slip). With nothing typed it switches at once. Silently dropping a category, or silently clearing the lines, were the alternatives.",
  "<b>Chips:</b> categories and sources in two rows scrolling sideways, as entry; goal and investment types in one row, as the movement sheet. No <b>Nueva</b> chip: creating categories is outside E23.",
  "<b>No type is preselected</b> on the first line of a goal or investment receipt (like entry’s category); later lines copy the line above. A wrong direction on a receipt is easy to miss.",
  "<b>The foot:</b> a goal shows its net (aportes − retiros); an investment shows <b>Aportado</b> (aportes − retiros) only, because dividends and valuations do not add up with them. v1.1 had no total for investments.",
  "<b>Removing a draft line</b> keeps v1.1’s swipe, long press and <b>Deshacer</b> toast. It is a draft, not a saved entry, so “no undo after saving” is untouched. The last line cannot be removed.",
  "<b>Reaching the receipt:</b> a <b>Varios</b> pill in the Anotar bar and <b>Anotar varios</b> at the foot of the category, goal, investment and income lists. v1.1’s long press on the dock and the investment’s overflow menu are not used.",
  "<b>A line’s title</b> is its category, source or type; its note sits under it. The date shows under it on goal and investment lines, and as a pill on the highlighted one.",
  "<b>A USD receipt stays in dollars:</b> no pesos equivalent on the receipt; the investment’s own screens show the converted total as before.",
  "<b>Leaving</b> with typed lines asks <b>¿Descartar 3 renglones?</b>; a restored draft opens exactly as it was left.",
  "<b>Drawn at 412 × 914 dp</b>, CI’s emulator size, instead of the 360 × 780 other v2 pages use: the receipt needs the height.",
];

function render() {
  const o = reviewOptions(["ink", "theme"]);
  const set = (id, h) => document.getElementById(id).innerHTML = h;
  const s = (k, name) => phone(o, sheet(k, SC[k][name]));
  const E = SC.expense;

  set("entries",
    card("From single entry", "The Anotar sheet, Gasto or Ingreso, gets a <b>Varios</b> pill in its bar. What is typed so far becomes line one.", phone(o, anotar())) +
    card("…arrives with line one filled", "The expense typed in Anotar is line one; a second empty line is highlighted and copies its category.", s("expense", "fromEntry")) +
    card("From a category", "<b>Anotar varios</b> at the foot of the category’s latest expenses. Every new line starts in that category.", phone(o, categoryDetail())) +
    card("…arrives with the category chosen", "Line one is empty with Transporte already chosen.", s("expense", "fromCategory")) +
    card("From a goal", "<b>Aportar varios</b> beside the goal’s movements. Opens section 4’s first card.", phone(o, goalDetail(false))) +
    card("From an investment", "<b>Varios movimientos</b> at the foot of the investment’s movements. Opens section 5’s first card.", phone(o, investDetail(false))) +
    card("From the income list", "<b>Anotar varios</b> under “Lo que entró”. Opens section 3’s first card.", phone(o, incomeList(false))));

  const common = (k, names) => names.map(([n, t, d]) => card(t, d, s(k, n))).join("");
  set("expense", common("expense", [
    ["opening", "Opening", "One empty line, already highlighted; the pad is live. No category chosen, so the action waits. “Otro renglón” waits for an amount."],
    ["three", "Three lines and the total", "The highlighted line has a dark edge; the chips and the note apply to it. The total sits at the foot: 18.400 + 75.100 + 32.500 = 126.000. The button counts the lines."],
    ["missing", "A line without a category", "Amount but no category: the line is outlined in loss and says what is missing, the header repeats it and the action waits."],
    ["swipe", "Removing a line", "Swipe left: past 40 % it reads “Suelta para quitar”. A long press offers the same in a menu."],
    ["removed", "…removed", "The draft keeps the other lines and the total follows. The undo is for the draft only."],
    ["limit", "The 30-line limit", "The lines scroll inside the receipt, the foot stays. The header becomes the message (as entry’s 12 digits) and “Otro renglón” waits."],
    ["note", "Writing a note", "The composer, docked to the keyboard, says which line the note belongs to."],
    ["busy", "Saving", "Spinner inside the button only. Everything is locked so the receipt cannot be sent twice."],
    ["rejected", "Not saved", "A definite failure: nothing was written, the draft stays and Anotar can be tried again."],
    ["refused", "Not saved · a line refused", "If the app refuses one line, nothing is written; the line says why and the action waits until it is fixed."],
    ["unknown", "Outcome unknown", "The only action checks the same operation; no second write."],
  ]));
  set("income",
    common("income", [
      ["opening", "Opening", "As expenses, with the source chips."],
      ["three", "Three lines and the total", "Gain green with a +. The highlighted line is Otro: its name has its own row, then the note. 450.000 + 120.000 + 850.000 = 1.420.000."],
      ["missing", "A line without a source", "Outlined in loss; the header and the action say the same."],
      ["swipe", "Removing a line", "As expenses."],
      ["limit", "The 30-line limit", "30 lines of 50.000 = 1.500.000."],
      ["otro", "Otro asks for its name", "Choosing Otro opens the note composer for that line’s source name."],
      ["busy", "Saving", "As expenses."],
      ["rejected", "Not saved", "The draft stays."],
      ["unknown", "Outcome unknown", "Comprobar si se guardó, nothing else."],
    ]) +
    card("Switching with lines typed", "On the expense receipt: the switch asks before it changes the whole receipt.", s("expense", "switching")) +
    card("…after switching", "The amounts and notes stayed; every line now needs a source. Nothing was repaired silently.", s("income", "switched")));
  set("goal", common("goal", [
    ["opening", "Opening", "One empty line, no type chosen yet. The pad is live; its date pill is today."],
    ["three", "Three lines and the total", "Aportes in gain green, a retiro neutral. +100.000 + 80.000 − 50.000 = +130.000. A new line copies the type and date above."],
    ["missing", "A line without a type", "80.000 without Aportar or Retirar: outlined in loss. It stays out of the total until it has a type."],
    ["swipe", "Removing a line", "As expenses."],
    ["limit", "The 30-line limit", "30 aportes of 20.000 = 600.000."],
    ["busy", "Saving", "As expenses."],
    ["rejected", "Not saved", "The draft stays."],
    ["refused", "Not saved · a retiro refused", "A retiro cannot take more than the goal has: the line says what is available (1.710.000 + 100.000 + 80.000 = 1.890.000)."],
    ["unknown", "Outcome unknown", "Comprobar si se guardó."],
  ]));
  set("invest",
    common("invest", [
      ["opening", "Opening", "The four types of the single movement, one row; no type chosen yet."],
      ["three", "Three lines and the foot", "An aporte, a dividend and today’s value, each with its date. Aportado: only the aporte, 500.000."],
      ["missing", "A line without a type", "Outlined in loss; the action waits."],
      ["swipe", "Removing a line", "As expenses."],
      ["limit", "The 30-line limit", "30 aportes of 100.000 = 3.000.000: a year of history in one go."],
      ["busy", "Saving", "As expenses."],
      ["rejected", "Not saved", "The draft stays."],
      ["refused", "Not saved · a retiro refused", "A retiro is limited to the last recorded value; here that is today’s Valor de hoy on the same receipt, 3.600.000."],
      ["unknown", "Outcome unknown", "Comprobar si se guardó."],
    ]) +
    card("USD · opening", "A USD investment’s lines are in dollars, with cents. The 000 key becomes the decimal comma, as in the single movement.", s("usd", "opening")) +
    card("USD · three lines", "250,50 + 300,00 = 550,50 aportado; the dividend of 12,40 stays out of it.", s("usd", "three")) +
    card("USD · a line without a type", "Same rule, in dollars.", s("usd", "missing")));
  set("saved",
    card("Expenses · Recibo", "“3 gastos anotados: 126.000”; Comida, Transporte and Salud already show what is left.", phone(o, recibo())) +
    card("Incomes · Ingresos", "“3 ingresos anotados: 1.420.000”; the three are in the list and the month’s total is 2.000.000 + 1.420.000 = 3.420.000.", phone(o, withToast(incomeList(true), "3 ingresos anotados: 1.420.000"))) +
    card("Goal · detail", "“3 movimientos anotados: +130.000”; 1.710.000 + 130.000 = 1.840.000, 61 %. A stamp earned by the receipt would celebrate here, once.", phone(o, withToast(goalDetail(true), "3 movimientos anotados: +130.000"))) +
    card("Investment · detail", "“3 movimientos anotados en Ecopetrol”; the list and the value are updated.", phone(o, withToast(investDetail(true), "3 movimientos anotados en Ecopetrol"))));
  set("more",
    card("Leaving with lines", "✕ with anything typed asks first; a restored draft opens as it was left.", s("expense", "discard")) +
    card("Loading", "While the chips load the sheet stays put; the draft is never lost.", s("expense", "loading")) +
    card("Failed read", "Never an empty set of chips: it says so and offers Reintentar; the lines stay.", s("expense", "failedRead")));
  set("open", OPEN.map(t => `<li>${t}</li>`).join(""));

  // Frames of 30 lines open scrolled to their last line.
  document.querySelectorAll(".rc-lines[data-end]").forEach(el => { el.scrollTop = el.scrollHeight; });
}
document.querySelectorAll(".controls select").forEach(sel => sel.addEventListener("change", render));
render();
