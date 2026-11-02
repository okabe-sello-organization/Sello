// Sello v2 · Goal detail (locked 2026-10-15). v1.1's "Detalle de meta" without the projected arrival
// date. Synthetic goal: Viaje a la costa, target 3.000.000.
// Contributions May–Oct: 200 + 250 + 260 + 300 + 350 + 350 thousand = 1.710.000 (57 %).
// 25 % (750.000) was crossed in August; 50 % (1.500.000) in October.

const GOAL = { name: "Viaje a la costa", icon: "flight", target: 3000000, date: "dic 2026", since: "may 2026" };
const MONTHS = [["May", 200000], ["Jun", 250000], ["Jul", 260000], ["Ago", 300000], ["Sep", 350000], ["Oct", 350000]];

const SCENES = {
  normal:   { saved: 1710000, pct: 57, reached: [25, 50], movs: [["in", "Aporte", "vie 2 oct", 350000, "50 %", null, 1710000], ["in", "Aporte", "mié 2 sep", 350000, null, null, 1360000], ["in", "Aporte", "dom 2 ago", 300000, "25 %", null, 1010000], ["in", "Aporte", "jue 2 jul", 260000, null, null, 710000]] },
  // A withdrawal of 400.000 on 20 Oct takes the goal from 57 % to 43 %, below the 50 % stamp.
  // Owner decision K: an earned stamp stays (faded) after a withdrawal.
  keep:     { saved: 1310000, pct: 43, reached: [25], kept: [50], withdrawal: true },
  // Vacunas de Luna: target 400.000, four aportes of 100.000; marked lograda on mar 30 sep.
  done:     { saved: 400000, target: 400000, pct: 100, reached: [25, 50, 75, 100], done: true,
              months: [["Jul", 100000], ["Ago", 100000], ["Sep", 200000]], max: 240000,
              movs: [["in", "Aporte", "mié 30 sep", 100000, null, null, 400000], ["in", "Aporte", "mar 15 sep", 100000, "75 %", null, 300000], ["in", "Aporte", "sáb 15 ago", 100000, "50 %", null, 200000], ["in", "Aporte", "mié 15 jul", 100000, "25 %", null, 100000]] },
};
const WITHDRAWAL = ["out", "Retiro", "mar 20 oct", -400000, null, "Arreglo del carro", 1310000];
// The full list (Ver los 7): May and June's aportes too, under a line per month with its net.
const EARLIER = [["in", "Aporte", "mar 2 jun", 250000, null, null, 450000], ["in", "Aporte", "sáb 2 may", 200000, null, null, 200000]];

function hero(s, o = {}) {
  const target = s.target ?? GOAL.target;
  const places = [25, 50, 75, 100].map(p => {
    const cls = s.reached.includes(p) ? "done" : (s.kept || []).includes(p) ? "kept" : "";
    return `<span class="v1-place ${cls}">${p === 100 ? "Meta" : `${p} %`}</span>`;
  }).join("");
  const stamps = s.reached.length + (s.kept || []).length;
  // The next stamp is the first one not yet earned (a kept stamp counts as earned).
  const next = [25, 50, 75, 100].find(p => !s.reached.includes(p) && !(s.kept || []).includes(p));
  const nextLine = s.done
    ? `<p class="gd-next">${icon("check")}<span>Meta lograda el <b>30 sep</b>. Lo que guardaste sigue aquí.</span></p>`
    : `<p class="gd-next">${icon("flag")}<span>Próximo sello: <b>${next === 100 ? "la meta" : `${next} %`}</b>, faltan <b>${fmt(target * next / 100 - s.saved)}</b></span></p>`;
  const keptNote = s.kept ? `<p class="gd-next">${icon("check")}<span>El sello del 50 % sigue siendo tuyo: lo lograste en octubre.</span></p>` : "";
  return `<section class="slip pinked"><div class="gd-head"><span class="label">Ya guardaste</span><b>${s.pct} %</b></div><div class="gd-saved">${money(s.saved)}</div><div class="gd-sub"><span>de ${fmt(target)}</span><span>${s.done ? "" : `faltan ${fmt(target - s.saved)}`}</span></div><span class="v1-track" aria-hidden="true"><i style="width:${s.pct}%"></i></span><div class="tear" aria-hidden="true"></div><div class="gd-stamps-head"><b>Sellos</b><span class="caption">${stamps} de 4</span></div><span class="v1-places">${places}</span>${keptNote}${nextLine}</section>`;
}

