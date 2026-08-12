/**
 * 增量解析 fetch 流中的 SSE block，保留跨 chunk 缓冲与 Last-Event-ID。
 */
export function consumeSseChunk(previousBuffer = '', chunk = '', flush = false) {
  let buffer = `${previousBuffer || ''}${chunk || ''}`.replaceAll('\r\n', '\n');
  const blocks = buffer.split('\n\n');
  buffer = flush ? '' : (blocks.pop() || '');
  if (flush && blocks.length === 0 && previousBuffer) {
    blocks.push(`${previousBuffer}${chunk || ''}`.replaceAll('\r\n', '\n'));
  } else if (flush && buffer.trim()) {
    blocks.push(buffer);
    buffer = '';
  }
  return {
    buffer,
    messages: blocks.map(parseSseBlock).filter(Boolean)
  };
}

function parseSseBlock(block) {
  if (!block || !block.trim()) return null;
  let id = '';
  let event = '';
  const data = [];
  for (const line of block.split('\n')) {
    if (line.startsWith(':')) continue;
    if (line.startsWith('id:')) id = line.slice(3).trim();
    else if (line.startsWith('event:')) event = line.slice(6).trim();
    else if (line.startsWith('data:')) data.push(line.slice(5).trimStart());
  }
  if (data.length === 0) return null;
  return { id, event, data: data.join('\n') };
}
