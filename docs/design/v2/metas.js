// Sello v2 · Metas tab (locked 2026-10-15): the v1.1 design in its own tab. Synthetic goals (COP, no symbol).
// Kept from v1.1: the saved total in the band, a pinked stamp card per goal with the saved
// amount large, a green progress bar and four stamp places (25/50/75 % and Meta), and
// completed goals as rows with their Lograda stamp.
// Changed on purpose: its own tab (no Patrimonio switch), no dates under the stamps (no
// projected months), no peso symbol, no Aportar on the cards, and a Metas dock:
// "Aportar a una meta" in green replaces Anotar on this tab only (owner decision).
// 2026-10-26 (owner decision): "+ Nueva meta" on the band replaces the top bar's bare +; later
// that day it moved beside the title, the gear left this tab and the total became the hero card.

// Highest completion first (owner decision).
const GOALS = [
  { name: "Viaje a la costa", icon: "flight", saved: 1710000, target: 3000000, pct: 57, date: "dic 2026" },
  { name: "Celular nuevo", icon: "phone_iphone", saved: 450000, target: 1200000, pct: 37 },
  { name: "Curso de inglés", icon: "school", saved: 300000, target: 2000000, pct: 15, date: "jun 2027" },
];
const DONE = [{ name: "Vacunas de Luna", icon: "pets", target: 400000, when: "30 sep" }];
const SAVED = 2460000;      // active goals: 1.710.000 + 450.000 + 300.000
const THIS_MONTH = 350000;  // contributions recorded in October (synthetic)

const hero = () => `<section class="slip pinked"><div class="answer-head"><span class="label">Guardado en metas</span></div><div class="hero">${money(SAVED)}</div><p class="perf gain" style="font-size:.95em">${icon("add")}<span>${fmt(THIS_MONTH)} en octubre</span></p></section>`;

function goalCard(g) {
  const places = [25, 50, 75, 100].map(p => {
    const reached = g.pct >= p;
    return `<span class="v1-place ${reached ? "done" : ""}" aria-label="${p === 100 ? "Meta" : `${p} %`}${reached ? ", logrado" : ""}">${p === 100 ? "Meta" : `${p} %`}</span>`;
  }).join("");
  return `<section class="slip pinked goal-v1"><button class="goal-card" aria-label="${g.name}: ${fmt(g.saved)} de ${fmt(g.target)}, ${g.pct} por ciento. Ver meta"><span class="v1-head">${icon(g.icon)}<b>${g.name}</b><span class="v1-pct">${g.pct} %</span></span><span class="v1-saved">${money(g.saved)}</span><span class="v1-meta"><span>de ${fmt(g.target)}</span><span>${g.date ? `${icon("flag")}${g.date}` : "Sin fecha"}</span></span><span class="v1-track" aria-hidden="true"><i style="width:${g.pct}%"></i></span><span class="v1-places">${places}</span></button></section>`;
}

const done = () => DONE.map(g => `<section class="slip pinked v1-done"><button class="goal-card">${icon(g.icon)}<span><b>${g.name}</b><small>${fmt(g.target)}, ${g.when}</small></span><span class="stamp">Lograda</span></button></section>`).join("");

function body(o, { state } = {}) {
  if (state === "empty") return `<section class="slip pinked empty">${icon("flag")}<h2>Ponle nombre a algo que quieras</h2><p>Un viaje, un fondo para imprevistos, algo para ti. Tú decides la meta y cuánto aportar.</p><span class="v1-places" style="margin:0 0 6px">${["25 %", "50 %", "75 %", "Meta"].map(t => `<span class="v1-place">${t}</span>`).join("")}</span></section>`;
  if (state === "loading") return `<div role="status" aria-label="Cargando"><section class="slip"><div class="skel" style="width:50%"></div><div class="skel big"></div><div class="skel" style="width:70%"></div></section><section class="slip"><div class="skel" style="width:50%"></div><div class="skel"></div></section></div>`;
  if (state === "failed") return `<section class="slip empty">${icon("error")}<h2>No pudimos leer tus metas</h2><p>Tus registros siguen guardados. Vuelve a intentarlo.</p><button class="outline-btn">Reintentar</button></section>`;
  return hero() + GOALS.map(goalCard).join("") + done();
}

// With no goals the dock creates one: there is nothing to contribute to yet.
const frame = (o, html, { tall = false, withBand = true, dock = "Aportar a una meta" } = {}) => `<div class="phone ${tall ? "tall" : ""}"><div class="app" data-ink="${o.ink}" data-theme="${o.theme}">${appShell({ title: "Metas", body: html, tabs: 3, active: 2, chip: withBand ? "Nueva meta" : null, settings: false, dock, dockClass: "goal" })}</div></div>`;
const card = (title, note, inner) => `<div class="frame-card"><h3>${title}</h3><p>${note}</p>${inner}</div>`;

function render() {
  const o = reviewOptions(["ink", "theme"]);
  const set = (id, h) => document.getElementById(id).innerHTML = h;
  set("lists",
    card("Metas tab", "Highest completion first. Tap a goal to open it. The total is the hero card. The dock is “Aportar a una meta” on this tab; “+ Nueva meta” beside the title creates a goal. No gear: settings is on Recibo.", frame(o, body(o))) +
    card("Whole screen, scrolled", "Completed goals stay at the bottom as rows with their Lograda stamp.", frame(o, body(o), { tall: true })));
  set("states",
    card("No goals yet", "v1.1’s empty state: the message and four empty stamp places. The dock becomes Crear una meta, the one main action; no button in the card, no hero card and no Nueva meta.", frame(o, body(o, { state: "empty" }), { withBand: false, dock: "Crear una meta" })) +
    card("Loading", "Skeleton slips.", frame(o, body(o, { state: "loading" }), { withBand: false })) +
    card("Failed read", "Never shown as having no goals.", frame(o, body(o, { state: "failed" }), { withBand: false })));
}
document.querySelectorAll(".controls select").forEach(s => s.addEventListener("change", render));
render();
