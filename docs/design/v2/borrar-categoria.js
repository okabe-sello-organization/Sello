// Sello v2 · Borrar categoría (approved by the owner 2026-11-02, SELLO-138, ADR 0024).
// Synthetic fixture on home.js's default scene, jue 22 oct 2026:
// Café: limit 50.000, 60.000 spent in October; 34 expenses for 412.000 (may–oct 2026); 1 fixed payment.
// Comida: limit 200.000, 80.000 spent in October. Moving Café's October 60.000: 80.000 + 60.000 = 140.000.
// After the move Recibo reads 1.000.000 − 50.000 = 950.000 limits − 400.000 spent − 200.000 reserved = 350.000.
// `archive` is a mock-only shape until the design system bundles Material's archive icon.

document.body.insertAdjacentHTML("afterbegin", `<svg width="0" height="0" style="position:absolute" aria-hidden="true"><symbol id="i-archive" viewBox="0 0 24 24"><path fill="currentColor" d="M4 3h16a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Zm0 6.5h16V19a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V9.5Zm5.5 3a1 1 0 0 0 0 2h5a1 1 0 0 0 0-2h-5Z"/></symbol></svg>`);

const phone = (o, inner) => `<div class="phone"><div class="app" data-ink="${o.ink}" data-theme="${o.theme}">${inner}</div></div>`;
const card = (title, note, inner) => `<div class="frame-card"><h3>${title}</h3><p>${note}</p>${inner}</div>`;
const padKeys = () => `<div class="pad" role="group" aria-label="Teclado de Sello">${["1", "2", "3", "4", "5", "6", "7", "8", "9", "000", "0"].map(k => `<button class="${k === "000" ? "k000" : ""}">${k}</button>`).join("")}<button class="del" aria-label="Borrar">${icon("backspace")}</button></div>`;

// The bar holds only ⋮: Archivar categoría in ink, Borrar categoría in red under it.
const menu = (archived, open) => {
  const items = archived ? [["delete", "Borrar categoría", true]] : [["archive", "Archivar categoría"], ["delete", "Borrar categoría", true]];
  return `<span class="more-wrap"><button class="icon-btn" aria-label="Más opciones" aria-expanded="${open}">${icon("more_vert")}</button>${open ? `<span class="more-pop" role="menu">${items.map(([ic, t, loss]) => `<button role="menuitem" ${loss ? "" : 'style="color:var(--ink)"'}>${icon(ic)}${t}</button>`).join("")}</span>` : ""}</span>`;
};

function editor({ archived = false, open = false, slip, sheet, typing } = {}) {
  const tint = archived ? "var(--outline)" : "var(--brand)";
  const id = `<section class="slip form-slip"><div class="ident" style="align-items:center"><button class="pick" style="color:${tint}"><span class="ident-icon" style="background:${tint}">${icon("local_cafe")}</span>Cambiar</button><div><label class="form-label">Nombre</label><div class="form-field">Café</div></div></div><div class="form-msg"><span></span><span>4/24</span></div></section>`;
  const arch = archived ? `<section class="slip"><div class="answer-head"><span class="label">Archivada</span><span class="stamp neutral">Archivada</span></div><p class="answer-note">No acepta gastos nuevos; su historia sigue aquí.</p><button class="outline-btn" style="margin-top:10px;width:100%">${icon("archive")}Desarchivar</button></section>` : "";
  const lim = `<section class="slip pinked" ${archived ? 'style="opacity:.55"' : ""}><div class="plan-label"><span class="label">Límite mensual</span><button class="chip" aria-pressed="false">Sin límite</button></div><p class="plan-amount">${money(50000)}</p><p class="caption" style="margin:4px 0 0">${archived ? "Está archivada: no tiene límite de aquí en adelante. Desarchívala para ponerle uno." : "Vale desde octubre en adelante. Los meses anteriores no cambian."}</p></section>`;
  let h = `<div class="entry-top">${statusbar()}<div class="entry-bar"><button class="icon-btn" aria-label="Volver">${icon("arrow_back")}</button><b style="flex:1;font-size:1.2em;margin-left:4px">Editar categoría</b>${menu(archived, open)}</div></div>`;
  h += `<div class="entry-mid" style="padding-top:2px">${id}${arch}${lim}</div>`;
  h += `<div class="entry-action"><button class="primary" disabled>Guardar cambios</button></div>${padKeys()}<div class="gesture" style="background:var(--desk)"></div>`;
  if (slip) h += `<div class="scrim center">${slip}</div>`;
  if (sheet) h += `<div class="scrim">${sheet}</div>`;
  if (typing) h += `<div class="scrim">${typing}<div class="ime" aria-hidden="true" style="height:200px;background:var(--dim);display:grid;place-items:center;color:var(--soft);font-size:.85em">teclado</div></div>`;
  return h;
}

