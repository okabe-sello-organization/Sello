// Sello v2 · Goal celebrations (locked 2026-10-16). Synthetic data (COP, no symbol).
// Owner decisions from round 1: S1 when the aporte starts from the goal, S2 from Metas (and
// Recibo, where no stamps are visible); reaching 100 % is announced and the person marks the
// goal as lograda to get the big moment (this reverses automatic completion).
// Stamp: Viaje a la costa had 1.360.000 (45 %); the 350.000 aporte of vie 2 oct takes it to
// 1.710.000 (57 %), past the 50 % stamp (1.500.000). 3.000.000 − 1.710.000 = 1.290.000 left.
// Two at once: Curso de inglés 300.000 (15 %) + 800.000 = 1.100.000 (55 %) of 2.000.000,
// past 25 % (500.000) and 50 % (1.000.000); 900.000 left.
// Back to a kept stamp: after the 400.000 withdrawal Viaje has 1.310.000 (43 %, 50 % kept);
// + 250.000 = 1.560.000 (52 %); 1.440.000 left; 75 % (2.250.000) is 690.000 away.
// 100 %: Celular nuevo had 1.050.000 (87 %); + 150.000 = 1.200.000 of 1.200.000.
// Metas band then: 1.200.000 + 1.710.000 + 300.000 = 3.210.000; October 350.000 + 150.000 = 500.000.
// Once Celular nuevo is lograda the band counts active goals only: 1.710.000 + 300.000 = 2.010.000.
// Big moment: Vacunas de Luna, 400.000 in 4 aportes, marked lograda on mar 30 sep 2026.

const VIAJE = { name: "Viaje a la costa", icon: "flight", saved: 1710000, target: 3000000, pct: 57, date: "dic 2026" };
const CELULAR = { name: "Celular nuevo", icon: "phone_iphone", saved: 1200000, target: 1200000, pct: 100 };
const CURSO = { name: "Curso de inglés", icon: "school", saved: 300000, target: 2000000, pct: 15, date: "jun 2027" };
const LUNA = { name: "Vacunas de Luna", icon: "pets", target: 400000, aportes: 4, when: "30 sep 2026" };
const LONG = { name: "Cuota inicial casa propia con jardín", icon: "home", target: 1250000000, aportes: 38, when: "12 ago 2029" };

const label = p => p === 100 ? "Meta" : `${p} %`;
const place = (p, cls = "", d = 0) => `<span class="v1-place ${cls}" style="--d:${d}ms" aria-label="${label(p)}${cls.includes("done") ? ", logrado" : cls.includes("ready") ? ", listo para sellar" : ""}">${label(p)}</span>`;
// A goal at 100 % that is not marked yet: the Meta place waits ("ready").
const placeFor = (g, p, landing = [], kept = []) => g.pct >= p && p === 100 ? place(p, "ready") : g.pct >= p ? place(p, landing.includes(p) ? `done land ${g.relight ? "from-kept" : ""}` : "done", 150) : kept.includes(p) ? place(p, "kept") : place(p);
const markButton = cls => `<button class="primary goal ${cls}">${icon("check")}Marcar como lograda</button>`;

// Goal detail main slip; `landing` stamps play the landing motion in place (S1).
function detailHero(g, { landing = [], kept = [] } = {}) {
  const places = [25, 50, 75, 100].map(p => placeFor(g, p, landing, kept)).join("");
  const earned = [25, 50, 75].filter(p => g.pct >= p || kept.includes(p)).length;
  const next = [25, 50, 75, 100].find(p => g.pct < p && !kept.includes(p));
  const foot = g.pct >= 100
    ? `<p class="gd-next">${icon("flag")}<span><b>Llegaste al 100 %.</b> Márcala como lograda para ganar el último sello.</span></p>${markButton("gd-mark")}`
    : `<p class="gd-next">${icon("flag")}<span>Próximo sello: <b>${label(next)}</b>, faltan <b>${fmt(g.target * next / 100 - g.saved)}</b></span></p>`;
  return `<section class="slip pinked"><div class="gd-head"><span class="label">Ya guardaste</span><b>${g.pct} %</b></div><div class="gd-saved">${money(g.saved)}</div><div class="gd-sub"><span>de ${fmt(g.target)}</span><span>${g.pct >= 100 ? "" : `faltan ${fmt(g.target - g.saved)}`}</span></div><span class="v1-track" aria-hidden="true"><i style="width:${Math.min(g.pct, 100)}%"></i></span><div class="tear" aria-hidden="true"></div><div class="gd-stamps-head"><b>Sellos</b><span class="caption">${earned} de 4</span></div><span class="v1-places">${places}</span>${foot}</section>`;
}
const facts = (target, date = "dic 2026", since = "may 2026") => `<section class="slip"><div class="leader"><span>Meta</span><i class="dots"></i><b>${money(target)}</b></div><div class="leader"><span>Fecha objetivo</span><i class="dots"></i><b>${date}</b></div><div class="leader"><span>Desde</span><i class="dots"></i><b>${since}</b></div></section>`;

