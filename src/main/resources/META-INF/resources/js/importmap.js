// Import map for the Red Hat Design System web components. The files are served from the mvnpm jars
// declared in pom.xml; keep the versions below in step with the versions there.
(() => {
  const s = '/_static';
  const map = {
    imports: {
      'lit': `${s}/lit/3.3.3/index.js`,
      'lit/': `${s}/lit/3.3.3/`,
      'lit-html': `${s}/lit-html/3.3.3/lit-html.js`,
      'lit-html/': `${s}/lit-html/3.3.3/`,
      'lit-element': `${s}/lit-element/4.2.2/index.js`,
      'lit-element/': `${s}/lit-element/4.2.2/`,
      '@lit/reactive-element': `${s}/reactive-element/2.1.2/reactive-element.js`,
      '@lit/reactive-element/': `${s}/reactive-element/2.1.2/`,
      '@lit/context': `${s}/context/1.1.6/index.js`,
      '@lit/context/': `${s}/context/1.1.6/`,
      '@lit-labs/ssr-dom-shim': `${s}/at/lit-labs/labs/ssr-dom-shim/1.6.0/index.js`,
      'tslib': `${s}/tslib/2.8.1/tslib.es6.js`,
      '@patternfly/pfe-core': `${s}/at/patternfly/pfe-core/6.0.0/core.js`,
      '@patternfly/pfe-core/': `${s}/at/patternfly/pfe-core/6.0.0/`,
      '@rhds/tokens': `${s}/at/rhds/tokens/3.2.0/js/tokens.js`,
      '@rhds/tokens/css/': `${s}/at/rhds/tokens/3.2.0/css/`,
      '@rhds/tokens/': `${s}/at/rhds/tokens/3.2.0/js/`,
      '@rhds/icons': `${s}/at/rhds/icons/2.3.1/icons.js`,
      '@rhds/icons/': `${s}/at/rhds/icons/2.3.1/`,
      '@rhds/elements/lib/': `${s}/at/rhds/elements/5.0.0/lib/`,
      '@rhds/elements/': `${s}/at/rhds/elements/5.0.0/elements/`,
    },
  };
  const script = document.createElement('script');
  script.type = 'importmap';
  script.textContent = JSON.stringify(map);
  document.currentScript.after(script);
})();
