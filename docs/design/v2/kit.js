// Shared helpers for the Sello v2 mockups. Money is exact COP text, never computed policy.
const fmt = n => (n < 0 ? "−" : "") + Math.abs(n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ".");
const spoken = n => (n < 0 ? "menos " : "") + fmt(Math.abs(n)) + " pesos";
const icon = (name, cls = "") => `<svg class="icon ${cls}" aria-hidden="true"><use href="#i-${name}"></use></svg>`;
const money = (n, cls = "") => `<span class="money ${n < 0 ? "neg" : ""} ${cls}" aria-label="${spoken(n)}">${fmt(n)}</span>`;
const statusbar = () => `<div class="statusbar"><span>9:41</span><span>▲ ● ▮</span></div>`;
const reviewOptions = ids => Object.fromEntries(ids.map(id => [id, document.getElementById(id).value]));

// App frame shared by the tab screens: month bar, scrolling content, Anotar dock and tabs.
function appShell({ month = "Octubre", body, tabs = 3, active = 0, toast, notice, current, title, addLabel, band = "", dock = "Anotar", dockClass = "" }) {
  const all = [["receipt_long", "Recibo"], ["insights", "Resumen"], ["flag", "Metas"], ["account_balance", "Inversiones"]].slice(0, tabs);
  const nav = all.map(([ic, t], i) => `<button ${i === active ? 'aria-current="page"' : ""}><span class="pill">${icon(ic)}</span><span class="t">${t}</span></button>`).join("");
  return `<div class="top">${statusbar()}<div class="topbar">${title ? `<h1 class="tab-title">${title}</h1>` : `<button class="month" aria-label="Cambiar mes, ${month} 2026">${month}<small>2026</small>${icon("keyboard_arrow_down")}</button>`}<span class="top-actions">${addLabel ? `<button class="icon-btn" aria-label="${addLabel}">${icon("add")}</button>` : ""}<button class="icon-btn" aria-label="Configuración">${icon("settings")}</button></span></div>${current ? pastRibbon(month, current) : ""}${band}</div><main class="content">${body}</main><div class="bottom"><div class="dock"><button class="primary ${dockClass}">${dockClass === "goal" ? icon("flag") : dockClass === "plain" ? "" : icon("add")}${dock}</button></div><nav class="nav">${nav}</nav><div class="gesture"></div></div>${toast ? `<div class="toast" role="status">${icon("check")}${toast}</div>` : ""}${notice ? noticeBar(notice) : ""}`;
}

// What catch-up recorded, above Anotar on Recibo until the person dismisses it (SELLO-114).
const noticeBar = text => `<div class="toast notice" role="status">${icon("autorenew")}<span>${text}</span><button class="undo">Ver</button></div>`;

// Shown on every tab screen while the selected month is not the current one.
const pastRibbon = (month, current) => `<div class="past-ribbon" role="status">${icon("event")}<span>Estás viendo <b>${month.toLowerCase()}</b></span><button>Volver a ${current}</button></div>`;

// Frame for a screen pushed inside a tab: back arrow and title, same dock and tabs.
function subShell({ title, body, tabs = 3, active = 1, dock = "Anotar", dockClass = "", month = "Octubre", current, action, band = "" }) {
  const all = [["receipt_long", "Recibo"], ["insights", "Resumen"], ["flag", "Metas"], ["account_balance", "Inversiones"]].slice(0, tabs);
  const nav = all.map(([ic, t], i) => `<button ${i === active ? 'aria-current="page"' : ""}><span class="pill">${icon(ic)}</span><span class="t">${t}</span></button>`).join("");
  return `<div class="top">${statusbar()}<div class="topbar sub"><button class="icon-btn" aria-label="Volver">${icon("arrow_back")}</button><h1 class="sub-title">${title}</h1>${(action ? (Array.isArray(action[0]) ? action : [action]) : []).map(([ic, label, pressed]) => `<button class="icon-btn sub-action" aria-label="${label}" ${pressed !== undefined ? `aria-pressed="${pressed}"` : ""}>${icon(ic)}</button>`).join("")}</div>${current ? pastRibbon(month, current) : ""}${band}</div><main class="content">${body}</main><div class="bottom">${dock ? `<div class="dock"><button class="primary ${dockClass}">${dockClass === "goal" ? icon("flag") : dockClass === "plain" ? "" : icon("add")}${dock}</button></div>` : ""}<nav class="nav">${nav}</nav><div class="gesture"></div></div>`;
}
