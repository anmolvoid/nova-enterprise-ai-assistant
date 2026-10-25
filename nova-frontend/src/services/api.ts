const API_BASE_URL = 'http://localhost:8080/api/v1';

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

async function getErrorMessage(response: Response): Promise<string> {
  const body = await response.text();
  return body || `Request failed with status ${response.status}`;
}

export async function sendMessage(
  sessionId: string,
  prompt: string
): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/chat`, {
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
  const response = await fetch(`${API_BASE_URL}/chat/stream`, {
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

    // Process any final event that was not followed by a blank line.
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

  const response = await fetch(`${API_BASE_URL}/documents/upload`, {
    method: 'POST',
    body: formData,
  });

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  return response.text();
}

export async function getDocuments(): Promise<DocumentUpload[]> {
  const response = await fetch(`${API_BASE_URL}/documents`);

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  return response.json();
}

export async function getDocument(
  documentId: string
): Promise<DocumentUpload> {
  const response = await fetch(
    `${API_BASE_URL}/documents/${encodeURIComponent(documentId)}`
  );

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  return response.json();
}

export async function deleteDocument(documentId: string): Promise<string> {
  const response = await fetch(
    `${API_BASE_URL}/documents/${encodeURIComponent(documentId)}`,
    { method: 'DELETE' }
  );

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  return response.text();
}
