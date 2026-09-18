const baseUrl = process.argv[2] || 'http://localhost:8088';
const sseResponse = await fetch(`${baseUrl}/mcp/sse`, {
  headers: { Accept: 'text/event-stream' },
});

if (!sseResponse.ok || !sseResponse.body) {
  throw new Error(`MCP SSE connection failed: ${sseResponse.status}`);
}

const pendingResponses = new Map();
let resolveEndpoint;
let rejectEndpoint;
const endpointPromise = new Promise((resolve, reject) => {
  resolveEndpoint = resolve;
  rejectEndpoint = reject;
});

const reader = sseResponse.body.getReader();
const decoder = new TextDecoder();
let buffer = '';

const readLoop = (async () => {
  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      throw new Error('MCP SSE connection closed unexpectedly');
    }
    buffer += decoder.decode(value, { stream: true }).replaceAll('\r\n', '\n');

    let eventEnd = buffer.indexOf('\n\n');
    while (eventEnd >= 0) {
      const eventBlock = buffer.slice(0, eventEnd);
      buffer = buffer.slice(eventEnd + 2);
      const lines = eventBlock.split('\n');
      const event = lines.find((line) => line.startsWith('event:'))?.slice(6).trim();
      const data = lines
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).trimStart())
        .join('\n');

      if (event === 'endpoint') {
        resolveEndpoint(data);
      } else if (event === 'message' && data) {
        const message = JSON.parse(data);
        pendingResponses.get(message.id)?.(message);
      }
      eventEnd = buffer.indexOf('\n\n');
    }
  }
})().catch((error) => rejectEndpoint(error));

const endpoint = await Promise.race([
  endpointPromise,
  new Promise((_, reject) => setTimeout(() => reject(new Error('Timed out waiting for MCP endpoint')), 5_000)),
]);
const messageUrl = new URL(endpoint, baseUrl);

async function post(message) {
  const response = await fetch(messageUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(message),
  });
  if (!response.ok) {
    throw new Error(`MCP message failed: ${response.status} ${await response.text()}`);
  }
}

async function request(id, method, params = {}) {
  const responsePromise = new Promise((resolve) => pendingResponses.set(id, resolve));
  await post({ jsonrpc: '2.0', id, method, params });
  const response = await Promise.race([
    responsePromise,
    new Promise((_, reject) => setTimeout(() => reject(new Error(`Timed out waiting for ${method}`)), 5_000)),
  ]);
  pendingResponses.delete(id);
  if (response.error) {
    throw new Error(`${method} failed: ${JSON.stringify(response.error)}`);
  }
  return response.result;
}

try {
  const initialized = await request(1, 'initialize', {
    protocolVersion: '2024-11-05',
    capabilities: {},
    clientInfo: { name: 'reading-agent-smoke-test', version: '1.0.0' },
  });
  await post({ jsonrpc: '2.0', method: 'notifications/initialized' });
  const tools = await request(2, 'tools/list');
  const books = await request(3, 'tools/call', {
    name: 'list_books',
    arguments: {},
  });
  if (books.isError) {
    throw new Error(`list_books failed: ${JSON.stringify(books.content)}`);
  }

  console.log(`Connected to ${initialized.serverInfo.name} ${initialized.serverInfo.version}`);
  console.log(`Tools (${tools.tools.length}): ${tools.tools.map((tool) => tool.name).join(', ')}`);
  console.log('list_books call: OK');
} finally {
  await reader.cancel();
  await readLoop.catch(() => {});
}
