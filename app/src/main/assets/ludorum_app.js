(() => {
  'use strict';

  if (window.__ludorumV103Booted) {
    try {
      if (typeof window.__ludorumRun === 'function') {
        window.__ludorumRun();
      }
    } catch (_) {}
    return;
  }
  window.__ludorumV103Booted = true;

  const BLUE = '#0B4DBB';
  const NAVY = '#071A33';
  const RED = '#CF1F1F';
  const YELLOW = '#F4C430';
  const MUTED = '#687586';
  const BORDER = '#E2E8F0';

  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));
  const norm = (value) => (value || '').replace(/\s+/g, ' ').trim();
  const low = (value) => norm(value).toLowerCase();
  const text = (el) => norm(el && el.innerText);
  const moneyRe = /\d+[\s\u00A0]*(?:[.,]\d{2})\s*€/;
  const pureMoneyRe = /^\s*\d+[\s\u00A0]*(?:[.,]\d{2})\s*€\s*$/;

  const current = () => (location.href || '').toLowerCase();
  const isCart = () => current().includes('/panier') || current().includes('/cart');
  const isCheckout = () => current().includes('/commande') || current().includes('/checkout') || current().includes('/order-pay');
  const isAccount = () => current().includes('/mon-compte');

  function addGlobalCss() {
    if ($('#ludorum-app-premium-css')) return;
    const style = document.createElement('style');
    style.id = 'ludorum-app-premium-css';
    style.textContent = `
      html, body {
        overflow-x: hidden !important;
        overflow-y: auto !important;
        height: auto !important;
        scroll-behavior: auto !important;
        overscroll-behavior-y: auto !important;
        touch-action: pan-y !important;
      }
      .elementor-location-header, .site-header, #masthead,
      .elementor-location-footer, .site-footer, #colophon {
        display: none !important;
      }
      .elementor-invisible {
        visibility: visible !important;
        opacity: 1 !important;
        transform: none !important;
      }
      .tinv-wraper.tinv-wishlist,
      .tinv-wishlist .tinvwl_add_to_wishlist_button,
      .tinvwl_add_to_wishlist_button {
        display: none !important;
      }
      body { padding-bottom: 18px !important; }

      /* Aucun popup marketing/cookies dans l'application. */
      .cmplz-cookiebanner,
      .cmplz-modal,
      .elementor-popup-modal,
      [class*="newsletter-popup"],
      [id*="newsletter-popup"],
      [class*="marketing-popup"],
      [id*="marketing-popup"] {
        display: none !important;
        pointer-events: none !important;
      }

      #ludorum-account-tabs {
        display: grid !important;
        grid-template-columns: 1fr 1fr !important;
        gap: 8px !important;
        width: 100% !important;
        margin: 0 0 18px !important;
        padding: 4px !important;
        box-sizing: border-box !important;
        border: 1px solid #E2E8F0 !important;
        border-radius: 16px !important;
        background: #F7F9FC !important;
      }

      #ludorum-account-tabs button {
        min-height: 46px !important;
        padding: 9px 10px !important;
        border: 1px solid transparent !important;
        border-radius: 12px !important;
        background: transparent !important;
        color: #071A33 !important;
        font-size: 14px !important;
        font-weight: 800 !important;
        box-shadow: none !important;
      }

      #ludorum-account-tabs button.is-active {
        border-color: #0B4DBB !important;
        background: #0B4DBB !important;
        color: #FFFFFF !important;
      }

      .ludorum-account-panel > h2:first-child,
      .ludorum-account-panel > h3:first-child {
        display: none !important;
      }
    `;
    document.head.appendChild(style);
  }

  function cleanAccountCopy() {
    if (!isAccount()) return;
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    let node;
    while ((node = walker.nextNode())) {
      const value = node.nodeValue || '';
      if (value.toLowerCase().includes('vos adresses')) {
        node.nodeValue = value
          .replace(/vos adresses,?\s*/gi, '')
          .replace(/vos commandes,\s*votre/gi, 'vos commandes, votre');
      }
    }
  }

  function accountTabs() {
    if (!isAccount() || $('#ludorum-account-tabs')) return;

    const loginForm =
      $('form.woocommerce-form-login') ||
      $('form.login');

    const registerForm =
      $('form.woocommerce-form-register') ||
      $('form.register');

    if (!loginForm || !registerForm) return;

    const loginPanel =
      loginForm.closest('.u-column1,.col-1,[class*=column1]') ||
      loginForm.parentElement;

    const registerPanel =
      registerForm.closest('.u-column2,.col-2,[class*=column2]') ||
      registerForm.parentElement;

    if (!loginPanel || !registerPanel || loginPanel === registerPanel) return;

    loginPanel.classList.add('ludorum-account-panel');
    registerPanel.classList.add('ludorum-account-panel');

    const tabs = document.createElement('div');
    tabs.id = 'ludorum-account-tabs';

    const loginButton = document.createElement('button');
    loginButton.type = 'button';
    loginButton.textContent = 'Se connecter';

    const registerButton = document.createElement('button');
    registerButton.type = 'button';
    registerButton.textContent = 'S’inscrire';

    function select(mode, doScroll) {
      const login = mode === 'login';

      loginButton.classList.toggle('is-active', login);
      registerButton.classList.toggle('is-active', !login);

      loginPanel.style.setProperty(
        'display',
        login ? 'block' : 'none',
        'important'
      );

      registerPanel.style.setProperty(
        'display',
        login ? 'none' : 'block',
        'important'
      );

      if (doScroll) {
        window.scrollTo({
          top: Math.max(
            0,
            tabs.getBoundingClientRect().top +
            window.scrollY -
            18
          ),
          behavior: 'smooth'
        });
      }
    }

    loginButton.addEventListener(
      'click',
      () => select('login', true)
    );

    registerButton.addEventListener(
      'click',
      () => select('register', true)
    );

    tabs.append(
      loginButton,
      registerButton
    );

    const parent =
      loginPanel.parentElement === registerPanel.parentElement
        ? loginPanel.parentElement
        : loginPanel.parentElement;

    parent.insertBefore(
      tabs,
      loginPanel
    );

    select(
      location.hash.toLowerCase().includes('inscription') ||
      location.hash.toLowerCase().includes('register')
        ? 'register'
        : 'login',
      false
    );
  }

  function notifyCartChanged() {
    try {
      if (
        window.LudorumAndroidBridge &&
        typeof window.LudorumAndroidBridge.cartChanged === 'function'
      ) {
        window.LudorumAndroidBridge.cartChanged();
      }
    } catch (_) {}
  }

  function hideCoupon() {
    if (!isCart() || isCheckout()) return;

    const selectors = [
      '.coupon',
      '.woocommerce-form-coupon-toggle',
      '.wc-block-components-totals-coupon',
      '.wc-block-cart__coupon',
      '.wc-block-components-panel--coupon',
      'form.checkout_coupon',
      'form.woocommerce-form-coupon',
      '[class*=coupon]',
      '[id*=coupon]'
    ];

    selectors.forEach((selector) => {
      $$(selector).forEach((el) => {
        const t = low(text(el));
        const hasInput = !!$('input', el);
        if (hasInput || t.includes('code promo') || t.includes('coupon') || t.includes('réduction')) {
          el.style.setProperty('display', 'none', 'important');
          el.style.setProperty('pointer-events', 'none', 'important');
        }
      });
    });

    $$('input').forEach((input) => {
      const descriptor = low(
        `${input.name || ''} ${input.id || ''} ${input.placeholder || ''}`
      );
      if (!descriptor.includes('coupon') && !descriptor.includes('promo')) return;
      let node = input.parentElement;
      for (let i = 0; i < 5 && node && node !== document.body; i += 1, node = node.parentElement) {
        const t = low(text(node));
        if (t.includes('promo') || t.includes('coupon') || t.includes('réduction')) {
          node.style.setProperty('display', 'none', 'important');
          break;
        }
      }
    });
  }

  function isVisible(el) {
    if (!el) return false;
    const s = getComputedStyle(el);
    const r = el.getBoundingClientRect();
    return s.display !== 'none' && s.visibility !== 'hidden' && r.width > 0 && r.height > 0;
  }

  function socialName(href) {
    const h = low(href);
    if (h.includes('instagram')) return 'Instagram';
    if (h.includes('facebook')) return 'Facebook';
    if (h.includes('tiktok')) return 'TikTok';
    if (h.includes('youtube')) return 'YouTube';
    if (h.includes('discord')) return 'Discord';
    return 'Réseau';
  }

  function socialGlyph(name) {
    return ({ Instagram: '◎', Facebook: 'f', TikTok: '♪', YouTube: '▶', Discord: '◉' })[name] || '•';
  }

  function socialColor(name) {
    return ({ Instagram: '#D62976', Facebook: '#1877F2', TikTok: '#111827', YouTube: '#FF0000', Discord: '#5865F2' })[name] || BLUE;
  }

  function buildSocialDock() {
    if ($('#ludorum-social-dock-v10')) return;

    const found = [];
    const seen = new Set();
    $$('a[href*=instagram],a[href*=facebook],a[href*=tiktok],a[href*=youtube],a[href*=discord]').forEach((a) => {
      const href = a.href || a.getAttribute('href') || '';
      if (!href || seen.has(href)) return;
      seen.add(href);
      found.push({ href, name: socialName(href), source: a });
    });

    if (!found.length) return;

    // Neutralise le conteneur flottant d'origine, y compris les parents transparents.
    found.forEach(({ source }) => {
      let node = source;
      for (let i = 0; i < 10 && node && node !== document.body; i += 1, node = node.parentElement) {
        const s = getComputedStyle(node);
        const r = node.getBoundingClientRect();
        if ((s.position === 'fixed' || s.position === 'sticky' || s.position === 'absolute') &&
            (r.width > innerWidth * 0.20 || r.height > innerHeight * 0.20)) {
          node.style.setProperty('pointer-events', 'none', 'important');
          if (r.width > innerWidth * 0.55 || r.height > innerHeight * 0.45) {
            node.style.setProperty('display', 'none', 'important');
          }
        }
      }
      source.style.setProperty('display', 'none', 'important');
    });

    const dock = document.createElement('div');
    dock.id = 'ludorum-social-dock-v10';
    dock.style.cssText = 'position:fixed;right:0;top:52%;transform:translateY(-50%);z-index:2147483000;display:flex;align-items:center;pointer-events:none;font-family:Arial,sans-serif;';

    const toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.textContent = '‹';
    toggle.setAttribute('aria-label', 'Afficher les réseaux sociaux');
    toggle.style.cssText = `width:25px;height:43px;border:1px solid ${BORDER};border-right:0;border-radius:13px 0 0 13px;background:${NAVY};color:#fff;font-size:22px;font-weight:800;line-height:40px;padding:0;pointer-events:auto;box-shadow:0 4px 14px rgba(7,26,51,.14);`;

    const list = document.createElement('div');
    list.style.cssText = `display:none;flex-direction:column;gap:6px;padding:8px 7px;background:rgba(255,255,255,.98);border:1px solid ${BORDER};border-right:0;border-radius:16px 0 0 16px;box-shadow:0 8px 22px rgba(7,26,51,.14);pointer-events:auto;`;

    found.forEach(({ href, name }) => {
      const a = document.createElement('a');
      a.href = href;
      a.target = '_blank';
      a.rel = 'noopener noreferrer';
      a.setAttribute('aria-label', name);
      a.textContent = socialGlyph(name);
      a.style.cssText = `display:flex;width:36px;height:36px;align-items:center;justify-content:center;border-radius:11px;background:#F8FAFC;border:1px solid #E5E7EB;text-decoration:none;font-size:20px;font-weight:800;color:${socialColor(name)};`;
      list.appendChild(a);
    });

    let open = false;
    toggle.addEventListener('click', (event) => {
      event.preventDefault();
      event.stopPropagation();
      open = !open;
      list.style.display = open ? 'flex' : 'none';
      toggle.textContent = open ? '›' : '‹';
      toggle.setAttribute('aria-label', open ? 'Réduire les réseaux sociaux' : 'Afficher les réseaux sociaux');
    });

    dock.append(toggle, list);
    document.body.appendChild(dock);
  }

  function findRemove(root) {
    const known = root.querySelector(
      [
        'a[href*="remove_item"]',
        'a.remove',
        '.wc-block-cart-item__remove-link',
        '.wc-block-components-product-remove-button',
        'button[name*="remove"]',
        'button[class*="remove"]',
        '[class*="remove-item"]',
        '[class*="remove_item"]',
        '[aria-label*="Retirer"]',
        '[aria-label*="retirer"]',
        '[aria-label*="Supprimer"]',
        '[aria-label*="supprimer"]'
      ].join(',')
    );

    if (known) return known;

    return $$('a,button,span', root).find((el) => {
      const value = low(text(el));
      return ['×', '✕', '✖', 'x'].includes(value);
    }) || null;
  }

  function removeHref(removeControl) {
    if (!removeControl) return '';

    const href =
      removeControl.href ||
      removeControl.getAttribute('href') ||
      '';

    return typeof href === 'string'
      ? href.trim()
      : '';
  }

  function findItemFromQty(input) {
    let node = input.parentElement;
    let best = null;

    for (let i = 0; i < 10 && node && node !== document.body; i += 1, node = node.parentElement) {
      const t = text(node);
      const r = node.getBoundingClientRect();
      const hasImage = !!$('img', node);
      const hasMoney = moneyRe.test(t);
      const hasRemove = !!findRemove(node);
      const sensible = r.width > innerWidth * 0.55 && r.height > 120 && r.height < innerHeight * 1.25;

      if (hasImage && hasMoney && sensible) {
        best = node;
        if (hasRemove || low(t).includes('sous-total') || low(t).includes('prix')) break;
      }
    }

    return best;
  }

  function findCartItems() {
    const quantityInputs = $$('input[type=number],input.qty,input[name*=quantity],select[name*=quantity],select[name*=qty]');
    const items = [];
    const seen = new Set();

    quantityInputs.forEach((input) => {
      const item = findItemFromQty(input);
      if (!item || seen.has(item)) return;
      seen.add(item);
      items.push({ root: item, qty: input });
    });

    // Woo standard fallback.
    $$('.cart_item,.woocommerce-cart-form__cart-item,.wc-block-cart-items__row').forEach((root) => {
      if (seen.has(root)) return;
      const qty = $('input[type=number],input.qty,input[name*=quantity],select[name*=quantity]', root);
      if (!qty) return;
      seen.add(root);
      items.push({ root, qty });
    });

    return items;
  }

  function extractName(root) {
    const known = $('.product-name a,.product-name,.wc-block-components-product-name,[class*=product-name],h2,h3,h4', root);
    if (known && text(known).length > 2) return text(known);

    const leaves = $$('a,strong,b,p,span,div', root).filter((el) => {
      if (el.children.length) return false;
      const t = text(el);
      const l = low(t);
      return t.length >= 4 && t.length <= 110 && !t.includes('€') &&
        !['prix', 'prix:', 'quantité', 'quantité:', 'sous-total', 'sous-total:', 'ttc', '(ttc)'].includes(l) &&
        !l.includes('supprimer');
    });

    return leaves.length ? text(leaves[0]) : 'Produit Ludorum';
  }

  function moneyValues(root) {
    const values = [];
    $$('span,strong,b,p,td,div', root).forEach((el) => {
      if (el.children.length > 3) return;
      const t = text(el);
      if (pureMoneyRe.test(t) && !values.includes(t)) values.push(t);
    });
    if (!values.length) {
      const matches = text(root).match(/\d+[\s\u00A0]*(?:[.,]\d{2})\s*€/g) || [];
      matches.forEach((m) => {
        const v = norm(m);
        if (!values.includes(v)) values.push(v);
      });
    }
    return values;
  }

  function extractItem(item) {
    const { root, qty } = item;
    const image = $('img', root);
    const remove = findRemove(root);
    const money = moneyValues(root);

    return {
      root,
      qty,
      remove,
      removeUrl: removeHref(remove),
      imageUrl: image ? (image.currentSrc || image.src || '') : '',
      name: extractName(root),
      unitPrice: money[0] || '',
      subtotal: money[money.length - 1] || money[0] || ''
    };
  }

  function removeCartItem(product, card, button) {
    if (!product || !button || button.dataset.removing === '1') {
      return;
    }

    const currentQuantity =
      Math.max(
        1,
        parseInt(
          product.qty && product.qty.value
            ? product.qty.value
            : '1',
          10
        ) || 1
      );

    button.dataset.removing = '1';
    button.disabled = true;
    button.textContent = '…';
    button.setAttribute('aria-busy', 'true');

    if (card) {
      card.classList.add('is-removing');
    }

    // La croix rouge signifie désormais "retirer 1 exemplaire".
    // À partir de 2, on décrémente simplement la quantité.
    if (currentQuantity > 1 && product.qty) {
      const nextQuantity =
        currentQuantity - 1;

      triggerCartUpdate(
        product.qty,
        nextQuantity
      );

      setTimeout(
        notifyCartChanged,
        500
      );

      // Si WooCommerce n'a finalement pas reconstruit le panier,
      // on ne laisse jamais la carte bloquée/grisée indéfiniment.
      setTimeout(() => {
        if (
          button.isConnected &&
          button.dataset.removing === '1'
        ) {
          button.dataset.removing = '0';
          button.disabled = false;
          button.textContent = '×';
          button.removeAttribute('aria-busy');

          if (card) {
            card.classList.remove('is-removing');
          }
        }
      }, 2800);

      return;
    }

    // Quantité = 1 : on peut supprimer réellement la ligne.
    const url =
      (product.removeUrl || '').trim();

    if (
      url &&
      (
        url.includes('remove_item=') ||
        url.includes('remove-item=') ||
        url.includes('remove_item%')
      )
    ) {
      try {
        notifyCartChanged();
        window.location.assign(url);
        return;
      } catch (_) {}
    }

    // WooCommerce Blocks / templates JS.
    if (product.remove) {
      try {
        product.remove.disabled = false;
        product.remove.removeAttribute('disabled');

        product.remove.dispatchEvent(
          new MouseEvent(
            'click',
            {
              bubbles: true,
              cancelable: true,
              view: window
            }
          )
        );

        setTimeout(
          notifyCartChanged,
          450
        );

        setTimeout(() => {
          if (
            button.isConnected &&
            button.dataset.removing === '1'
          ) {
            button.dataset.removing = '0';
            button.disabled = false;
            button.textContent = '×';
            button.removeAttribute('aria-busy');

            if (card) {
              card.classList.remove('is-removing');
            }
          }
        }, 2500);

        return;
      } catch (_) {}
    }

    button.dataset.removing = '0';
    button.disabled = false;
    button.textContent = '×';
    button.removeAttribute('aria-busy');

    if (card) {
      card.classList.remove('is-removing');
    }
  }


  function findUpdateButton() {
    return $('button[name=update_cart],input[name=update_cart],button[class*=update-cart],button[class*=update_cart]');
  }

  function triggerCartUpdate(originalQty, value) {
    const parsed = Math.max(1, parseInt(value || '1', 10) || 1);
    originalQty.value = String(parsed);
    originalQty.dispatchEvent(new Event('input', { bubbles: true }));
    originalQty.dispatchEvent(new Event('change', { bubbles: true }));

    clearTimeout(window.__ludoUpdateCartTimer);
    window.__ludoUpdateCartTimer = setTimeout(() => {
      const update = findUpdateButton();
      if (update) {
        update.disabled = false;
        update.removeAttribute('disabled');
        update.click();
        setTimeout(notifyCartChanged, 700);
      } else {
        const form = originalQty.closest('form');
        if (form && typeof form.requestSubmit === 'function') {
          form.requestSubmit();
          setTimeout(notifyCartChanged, 700);
        }
      }
    }, 280);
  }

  function findTotalsBlock() {
    const known = $('.cart_totals,.wc-block-cart__totals,.wc-block-components-totals-wrapper,[class*=cart-total],[class*=cart_total]');
    if (known) return known;

    return $$('div,section,aside').find((el) => {
      const t = low(text(el));
      const r = el.getBoundingClientRect();
      return r.width > innerWidth * 0.55 && r.height > 80 && r.height < innerHeight * 1.4 &&
        t.includes('sous-total') && (t.includes('total panier') || t.includes('total du panier') || t.includes('total:')) && moneyRe.test(t);
    }) || null;
  }

  function extractSummary(totals) {
    const whole = text(totals || document.body);
    const amounts = whole.match(/\d+[\s\u00A0]*(?:[.,]\d{2})\s*(?:€|EUR)/g) || [];
    const clean = amounts.map(norm);
    return {
      subtotal: clean[0] || '',
      total: clean[clean.length - 1] || clean[0] || ''
    };
  }

  function findCheckoutAction() {
    return $$('.wc-proceed-to-checkout a,a.checkout-button,.wc-block-cart__submit-button,a,button').find((el) => {
      const t = low(text(el));
      const href = low(el.href || '');
      return href.includes('/commande') || href.includes('/checkout') ||
        t.includes('passer à la commande') || t.includes('procéder à la commande') ||
        t.includes('commander') || t.includes('checkout');
    }) || null;
  }

  function premiumCartCss() {
    if ($('#ludorum-cart-premium-css')) return;
    const style = document.createElement('style');
    style.id = 'ludorum-cart-premium-css';
    style.textContent = `
      #ludorum-cart-premium {
        width: 100%; box-sizing: border-box; padding: 12px 14px 28px;
        font-family: Arial, sans-serif; color: ${NAVY}; background: #fff;
        display: block !important; visibility: visible !important;
        opacity: 1 !important; pointer-events: auto !important;
        position: relative !important; z-index: 2 !important;
      }
      #ludorum-cart-premium * {
        visibility: visible !important;
      }
      .ludo-cart-kicker { font-size: 11px; font-weight: 800; color: ${BLUE}; letter-spacing: .55px; text-transform: uppercase; margin: 2px 2px 4px; }
      .ludo-cart-title { font-size: 27px; line-height: 1.1; font-weight: 850; color: ${NAVY}; margin: 0 2px 7px; }
      .ludo-cart-subtitle { font-size: 14px; line-height: 1.45; color: ${MUTED}; margin: 0 2px 17px; }
      .ludo-cart-card {
        position: relative; display: grid; grid-template-columns: 88px minmax(0,1fr); gap: 0 14px;
        margin: 0 0 12px; padding: 14px; border: 1px solid ${BORDER}; border-radius: 20px;
        background: #fff; box-shadow: 0 5px 16px rgba(7,26,51,.07); box-sizing: border-box;
      }
      .ludo-cart-image { width: 88px; height: 88px; object-fit: contain; border-radius: 14px; border: 1px solid #EDF1F5; background: #F7F9FC; padding: 7px; box-sizing: border-box; grid-row: 1 / span 4; }
      .ludo-cart-image-placeholder { width: 88px; height: 88px; border-radius: 14px; border: 1px solid #EDF1F5; background: linear-gradient(135deg,#F7F9FC,#FFF); grid-row: 1 / span 4; }
      .ludo-cart-name { padding-right: 50px; font-size: 16px; line-height: 1.25; font-weight: 800; color: ${NAVY}; margin: 1px 0 5px; }
      .ludo-cart-unit { font-size: 17px; line-height: 1.2; font-weight: 850; color: ${BLUE}; margin-bottom: 10px; }
      .ludo-cart-remove {
        position: absolute; top: 9px; right: 9px;
        width: 42px; height: 42px; min-width: 42px; min-height: 42px;
        border: 1px solid #F4CACA; border-radius: 13px;
        background: #FFF3F3; color: ${RED};
        font-size: 26px; font-weight: 850; line-height: 38px;
        text-align: center; padding: 0; z-index: 20;
        touch-action: manipulation; -webkit-tap-highlight-color: transparent;
      }
      .ludo-cart-remove:active { transform: scale(.94); }
      .ludo-cart-remove:disabled { opacity: .68; }
      .ludo-cart-card.is-removing {
        opacity: .55 !important;
        pointer-events: none !important;
        transition: opacity .16s ease !important;
      }
      .ludo-cart-card.is-removing .ludo-cart-remove {
        pointer-events: auto !important;
      }
      .ludo-cart-bottom { grid-column: 2; display: flex; align-items: end; justify-content: space-between; gap: 10px; margin-top: 2px; }
      .ludo-qty-block { display: flex; flex-direction: column; gap: 5px; }
      .ludo-label { font-size: 10px; font-weight: 800; letter-spacing: .35px; text-transform: uppercase; color: ${MUTED}; }
      .ludo-stepper { display: inline-flex; align-items: center; height: 37px; border: 1px solid #D8E0EA; border-radius: 12px; overflow: hidden; background: #F8FAFC; }
      .ludo-stepper button { width: 34px; height: 37px; border: 0; background: #fff; color: ${BLUE}; font-size: 20px; font-weight: 800; padding: 0; }
      .ludo-stepper input { width: 38px; height: 37px; border: 0; border-left: 1px solid #E5EAF0; border-right: 1px solid #E5EAF0; background: #F8FAFC; color: ${NAVY}; text-align: center; font-size: 14px; font-weight: 800; padding: 0; -moz-appearance: textfield; }
      .ludo-stepper input::-webkit-outer-spin-button,.ludo-stepper input::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }
      .ludo-subtotal { text-align: right; }
      .ludo-subtotal strong { display: block; margin-top: 5px; color: ${NAVY}; font-size: 16px; font-weight: 850; }
      .ludo-summary { margin-top: 18px; padding: 18px; border: 1px solid #E5E8EE; border-radius: 21px; background: linear-gradient(135deg,#FFF9E9 0%,#FFFFFF 72%); box-shadow: 0 5px 16px rgba(7,26,51,.055); }
      .ludo-summary h2 { margin: 0 0 15px; color: ${NAVY}; font-size: 21px; line-height: 1.2; font-weight: 850; }
      .ludo-summary-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 9px 0; border-bottom: 1px solid #ECEFF4; font-size: 13px; color: ${MUTED}; }
      .ludo-summary-row strong { color: ${NAVY}; font-size: 14px; }
      .ludo-summary-row.total { border-bottom: 0; padding-top: 13px; font-weight: 800; color: ${NAVY}; }
      .ludo-summary-row.total strong { color: ${RED}; font-size: 21px; font-weight: 900; }
      .ludo-checkout { width: 100%; min-height: 51px; margin-top: 16px; border: 0; border-radius: 15px; background: ${BLUE}; color: #fff; font-size: 16px; font-weight: 850; box-shadow: 0 5px 13px rgba(11,77,187,.19); }
      .ludo-cart-empty { padding: 28px 20px; border: 1px solid ${BORDER}; border-radius: 20px; background: #F8FAFC; text-align: center; color: ${MUTED}; }
      .ludo-cart-empty strong { display: block; color: ${NAVY}; font-size: 19px; margin-bottom: 7px; }
      .ludo-return { width: 100%; min-height: 46px; margin-top: 17px; border: 1px solid ${BLUE}; border-radius: 14px; background: #fff; color: ${BLUE}; font-size: 14px; font-weight: 800; }
      .ludo-original-cart-hidden { display: none !important; }
    `;
    document.head.appendChild(style);
  }

  let rebuilding = false;
  let observerLockedUntil = 0;
  let lastCartSignature = '';

  function lockObserver(ms = 700) { observerLockedUntil = Date.now() + ms; }

  function mutationTouchesPremiumOnly(records) {
    return records.every((record) => {
      const nodes = [record.target, ...Array.from(record.addedNodes || []), ...Array.from(record.removedNodes || [])]
        .filter((node) => node && node.nodeType === 1);
      if (!nodes.length) return true;
      return nodes.every((node) => {
        const el = node;
        return !!(el.closest && (el.closest('#ludorum-cart-premium') || el.closest('#ludorum-social-dock-v10') || el.id === 'ludorum-cart-premium-css' || el.id === 'ludorum-app-premium-css'));
      });
    });
  }

  function cartSignature(items, summary) {
    const bits = items.map((product) => [product.name, product.qty && product.qty.value, product.unitPrice, product.subtotal].join('|'));
    bits.push('SUMMARY:' + (summary.subtotal || '') + '|' + (summary.total || ''));
    return bits.join('||');
  }

  function hideOriginalCartChrome(parsed, totals, checkout) {
    const shell = $('#ludorum-cart-premium');
    const wrappers = new Set();

    // Blocs WooCommerce explicitement connus.
    [
      'form.woocommerce-cart-form',
      '.woocommerce-cart-form',
      '.wc-block-cart',
      'table.shop_table.cart',
      'table.shop_table.shop_table_responsive.cart'
    ].forEach((selector) => {
      $$(selector).forEach((el) => wrappers.add(el));
    });

    if (totals) {
      wrappers.add(totals);
    }

    const originalCheckout =
      checkout &&
      checkout.closest(
        '.wc-proceed-to-checkout,.wc-block-cart__submit,.cart_totals,.cart-collaterals'
      );

    if (originalCheckout) {
      wrappers.add(originalCheckout);
    }

    // Pour les templates personnalisés, on masque seulement la ligne/article
    // produit — jamais ses grands parents.
    parsed.forEach((product) => {
      if (!product || !product.root) return;

      const specific =
        product.root.closest &&
        product.root.closest(
          '.cart_item,.woocommerce-cart-form__cart-item,.wc-block-cart-items__row,tr,li'
        );

      wrappers.add(
        specific || product.root
      );
    });

    wrappers.forEach((el) => {
      if (!el) return;

      // Sécurité absolue : ne jamais masquer le shell premium,
      // ni un parent qui le contient.
      if (
        el === shell ||
        (shell && el.contains(shell)) ||
        (shell && shell.contains(el))
      ) {
        return;
      }

      el.classList.add(
        'ludo-original-cart-hidden'
      );

      el.style.setProperty(
        'display',
        'none',
        'important'
      );

      el.style.setProperty(
        'pointer-events',
        'none',
        'important'
      );
    });

    if (shell) {
      shell.classList.remove(
        'ludo-original-cart-hidden'
      );

      shell.style.setProperty(
        'display',
        'block',
        'important'
      );

      shell.style.setProperty(
        'visibility',
        'visible',
        'important'
      );

      shell.style.setProperty(
        'opacity',
        '1',
        'important'
      );
    }
  }

  function rebuildPremiumCart() {
    if (!isCart() || rebuilding) return;
    rebuilding = true;
    lockObserver();
    try {
      premiumCartCss();
      hideCoupon();

      const parsed = findCartItems().map(extractItem);
      const totals = findTotalsBlock();
      const checkout = findCheckoutAction();
      const summary = extractSummary(totals);
      const signature = cartSignature(parsed, summary);

      let shell = $('#ludorum-cart-premium');

      // WooCommerce peut brièvement reconstruire son DOM pendant une mise à jour.
      // Si notre panier existe déjà, on le garde visible au lieu de le supprimer
      // et de fabriquer un écran vide.
      if (shell && !parsed.length) {
        shell.style.setProperty('display', 'block', 'important');
        shell.style.setProperty('visibility', 'visible', 'important');
        shell.style.setProperty('opacity', '1', 'important');
        hideCoupon();
        return;
      }

      if (shell && signature === lastCartSignature) {
        hideOriginalCartChrome(parsed, totals, checkout);
        guardCartLinks();
        hideCoupon();
        return;
      }

      if (shell) shell.remove();
      shell = document.createElement('section');
      shell.id = 'ludorum-cart-premium';

      const kicker = document.createElement('div');
      kicker.className = 'ludo-cart-kicker';
      kicker.textContent = 'Votre commande';

      const title = document.createElement('h1');
      title.className = 'ludo-cart-title';
      title.textContent = 'Votre panier';

      const subtitle = document.createElement('p');
      subtitle.className = 'ludo-cart-subtitle';
      subtitle.textContent = 'Vérifiez vos articles avant de poursuivre vers la livraison et le paiement.';

      shell.append(kicker, title, subtitle);

      if (!parsed.length) {
        const empty = document.createElement('div');
        empty.className = 'ludo-cart-empty';
        empty.innerHTML = '<strong>Votre panier est vide</strong>Ajoutez un produit depuis la boutique Ludorum.';
        const back = document.createElement('button');
        back.className = 'ludo-return';
        back.textContent = 'Découvrir les produits';
        back.addEventListener('click', () => { location.href = 'https://ludorum.fr/boutique/'; });
        empty.appendChild(back);
        shell.appendChild(empty);
      }

      parsed.forEach((product) => {
        const card = document.createElement('article');
        card.className = 'ludo-cart-card';

        if (product.imageUrl) {
          const image = document.createElement('img');
          image.className = 'ludo-cart-image';
          image.src = product.imageUrl;
          image.alt = product.name;
          card.appendChild(image);
        } else {
          const placeholder = document.createElement('div');
          placeholder.className = 'ludo-cart-image-placeholder';
          card.appendChild(placeholder);
        }

        const name = document.createElement('div');
        name.className = 'ludo-cart-name';
        name.textContent = product.name;

        const unit = document.createElement('div');
        unit.className = 'ludo-cart-unit';
        unit.textContent = product.unitPrice || product.subtotal || '';

        const remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'ludo-cart-remove';
        remove.textContent = '×';
        remove.setAttribute('aria-label', `Retirer un exemplaire de ${product.name}`);
        remove.addEventListener('click', (event) => {
          event.preventDefault();
          event.stopPropagation();

          removeCartItem(
            product,
            card,
            remove
          );
        });

        const bottom = document.createElement('div');
        bottom.className = 'ludo-cart-bottom';

        const qtyBlock = document.createElement('div');
        qtyBlock.className = 'ludo-qty-block';
        const qtyLabel = document.createElement('span');
        qtyLabel.className = 'ludo-label';
        qtyLabel.textContent = 'Quantité';

        const stepper = document.createElement('div');
        stepper.className = 'ludo-stepper';
        const minus = document.createElement('button');
        minus.type = 'button';
        minus.textContent = '−';
        const visibleQty = document.createElement('input');
        visibleQty.type = 'number';
        visibleQty.min = product.qty.min || '1';
        visibleQty.max = product.qty.max || '';
        visibleQty.value = product.qty.value || '1';
        const plus = document.createElement('button');
        plus.type = 'button';
        plus.textContent = '+';

        minus.addEventListener('click', () => {
          const next = Math.max(1, (parseInt(visibleQty.value || '1', 10) || 1) - 1);
          visibleQty.value = String(next);
          triggerCartUpdate(product.qty, next);
        });
        plus.addEventListener('click', () => {
          const currentValue = parseInt(visibleQty.value || '1', 10) || 1;
          const max = parseInt(visibleQty.max || '0', 10) || 0;
          const next = max ? Math.min(max, currentValue + 1) : currentValue + 1;
          visibleQty.value = String(next);
          triggerCartUpdate(product.qty, next);
        });
        visibleQty.addEventListener('change', () => {
          triggerCartUpdate(product.qty, visibleQty.value);
        });

        stepper.append(minus, visibleQty, plus);
        qtyBlock.append(qtyLabel, stepper);

        const subtotal = document.createElement('div');
        subtotal.className = 'ludo-subtotal';
        subtotal.innerHTML = `<span class="ludo-label">Sous-total</span><strong>${product.subtotal || product.unitPrice || ''}</strong>`;

        bottom.append(qtyBlock, subtotal);
        card.append(name, unit, remove, bottom);
        shell.appendChild(card);

      });

      if (parsed.length) {
        const block = document.createElement('section');
        block.className = 'ludo-summary';
        block.innerHTML = `
          <h2>Résumé de la commande</h2>
          <div class="ludo-summary-row"><span>Sous-total</span><strong>${summary.subtotal || '—'}</strong></div>
          <div class="ludo-summary-row"><span>Livraison</span><strong>Calculée à l'étape suivante</strong></div>
          <div class="ludo-summary-row total"><span>Total TTC</span><strong>${summary.total || summary.subtotal || '—'}</strong></div>
        `;

        const checkoutButton = document.createElement('button');
        checkoutButton.type = 'button';
        checkoutButton.className = 'ludo-checkout';
        checkoutButton.textContent = 'Passer à la commande';
        checkoutButton.addEventListener('click', () => {
          if (checkout) {
            checkout.click();
          } else {
            location.href = 'https://ludorum.fr/commande/';
          }
        });
        block.appendChild(checkoutButton);
        shell.appendChild(block);
      }

      // Le shell premium doit être le FRÈRE du panier WooCommerce,
      // jamais son enfant. Ainsi le masquage du panier original ne peut
      // plus faire disparaître l'interface Ludorum.
      const originalAnchor =
        $('form.woocommerce-cart-form') ||
        $('.woocommerce-cart-form') ||
        $('.wc-block-cart') ||
        $('table.shop_table.cart') ||
        (parsed.length ? parsed[0].root : null) ||
        totals;

      if (originalAnchor && originalAnchor.parentElement) {
        originalAnchor.parentElement.insertBefore(
          shell,
          originalAnchor
        );
      } else {
        const host =
          $('.woocommerce') ||
          $('.entry-content') ||
          $('main') ||
          $('article') ||
          document.body;

        host.insertBefore(
          shell,
          host.firstChild
        );
      }

      // Hide the original WooCommerce cart UI after extraction so only the Ludorum premium UI remains visible.
      hideOriginalCartChrome(parsed, totals, checkout);
      hideCoupon();
      lastCartSignature = signature;

    } finally {
      rebuilding = false;
    }
  }

  function guardCartLinks() {
    if (!isCart()) return;
    $$('.woocommerce-breadcrumb,.wc-block-breadcrumbs').forEach((el) => {
      el.style.setProperty('display', 'none', 'important');
    });
    $$('.product-name a,.product-thumbnail a,.wc-block-components-product-name,.wc-block-cart-item__image a').forEach((el) => {
      el.style.setProperty('pointer-events', 'none', 'important');
      el.removeAttribute('href');
    });
  }

  function forceReturnToShopLinks() {
    if (!isCart()) return;

    $$('a,button').forEach((el) => {
      const label = low(
        `${text(el)} ${el.getAttribute('aria-label') || ''}`
      );

      const href = low(
        el.href ||
        el.getAttribute('href') ||
        ''
      );

      const isReturnText =
        label.includes('revenir vers la boutique') ||
        label.includes('retour à la boutique') ||
        label.includes('retour boutique') ||
        label.includes('continuer mes achats') ||
        label.includes('continuer les achats') ||
        label.includes('continue shopping');

      const isShopHref =
        href.includes('/boutique') ||
        href.includes('/shop') ||
        href.includes('return-to-shop') ||
        href.includes('continue-shopping');

      if (!isReturnText && !isShopHref) return;

      if (el.tagName === 'A') {
        el.setAttribute(
          'href',
          'https://ludorum.fr/boutique/'
        );
      }

      if (!el.dataset.ludorumShopBound) {
        el.dataset.ludorumShopBound = '1';

        el.addEventListener(
          'click',
          (event) => {
            event.preventDefault();
            event.stopPropagation();

            location.href =
              'https://ludorum.fr/boutique/';
          },
          true
        );
      }
    });
  }

  function run() {
    addGlobalCss();
    cleanAccountCopy();
    accountTabs();
    buildSocialDock();
  }

  window.__ludorumRun = run;

  run();
  setTimeout(run, 140);
  setTimeout(run, 600);
  setTimeout(run, 1400);

  if (
    isAccount() &&
    !window.__ludorumPremiumObserver
  ) {
    let timer = null;
    window.__ludorumPremiumObserver = new MutationObserver((records) => {
      if (rebuilding || Date.now() < observerLockedUntil) return;
      if (mutationTouchesPremiumOnly(records)) return;
      clearTimeout(timer);
      timer = setTimeout(() => {
        try { run(); } catch (_) {}
      }, 260);
    });
    window.__ludorumPremiumObserver.observe(
      document.body,
      {
        childList: true,
        subtree: true
      }
    );
  }
})();
