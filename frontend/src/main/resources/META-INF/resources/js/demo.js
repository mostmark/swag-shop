import '@rhds/elements/rh-button/rh-button.js';
import '@rhds/elements/rh-switch/rh-switch.js';
import '@rhds/elements/rh-dialog/rh-dialog.js';
import '@rhds/elements/rh-tabs/rh-tabs.js';
import '@rhds/elements/rh-spinner/rh-spinner.js';
import '@rhds/elements/rh-disclosure/rh-disclosure.js';
import { api, escapeHtml } from './shop.js';

const POLL_MS = 2000;

const scenariosEl = document.getElementById('scenarios');
const trafficSwitch = document.getElementById('traffic');
const dialog = document.getElementById('code-dialog');
// Scenarios whose Cryostat hint is unfolded; a card is redrawn when it changes and must keep it unfolded
const openHints = new Set();
let state;

const LEVELS = ['LOW', 'MEDIUM', 'HIGH'];
const levelName = level => level.charAt(0) + level.slice(1).toLowerCase();

function scenarioCard(s) {
  return `
    <article class="scenario ${s.problemEnabled ? 'is-problem' : ''}" data-id="${s.id}" aria-labelledby="title-${s.id}">
      <header>
        <div>
          <h2 id="title-${s.id}">${escapeHtml(s.feature)}</h2>
          <div class="problem-type">${escapeHtml(s.problemType)}</div>
        </div>
        <span class="state-label">${s.problemEnabled ? 'Problem on' : 'Fixed'}</span>
      </header>
      <rh-switch class="problem-switch" accessible-label="${escapeHtml(s.feature)}: run the naive code"
                 ${s.problemEnabled ? 'checked' : ''}
                 message-on="Naive code: the problem is on" message-off="Fixed code"></rh-switch>
      <div>
        <span class="field-label" id="load-${s.id}">Simulated load</span>
        <div class="segmented" role="group" aria-labelledby="load-${s.id}">
          ${LEVELS.map(level => `
            <button type="button" data-load="${level}" aria-pressed="${s.load === level}">
              ${levelName(level)} · ${s.requestsPerSecond[level]}/s
            </button>`).join('')}
        </div>
      </div>
      <div class="metrics" aria-live="off">
        <div><div class="metric-value" data-metric="rps">–</div><div class="metric-label">requests/s</div></div>
        <div><div class="metric-value" data-metric="avg">–</div><div class="metric-label">average</div></div>
        <div><div class="metric-value" data-metric="p95">–</div><div class="metric-label">95th percentile</div></div>
        <div class="jvm-line" data-metric="jvm" data-service="${escapeHtml(s.jvmService)}">${jvmLine(s.jvmService, null)}</div>
      </div>
      <div class="symptom">
        ${s.id === 'pricing' ? '<div class="muted" data-metric="leak"></div>' : ''}
        <p class="muted">${escapeHtml(s.symptom)}</p>
      </div>
      <rh-disclosure class="hint" variant="compact" summary="What to look for in Cryostat"
                     ${openHints.has(s.id) ? 'open' : ''}>
        <p>${escapeHtml(s.cryostatHint)}</p>
      </rh-disclosure>
      <footer>
        <span class="defaults">Default: ${s.defaultProblemEnabled ? 'problem on' : 'fixed'} · ${levelName(s.defaultLoad)} load</span>
        <rh-button variant="secondary" class="view-code" icon="code" icon-set="ui">View code</rh-button>
      </footer>
    </article>`;
}

function renderState(next) {
  state = next;
  trafficSwitch.checked = state.trafficEnabled;
  document.getElementById('traffic-default').textContent =
    `Default: shoppers ${state.defaultTrafficEnabled ? 'on' : 'off'}`;
  scenariosEl.innerHTML = state.scenarios.map(scenarioCard).join('');
}

async function updateScenario(id, change) {
  try {
    const { body } = await api(`/api/demo/scenarios/${id}`, { method: 'PUT', body: JSON.stringify(change) });
    state.scenarios = state.scenarios.map(s => (s.id === id ? body : s));
    const card = scenariosEl.querySelector(`[data-id="${id}"]`);
    card.outerHTML = scenarioCard(body);
  } catch (error) {
    alert(`Could not change the scenario: ${error.message}`);
  }
}

scenariosEl.addEventListener('change', event => {
  const toggle = event.target.closest('.problem-switch');
  if (toggle) {
    updateScenario(toggle.closest('.scenario').dataset.id, { problemEnabled: toggle.checked });
  }
});

scenariosEl.addEventListener('click', event => {
  const loadButton = event.target.closest('[data-load]');
  if (loadButton) {
    updateScenario(loadButton.closest('.scenario').dataset.id, { load: loadButton.dataset.load });
    return;
  }
  if (event.target.closest('.view-code')) {
    showCode(event.target.closest('.scenario').dataset.id);
  }
});

scenariosEl.addEventListener('toggle', event => {
  const hint = event.target.closest('rh-disclosure.hint');
  if (hint) {
    const id = hint.closest('.scenario').dataset.id;
    // The event fires just before the disclosure updates its open property
    queueMicrotask(() => (hint.open ? openHints.add(id) : openHints.delete(id)));
  }
});

trafficSwitch.addEventListener('change', async () => {
  const { body } = await api('/api/demo/traffic', {
    method: 'PUT',
    body: JSON.stringify({ enabled: trafficSwitch.checked }),
  });
  state.trafficEnabled = body.trafficEnabled;
});

document.getElementById('reset').addEventListener('click', async () => {
  const { body } = await api('/api/demo/reset', { method: 'POST' });
  renderState(body);
});