const actions = (go, disabled = false) => `<div class="confirm-actions"><button class="ghost">Cancelar</button><button class="del" ${disabled ? "disabled" : ""}>${go}</button></div>`;
const ba = rows => `<div class="ba">${rows.map(([l, v, same]) => `<div class="${same ? "same" : ""}"><span>${l}</span><b>${v}</b></div>`).join("")}</div>`;

const EMPTY = `<div class="confirm" role="alertdialog" aria-label="Borrar Café"><h2>¿Borrar Café?</h2><p>No tiene gastos ni pagos fijos. Se borran la categoría y su límite. <b>Esto no se puede deshacer.</b></p><div class="tear" aria-hidden="true"></div>${actions("Borrar")}</div>`;

const choice = (ic, title, sub, danger) => `<button class="fx-row choice ${danger ? "danger" : ""}"><span class="fx-ic">${icon(ic)}</span><span class="fx-text"><b>${title}</b><small>${sub}</small></span>${icon("chevron_right", "chev")}</button>`;
const CHOOSE = `<div class="confirm list" role="alertdialog" aria-label="Borrar Café"><div class="scroll"><h2>¿Borrar Café?</h2><p>Café tiene <b>34 gastos</b> por <b>412.000</b>, de mayo a octubre de 2026, y <b>1 pago fijo</b>. ¿Qué hacemos con ellos?</p><div style="margin-top:10px">${choice("receipt_long", "Moverlos a otra categoría", "Los totales de cada mes no cambian.")}${choice("delete", "Borrar todo", "Desaparecen de todos los meses.", true)}</div><p style="margin-top:10px;font-size:.9em">¿Solo quieres dejar de usarla? <b style="color:var(--brand)">Archívala</b>: no se borra nada.</p></div><div class="tear" aria-hidden="true"></div><div class="confirm-actions" style="grid-template-columns:1fr"><button class="ghost">Cancelar</button></div></div>`;

const CATS = [["Hogar", "home", 200000], ["Comida", "restaurant", 120000], ["Transporte", "directions_bus", 60000], ["Salud", "medical_services", 80000], ["Ocio", "theaters", 80000], ["Ropa", "checkroom", 70000]];
const PICK = `<div class="composer" role="dialog" aria-label="Mover los gastos de Café" style="padding:12px 16px 18px"><div class="composer-head"><b style="font-size:1.15em">Mover los gastos de Café a</b><button class="icon-btn" aria-label="Cerrar">${icon("close")}</button></div><p class="caption" style="margin:-4px 2px 6px">34 gastos y 1 pago fijo. Lo que queda en octubre:</p>${CATS.map(([n, ic, left], i) => `<button class="fx-row ${i === CATS.length - 1 ? "last" : ""}"><span class="fx-ic">${icon(ic)}</span><span class="fx-text"><b>${n}</b></span><b>${money(left)}</b></button>`).join("")}<p class="caption" style="margin:8px 2px 0">Las archivadas no aparecen: no aceptan gastos.</p></div>`;

const MOVE = `<div class="confirm list" role="alertdialog" aria-label="Mover a Comida y borrar Café"><div class="scroll"><h2>¿Mover a Comida y borrar Café?</h2><p>Los <b>34 gastos</b> y su <b>pago fijo</b> pasan a Comida, en sus mismas fechas. Los límites y el arrastre de Café se borran. <b>Esto no se puede deshacer.</b></p>${ba([["Gastado en Comida, octubre", "80.000 → 140.000"], ["Límite de Café, octubre", "50.000, se borra"], ["Límite de Comida", "200.000, no cambia", true]])}</div><div class="tear" aria-hidden="true"></div>${actions("Mover y borrar")}</div>`;

const word = typed => `<label class="form-label" style="display:block;margin-top:12px">Escribe BORRAR para confirmar</label><div class="form-field ${typed ? "" : "focus placeholder"}">${typed ? "BORRAR" : ""}<span class="caret"></span></div>`;
const ALL = typed => `<div class="confirm list" role="alertdialog" aria-label="Borrar Café y todo lo suyo" style="margin:auto 12px 12px"><div class="scroll"><h2>¿Borrar Café y todo lo suyo?</h2><p>Se borran para siempre:</p>${ba([["34 gastos, may – oct 2026", "412.000"], ["Pagos fijos", "1"], ["Sus límites y su arrastre", "Todos los meses"]])}<p style="margin-top:10px">Los meses pasados mostrarán menos gasto. <b>Esto no se puede deshacer.</b></p>${word(typed)}</div><div class="tear" aria-hidden="true"></div>${actions("Borrar todo", !typed)}</div>`;

