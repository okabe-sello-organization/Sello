/*
 * Sello design system pages: shared nav, screenshot figures, icons and the findings page.
 * No dependencies. Every path is worked out from where this script lives, so pages in
 * docs/design/system/ and docs/design/system/features/ use the same markup.
 *
 * Markup it reads (as every page under features/ uses it):
 *   <body data-page="index|foundations|components|findings|features/NAME">
 *   <nav id="sello-nav"></nav>
 *   <figure data-shot="NAME" data-module="app|design-system"
 *           [data-variants="claro,oscuro"] [data-files="a.png,b.png"]
 *           [data-caption="What this shows"]></figure>
 *   <div class="icon-cell" data-drawable="ic_sello_add"></div>   (foundations only)
 *   <div id="findings"></div> with window.SELLO_FINDINGS set     (findings only: misuse,
 *                                                               copies, hard-coded values)
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

  var SOURCE_BASE = "../../../";

  function featureLink(name) {
    return el("a", { class: "tag", href: new URL("features/" + name + ".html", SYSTEM).href, text: name });
  }

  /** The place's area as text, plus a link to each feature page it touches. */
  function areaTags(place) {
    var tags = (place.features || []).map(featureLink);
    if (!tags.length && place.area) tags.push(el("span", { class: "tag internal", text: place.area }));
    return tags;
  }

  function codeRef(text) { return el("code", { text: text }); }

  /** A component reference: components.html#c-Name, foundations.html, or the shared file. */
  function componentLink(ref, shared) {
    if (ref) {
      var name = ref.indexOf("#c-") >= 0 ? "catalog: " + ref.split("#c-")[1] : ref.replace(/\.html.*$/, "");
      return el("a", { class: "tag", href: new URL(ref, SYSTEM).href, text: name });
    }
    if (shared) {
      var path = shared.replace(/:\d+$/, "");
      return el("span", null, ["shared at ", el("a", { href: new URL(SOURCE_BASE + path, SYSTEM).href }, [codeRef(shared)])]);
    }
    return null;
  }

  function shotRow(list) {
    if (!list || !list.length) return el("p", { class: "muted small", text: "No screenshot shows this." });
    var row = el("div", { class: "shot-row" });
    list.forEach(function (shot) { row.appendChild(shotCard(shot.module || "app", shot.file, "")); });
    return row;
  }

  function unsureBadge() { return el("span", { class: "badge unsure", text: "unsure" }); }

  function misuseCard(card) {
    var node = el("article", { class: "finding", id: card.id });
    node.appendChild(el("header", null, [
      el("span", { class: "fid", text: card.id }),
      el("h3", null, [codeRef(card.component)]),
      componentLink(card.ref)
    ]));
    node.appendChild(el("p", null, [el("strong", { text: "Its meaning: " }), card.meaning]));
    node.appendChild(el("details", { class: "evidence" }, [
      el("summary", { text: "Evidence for the meaning (" + card.evidence.length + ")" }),
      el("ul", { class: "files" }, card.evidence.map(function (e) {
        return el("li", null, [codeRef(e.file), e.note ? " — " + e.note : null]);
      }))
    ]));
    card.places.forEach(function (place) {
      node.appendChild(el("div", { class: "place" + (place.unsure ? " is-unsure" : ""), id: place.id }, [
        el("p", { class: "place-head" }, [
          el("span", { class: "pid", text: place.id }), " ", codeRef(place.file), " "
        ].concat(areaTags(place), [place.unsure ? unsureBadge() : null])),
        el("p", null, [el("strong", { text: "What: " }), place.what]),
        el("p", null, [el("strong", { text: place.unsure ? "Why, and the doubt: " : "Why: " }), place.why]),
        shotRow(place.shots)
      ]));
    });
    return node;
  }

  function copyCard(card) {
    var node = el("article", { class: "finding", id: card.id });
    node.appendChild(el("header", null, [
      el("span", { class: "fid", text: card.id }),
      el("h3", null, [codeRef(card.component)]),
      componentLink(card.ref, card.ref ? null : card.shared)
    ]));
    if (card.ref) node.appendChild(el("p", { class: "muted" }, ["Shared at ", codeRef(card.shared)]));
    card.copies.forEach(function (copy) {
      var kind = copy.drift ? "drifted" : copy.identical ? "identical" : "differs";
      node.appendChild(el("div", { class: "place copy-" + kind + (copy.unsure ? " is-unsure" : ""), id: copy.id }, [
        el("p", { class: "place-head" }, [
          el("span", { class: "pid", text: copy.id }), " ", codeRef(copy.file), " "
        ].concat(areaTags(copy), [
          el("span", { class: "badge " + kind, text: kind }),
          copy.unsure ? unsureBadge() : null
        ])),
        copy.drift ? el("p", { class: "drift" }, [el("strong", { text: "Drifted: " }), copy.drift]) : null,
        el("p", null, [el("strong", { text: "How it differs: " }), copy.differs]),
        copy.unsure ? el("p", null, [el("strong", { text: "Doubt: " }), copy.doubt]) : null,
        shotRow(copy.shots)
      ]));
    });
    return node;
  }

  function placeList(places) {
    var list = el("ul", { class: "files" }, places.map(function (p) {
      return el("li", null, [codeRef(p.file), " "].concat(areaTags(p), [" ", p.note]));
    }));
    if (places.length <= 4) return list;
    return el("details", null, [el("summary", { text: places.length + " places" }), list]);
  }

  function hardTable(groups, colour) {
    var head = ["ID", "Value", "Token"].concat(colour ? ["Dynamic colour"] : [], ["Places"]);
    var body = groups.map(function (g) {
      var cells = [
        el("td", null, [el("span", { class: "fid", text: g.id })]),
        el("td", null, [codeRef(g.value), g.note ? el("p", { class: "muted small", text: g.note }) : null]),
        el("td", null, [g.token ? codeRef(g.token) : el("span", { class: "muted", text: "none" })])
      ];
      if (colour) cells.push(el("td", null, [g.risk ? el("span", { class: "badge risk", text: "at risk" }) : el("span", { class: "muted", text: "no" })]));
      cells.push(el("td", null, [placeList(g.places), g.shots && g.shots.length ? shotRow(g.shots) : null]));
      return el("tr", { id: g.id, class: g.risk ? "risk" : null }, cells);
    });
    return el("div", { class: "table-wrap" }, [el("table", { class: "hard" }, [
      el("thead", null, [el("tr", null, head.map(function (t) { return el("th", { text: t }); }))]),
      el("tbody", null, body)
    ])]);
  }

  function dynamicBox(d) {
    return el("div", { class: "note dynamic", id: "dynamic-colours" }, [
      el("h3", { text: "Dynamic colours" }),
      el("p", null, [d.summary, " ", el("a", { href: "foundations.html#dynamic-colours", text: "Dynamic colours on Foundations" }), "."]),
      el("p", null, [el("strong", { text: "Colour findings, closed or explained" })]),
      el("ul", null, d.explained.map(function (e) {
        return el("li", null, [el("strong", { text: e.ids + " · " + e.what + ": " }), e.why]);
      })),
      el("p", { class: "muted" }, [el("strong", { text: "Not listed: " }), d.notListed])
    ]);
  }

  function count(list, fn) { return list.filter(fn).length; }

  function summary(data) {
    var misusePlaces = [].concat.apply([], data.misuse.map(function (c) { return c.places; }));
    var copyPlaces = [].concat.apply([], data.copies.map(function (c) { return c.copies; }));
    function kind(k) { return data.hardcoded.filter(function (g) { return g.kind === k; }); }
    function places(gs) { return gs.reduce(function (n, g) { return n + g.places.length; }, 0); }
    var colours = kind("colour");
    var rows = [
      [el("a", { href: "#misuse", text: "Misuse" }), data.misuse.length + " components",
        misusePlaces.length + " places: " + count(misusePlaces, function (p) { return !p.unsure; }) + " sure, " +
        count(misusePlaces, function (p) { return p.unsure; }) + " unsure"],
      [el("a", { href: "#copies", text: "Copies" }), data.copies.length + " shared components",
        copyPlaces.length + " copies: " + count(copyPlaces, function (p) { return p.drift; }) + " drifted, " +
        count(copyPlaces, function (p) { return p.identical; }) + " identical"],
      [el("a", { href: "#hard-colour", text: "Hard-coded colour" }), colours.length + " values",
        places(colours) + " places: " + places(colours.filter(function (g) { return g.risk; })) + " would not follow dynamic colour"]
    ];
    ["type", "shape", "spacing", "alpha"].forEach(function (k) {
      var gs = kind(k);
      rows.push([el("a", { href: "#hard-" + k, text: "Hard-coded " + k }), gs.length + " values", places(gs) + " places"]);
    });
    return el("div", { class: "table-wrap" }, [el("table", { class: "summary" }, [
      el("tbody", null, rows.map(function (r) {
        return el("tr", null, r.map(function (cell) { return el("td", null, [cell]); }));
      }))
    ])]);
  }

  var KIND_TITLES = { colour: "Colours", type: "Type", shape: "Shape", spacing: "Spacing", alpha: "Alpha" };

  function findings(container, input) {
    var holder = container || document.getElementById("findings");
    if (!holder) return;
    var data = input || window.SELLO_FINDINGS;
    holder.innerHTML = "";
    if (!data) {
      holder.appendChild(el("div", { class: "empty-state" }, [el("p", { text: "No findings loaded." })]));
      return;
    }
    holder.appendChild(summary(data));

    holder.appendChild(el("h2", { id: "misuse", text: "Misuse" }));
    holder.appendChild(el("p", { class: "muted", text: "One card per component: what it means in the app, then each place that uses it against that meaning. Sure places first; unsure ones are marked and say why." }));
    if (!data.misuse.length) holder.appendChild(el("p", { text: "None left: every component is used for what it means." }));
    data.misuse.forEach(function (card) { holder.appendChild(misuseCard(card)); });

    holder.appendChild(el("h2", { id: "copies", text: "Copies" }));
    holder.appendChild(el("p", { class: "muted", text: "One card per shared component: each place that rebuilds it by hand and how the copy differs. Drifted copies say something the shared part would not." }));
    data.copies.forEach(function (card) { holder.appendChild(copyCard(card)); });

    holder.appendChild(el("h2", { id: "hardcoded", text: "Hard-coded values" }));
    holder.appendChild(dynamicBox(data.dynamic));
    ["colour", "type", "shape", "spacing", "alpha"].forEach(function (k) {
      var groups = data.hardcoded.filter(function (g) { return g.kind === k; });
      holder.appendChild(el("h3", { id: "hard-" + k, class: "theme-heading", text: KIND_TITLES[k] }));
      if (k === "colour") holder.appendChild(el("p", { class: "muted", text: "Every colour not read from the theme. Those that would stay put under a dynamic scheme come first." }));
      if (!groups.length) {
        holder.appendChild(el("p", { text: k === "colour"
          ? "None left: every colour comes from the theme; the box above says why the remaining literals stay."
          : "None left: every value reads its token, or is a one-off named in its own component (see Foundations)." }));
        return;
      }
      holder.appendChild(hardTable(groups, k === "colour"));
    });

    holder.appendChild(el("details", { class: "not-listed", id: "not-listed" }, [
      el("summary", { text: "Not listed (" + data.notListed.length + " look-alikes the copies audit left out)" }),
      el("ul", null, data.notListed.map(function (d) {
        return el("li", null, [el("strong", { text: d.what }), " — " + d.why]);
      }))
    ]));

    shots(holder);
    if (location.hash) {
      var target = document.getElementById(location.hash.slice(1));
      if (target) target.scrollIntoView();
    }
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
