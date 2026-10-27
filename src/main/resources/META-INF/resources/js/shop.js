// Shared by every page: masthead, footer, cart storage and API helpers.
import '@rhds/elements/rh-icon/rh-icon.js';
import '@rhds/elements/rh-scheme-toggle/rh-scheme-toggle.js';

const CART_KEY = 'swag-shop-cart';

/** Time after which a response counts as slow in the UI. */
export const SLOW_MS = 250;

export async function api(path, options = {}) {
  const started = performance.now();
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    ...options,
  });
  const elapsed = performance.now() - started;
  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `${response.status} ${response.statusText}`);
  }
  const body = response.status === 204 ? null : await response.json();
  return { body, elapsed };
}

const currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

export function money(value) {
  return currency.format(Number(value));
}

export function duration(ms) {
  return ms >= 1000 ? `${(ms / 1000).toFixed(2)} s` : `${Math.round(ms)} ms`;
}

export function escapeHtml(text) {
  return String(text).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

// ---------- Cart, kept in the browser ----------

export function readCart() {
  try {
    const items = JSON.parse(localStorage.getItem(CART_KEY) ?? '[]');
    return Array.isArray(items) ? items : [];
  } catch {
    return [];
  }
}

export function writeCart(items) {
  try {
    localStorage.setItem(CART_KEY, JSON.stringify(items));
  } catch {
    // storage unavailable: the cart lasts as long as the page
  }
  updateCartCount(items);
}

export function addToCart(product, quantity) {
  const items = readCart();
  const existing = items.find(item => item.productId === product.id);
  if (existing) {
    existing.quantity = Math.min(10, existing.quantity + quantity);
  } else {
    items.push({ productId: product.id, name: product.name, price: product.price, image: product.image, quantity });
  }
  writeCart(items);
}

function updateCartCount(items = readCart()) {
  const count = items.reduce((sum, item) => sum + item.quantity, 0);
  const badge = document.querySelector('.cart-count');
  if (badge) {
    badge.textContent = count;
    badge.hidden = count === 0;
  }
}

// ---------- Page chrome ----------

async function inlineFooterLogo() {
  const holder = document.querySelector('.footer-logo');
  if (!holder) {
    return;
  }
  try {
    const svg = await (await fetch('/img/brand/logo-red-hat-standard.svg')).text();
    // The wordmark follows the page's text colour, so the footer logo works on light and dark surfaces
    holder.innerHTML = svg.replace(/style="fill:#000"/g, 'style="fill:currentColor"');
    holder.querySelector('svg')?.setAttribute('role', 'img');
    holder.querySelector('svg')?.setAttribute('aria-label', 'Red Hat');
  } catch {
    holder.textContent = 'Red Hat';
  }
}

updateCartCount();
inlineFooterLogo();
window.addEventListener('storage', () => updateCartCount());