const GRID = [["Hogar", "home", 400000, 200000], ["Comida", "restaurant", 200000, 140000], ["Transporte", "directions_bus", 100000, 40000], ["Salud", "medical_services", 80000, 0], ["Ocio", "theaters", 100000, 20000], ["Ropa", "checkroom", 70000, 0]];
const recibo = `<section class="slip pinked"><div class="answer-head"><span class="label">Restante del presupuesto</span><span class="stamp">Al día</span></div><div class="hero">${money(350000)}</div><div class="tear" aria-hidden="true"></div><p class="daily-big"><b>${money(35000)}</b><span>por día</span></p><span class="caption">10 días, incluido hoy</span></section><section class="slip"><div class="section-head"><h2>Tus categorías</h2><button class="band-chip paper">${icon("add")}Nueva categoría</button></div><div class="grid">${GRID.map(([n, ic, lim, spent]) => `<button class="cell"><span class="circle" style="--fill:${Math.round(100 * spent / lim)}%">${icon(ic)}<span class="on">${icon(ic)}</span></span><span class="cell-text"><span>${n}</span><b>${money(lim - spent)}</b></span></button>`).join("")}</div></section>`;

// When it did not happen: nothing changed, why, and the one way forward.
const why = (title, body, go) => `<div class="confirm" role="alertdialog" aria-label="${title}"><h2>${title}</h2>${body}<div class="tear" aria-hidden="true"></div><div class="confirm-actions" ${go ? "" : 'style="grid-template-columns:1fr"'}><button class="ghost">Cancelar</button>${go ? `<button class="go">${go}</button>` : ""}</div></div>`;
const P = t => `<p style="margin-top:6px">${t}</p>`;
const NOTHING = `<p><b>No se borró ni se movió nada.</b></p>`;
const CHANGED = why("Café cambió", NOTHING + P("Mientras lo veías, cambió lo que tiene Café: ahora son <b>35 gastos</b> por <b>472.000</b>, no 34 por 412.000.") + P("Revisa las cifras nuevas antes de seguir."), "Revisar");
const TARGET = why("Comida ya no acepta gastos", NOTHING + P("Comida se archivó o se borró mientras elegías. Los gastos de Café no pueden ir allí."), "Elegir otra");
const STORAGE = why("No se pudo guardar", NOTHING + P("El teléfono no pudo guardar el cambio. Café sigue con sus 34 gastos, sus límites y su pago fijo."), "Reintentar");
const COUNT = why("Aún no se puede borrar", P("No pudimos contar lo que tiene Café. Sello no borra nada sin decirte antes exactamente qué se pierde.").replace("margin-top:6px", "margin-top:0"), "Reintentar");
const UNKNOWN = `<div class="confirm" role="alertdialog" aria-label="Comprobando"><h2>Comprobando…</h2><p>Sello no supo si terminó de borrar Café y lo está revisando. <b>No se borrará dos veces.</b></p></div>`;

function render() {
  const o = reviewOptions(["ink", "theme"]);
  document.getElementById("flow").innerHTML =
    card("1 · Where it lives", "Editar categoría's bar has only ⋮: Archivar categoría in ink, Borrar categoría in red. Not on category detail.", phone(o, editor({ open: true }))) +
    card("2 · An archived category", "An Archivada slip with Desarchivar in the form, as a paused fixed payment's Reanudar; the ⋮ keeps only Borrar.", phone(o, editor({ archived: true }))) +
    card("3 · Nothing in it", "No expenses and no fixed payments: one confirm slip.", phone(o, editor({ slip: EMPTY }))) +
    card("4 · It holds expenses: choose", "Counts first, then Mover or Borrar todo, and a pointer to Archivar.", phone(o, editor({ slip: CHOOSE }))) +
    card("5 · Mover → pick a category", "Active categories but Café, with what each has left this month.", phone(o, editor({ sheet: PICK }))) +
    card("6 · Confirm the move", "Exact facts: the spending that moves this month and the limits that go or stay. A carrying target adds the correction list of ADR 0020.", phone(o, editor({ slip: MOVE }))) +
    card("7 · Borrar todo", "Counts what goes; Borrar todo waits for BORRAR, as reset.", phone(o, editor({ typing: ALL(false) }))) +
    card("8 · BORRAR typed", "One all-or-nothing command; a second tap does nothing.", phone(o, editor({ typing: ALL(true) }))) +
    card("9 · Done", "The editor and the detail close onto Recibo, with the notice.", phone(o, appShell({ body: recibo, tabs: 3, active: 0, toast: "Café se borró; sus 34 gastos están en Comida" })));
  document.getElementById("fail").innerHTML =
    card("Café changed", "Changed contents: Revisar shows the new preview.", phone(o, editor({ slip: CHANGED }))) +
    card("The target closed", "Moving only: Elegir otra reopens the list.", phone(o, editor({ slip: TARGET }))) +
    card("Couldn't save", "Says what is still there.", phone(o, editor({ slip: STORAGE }))) +
    card("Couldn't count", "No deletion is offered without exact figures.", phone(o, editor({ slip: COUNT }))) +
    card("Unknown outcome", "Checked with the same operation before anything else.", phone(o, editor({ slip: UNKNOWN })));
}
document.querySelectorAll(".controls select").forEach(s => s.addEventListener("change", render));
render();