// ---------- Live statistics ----------

// Values that point at the problem: busy CPUs (the search hotspot) and a heap that stays full (the leak). CPU is
// counted in cores, so it reads the same on a laptop with many cores and in a pod limited to a few.
const BUSY_CORES = 1.5;
const FULL_HEAP = 0.6;

/** One line about the JVM of the service that the card's shoppers call. */
function jvmLine(service, jvm) {
  const name = `<span><strong>${escapeHtml(service)}</strong> JVM</span>`;
  if (!jvm) {
    return `${name} · –`;
  }
  const cores = jvm.processCpuLoad * jvm.availableProcessors;
  const cpu = `CPU ${cores.toFixed(1)} of ${jvm.availableProcessors} cores`;
  const heap = `heap ${jvm.heapUsedMb} of ${jvm.heapMaxMb} MB`;
  // Each part is kept on one line; the line breaks only between parts
  const part = (text, bad = false) => `<span${bad ? ' class="bad"' : ''}>${text}</span>`;
  return [name, part(cpu, cores >= BUSY_CORES), part(heap, jvm.heapUsedMb > jvm.heapMaxMb * FULL_HEAP),
    part(`${jvm.liveThreads} threads`)].join(' · ');
}

function formatMs(ms) {
  if (!ms) {
    return '–';
  }
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)} s` : `${ms < 10 ? ms.toFixed(1) : Math.round(ms)} ms`;
}

async function poll() {
  try {
    const { body: stats } = await api('/api/demo/stats');
    // Each part is null while its service does not answer
    document.getElementById('orders').textContent = stats.ordersPlaced?.toLocaleString('en-US') ?? '–';
    for (const line of scenariosEl.querySelectorAll('[data-metric="jvm"]')) {
      line.innerHTML = jvmLine(line.dataset.service, stats.jvm[line.closest('.scenario').dataset.id]);
    }
    if (typeof stats.trafficEnabled === 'boolean' && stats.trafficEnabled !== trafficSwitch.checked) {
      trafficSwitch.checked = stats.trafficEnabled;
    }
    for (const [id, summary] of Object.entries(stats.requests ?? {})) {
      const card = scenariosEl.querySelector(`[data-id="${id}"]`);
      if (!card) {
        continue;
      }
      card.querySelector('[data-metric="rps"]').textContent = summary.requestsPerSecond.toFixed(1);
      const avg = card.querySelector('[data-metric="avg"]');
      const p95 = card.querySelector('[data-metric="p95"]');
      avg.textContent = formatMs(summary.averageMillis);
      p95.textContent = formatMs(summary.p95Millis);
      avg.classList.toggle('bad', summary.averageMillis > 100);
      p95.classList.toggle('bad', summary.p95Millis > 250);
    }
    const leak = scenariosEl.querySelector('[data-metric="leak"]');
    if (leak && stats.priceCache) {
      const { entries, maxEntries, estimatedMb } = stats.priceCache;
      leak.textContent = entries === 0
        ? 'No leaked price quotes on the heap.'
        : `${entries.toLocaleString('en-US')} leaked price quotes, about ${estimatedMb} MB`
          + (entries >= maxEntries ? ' (safety limit reached, the leak has stopped growing)' : '');
    }
  } catch {
    // the frontend restarted or is busy; try again on the next tick
  } finally {
    setTimeout(poll, POLL_MS);
  }
}

// ---------- Source code viewer ----------

const KEYWORDS = /\b(public|private|protected|static|final|synchronized|class|record|interface|implements|return|if|else|for|while|new|try|finally|catch|throw|int|long|void|boolean)\b/g;

function highlight(line) {
  const escaped = escapeHtml(line);
  if (/^\s*(\/\/|\/\*|\*)/.test(line)) {
    return `<span class="comment">${escaped}</span>`;
  }
  return escaped.replace(KEYWORDS, '<span class="keyword">$1</span>');
}

function codePanel(file) {
  const lines = file.code.replace(/\n$/, '').split('\n');
  // Start at the class comment, but keep the file's line numbers so they match JFR stack traces
  const start = Math.max(0, lines.findIndex(l => /^(\/\*\*|@|public )/.test(l)));
  return `
    <p class="code-path">${escapeHtml(file.path)}${start > 0 ? ` · package and imports (lines 1–${start}) hidden` : ''}</p>
    <pre class="code-view" tabindex="0" style="counter-reset: line ${start}"><code>${lines.slice(start)
      .map(l => `<span class="line">${highlight(l)}</span>`).join('')}</code></pre>`;
}

async function showCode(id) {
  const scenario = state.scenarios.find(s => s.id === id);
  dialog.querySelector('[slot="header"]').textContent = `${scenario.feature}: ${scenario.problemType}`;
  const body = document.getElementById('code-body');
  body.innerHTML = '<rh-spinner>Loading code…</rh-spinner>';
  dialog.showModal?.() ?? (dialog.open = true);
  try {
    const { body: source } = await api(`/api/demo/source/${id}`);
    body.innerHTML = `
      <rh-tabs>
        <rh-tab slot="tab">Problem: ${escapeHtml(source.problem.className)}</rh-tab>
        <rh-tab-panel>${codePanel(source.problem)}</rh-tab-panel>
        <rh-tab slot="tab">Fix: ${escapeHtml(source.fix.className)}</rh-tab>
        <rh-tab-panel>${codePanel(source.fix)}</rh-tab-panel>
      </rh-tabs>`;
  } catch (error) {
    body.textContent = `Could not load the code: ${error.message}`;
  }
}

const { body: initial } = await api('/api/demo/settings');
renderState(initial);
poll();
