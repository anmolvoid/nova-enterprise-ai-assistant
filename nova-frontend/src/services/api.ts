
const API_BASE_URL = 'http://localhost:8080/api/v1';

const TOKEN_KEY = 'nova-token';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
  role: 'USER' | 'ADMIN';
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  userId: number;
  name: string;
  email: string;
  role: 'USER' | 'ADMIN';
}

export interface ChatRequest {
  sessionId: string;
  prompt: string;
}

export interface ChatResponse {
  response: string;
}

export interface DocumentUpload {
  documentId: string;
  filename: string;
  uploadedAt: string;
  chunkCount: number;
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function saveToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

export function getAuthHeaders(): HeadersInit {
  const token = getToken();

  return token
    ? { Authorization: `Bearer ${token}` }
    : {};
}

async function getErrorMessage(response: Response): Promise<string> {
  const body = await response.text();

  if (response.status === 401) {
    return 'Your session has expired or you are not logged in. Please log in again.';
  }

  if (response.status === 403) {
    return 'Access denied. You do not have permission to perform this action.';
  }

  return body || `Request failed with status ${response.status}`;
}

async function authenticatedFetch(
  url: string,
  init: RequestInit = {}
): Promise<Response> {
  const headers = new Headers(init.headers);
  const authHeaders = new Headers(getAuthHeaders());

  authHeaders.forEach((value, key) => {
    headers.set(key, value);
  });

  return fetch(url, {
    ...init,
    headers,
  });
}

export async function login(
  request: LoginRequest
): Promise<AuthResponse> {
  const response = await fetch(`${API_BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  const data: AuthResponse = await response.json();
  saveToken(data.token);

  return data;
}

export async function register(
  request: RegisterRequest
): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  return response.text();
}

export async function sendMessage(
  sessionId: string,
  prompt: string
): Promise<string> {
  const response = await authenticatedFetch(`${API_BASE_URL}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionId, prompt } satisfies ChatRequest),
  });

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  const data: ChatResponse = await response.json();
  return data.response;
}

export async function streamMessage(
  sessionId: string,
  prompt: string,
  onToken: (token: string) => void
): Promise<void> {
  const response = await authenticatedFetch(`${API_BASE_URL}/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify({ sessionId, prompt } satisfies ChatRequest),
  });

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  if (!response.body) {
    throw new Error('Streaming is not supported by this response.');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  function processEvent(event: string): void {
    const dataLines = event
      .split(/\r\n|\n|\r/)
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).replace(/^ /, ''));

    if (dataLines.length === 0) return;

    const data = dataLines.join('\n');

    try {
      const payload = JSON.parse(data) as {
        type?: string;
        content?: string;
        token?: string;
        text?: string;
      };

      if (payload.type === 'token') {
        const text = payload.content ?? payload.token ?? payload.text ?? '';
        if (text) onToken(text);
      }
    } catch {
      // Ignore malformed events.
    }
  }

  try {
    while (true) {
      const { value, done } = await reader.read();

      if (done) {
        buffer += decoder.decode();
        break;
      }

      buffer += decoder.decode(value, { stream: true });

      const events = buffer.split(/\r\n\r\n|\n\n|\r\r/);
      buffer = events.pop() ?? '';

      for (const event of events) {
        processEvent(event);
      }
    }

    if (buffer.trim()) {
      processEvent(buffer);
    }
  } finally {
    reader.releaseLock();
  }
}

export async function uploadDocument(file: File): Promise<string> {
  const formData = new FormData();
  formData.append('file', file);

  const response = await authenticatedFetch(
    `${API_BASE_URL}/documents/upload`,
    {
      method: 'POST',
      body: formData,
    }
  );

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  return response.text();
}

export async function getDocuments(): Promise<DocumentUpload[]> {
  const response = await authenticatedFetch(`${API_BASE_URL}/documents`);

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  return response.json();
}

export async function getDocument(
  documentId: string
): Promise<DocumentUpload> {
  const response = await authenticatedFetch(
    `${API_BASE_URL}/documents/${encodeURIComponent(documentId)}`
  );

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  return response.json();
}

export async function deleteDocument(documentId: string): Promise<string> {
  const response = await authenticatedFetch(
    `${API_BASE_URL}/documents/${encodeURIComponent(documentId)}`,
    { method: 'DELETE' }
  );

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  return response.text();
}
