import { it, expect } from 'vitest';
import { cspPlugin } from './vite-csp-plugin.js';
it('고객 빌드에서만 공식 PG SDK와 프레임을 허용한다', () => {
  const html = '<meta charset="UTF-8" />';
  expect(cspPlugin({ payments: true }).transformIndexHtml(html)).toContain(
    'https://js.tosspayments.com'
  );
  expect(cspPlugin().transformIndexHtml(html)).not.toContain('tosspayments.com');
  expect(() => cspPlugin().transformIndexHtml('')).toThrow('CSP');
});
