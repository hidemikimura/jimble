/**
 * vendor/lit.js の型
 *
 * <p>
 * 実体は esbuild で束ねた lit 3.x。型だけ npm の lit から借りる
 * （lit は devDependencies に入っており、実行時には使わない）。
 * </p>
 */
export { LitElement, html, css, render, nothing, noChange, svg } from 'lit';
export type { TemplateResult, CSSResult, PropertyValues, PropertyDeclarations } from 'lit';
export { repeat } from 'lit/directives/repeat.js';
export { classMap } from 'lit/directives/class-map.js';
export { styleMap } from 'lit/directives/style-map.js';
