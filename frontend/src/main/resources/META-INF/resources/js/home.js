import '@rhds/elements/rh-button/rh-button.js';
import '@rhds/elements/rh-spinner/rh-spinner.js';
import { api, money, duration, escapeHtml, SLOW_MS } from './shop.js';

const grid = document.getElementById('products');
const title = document.getElementById('results-title');
const timing = document.getElementById('timing');
const form = document.querySelector('.search-form');
const input = document.getElementById('search');

function card(product) {
  return `
    <a class="product-card" href="/product.html?id=${encodeURIComponent(product.id)}">
      <img src="${product.image}" alt="" width="360" height="360" loading="lazy">
      <div class="body">
        <span class="category">${escapeHtml(product.category)}</span>
        <h3>${escapeHtml(product.name)}</h3>
        <span class="price">${money(product.price)}</span>
      </div>
    </a>`;
}

function render(products, query) {
  if (products.length === 0) {
    grid.innerHTML = `
      <div class="empty-state">
        <p>No products match “${escapeHtml(query)}”.</p>
        <a href="/">Show all products</a>
      </div>`;
    return;
  }
  grid.innerHTML = products.map(card).join('');
}

async function load(query) {
  grid.classList.add('is-loading');
  grid.setAttribute('aria-busy', 'true');
  timing.classList.remove('slow');
  timing.innerHTML = query ? '<rh-spinner size="sm"></rh-spinner> Searching…' : '';
  try {
    if (query) {
      const { body, elapsed } = await api(`/api/search?q=${encodeURIComponent(query)}`);
      title.textContent = `${body.count} ${body.count === 1 ? 'result' : 'results'} for “${query}”`;
      timing.textContent = `Search took ${duration(elapsed)}`;
      timing.classList.toggle('slow', elapsed > SLOW_MS);
      render(body.products, query);
    } else {
      const { body } = await api('/api/products');
      title.textContent = 'All products';
      timing.textContent = '';
      render(body, '');
    }
  } catch (error) {
    timing.textContent = `Something went wrong: ${error.message}`;
  } finally {
    grid.classList.remove('is-loading');
    grid.setAttribute('aria-busy', 'false');
  }
}

form.addEventListener('submit', event => {
  event.preventDefault();
  const query = input.value.trim();
  const url = new URL(location.href);
  if (query) {
    url.searchParams.set('q', query);
  } else {
    url.searchParams.delete('q');
  }
  history.replaceState(null, '', url);
  load(query);
});

const initialQuery = new URLSearchParams(location.search).get('q') ?? '';
input.value = initialQuery;
load(initialQuery);