// S2: the save toast becomes a paper ticket; the earned stamp lands on it.
function ticket(stamps, title, sub, { kept = false, ready = false } = {}) {
  const marks = stamps.map((p, i) => place(p, ready ? "ready" : `done land ${kept ? "from-kept" : ""}`, 260 + i * 160)).join("");
  return `<div class="ticket-wrap"><div class="stamp-ticket" role="status">${stamps.length > 1 ? `<span class="pair">${marks}</span>` : marks}<span><b>${title.replace(/ %/g, " %")}</b><small>${sub}</small></span></div></div>`;
}

// Metas tab (locked design). A goal at 100 % gets Marcar como lograda under its card.
function metasCard(g) {
  const places = [25, 50, 75, 100].map(p => placeFor(g, p)).join("");
  return `<section class="slip pinked goal-v1"><button class="goal-card"><span class="v1-head">${icon(g.icon)}<b>${g.name}</b><span class="v1-pct">${g.pct} %</span></span><span class="v1-saved">${money(g.saved)}</span><span class="v1-meta"><span>de ${fmt(g.target)}</span><span>${g.date ? `${icon("flag")}${g.date}` : "Sin fecha"}</span></span><span class="v1-track" aria-hidden="true"><i style="width:${g.pct}%"></i></span><span class="v1-places">${places}</span></button>${g.pct >= 100 ? markButton("") : ""}</section>`;
}
const metasBand = (saved, month) => `<div class="goals-band"><span>Guardado en metas</span><b>${money(saved)}</b><small>${icon("add")}${fmt(month)} en octubre</small></div>`;

// Paper chads: punched holes, receipt teeth and strips, in colours that read on green.
function confetti(n = 64) {
  let seed = 7;
  const r = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  const colors = ["#fff", "var(--warn)", "var(--brandsoft)", "#fff", "var(--brand)"];
  return `<div class="confetti" aria-hidden="true">${Array.from({ length: n }, (_, i) => {
    const kind = ["hole", "tooth", "strip"][i % 3];
    const [w, h] = kind === "strip" ? [7, 18] : kind === "tooth" ? [16, 9] : [12, 12];
    return `<i class="chad ${kind}" style="left:${(r() * 100).toFixed(1)}%;width:${w}px;height:${h}px;--c:${colors[i % colors.length]};--t:${(2.6 + r() * 1.6).toFixed(2)}s;--dl:${(2.0 + r() * 0.9).toFixed(2)}s;--x:${Math.round(r() * 80 - 40)}px;--r:${Math.round(r() * 720 - 360)}deg"></i>`;
  }).join("")}</div>`;
}

const sizeClass = n => { const d = String(n).length; return d > 9 ? "long" : d > 6 ? "mid" : ""; };

