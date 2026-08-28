import assert from 'node:assert/strict';
import test from 'node:test';
import {compactDokkaHtml} from '../compact-dokka-html.mjs';

test('Dokka compaction removes generated indentation and preserves literal blocks', () => {
  const source = [
    '<html>',
    '    <body>',
    '      <p>Text</p>',
    '      <pre><code>sample(',
    '    indented = true,',
    ')</code></pre>',
    '      <script>',
    '    const value = true;',
    '      </script>',
    '    </body>',
    '</html>',
  ].join('\n');

  const compacted = compactDokkaHtml(source);

  assert.match(compacted, /\n<body>\n<p>Text<\/p>/u);
  assert.match(compacted, /<pre><code>sample\(\n    indented = true,\n\)<\/code><\/pre>/u);
  assert.match(compacted, /<script>\n    const value = true;\n      <\/script>/u);
});

