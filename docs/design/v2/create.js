// Sello v2 · Create and edit a goal (locked 2026-10-15; made alike with Nueva categoría by the
// owner 2026-10-26: icon circle opening a sheet, the pad always there, the date in the
// amount slip's corner, Sin fecha on the month sheet's title line, the stamps centred).
// v1.1's "Nueva meta" blended with the first round.
// From v1.1: name and icon as their own section, the amount as the big number, the monthly
// reminder and the four stamps with the months they could arrive (a plan, shown only while
// creating or editing, with its assumption spelled out). From the first round: the date / Sin fecha
// chips, the monthly line and the green Metas action. Amounts use the Sello pad.
// Synthetic data (COP, no symbol). No rounding beyond whole pesos:
// 20.000.000 / 26 months (nov 2026 – dic 2028) = 769.230,77 → 769.231 (26 × 769.231 reaches
// the goal). Stamps at that pace: 25 % (5.000.000) in month 7 = may 2027; 50 % in month 13 =
// nov 2027; 75 % in month 20 = jun 2028; Meta in month 26 = dic 2028.
// Editing Viaje a la costa: 1.290.000 left / 2 months = 645.000; 75 % (2.250.000) in nov 2026.

const ICONS = ["home", "flight", "school", "phone_iphone", "pets", "fitness_center", "child_care", "shopping_cart", "theaters", "medical_services"];
const ICON_NAMES = { home: "Casa", flight: "Viaje", school: "Estudio", phone_iphone: "Tecnología", pets: "Mascota", fitness_center: "Deporte", child_care: "Familia", shopping_cart: "Compras", theaters: "Ocio", medical_services: "Salud" };
const LONG_NAME = "Cuota inicial para la casa propia con jardín y terraza";

const SCENES = {
  empty:  { name: "", icon: null, amount: null, date: null },
  dated:  { name: "Casa propia", icon: "home", amount: 20000000, date: "dic 2028", monthly: [769231, 26], stamps: [["25 %", "may 27"], ["50 %", "nov 27"], ["75 %", "jun 28"], ["Meta", "dic 28"]] },
  amount: { name: "Casa propia", icon: "home", amount: 20000000, date: null, focus: "amount" },
  nodate: { name: "Casa propia", icon: "home", amount: 20000000, date: null },
  long:   { name: LONG_NAME, icon: "home", amount: 20000000, date: null, focus: "name" },
  edit:   { name: "Viaje a la costa", icon: "flight", amount: 3000000, date: "dic 2026", monthly: [645000, 2], edit: true, stamps: [["25 %", null, true], ["50 %", null, true], ["75 %", "nov 26"], ["Meta", "dic 26"]] },
};

// The circle opens the icon sheet: dashed with a centred + while empty, filled with "Cambiar".
const pick = s => s.icon
  ? `<button class="pick" aria-label="Ícono: ${ICON_NAMES[s.icon] ?? s.icon}. Cambiar"><span class="ident-icon">${icon(s.icon)}</span>Cambiar</button>`
  : `<button class="pick" aria-label="Elegir ícono"><span class="ident-icon empty pick-empty">${icon("add")}</span>Elegir ícono</button>`;

function identity(s) {
  const len = [...s.name].length, tooLong = len > 40;
  return `<section class="slip form-slip"><div class="ident" style="align-items:center">${pick(s)}<div><label class="form-label">Nombre</label><div class="form-field ${s.focus === "name" ? "focus" : ""} ${s.name ? "" : "placeholder"} ${tooLong ? "error" : ""}">${s.name || "¿Para qué estás guardando?"}${s.focus === "name" ? `<span class="caret"></span>` : ""}</div></div></div><div class="form-msg">${tooLong ? `<span class="err">Máximo 40 caracteres. Tu texto sigue aquí.</span>` : "<span></span>"}<span class="${tooLong ? "err" : ""}">${len}/40</span></div></section>`;
}

function plan(s) {
  const value = s.amount === null ? `<p class="plan-amount placeholder">0${s.focus === "amount" ? `<span class="caret"></span>` : ""}</p>` : `<p class="plan-amount ${String(s.amount).length > 9 ? "mid" : ""}">${money(s.amount)}${s.focus === "amount" ? `<span class="caret"></span>` : ""}</p>`;
  const monthly = s.monthly ? `<p class="monthly">${icon("flag")}<span>Son <b>${fmt(s.monthly[0])}</b> al mes durante <b>${s.monthly[1]} ${s.monthly[1] === 1 ? "mes" : "meses"}</b>.</span></p>` : "";
  return `<section class="slip pinked"><div class="plan-label"><span class="label">Cuánto necesitas</span><button class="chip" aria-label="Para cuándo: ${s.date ?? "Sin fecha"}. Cambiar">${icon("event")}${s.date ?? "Sin fecha"}</button></div>${value}${monthly}</section>`;
}