// "Show on Recibo": a small star pill in the band, under the goal's name (owner pick Q3).
const bandPill = on => `<div class="band-pin"><button class="pin-band ${on ? "on" : ""}" aria-pressed="${on}">${icon(on ? "star" : "star_outline")}${on ? "En tu Recibo" : "Mostrar en Recibo"}</button></div>`;
const facts = (s, o = {}) => `<section class="slip"><div class="leader"><span>Meta</span><i class="dots"></i><b>${money(GOAL.target)}</b></div><div class="leader"><span>Fecha objetivo</span><i class="dots"></i><b>${GOAL.date}</b></div><div class="leader"><span>Desde</span><i class="dots"></i><b>${GOAL.since}</b></div></section>`;

function chart(months = MONTHS, max = 420000) {
  const cols = months.map(([m, n], i) => `<button class="col ${i === months.length - 1 ? "now" : ""}" aria-label="${m}: ${fmt(n)} pesos">${i === months.length - 1 ? `<span class="v">${Math.round(n / 1000)} mil</span>` : ""}<span class="b" style="height:${100 * n / max}%"></span></button>`).join("");
  return `<section class="slip"><h2 class="q">Aportes por mes</h2><div class="chart" style="height:120px">${cols}</div><div class="axis">${months.map(([m], i) => `<span class="${i === months.length - 1 ? "now" : ""}">${m}</span>`).join("")}</div></section>`;
}

// Milestones in the movement list (owner pick MR2): the contribution that earned a stamp shows
// the stamp in place of its + icon, with "Ganaste el sello del 50 %".
// Owner, 2026-11-02: the amount sits at the end (green for an aporte) with the goal's saldo
// under it, worked out down the list by date (MT40–MT42); a note is the row's title, its kind
// and day under it; the full list (after Ver los N) gets a line per month with its net.
function movements(s, o = {}) {
  const base = s.withdrawal ? [WITHDRAWAL, ...SCENES.normal.movs] : s.movs ?? SCENES.normal.movs;
  const list = o.all ? [...base, ...EARLIER] : base.slice(0, 4);
  const NETS = { oct: -50000, sep: 350000, ago: 300000, jul: 260000, jun: 250000, may: 200000 };
  const NAMES = { oct: "Octubre", sep: "Septiembre", ago: "Agosto", jul: "Julio", jun: "Junio", may: "Mayo" };
  const month = r => r[2].split(" ")[2];
  const rows = list.map(([dir, kind, date, n, stamp, note, saldo], i) => {
    const next = list[i + 1];
    const last = !next || (o.all && month(next) !== month(list[i])) ? "last" : "";
    const head = o.all && (i === 0 || month(list[i - 1]) !== month(list[i])) ? `<div class="mov-month"><span>${NAMES[month(list[i])]}</span><span>${NETS[month(list[i])] < 0 ? "" : "+"}${fmt(NETS[month(list[i])])}</span></div>` : "";
    const mark = stamp ? `<span class="mi mi-stamp">${stamp.replace(" ", "")}</span>` : `<span class="mi">${icon(dir === "out" ? "arrow_back" : "add")}</span>`;
    const sub = stamp ? `<small class="ms-line">Ganaste el sello del ${stamp}</small>` : "";
    return `${head}<div class="mov-row ${dir === "out" ? "out" : ""} ${last}">${mark}<span><b>${note ?? kind}</b><small>${note ? `${kind} · ` : ""}${date}</small>${sub}</span><span class="mov-amt"><b class="${dir === "out" ? "" : "in"}">${dir === "out" ? "−" : "+"}${fmt(Math.abs(n))}</b><small>saldo ${fmt(saldo)}</small></span></div>`;
  }).join("");
  const count = s.withdrawal ? 7 : s.done ? s.movs.length : 6;
  return `<section class="slip"><div class="mov-head"><h2 class="q strong" style="margin:0">Movimientos</h2>${s.done ? "" : `<button class="text-btn">Retirar dinero</button>`}</div>${rows}${o.all ? "" : `<button class="text-btn">Ver los ${count}${icon("chevron_right")}</button>`}</section>`;
}