// The big moment. Timeline: stamps land 0.3 / 0.65 / 1.0 / 1.4 s on the desk (one haptic each,
// a stronger one on Meta); 1.75 s green floods out of the Meta stamp; 2.05 s the stamps rise;
// 2.4 s ¡Lo lograste!; 2.75 s the plate is stamped (haptic); chads from 2.0 s; buttons 3.1 s.
function bigMoment(g) {
  const places = [25, 50, 75, 100].map((p, i) => place(p, "done land", [300, 650, 1000, 1400][i])).join("");
  return `<div class="celebrate fin" role="dialog" aria-modal="true" aria-label="Meta lograda: ${g.name}, ${spoken(g.target)}"><div class="flood" aria-hidden="true"></div>${statusbar()}${confetti()}<div class="fin-body"><span class="v1-places fin-stamps">${places}</span><p class="fin-title" role="heading" aria-level="2">¡Lo lograste!</p><div class="plate"><div class="plate-in"><span class="pl-top">Meta lograda</span><span class="pl-name">${icon(g.icon)}<span>${g.name}</span></span><span class="pl-amount ${sizeClass(g.target)}">${money(g.target)}</span><span class="pl-foot">${g.when} · ${g.aportes} aportes</span></div></div></div><div class="final-actions"><button class="primary">Listo</button><button class="text-btn">Ver mi meta${icon("chevron_right")}</button></div></div>`;
}

// Revisit: a completed goal opens on its slip; LOGRADA lands once per visit.
function lograda() {
  const places = [25, 50, 75, 100].map(p => place(p, "done")).join("");
  return `<section class="slip pinked lograda-hero"><div class="gd-head"><span class="label">Guardaste</span><b>100 %</b></div><div class="final-amount">${money(LUNA.target)}</div><span class="stamp big">Lograda</span><div class="gd-sub"><span>de ${fmt(LUNA.target)}</span><span></span></div><span class="v1-track" aria-hidden="true"><i style="width:100%"></i></span><div class="tear" aria-hidden="true"></div><div class="gd-stamps-head"><b>Sellos</b><span class="caption">4 de 4</span></div><span class="v1-places">${places}</span><p class="gd-next">${icon("check")}<span>Meta lograda el <b>mar 30 sep</b>. Lo que guardaste sigue aquí.</span></p></section><section class="slip"><div class="leader"><span>Aportes</span><i class="dots"></i><b>4</b></div><div class="leader"><span>Desde</span><i class="dots"></i><b>jul 2026</b></div></section>`;
}

const app = (o, inner, cls = "") => `<div class="phone"><div class="app ${o.motion === "reduced" ? "reduced" : ""} ${cls}" data-ink="${o.ink}" data-theme="${o.theme}">${inner}</div></div>`;
const pin = `<div class="band-pin"><button class="pin-band on" aria-pressed="true">${icon("star")}Destacada en Recibo</button></div>`;
// A completed goal is read-only: no edit action and no Aportar dock.
const detail = (o, body, extra = "", { title = VIAJE.name, dock = "Aportar a esta meta", band = pin, done = false } = {}) => app(o, subShell({ title, body, active: 2, dock, dockClass: "goal", action: done ? undefined : ["edit_note", "Editar meta"], band }) + extra);
const metasTab = (o, body, band, extra = "") => app(o, appShell({ title: "Metas", addLabel: "Crear una meta", body, tabs: 3, active: 2, band, dock: "Aportar a una meta", dockClass: "goal" }) + extra);
const toast = text => `<div class="toast" role="status">${icon("check")}${text}</div>`;
// After Listo: the goal is lograda, with a short Deshacer. About 10 s (longer with TalkBack);
// once it is gone the goal is final. Deshacer only returns the goal to "100 %, waiting".
const undoToast = text => `<div class="toast" role="status">${icon("check")}<span>${text}</span><button class="undo">Deshacer</button></div>`;
const logradaRow = (g, when) => `<section class="slip pinked v1-done"><button class="goal-card">${icon(g.icon)}<span><b>${g.name}</b><small>${fmt(g.target)}, ${when}</small></span><span class="stamp">Lograda</span></button></section>`;
const card = (title, note, inner) => `<div class="frame-card"><h3>${title}</h3><p>${note}</p>${inner}</div>`;

