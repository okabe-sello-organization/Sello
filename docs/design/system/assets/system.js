/*
 * Sello design system pages: shared nav, screenshot figures, icons and the findings form.
 * No dependencies. Every path is worked out from where this script lives, so pages in
 * docs/design/system/ and docs/design/system/features/ use the same markup.
 *
 * Markup it reads (see features/TEMPLATE.md):
 *   <body data-page="index|foundations|components|findings|features/NAME">
 *   <nav id="sello-nav"></nav>
 *   <figure data-shot="NAME" data-module="app|design-system"
 *           [data-variants="claro,oscuro"] [data-files="a.png,b.png"]
 *           [data-caption="What this shows"]></figure>
 *   <div class="icon-cell" data-drawable="ic_sello_add"></div>   (foundations only)
 *   <div id="findings"></div> with window.SELLO_FINDINGS set     (findings only)
 */
(function () {
  "use strict";

  var script = document.currentScript;
  // .../docs/design/system/assets/system.js -> .../docs/design/system/
  var SYSTEM = new URL("../", script.src);
  // -> repository root
  var ROOT = new URL("../../../", SYSTEM);

  var SHOT_DIRS = {
    "app": "app/src/test/screenshots/",
    "design-system": "design-system/src/test/screenshots/"
  };
  var DRAWABLES = "design-system/src/main/res/drawable/";

  /* ---------------------------------------------------------------- nav */

  var PAGES = [
    { id: "index", title: "Overview", href: "index.html" },
    { id: "foundations", title: "Foundations", href: "foundations.html" },
    { id: "components", title: "Components", href: "components.html" },
    { id: "findings", title: "Findings", href: "findings.html" }
  ];

  var FEATURES = [
    { id: "recibo", title: "Recibo" },
    { id: "resumen", title: "Resumen" },
    { id: "startup", title: "Start-up" },
    { id: "shell", title: "Shell" },
    { id: "expense", title: "Expense" },
    { id: "income", title: "Income" },
    { id: "bulk", title: "Bulk entry" },
    { id: "goals", title: "Goals" },
    { id: "investments", title: "Investments" },
    { id: "category", title: "Category" },
    { id: "recurrence", title: "Recurrence" },
    { id: "settings", title: "Settings" },
    { id: "backup", title: "Backup" },
    { id: "reset", title: "Reset" }
  ];

  function el(tag, attrs, children) {
    var node = document.createElement(tag);
    if (attrs) {
      Object.keys(attrs).forEach(function (key) {
        var value = attrs[key];
        if (value === null || value === undefined || value === false) return;
        if (key === "text") node.textContent = value;
        else if (key === "class") node.className = value;
        else node.setAttribute(key, value === true ? "" : value);
      });
    }
    (children || []).forEach(function (child) {
      if (child === null || child === undefined) return;
      node.appendChild(typeof child === "string" ? document.createTextNode(child) : child);
    });
    return node;
  }

  function nav(target) {
    var holder = target || document.getElementById("sello-nav");
    if (!holder) return;
    var current = document.body.getAttribute("data-page") || "";
    var list = el("ul");
    function item(id, title, href) {
      var link = el("a", { href: new URL(href, SYSTEM).href, text: title });
      if (id === current) link.setAttribute("aria-current", "page");
      list.appendChild(el("li", null, [link]));
    }
    PAGES.forEach(function (page) { item(page.id, page.title, page.href); });
    list.appendChild(el("li", { class: "sep", "aria-hidden": "true" }));
    list.appendChild(el("li", { class: "group", text: "Features" }));
    FEATURES.forEach(function (feature) {
      item("features/" + feature.id, feature.title, "features/" + feature.id + ".html");
    });
    holder.classList.add("site-nav");
    holder.setAttribute("aria-label", "Design system pages");
    holder.innerHTML = "";
    holder.appendChild(el("div", { class: "inner" }, [list]));
  }

  /* -------------------------------------------------------- screenshots */

  function shotUrl(module, file) {
    var dir = SHOT_DIRS[module];
    if (!dir) throw new Error("unknown screenshot module: " + module);
    return new URL(dir + file, ROOT).href;
  }

  function variantLabel(variant) {
    var known = {
      "claro": "Light",
      "oscuro": "Dark",
      "cobalto-light-font1.0": "Cobalto light",
      "cobalto-dark-font1.0": "Cobalto dark",
      "violeta-light-font1.0": "Violeta light",
      "violeta-dark-font1.0": "Violeta dark",
      "cobalto-light": "Cobalto light"
    };
    return known[variant] || variant;
  }

  /** Resolves with whether the image at [url] loads. */
  function probe(url) {
    return new Promise(function (resolve) {
      var image = new Image();
      image.onload = function () { resolve(true); };
      image.onerror = function () { resolve(false); };
      image.src = url;
    });
  }

  function missingBox(file) {
    return el("div", { class: "shot-missing", role: "img",
      "aria-label": "missing screenshot: " + file, text: "missing screenshot: " + file });
  }

  /** One image with its label; turns into a red box when the file is not there. */
  function shotCard(module, file, label) {
    var url = shotUrl(module, file);
    var image = el("img", { src: url, alt: label + " — " + file, loading: "lazy" });
    var card = el("div", { class: "shot" }, [
      el("a", { href: url, target: "_blank", rel: "noopener", title: "Open full size" }, [image]),
      el("span", { class: "shot-label" }, [label ? label + " · " : "", el("code", { text: file })])
    ]);
    image.addEventListener("error", function () {
      card.replaceWith(missingBox(module + "/" + file));
    });
    return card;
  }

  /** The files a figure shows: [[file, label], ...], or null when nothing exists. */
  function resolveFiles(figure) {
    var name = figure.getAttribute("data-shot") || "";
    var module = figure.getAttribute("data-module") || "app";
    var files = figure.getAttribute("data-files");
    var variants = figure.getAttribute("data-variants");
    function split(text) {
      return text.split(",").map(function (part) { return part.trim(); }).filter(Boolean);
    }
    if (files) {
      return Promise.resolve(split(files).map(function (file) {
        var match = file.match(/-(claro|oscuro|(?:cobalto|violeta)-(?:light|dark)-font[\d.]+)\.png$/);
        return [file, match ? variantLabel(match[1]) : ""];
      }));
    }
    if (variants) {
      return Promise.resolve(split(variants).map(function (variant) {
        return [name + "-" + variant + ".png", variantLabel(variant)];
      }));
    }
    // No hint: use the first naming the baselines follow for this name.
    var sets = [
      ["claro", "oscuro"],
      ["cobalto-light-font1.0", "cobalto-dark-font1.0"],
      [""]
    ];
    function attempt(index) {
      if (index >= sets.length) return Promise.resolve(null);
      var set = sets[index];
      var first = set[0] ? name + "-" + set[0] + ".png" : name + ".png";
      return probe(shotUrl(module, first)).then(function (found) {
        if (!found) return attempt(index + 1);
        return set.map(function (variant) {
          return variant ? [name + "-" + variant + ".png", variantLabel(variant)] : [name + ".png", ""];
        });
      });
    }
    return attempt(0);
  }

  function renderFigure(figure) {
    if (figure.getAttribute("data-rendered")) return Promise.resolve();
    figure.setAttribute("data-rendered", "true");
    figure.classList.add("shots");
    var name = figure.getAttribute("data-shot") || "";
    var module = figure.getAttribute("data-module") || "app";
    var caption = figure.getAttribute("data-caption");
    var row = el("div", { class: "shot-row" }, [el("div", { class: "shot-loading" })]);
    var existingCaption = figure.querySelector("figcaption");
    if (!existingCaption && caption) figure.appendChild(el("figcaption", { text: caption }));
    figure.appendChild(row);
    return resolveFiles(figure).then(function (files) {
      row.innerHTML = "";
      if (!files) {
        row.appendChild(missingBox(module + "/" + name));
        return;
      }
      files.forEach(function (pair) { row.appendChild(shotCard(module, pair[0], pair[1])); });
    });
  }

  function shots(container) {
    var root = container || document;
    var figures = root.querySelectorAll("figure[data-shot], figure[data-files]");
    return Promise.all(Array.prototype.map.call(figures, renderFigure));
  }

  /* -------------------------------------------------------------- icons */

  /** Draws an Android vector drawable as inline SVG; it reads the XML itself. */
  function vectorToSvg(xmlText) {
    var doc = new DOMParser().parseFromString(xmlText, "application/xml");
    var A = "http://schemas.android.com/apk/res/android";
    var vector = doc.documentElement;
    var svgNs = "http://www.w3.org/2000/svg";
    var svg = document.createElementNS(svgNs, "svg");
    svg.setAttribute("viewBox", "0 0 " + vector.getAttributeNS(A, "viewportWidth") + " " +
      vector.getAttributeNS(A, "viewportHeight"));
    svg.setAttribute("aria-hidden", "true");
    function walk(source, target) {
      Array.prototype.forEach.call(source.children, function (child) {
        if (child.localName === "group") {
          var g = document.createElementNS(svgNs, "g");
          var tx = child.getAttributeNS(A, "translateX") || 0;
          var ty = child.getAttributeNS(A, "translateY") || 0;
          g.setAttribute("transform", "translate(" + tx + " " + ty + ")");
          target.appendChild(g);
          walk(child, g);
        } else if (child.localName === "path") {
          var path = document.createElementNS(svgNs, "path");
          path.setAttribute("d", child.getAttributeNS(A, "pathData"));
          if (child.getAttributeNS(A, "fillType") === "evenOdd") path.setAttribute("fill-rule", "evenodd");
          target.appendChild(path);
        }
      });
    }
    walk(vector, svg);
    return svg;
  }

  function icons(container) {
    var root = container || document;
    var cells = root.querySelectorAll("[data-drawable]");
    return Promise.all(Array.prototype.map.call(cells, function (cell) {
      var names = cell.getAttribute("data-drawable").split(",");
      var pair = el("span", { class: "icon-pair" });
      cell.insertBefore(pair, cell.firstChild);
      return Promise.all(names.map(function (drawable) {
        var url = new URL(DRAWABLES + drawable.trim() + ".xml", ROOT).href;
        return fetch(url).then(function (response) {
          if (!response.ok) throw new Error(response.status);
          return response.text();
        }).then(function (text) {
          pair.appendChild(vectorToSvg(text));
        }).catch(function () {
          pair.appendChild(missingBox(drawable + ".xml"));
        });
      }));
    }));
  }

  /* ----------------------------------------------------------- findings */

  var STORE = "sello-design-findings:";

  function loadAnswer(id) {
    try {
      return JSON.parse(localStorage.getItem(STORE + id)) || { choice: "", note: "" };
    } catch (ignored) {
      return { choice: "", note: "" };
    }
  }

  function saveAnswer(id, answer) {
    try { localStorage.setItem(STORE + id, JSON.stringify(answer)); } catch (ignored) { /* private mode */ }
  }

  function featureLink(name) {
    return el("a", { class: "tag", href: new URL("features/" + name + ".html", SYSTEM).href, text: name });
  }

  function answersText(list) {
    return list.map(function (finding) {
      var answer = loadAnswer(finding.id);
      if (!answer.choice && !answer.note) return finding.id + ": (no answer)";
      var line = finding.id + ": " + (answer.choice || "(no choice)");
      if (answer.note && answer.note.trim()) line += " — " + answer.note.trim().replace(/\s+/g, " ");
      return line;
    }).join("\n");
  }

  function copyText(text) {
    if (navigator.clipboard && window.isSecureContext) {
      return navigator.clipboard.writeText(text);
    }
    var area = el("textarea", { style: "position:fixed;left:-9999px" });
    area.value = text;
    document.body.appendChild(area);
    area.select();
    var done = document.execCommand("copy");
    area.remove();
    return done ? Promise.resolve() : Promise.reject(new Error("copy refused"));
  }

  function findingCard(finding) {
    var answer = loadAnswer(finding.id);
    var card = el("article", { class: "finding", id: finding.id });
    if (answer.choice) card.classList.add("answered");

    card.appendChild(el("header", null, [
      el("span", { class: "fid", text: finding.id }),
      el("h2", { text: finding.title || "" }),
      finding.category ? el("span", { class: "tag", text: finding.category }) : null
    ]));
    if (finding.purpose) card.appendChild(el("p", { text: finding.purpose }));
    if (finding.features && finding.features.length) {
      card.appendChild(el("p", null, ["Features: "].concat(finding.features.map(featureLink))));
    }

    var variants = finding.variants || [];
    var row = el("div", { class: "variants" });
    variants.forEach(function (variant) {
      var box = el("div", { class: "variant" }, [
        el("h3", { text: variant.label }),
        variant.note ? el("p", { text: variant.note }) : null
      ]);
      if (variant.files && variant.files.length) {
        box.appendChild(el("ul", { class: "files" }, variant.files.map(function (file) {
          return el("li", null, [el("code", { text: file })]);
        })));
      }
      if (variant.shots && variant.shots.length) {
        var shotRow = el("div", { class: "shot-row" });
        variant.shots.forEach(function (shot) {
          shotRow.appendChild(shotCard(shot.module || "app", shot.file, ""));
        });
        box.appendChild(shotRow);
      }
      row.appendChild(box);
    });
    card.appendChild(row);

    if (finding.recommend) {
      card.appendChild(el("div", { class: "recommend" }, [
        el("strong", { text: "Recommended: " + finding.recommend }),
        finding.reason ? el("span", { text: " — " + finding.reason }) : null
      ]));
    }

    var choices = el("div", { class: "choices", role: "radiogroup" });
    var options = variants.map(function (variant) { return variant.label; });
    options.push(variants.length > 2 ? "All are fine" : "Both are fine", "Other");
    var note = el("textarea", { "aria-label": finding.id + " note", placeholder: "Note (optional)" });
    note.value = answer.note || "";
    function store() {
      var picked = choices.querySelector("input:checked");
      var next = { choice: picked ? picked.value : "", note: note.value };
      saveAnswer(finding.id, next);
      card.classList.toggle("answered", Boolean(next.choice));
      document.dispatchEvent(new CustomEvent("sello-answers-changed"));
    }
    options.forEach(function (option) {
      var input = el("input", { type: "radio", name: "choice-" + finding.id, value: option });
      if (answer.choice === option) input.checked = true;
      input.addEventListener("change", store);
      choices.appendChild(el("label", null, [input, " " + option]));
    });
    note.addEventListener("input", store);
    card.appendChild(el("fieldset", null, [el("legend", { text: "Your choice" }), choices, note]));
    return card;
  }

  function findings(container, list) {
    var holder = container || document.getElementById("findings");
    if (!holder) return;
    var items = list || window.SELLO_FINDINGS || [];
    holder.innerHTML = "";
    if (!items.length) {
      holder.appendChild(el("div", { class: "empty-state" }, [
        el("p", { text: "No findings yet." })
      ]));
      return;
    }
    var output = el("textarea", { class: "answers-out", readonly: true, "aria-label": "Answers" });
    var status = el("span", { class: "copy-status", "aria-live": "polite" });
    function refresh() { output.value = answersText(items); }
    var copy = el("button", { type: "button", text: "Copy answers" });
    copy.addEventListener("click", function () {
      refresh();
      copyText(output.value).then(function () {
        status.textContent = "Copied " + items.length + " lines.";
      }, function () {
        output.select();
        status.textContent = "Copy was refused: select the text below and copy it.";
      });
    });
    var answered = el("span", { class: "muted" });
    function count() {
      var done = items.filter(function (f) { return loadAnswer(f.id).choice; }).length;
      answered.textContent = done + " of " + items.length + " answered";
    }
    holder.appendChild(el("div", { class: "findings-toolbar" }, [copy, answered, status]));
    items.forEach(function (finding) { holder.appendChild(findingCard(finding)); });
    holder.appendChild(el("h2", { text: "Answers" }));
    holder.appendChild(output);
    document.addEventListener("sello-answers-changed", function () { refresh(); count(); });
    refresh();
    count();
    shots(holder);
  }

  /* ---------------------------------------------------------------- run */

  window.Sello = {
    PAGES: PAGES,
    FEATURES: FEATURES,
    nav: nav,
    shots: shots,
    icons: icons,
    findings: findings,
    shotUrl: shotUrl
  };

  function start() {
    nav();
    shots(document);
    if (document.querySelector("[data-drawable]")) icons(document);
    if (document.getElementById("findings")) findings();
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start);
  else start();
})();
