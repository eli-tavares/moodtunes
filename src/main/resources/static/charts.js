// Small dependency-free SVG chart kit for MoodTunes: a line chart and a stacked bar chart, each with a
// crosshair/hover tooltip (also on keyboard focus and touch), a legend, and an HTML table twin.
//
// Colors come from CSS classes (s1..s4 strokes, f1..f4 fills in styles.css), never from the data, so light/dark
// mode and the strict Content-Security-Policy (no inline styles) both keep working. All text from data is inserted
// with textContent, never as HTML.

const NS = 'http://www.w3.org/2000/svg';
const W = 330;
const H = 196;
const TOP = 10;
const BOTTOM = 26;
const LEFT = 30;

function svgEl(tag, attrs, parent) {
    const node = document.createElementNS(NS, tag);
    for (const [k, v] of Object.entries(attrs || {})) node.setAttribute(k, String(v));
    if (parent) parent.appendChild(node);
    return node;
}

function htmlEl(tag, className, text, parent) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    if (parent) parent.appendChild(node);
    return node;
}

const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

/** Round a count axis up to a clean maximum with integer steps. */
export function niceCountAxis(max) {
    const m = Math.max(1, max);
    const step = m <= 4 ? 1 : m <= 10 ? 2 : m <= 25 ? 5 : m <= 50 ? 10 : 20;
    const top = Math.ceil(m / step) * step;
    const ticks = [];
    for (let t = 0; t <= top; t += step) ticks.push(t);
    return { min: 0, max: top, ticks };
}

function labelIndices(n) {
    const stride = n <= 8 ? 1 : Math.ceil(n / 6);
    const out = [];
    for (let i = 0; i < n; i += stride) out.push(i);
    return out;
}

/** Shared frame: surface, hairline grid, y ticks, x labels. Returns the geometry the charts draw into. */
function frame(host, cfg, rightMargin) {
    host.replaceChildren();
    host.classList.add('chart-host');

    const right = rightMargin;
    const plotW = W - LEFT - right;
    const plotH = H - TOP - BOTTOM;
    const n = cfg.labels.length;
    const band = plotW / n;
    const x = (i) => LEFT + band * (i + 0.5);
    const { min, max, ticks } = cfg.y;
    const y = (v) => TOP + plotH * (1 - (v - min) / (max - min));

    const svg = svgEl('svg', {
        viewBox: `0 0 ${W} ${H}`, class: 'chart', role: 'group', tabindex: 0,
        'aria-roledescription': 'chart', 'aria-label': `${cfg.ariaLabel} Use the left and right arrow keys to read each value.`,
    }, host);

    // Texture patterns: only used when the user asks for high contrast or prints (see styles.css), never by default.
    const defs = svgEl('defs', {}, svg);
    [['tex-a', 'M0 6 L6 0'], ['tex-b', 'M0 0 L6 6']].forEach(([id, d]) => {
        const p = svgEl('pattern', { id, width: 6, height: 6, patternUnits: 'userSpaceOnUse' }, defs);
        svgEl('path', { d, class: 'tex-line' }, p);
    });

    ticks.forEach((t) => {
        svgEl('line', { x1: LEFT, x2: W - right, y1: y(t), y2: y(t), class: t === (cfg.baseline ?? min) ? 'axis' : 'grid' }, svg);
        const label = svgEl('text', { x: LEFT - 6, y: y(t) + 3.5, class: 'tick', 'text-anchor': 'end' }, svg);
        label.textContent = cfg.y.fmt ? cfg.y.fmt(t) : String(t);
    });
    labelIndices(n).forEach((i) => {
        const label = svgEl('text', { x: x(i), y: H - 8, class: 'tick', 'text-anchor': 'middle' }, svg);
        label.textContent = cfg.labels[i];
    });

    return { svg, x, y, band, plotW, plotH, right, n };
}

function legend(host, items) {
    if (items.length < 2) return;       // a single series is named by the card title
    const row = htmlEl('ul', 'legend', null, host);
    items.forEach((item) => {
        const li = htmlEl('li', null, null, row);
        htmlEl('span', `swatch ${item.kind === 'line' ? 'swatch-line' : 'swatch-box'} sw${item.slot}`, null, li);
        htmlEl('span', 'legend-name', item.name, li);
    });
}