function render() {
  const o = reviewOptions(["ink", "theme", "motion"]);
  const reduced = { ...o, motion: "reduced" };
  const set = (id, h) => document.getElementById(id).innerHTML = h;
  set("small",
    card("S1 · Aportar from the goal", "Back on the goal, the 50 % stamp lands in its place with an ink ring; the save toast adds one line.", detail(o, detailHero(VIAJE, { landing: [50] }) + facts(3000000), toast("Aporte guardado. Ganaste el sello del 50 %."))) +
    card("S2 · Aportar from Metas", "Back on Metas, the save toast becomes a paper ticket and the stamp lands on it. Recibo uses the same ticket: no stamps are visible there.", metasTab(o, metasCard(VIAJE) + metasCard({ ...CELULAR, saved: 450000, pct: 37 }), metasBand(2460000, 350000), ticket([50], "Ganaste el sello del 50 %", `Viaje a la costa · faltan ${fmt(1290000)} para la meta`))) +
    card("S2 · Two stamps in one aporte", "One ticket; both stamps land one after the other.", metasTab(o, metasCard(VIAJE) + metasCard({ ...CURSO, saved: 1100000, pct: 55 }), metasBand(3260000, 1150000), ticket([25, 50], "Ganaste los sellos del 25 % y del 50 %", `Curso de inglés · faltan ${fmt(900000)} para la meta`))) +
    card("S1 · Back above a kept stamp", "After a withdrawal the 50 % stamp stayed, faded (decision K). Reaching 50 % again lights it back up in place.", detail(o, detailHero({ ...VIAJE, saved: 1560000, pct: 52, relight: true }, { landing: [50] }) + facts(3000000), toast("Aporte guardado. De vuelta en el 50 %."))));
  set("reach",
    card("100 % from the goal", "The Meta place waits in green. One line says what is left to do, and Marcar como lograda sits in the main slip. Aportar stays available.", detail(o, detailHero(CELULAR) + facts(1200000, "Sin fecha", "mar 2026"), toast("Aporte guardado. Llegaste al 100 %."), { title: CELULAR.name, band: "" })) +
    card("100 % from Metas", "The ticket announces it with the waiting Meta stamp. The card goes first (100 %) and carries Marcar como lograda.", metasTab(o, metasCard(CELULAR) + metasCard(VIAJE), metasBand(3210000, 500000), ticket([100], "Llegaste al 100 %", "Celular nuevo · márcala como lograda cuando quieras", { ready: true }))) +
    card("On Recibo", "Already locked on Home: a green row under the goal pill with Marcar como lograda.", `<p style="font-size:13px;color:#434e72;margin:0">See <a class="frame-link" href="home.html">home.html</a>, “Another goal reached 100 %”.</p>`));
  set("big",
    card("1 · The stamps", "Starts on the desk: the four stamps land one by one in the middle of the screen, Meta last (frozen here before the flood).", app(o, bigMoment(LUNA), "phase1")) +
    card("2 · Lograda (live)", "Green floods out of the Meta stamp, the stamps rise, ¡Lo lograste! and the plate is stamped; paper chads fall. Use Replay.", app(o, bigMoment(LUNA))) +
    card("Long name and long amount", "The plate is a rectangle, not a circle: the name wraps to two lines and the amount steps down like the entry amount.", app(reduced, bigMoment(LONG))) +
    card("After Listo: Deshacer", "Back where Marcar como lograda was tapped. The goal is now a Lograda row; the toast offers Deshacer for about 10 seconds, then the goal is final.", metasTab(o, metasCard(VIAJE) + metasCard(CURSO) + logradaRow(CELULAR, "16 oct") + logradaRow(LUNA, "30 sep"), metasBand(2010000, 500000), undoToast("Celular nuevo, meta lograda."))) +
    card("Reduced motion", "No landing, flood or chads: the final screen appears with a short fade. TalkBack reads “Meta lograda: Vacunas de Luna, 400.000 pesos”.", app(reduced, bigMoment(LUNA))));
  set("revisit",
    card("Opening a completed goal", "The goal opens on its slip and LOGRADA lands once per visit. In Metas, the Lograda stamps on the rows land as they scroll into view.", detail(o, lograda(), "", { title: LUNA.name, dock: "", band: "", done: true })));
}
document.querySelectorAll(".controls select").forEach(s => s.addEventListener("change", render));
document.getElementById("replay").addEventListener("click", render);
render();
