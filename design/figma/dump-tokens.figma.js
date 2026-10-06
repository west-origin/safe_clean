// design/figma/dump-tokens.figma.js
// Figma 플러그인 API로 실행하는 "읽기 전용" 스크립트입니다. 파일을 바꾸지 않습니다.
//
// 실행 방법 (둘 중 하나)
//   1) Claude + Figma MCP: use_figma 도구에 이 코드를 그대로 넣어 실행
//   2) Figma 커뮤니티 플러그인 "Scripter"에 붙여넣고 실행
//
// 반환된 JSON을 그대로 design/figma-tokens.json 에 저장한 뒤
//   npm run tokens
// 를 실행하면 packages/ui/src/tokens/ 아래 CSS·JS 파일이 다시 생성됩니다.
// 값을 손으로 옮겨 적지 마세요. 이 스크립트 → JSON → 생성기 순서가 유일한 경로입니다.

const COLLECTION_NAME = 'Design Tokens';

const toHex = (c) => {
  const h = (x) => Math.round(x * 255).toString(16).padStart(2, '0');
  const alpha = c.a !== undefined && c.a < 1 ? h(c.a) : '';
  return '#' + h(c.r) + h(c.g) + h(c.b) + alpha;
};

const cols = await figma.variables.getLocalVariableCollectionsAsync();
const col = cols.find((c) => c.name === COLLECTION_NAME);
if (!col) {
  throw new Error(`컬렉션 "${COLLECTION_NAME}" 없음. 있는 것: ` + cols.map((c) => c.name).join(', '));
}

const variables = [];
for (const id of col.variableIds) {
  const v = await figma.variables.getVariableByIdAsync(id);
  const values = {};
  for (const m of col.modes) {
    let val = v.valuesByMode[m.modeId];
    if (val && typeof val === 'object' && val.type === 'VARIABLE_ALIAS') {
      const target = await figma.variables.getVariableByIdAsync(val.id);
      val = { alias: target ? target.name : val.id };
    } else if (val && typeof val === 'object' && 'r' in val) {
      val = toHex(val);
    }
    values[m.name] = val;
  }
  variables.push({
    name: v.name,
    type: v.resolvedType,
    scopes: v.scopes,
    description: v.description || '',
    values,
  });
}
variables.sort((a, b) => a.name.localeCompare(b.name));

const textStyles = (await figma.getLocalTextStylesAsync())
  .map((s) => ({
    name: s.name,
    family: s.fontName.family,
    style: s.fontName.style,
    size: s.fontSize,
    lineHeight: s.lineHeight,
    letterSpacing: s.letterSpacing,
    description: s.description || '',
  }))
  .sort((a, b) => a.name.localeCompare(b.name));

return {
  source: {
    fileName: figma.root.name,
    collection: col.name,
    collectionId: col.id,
  },
  modes: col.modes.map((m) => m.name),
  variables,
  textStyles,
};
