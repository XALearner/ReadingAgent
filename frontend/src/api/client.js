const API_BASE = import.meta.env.VITE_API_BASE || '/api';

async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, options);
  } catch (error) {
    throw new Error(`无法连接后端服务：请确认 ${API_BASE} 可访问，且后端 CORS 配置允许当前前端地址。`);
  }
  if (!response.ok) {
    let message = `请求失败：${response.status}`;
    try {
      const data = await response.json();
      message = data.message || message;
    } catch {
      // Keep the generic message when the backend did not return JSON.
    }
    throw new Error(message);
  }
  if (response.status === 204) {
    return null;
  }
  return response.json();
}

export const api = {
  listBooks: () => request('/books'),
  uploadBook: (formData) => request('/books', { method: 'POST', body: formData }),
  deleteBook: (bookId) => request(`/books/${bookId}`, { method: 'DELETE' }),
  listChapters: (bookId) => request(`/books/${bookId}/chapters`),
  getChapter: (chapterId) => request(`/books/chapters/${chapterId}`),
  ask: (bookId, payload) =>
    request(`/books/${bookId}/ai/ask`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }),
  reindexBook: (bookId) => request(`/books/${bookId}/ai/reindex`, { method: 'POST' }),
  listHighlights: (bookId) => request(`/books/${bookId}/highlights`),
  createHighlight: (bookId, payload) =>
    request(`/books/${bookId}/highlights`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }),
  saveProgress: (bookId, payload) =>
    request(`/books/${bookId}/progress`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }),
};