function stamps(s) {
  const list = s.stamps ?? [["25 %"], ["50 %"], ["75 %"], ["Meta"]];
  const places = list.map(([t, when, done]) => `<span class="stamp-when"><span class="v1-place ${done ? "done" : ""}">${t}</span>${done ? "logrado" : when ?? "&nbsp;"}</span>`).join("");
  const caption = s.monthly ? `Si aportas <b>${fmt(s.monthly[0])}</b> cada mes` : "Ponle fecha para ver cuándo podrías llegar a cada uno";
  return `<section class="slip stamps-plan" style="text-align:center"><div class="gd-stamps-head" style="justify-content:center"><b>Tus sellos</b></div><p class="caption" style="margin:2px 0 0">${caption}</p><div class="v1-places" style="display:grid;grid-template-columns:repeat(4,1fr);justify-items:center">${places}</div></section>`;
}

const doneAsk = () => `<section class="slip done-ask"><h2>¿Ya la lograste?</h2><p>Si llegaste a tu meta por fuera de Sello, márcala como lograda.</p><button class="outline-btn gain wide">${icon("check")}Marcar como lograda</button></section>`;
const confirmSlip = () => `<div class="scrim center"><div class="confirm" role="alertdialog" aria-label="Borrar meta"><h2>¿Borrar Viaje a la costa?</h2><p>Se borran la meta y sus <b>6 movimientos</b>. Esto no se puede deshacer.</p><div class="tear" aria-hidden="true"></div><div class="confirm-actions"><button class="ghost">Cancelar</button><button class="del">Borrar</button></div></div></div>`;
const pad = () => `<div class="pad" role="group" aria-label="Teclado de Sello">${["1", "2", "3", "4", "5", "6", "7", "8", "9", "000", "0"].map(k => `<button class="${k === "000" ? "k000" : ""}">${k}</button>`).join("")}<button class="del" aria-label="Borrar">${icon("backspace")}</button></div>`;

function form(s, { confirm = false } = {}) {
  const tooLong = [...s.name].length > 40;
  const ok = s.name && !tooLong && s.icon && s.amount;
  const missing = !s.icon ? "Elige un ícono." : !s.name ? "Escribe un nombre." : !s.amount ? "Escribe cuánto necesitas." : "";
  const action = `<div class="entry-action">${missing ? `<p class="caption" style="margin:0 2px 6px">${missing}</p>` : ""}<button class="primary goal" ${ok ? "" : "disabled"}>${icon("flag")}${s.edit ? "Guardar cambios" : "Crear meta"}</button></div>${pad()}`;
  const extra = s.edit ? doneAsk() : "";
  return `<div class="entry-top">${statusbar()}<div class="entry-bar"><button class="icon-btn" aria-label="Cerrar">${icon("close")}</button><b style="flex:1;font-size:1.2em;margin-left:4px">${s.edit ? "Editar meta" : "Nueva meta"}</b>${s.edit ? moreMenu("Borrar meta") : ""}</div></div><div class="entry-mid" style="padding-top:2px">${identity(s)}${plan(s)}${stamps(s)}${extra}</div>${action}<div class="gesture" style="background:var(--desk)"></div>${confirm ? confirmSlip() : ""}`;
}

const phone = (o, inner, tall) => `<div class="phone ${tall ? "tall" : ""}"><div class="app" data-ink="${o.ink}" data-theme="${o.theme}">${inner}</div></div>`;
const card = (title, note, inner) => `<div class="frame-card"><h3>${title}</h3><p>${note}</p>${inner}</div>`;

function render() {
  const o = reviewOptions(["ink", "theme"]);
  const set = (id, h) => document.getElementById(id).innerHTML = h;
  set("top",
    card("Nueva meta", "The icon circle beside the name; the amount is the big number on the pad, with the month in the slip's corner and the monthly line under it; the stamps, centred, show when they could arrive at that pace.", phone(o, form(SCENES.dated), true)) +
    card("Typing the amount", "The Sello pad is always under the form; the keyboard covers it only while the name is typed.", phone(o, form(SCENES.amount))) +
    card("Sin fecha", "The corner says Sin fecha; no monthly line; the stamps have no months and invite setting a date. The month sheet has Sin fecha on its title line.", phone(o, form(SCENES.nodate))) +
    card("Just opened", "A dashed + circle, Elegir ícono; the name with the keyboard up; 0. The line above Crear meta says what is missing first.", phone(o, form(SCENES.empty))));
  set("edit",
    card("Editar meta", "Same screen. Earned stamps show “logrado”; the rest show when they could arrive at the new pace. Then two clear decisions, each in its own card: mark as reached, or delete.", phone(o, form(SCENES.edit), true)) +
    card("Deleting asks once more", "Sello’s confirm slip: what is deleted and how many movements, a tear line, Cancelar and a red Borrar.", phone(o, form(SCENES.edit, { confirm: true }))) +
    card("Name too long", "Text kept, red line and counter, button waits.", phone(o, form(SCENES.long))));
}
document.querySelectorAll(".controls select").forEach(s => s.addEventListener("change", render));
render();