/** Crosshair + tooltip + keyboard handling shared by both charts. */
function interactive(host, geo, cfg, drawActive) {
    const { svg, band, plotH, n } = geo;
    const tip = htmlEl('div', 'tooltip', null, host);
    tip.hidden = true;
    tip.setAttribute('role', 'status');
    let active = -1;

    const hit = svgEl('rect', { x: LEFT, y: TOP, width: geo.plotW, height: plotH, class: 'hit' }, svg);
    const cross = svgEl('line', { y1: TOP, y2: TOP + plotH, class: 'crosshair' }, svg);
    cross.setAttribute('visibility', 'hidden');
    const overlay = svgEl('g', {}, svg);

    function show(i) {
        active = clamp(i, 0, n - 1);
        const cx = geo.x(active);
        cross.setAttribute('x1', cx);
        cross.setAttribute('x2', cx);
        cross.setAttribute('visibility', 'visible');
        overlay.replaceChildren();
        drawActive(overlay, active);

        tip.replaceChildren();
        htmlEl('div', 'tt-title', cfg.titles[active], tip);
        cfg.series.forEach((s) => {
            const row = htmlEl('div', 'tt-row', null, tip);
            htmlEl('span', `tt-key k${s.slot}`, null, row);
            htmlEl('strong', null, s.fmt(s.values[active]), row);
            htmlEl('span', 'tt-name', s.name, row);
        });
        tip.hidden = false;
        const box = host.getBoundingClientRect();
        const svgBox = svg.getBoundingClientRect();
        const px = (cx / W) * svgBox.width + (svgBox.left - box.left);
        const half = tip.offsetWidth / 2;
        tip.style.left = `${clamp(px, half + 4, box.width - half - 4)}px`;
    }

    function hide() {
        active = -1;
        cross.setAttribute('visibility', 'hidden');
        overlay.replaceChildren();
        tip.hidden = true;
    }

    function indexAt(event) {
        const rect = svg.getBoundingClientRect();
        const vx = ((event.clientX - rect.left) / rect.width) * W;
        return clamp(Math.floor((vx - LEFT) / band), 0, n - 1);
    }

    hit.addEventListener('pointermove', (e) => show(indexAt(e)));
    hit.addEventListener('pointerdown', (e) => show(indexAt(e)));
    svg.addEventListener('pointerleave', (e) => { if (e.pointerType === 'mouse') hide(); });
    svg.addEventListener('keydown', (e) => {
        if (e.key === 'ArrowRight') { show(active < 0 ? 0 : active + 1); e.preventDefault(); }
        else if (e.key === 'ArrowLeft') { show(active < 0 ? n - 1 : active - 1); e.preventDefault(); }
        else if (e.key === 'Escape') hide();
    });
    svg.addEventListener('blur', hide);
}

/**
 * Line chart.
 * cfg: { labels, titles, series:[{name, slot, values (number|null), fmt}], y:{min,max,ticks,fmt}, baseline,
 *        points (bool: a marker on every value), endLabel:(series)=>string|null, ariaLabel }
 */
