import '@rhds/elements/rh-button/rh-button.js';
import '@rhds/elements/rh-spinner/rh-spinner.js';
import '@rhds/elements/rh-alert/rh-alert.js';
import { api, money, duration, escapeHtml, addToCart, SLOW_MS } from './shop.js';

const container = document.getElementById('product');
const id = new URLSearchParams(location.search).get('id');

function chart(points) {
  const prices = points.map(p => Number(p.price));
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  const range = max - min || 1;
  const x = i => (i / (prices.length - 1)) * 100;
  const y = price => 95 - ((price - min) / range) * 90;
  const line = prices.map((price, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(2)},${y(price).toFixed(2)}`).join(' ');
  return `
    <svg viewBox="0 0 100 100" preserveAspectRatio="none" role="img"
         aria-label="Price over the last ${points.length} days, from ${money(min)} to ${money(max)}">
      <path class="area" d="${line} L100,100 L0,100 Z"></path>
      <path class="line" d="${line}"></path>
    </svg>
    <div class="chart-legend">
      <span>${points[0].date}</span>
      <span>Low ${money(min)} · High ${money(max)}</span>
      <span>Today</span>
    </div>`;
}

function render(details, elapsed) {
  const { product } = details;
  document.title = `${product.name} · Swag Shop`;
  container.innerHTML = `
    <div class="product-layout">
      <img class="product-photo" src="${product.image}" alt="${escapeHtml(product.name)}" width="720" height="720">
      <div>
        <span class="category">${escapeHtml(product.category)}</span>
        <h1>${escapeHtml(product.name)}</h1>
        <p class="product-price">${money(details.price)}</p>
        <p class="price-note">Lowest price in the last 30 days: ${money(details.lowestLast30Days)}
          · <span class="${elapsed > SLOW_MS ? 'timing slow' : ''}">price calculated in ${duration(elapsed)}</span></p>
        <p>${escapeHtml(product.description)}</p>
        <div class="buy-row">
          <div class="quantity" role="group" aria-label="Quantity">
            <button type="button" data-step="-1" aria-label="Decrease quantity">−</button>
            <output id="quantity" aria-live="polite">1</output>
            <button type="button" data-step="1" aria-label="Increase quantity">+</button>
          </div>
          <rh-button id="add" icon="shopping-cart" icon-set="ui">Add to cart</rh-button>
          <span class="stock"><rh-icon set="ui" icon="check-circle"></rh-icon> ${details.inStock} in stock</span>
        </div>
        <div id="added"></div>
        <section class="price-chart" aria-labelledby="chart-title">
          <h2 id="chart-title">Price history</h2>
          ${chart(details.priceHistory)}
        </section>
      </div>
    </div>`;

  let quantity = 1;
  const output = container.querySelector('#quantity');
  container.querySelectorAll('[data-step]').forEach(button => button.addEventListener('click', () => {
    quantity = Math.min(10, Math.max(1, quantity + Number(button.dataset.step)));
    output.textContent = quantity;
  }));
  container.querySelector('#add').addEventListener('click', () => {
    addToCart({ ...product, price: details.price }, quantity);
    container.querySelector('#added').innerHTML = `
      <rh-alert state="success" variant="inline">
        <h3 slot="header">Added to your cart</h3>
        <p>${quantity} × ${escapeHtml(product.name)}. <a href="/cart.html">Go to cart</a></p>
      </rh-alert>`;
  });
}

async function load() {
  if (!id) {
    container.innerHTML = '<p>No product selected. <a href="/">Browse all products</a></p>';
    return;
  }
  try {
    const { body, elapsed } = await api(`/api/products/${encodeURIComponent(id)}`, {
      headers: { Accept: 'application/json', 'X-Request-Id': crypto.randomUUID?.() ?? String(Date.now()) },
    });
    render(body, elapsed);
  } catch (error) {
    container.innerHTML = `<p>This product could not be loaded (${escapeHtml(error.message)}). <a href="/">Browse all products</a></p>`;
  }
}

load();