function body(scene, { state } = {}, o = {}) {
  if (state === "loading") return `<div role="status" aria-label="Cargando"><section class="slip"><div class="skel" style="width:40%"></div><div class="skel big"></div><div class="skel"></div></section></div>`;
  if (state === "failed") return `<section class="slip empty">${icon("error")}<h2>No pudimos leer esta meta</h2><p>Tus registros siguen guardados. Vuelve a intentarlo.</p><button class="outline-btn">Reintentar</button></section>`;
  const s = SCENES[scene];
  // A completed goal is read-only: no edit, no Aportar or Retirar; deleting, from the ⋮, is its only action.
  return hero(s, o) + (s.done ? "" : facts(s, o)) + chart(s.months, s.max) + movements(s, o);
}

const frame = (o, html, { tall = false, done = false, title = GOAL.name, featured = true, pin = true } = {}) => `<div class="phone ${tall ? "tall" : ""}"><div class="app" data-ink="${o.ink}" data-theme="${o.theme}">${subShell({ title, body: html, active: 2, dock: done ? "" : "Aportar a esta meta", dockClass: "goal", action: done ? ["more_vert", "Más opciones"] : ["edit_note", "Editar meta"], band: done || !pin ? "" : bandPill(featured) })}</div></div>`;
const card = (title, note, inner) => `<div class="frame-card"><h3>${title}</h3><p>${note}</p>${inner}</div>`;

function render() {
  const o = reviewOptions(["ink", "theme"]);
  const set = (id, h) => document.getElementById(id).innerHTML = h;
  set("detail",
    card("Goal detail", "The star pill under the name chooses this goal for Recibo. Then saved amount and stamps, the goal’s facts, contributions by month and the movements. Dock: Aportar a esta meta.", frame(o, body("normal", {}, o))) +
    card("Whole screen, scrolled", "A contribution that earned a stamp shows it instead of its + icon. Retirar dinero sits with the movements.", frame(o, body("normal", {}, o), { tall: true })) +
    card("While another goal is on Recibo", "Outlined pill with an empty star: tapping it moves Home’s goal here.", frame(o, body("normal", {}, o), { featured: false })));
  set("states",
    card("After a withdrawal", "The 50 % stamp stays, faded, with one line saying it is still yours. Progress shows the honest 43 %; the next stamp to earn is 75 %.", frame(o, body("keep", {}, o))) +
    card("Completed goal", "Read-only: four stamps, “Meta lograda el 30 sep”, the history stays. No edit, no Aportar dock, no Retirar and no star; Borrar meta in the top bar’s ⋮ (with the confirm slip) is its only action.", frame(o, body("done", {}, o), { done: true, title: "Vacunas de Luna", tall: true })) +
    card("Loading", "Skeleton slip.", frame(o, body("normal", { state: "loading" }, o), { pin: false })) +
    card("Failed read", "Never shown as an empty goal.", frame(o, body("normal", { state: "failed" }, o), { pin: false })) +
    card("All movements (after Ver los 7)", "Owner, 2026-11-02: a line per month with its net (aportes less retiros) over that month’s rows. The short list has none. Each saldo is the one below it plus the row’s amount.", frame(o, movements(SCENES.keep, { all: true }), { tall: true })));
}
document.querySelectorAll(".controls select").forEach(s => s.addEventListener("change", render));
render();