export function lineChart(host, cfg) {
    const wantsEndLabels = typeof cfg.endLabel === 'function';
    const geo = frame(host, cfg, wantsEndLabels ? 44 : 12);
    const { svg, x, y } = geo;

    const ends = [];
    cfg.series.forEach((s) => {
        // consecutive non-null values form one segment, so a missing day is a gap, never a zero
        const segments = [];
        let current = [];
        s.values.forEach((v, i) => {
            if (v === null || v === undefined) { if (current.length) segments.push(current); current = []; }
            else current.push([x(i), y(v), i]);
        });
        if (current.length) segments.push(current);

        segments.forEach((seg) => {
            if (seg.length > 1) {
                svgEl('path', { d: seg.map(([px, py], k) => `${k ? 'L' : 'M'}${px.toFixed(1)} ${py.toFixed(1)}`).join(' '),
                    class: `line s${s.slot}` }, svg);
            }
        });
        const lastSeg = segments[segments.length - 1];
        const last = lastSeg ? lastSeg[lastSeg.length - 1] : null;
        segments.forEach((seg) => seg.forEach((pt) => {
            const isolated = seg.length === 1;
            if (cfg.points || isolated || pt === last) {
                svgEl('circle', { cx: pt[0], cy: pt[1], r: 4, class: `dot f${s.slot}` }, svg);
            }
        }));
        if (last && wantsEndLabels) ends.push({ s, px: last[0], py: last[1] });
    });

    // Direct end labels - only when they do not collide (otherwise the legend and tooltip carry the names).
    if (wantsEndLabels && ends.length) {
        const ys = ends.map((e) => e.py).sort((a, b) => a - b);
        const collide = ys.some((v, i) => i > 0 && v - ys[i - 1] < 13);
        if (!collide) {
            ends.forEach(({ s, px, py }) => {
                const text = cfg.endLabel(s);
                if (text) {
                    const t = svgEl('text', { x: px + 9, y: py + 4, class: 'end-label' }, svg);
                    t.textContent = text;
                }
            });
        }
    }

    interactive(host, geo, cfg, (g, i) => {
        cfg.series.forEach((s) => {
            const v = s.values[i];
            if (v !== null && v !== undefined) svgEl('circle', { cx: x(i), cy: y(v), r: 5, class: `dot f${s.slot}` }, g);
        });
    });
    legend(host, cfg.series.map((s) => ({ name: s.name, slot: s.slot, kind: 'line' })));
}

/**
 * Stacked bar chart (one column per bucket). Segments are stacked in series order from the baseline up; the top
 * segment of each column has the only rounded corners (4px), segments are separated by a 2px surface gap.
 */
export function stackedBarChart(host, cfg) {
    const geo = frame(host, cfg, 12);
    const { svg, x, y, band } = geo;
    const barW = Math.min(24, Math.max(4, band - 3));

    cfg.labels.forEach((_, i) => {
        let base = 0;
        const parts = cfg.series.map((s) => ({ s, v: s.values[i] || 0 })).filter((p) => p.v > 0);
        parts.forEach((p, k) => {
            const top = base + p.v;
            const yTop = y(top);
            const h = y(base) - yTop;
            const gap = k === 0 ? 0 : 2;                 // 2px of surface between touching segments
            const segTop = yTop;
            const segH = Math.max(1, h - gap);
            const isTop = k === parts.length - 1;
            const r = isTop ? Math.min(4, barW / 2, segH) : 0;
            const x0 = x(i) - barW / 2;
            const d = isTop
                ? `M${x0} ${segTop + segH} V${segTop + r} Q${x0} ${segTop} ${x0 + r} ${segTop} H${x0 + barW - r} `
                  + `Q${x0 + barW} ${segTop} ${x0 + barW} ${segTop + r} V${segTop + segH} Z`
                : `M${x0} ${segTop + segH} V${segTop} H${x0 + barW} V${segTop + segH} Z`;
            svgEl('path', { d, class: `bar f${p.s.slot} t${p.s.slot % 2 ? 'a' : 'b'}` }, svg);
            base = top;
        });
    });

    interactive(host, geo, cfg, (g, i) => {
        const total = cfg.series.reduce((sum, s) => sum + (s.values[i] || 0), 0);
        if (total > 0) {
            svgEl('rect', { x: x(i) - barW / 2 - 2, y: y(total) - 2, width: barW + 4, height: y(0) - y(total) + 2,
                rx: 4, class: 'lift' }, g);
        }
    });
    legend(host, cfg.series.map((s) => ({ name: s.name, slot: s.slot, kind: 'box' })));
}

/** The accessible twin of a chart: the same numbers as an HTML table. */
export function tableFor(cfg, firstHeader = 'Period') {
    const table = document.createElement('table');
    table.className = 'data-table';
    const head = htmlEl('tr', null, null, htmlEl('thead', null, null, table));
    [firstHeader, ...cfg.series.map((s) => s.name)].forEach((h) => htmlEl('th', null, h, head));
    const body = htmlEl('tbody', null, null, table);
    cfg.titles.forEach((title, i) => {
        const row = htmlEl('tr', null, null, body);
        htmlEl('th', null, title, row).setAttribute('scope', 'row');
        cfg.series.forEach((s) => htmlEl('td', null, s.fmt(s.values[i]), row));
    });
    return table;
}
