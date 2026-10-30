import '@rhds/elements/rh-button/rh-button.js';
import '@rhds/elements/rh-cta/rh-cta.js';
import '@rhds/elements/rh-alert/rh-alert.js';
import { api, money, duration, escapeHtml, readCart, writeCart, SLOW_MS } from './shop.js';

const container = document.getElementById('cart');

function total(items) {
  return items.reduce((sum, item) => sum + Number(item.price) * item.quantity, 0);
}

function renderEmpty() {
  container.innerHTML = `
    <div class="empty-state">
      <p>Your cart is empty.</p>
      <rh-cta href="/">Browse products</rh-cta>
    </div>`;
}

function render() {
  const items = readCart();
  if (items.length === 0) {
    renderEmpty();
    return;
  }
  container.innerHTML = `
    <div class="cart-layout">
      <table class="cart-table">
        <thead>
          <tr><th scope="col">Product</th><th scope="col" class="num">Price</th><th scope="col">Quantity</th><th scope="col" class="num">Total</th><th scope="col"><span class="visually-hidden">Remove</span></th></tr>
        </thead>
        <tbody>
          ${items.map((item, index) => `
            <tr>
              <td><div class="cart-item"><img src="${item.image}" alt="" width="64" height="64"><a href="/product.html?id=${encodeURIComponent(item.productId)}">${escapeHtml(item.name)}</a></div></td>
              <td class="num">${money(item.price)}</td>
              <td>
                <div class="quantity" role="group" aria-label="Quantity of ${escapeHtml(item.name)}">
                  <button type="button" data-index="${index}" data-step="-1" aria-label="Decrease quantity">−</button>
                  <output>${item.quantity}</output>
                  <button type="button" data-index="${index}" data-step="1" aria-label="Increase quantity">+</button>
                </div>
              </td>
              <td class="num">${money(Number(item.price) * item.quantity)}</td>
              <td><rh-button variant="close" data-remove="${index}" accessible-label="Remove ${escapeHtml(item.name)}"></rh-button></td>
            </tr>`).join('')}
        </tbody>
      </table>
      <aside class="summary" aria-label="Order summary">
        <h2>Order summary</h2>
        <div class="summary-row"><span>Subtotal</span><span>${money(total(items))}</span></div>
        <div class="summary-row"><span>Shipping</span><span>Free</span></div>
        <div class="summary-row total"><span>Total</span><span>${money(total(items))}</span></div>
        <rh-button id="checkout">Place order</rh-button>
        <div id="checkout-status" class="timing" aria-live="polite"></div>
      </aside>
    </div>`;

  container.querySelectorAll('[data-step]').forEach(button => button.addEventListener('click', () => {
    const cart = readCart();
    const item = cart[Number(button.dataset.index)];
    item.quantity = Math.min(10, Math.max(1, item.quantity + Number(button.dataset.step)));
    writeCart(cart);
    render();
  }));
  container.querySelectorAll('[data-remove]').forEach(button => button.addEventListener('click', () => {
    const cart = readCart();
    cart.splice(Number(button.dataset.remove), 1);
    writeCart(cart);
    render();
  }));
  container.querySelector('#checkout').addEventListener('click', checkout);
}

async function checkout() {
  const button = container.querySelector('#checkout');
  const status = container.querySelector('#checkout-status');
  button.disabled = true;
  status.textContent = 'Placing your order…';
  const items = readCart().map(({ productId, quantity }) => ({ productId, quantity }));
  try {
    const { body, elapsed } = await api('/api/checkout', { method: 'POST', body: JSON.stringify({ items }) });
    writeCart([]);
    container.innerHTML = `
      <div class="order-confirmation">
        <img src="/img/brand/illustration-delivery.svg" alt="" aria-hidden="true">
        <div>
          <h2>Thank you for your order</h2>
          <p>Order <span class="order-id">${escapeHtml(body.orderId)}</span>, ${money(body.total)}, is on its way.</p>
          <p><span class="timing ${elapsed > SLOW_MS ? 'slow' : ''}">Checkout took ${duration(elapsed)}</span></p>
          <p><rh-cta href="/">Continue shopping</rh-cta></p>
        </div>
      </div>`;
  } catch (error) {
    button.disabled = false;
    status.innerHTML = '';
    status.insertAdjacentHTML('afterend', `
      <rh-alert state="danger" variant="inline"><h3 slot="header">The order could not be placed</h3><p>${escapeHtml(error.message)}</p></rh-alert>`);
  }
}

render();
