import { useEffect, useRef, useState } from 'react';
import {
  Bot,
  FileText,
  LayoutDashboard,
  MessageSquare,
  Paperclip,
  Plus,
  RefreshCw,
  Send,
  ShieldCheck,
  Sparkles,
  Trash2,
  Upload,
  User,
  X,
  Database,
} from 'lucide-react';

import {
  clearToken,
  deleteDocument,
  getDocuments,
  getToken,
  login,
  register,
  streamMessage,
  uploadDocument,
  type DocumentUpload,
} from './services/api';

import './App.css';

type Message = {
  id: string;
  role: 'user' | 'assistant';
  content: string;
};

type View = 'chat' | 'knowledge';
type AuthMode = 'login' | 'register';
type AccountRole = 'USER' | 'ADMIN';

type AuthUser = { email: string; role: string };

function readUserFromToken(token: string | null): AuthUser | null {
  if (!token) return null;
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const claims = JSON.parse(atob(normalized)) as {
      sub?: string; email?: string; role?: string; exp?: number;
    };
    if (claims.exp && claims.exp * 1000 <= Date.now()) return null;
    return { email: claims.email ?? claims.sub ?? 'Signed-in user', role: (claims.role ?? 'USER').replace(/^ROLE_/, '').toUpperCase() };
  } catch {
    return null;
  }
}

function createId() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
}

function getSessionId() {
  const key = 'nova-session-id';
  let sessionId = localStorage.getItem(key);

  if (!sessionId) {
    sessionId = crypto.randomUUID();
    localStorage.setItem(key, sessionId);
  }

  return sessionId;
}

function App() {
  const [token, setToken] = useState<string | null>(() => getToken());
  const [authUser, setAuthUser] = useState<AuthUser | null>(() => readUserFromToken(getToken()));
  const [authMode, setAuthMode] = useState<AuthMode>('login');
  const [authName, setAuthName] = useState('');
  const [authEmail, setAuthEmail] = useState('');
  const [authPassword, setAuthPassword] = useState('');
  const [authRole, setAuthRole] = useState<AccountRole>('USER');
  const [authBusy, setAuthBusy] = useState(false);
  const [authError, setAuthError] = useState('');
  const [view, setView] = useState<View>('chat');
  const [messages, setMessages] = useState<Message[]>([]);
  const [prompt, setPrompt] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState('');
  const [documents, setDocuments] = useState<DocumentUpload[]>([]);
  const [isLoadingDocuments, setIsLoadingDocuments] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadMessage, setUploadMessage] = useState('');
  const [selectedDocument, setSelectedDocument] =
    useState<DocumentUpload | null>(null);
  const [sessionId] = useState(getSessionId);
  const [dragActive, setDragActive] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const promptRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  useEffect(() => {
    const currentToken = getToken();
    const currentUser = readUserFromToken(currentToken);
    if (!currentToken || !currentUser) {
      clearToken();
      setToken(null);
      setAuthUser(null);
    } else {
      setToken(currentToken);
      setAuthUser(currentUser);
    }
  }, []);

  useEffect(() => {
    if (token && view === 'knowledge') {
      void loadDocuments();
    }
  }, [view]);

  async function handleAuthSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAuthBusy(true);
    setAuthError('');
    try {
      if (authMode === 'register') {
        await register({ name: authName.trim(), email: authEmail.trim(), password: authPassword, role: authRole });
      }
      await login({ email: authEmail.trim(), password: authPassword });
      const savedToken = getToken();
      const user = readUserFromToken(savedToken);
      if (!savedToken || !user) {
        throw new Error('Login succeeded, but the JWT token or user role could not be read. Check the login response and JWT claims in api.ts/backend.');
      }
      setToken(savedToken);
      setAuthUser(user);
      setAuthPassword('');
      setAuthError('');
    } catch (err) {
      setAuthError(err instanceof Error ? err.message : 'Authentication failed. Please try again.');
    } finally {
      setAuthBusy(false);
    }
  }

  function handleLogout() {
    clearToken();
    setToken(null);
    setAuthUser(null);
    setAuthPassword('');
    setMessages([]);
    setDocuments([]);
    setSelectedDocument(null);
    setView('chat');
    setError('');
    setUploadMessage('');
    setAuthMode('login');
  }

  async function loadDocuments() {
    setIsLoadingDocuments(true);
    setError('');

    try {
      const result = await getDocuments();
      setDocuments(result);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Could not load documents. Check that the backend is running.'
      );
    } finally {
      setIsLoadingDocuments(false);
    }
  }

  function startNewChat() {
    setMessages([]);
    setPrompt('');
    setError('');
    promptRef.current?.focus();
  }

  async function handleSend(event?: React.FormEvent) {
    event?.preventDefault();

    const text = prompt.trim();
    if (!text || isSending) return;

    setError('');
    setPrompt('');
    setIsSending(true);

    const userMessage: Message = {
      id: createId(),
      role: 'user',
      content: text,
    };

    const assistantId = createId();

    setMessages((current) => [
      ...current,
      userMessage,
      { id: assistantId, role: 'assistant', content: '' },
    ]);

    try {
      await streamMessage(sessionId, text, (token) => {
        setMessages((current) =>
          current.map((message) =>
            message.id === assistantId
              ? { ...message, content: message.content + token }
              : message
          )
        );
      });
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Something went wrong.';

      setMessages((current) =>
        current.map((item) =>
          item.id === assistantId
            ? {
                ...item,
                content:
                  item.content ||
                  'I could not complete the request. Please try again.',
              }
            : item
        )
      );

      setError(message);
    } finally {
      setIsSending(false);
      promptRef.current?.focus();
    }
  }

  async function handleFiles(files: FileList | File[]) {
    const fileList = Array.from(files);
    if (fileList.length === 0) return;

    const supportedExtensions = ['.pdf', '.doc', '.docx', '.txt'];
    const invalidFiles = fileList.filter((file) => {
      const name = file.name.toLowerCase();
      return !supportedExtensions.some((extension) =>
        name.endsWith(extension)
      );
    });

    if (invalidFiles.length > 0) {
      setError('Supported formats are PDF, DOC, DOCX, and TXT.');
      return;
    }

    setIsUploading(true);
    setError('');
    setUploadMessage('');

    const results: string[] = [];

    try {
      for (const file of fileList) {
        const result = await uploadDocument(file);
        results.push(result);
      }

      setUploadMessage(results.join('\n'));
      await loadDocuments();
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Document upload failed.'
      );
    } finally {
      setIsUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  }

  async function handleDelete(document: DocumentUpload) {
    const confirmed = window.confirm(
      `Delete "${document.filename}" and its indexed vectors?`
    );

    if (!confirmed) return;

    setError('');

    try {
      await deleteDocument(document.documentId);
      setDocuments((current) =>
        current.filter((item) => item.documentId !== document.documentId)
      );
      setSelectedDocument(null);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Could not delete the document.'
      );
    }
  }

  function formatDate(date: string) {
    const parsed = new Date(date);

    if (Number.isNaN(parsed.getTime())) return date;

    return parsed.toLocaleString(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
    });
  }

  if (!token || !authUser) {
    return (
      <div className="auth-screen">
        <div className="auth-card">
          <div className="auth-brand-mark"><Sparkles size={25} /></div>
          <div className="auth-eyebrow">AI WORKSPACE</div>
          <h1>NOVA</h1>
          <p className="auth-subtitle">Your knowledge, amplified.</p>
          <div className="auth-tabs">
            <button type="button" className={authMode === 'login' ? 'active' : ''} onClick={() => { setAuthMode('login'); setAuthError(''); }}>Sign in</button>
            <button type="button" className={authMode === 'register' ? 'active' : ''} onClick={() => { setAuthMode('register'); setAuthError(''); }}>Create account</button>
          </div>
          <form className="auth-form" onSubmit={(event) => void handleAuthSubmit(event)}>
            {authMode === 'register' && (
              <label>
                Full name
                <input autoComplete="name" value={authName} onChange={(event) => setAuthName(event.target.value)} required maxLength={100} placeholder="Your name" />
              </label>
            )}
            <label>
              Email address
              <input type="email" autoComplete="email" value={authEmail} onChange={(event) => setAuthEmail(event.target.value)} required placeholder="you@example.com" />
            </label>
            {authMode === 'register' && (
              <label>
                Account role
                <select
                  value={authRole}
                  onChange={(event) => setAuthRole(event.target.value as AccountRole)}
                  required
                >
                  <option value="USER">User</option>
                  <option value="ADMIN">Admin</option>
                </select>
              </label>
            )}
            <label>
              Password
              <input type="password" autoComplete={authMode === 'login' ? 'current-password' : 'new-password'} value={authPassword} onChange={(event) => setAuthPassword(event.target.value)} required minLength={authMode === 'register' ? 8 : undefined} placeholder="Enter your password" />
            </label>
            {authError && <div className="auth-error" role="alert">{authError}</div>}
            <button className="auth-submit" type="submit" disabled={authBusy}>
              {authBusy ? 'Please wait…' : authMode === 'login' ? 'Sign in to NOVA' : 'Create account'}
            </button>
          </form>
          <p className="auth-footnote">Secure access to your AI workspace.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="nova-app">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">
            <Sparkles size={22} />
          </div>
          <div>
            <div className="brand-name">NOVA</div>
            <div className="brand-caption">AI WORKSPACE</div>
          </div>
        </div>

        <button type="button" className="new-chat-button" onClick={startNewChat}>
          <Plus size={18} />
          <span>New conversation</span>
        </button>

        <div className="sidebar-label">WORKSPACE</div>

        <nav className="sidebar-nav">
          <button
            type="button"
            className={`nav-item ${view === 'chat' ? 'active' : ''}`}
            onClick={() => setView('chat')}
          >
            <MessageSquare size={18} />
            <span>AI Assistant</span>
          </button>

          <button
            type="button"
            className={`nav-item ${view === 'knowledge' ? 'active' : ''}`}
            onClick={() => setView('knowledge')}
          >
            <Database size={18} />
            <span>Knowledge Base</span>
            <span className="nav-count">{documents.length}</span>
          </button>
        </nav>

        <div className="sidebar-spacer" />

        <div className="sidebar-status">
          <span className="status-dot" />
          <div>
            <strong>NOVA workspace</strong>
            <span>Local development</span>
          </div>
        </div>

        <div className="sidebar-footer">
          <ShieldCheck size={15} />
          <span>Built for your knowledge</span>
        </div>
      </aside>

      <main className="main-panel">
        <header className="topbar">
          <div className="breadcrumb">
            <LayoutDashboard size={17} />
            <span className="breadcrumb-divider">/</span>
            <span>{view === 'chat' ? 'AI Assistant' : 'Knowledge Base'}</span>
          </div>

          <div className="topbar-right">
            <span className="connection-indicator">
              <span className="status-dot" />
              API workspace
            </span>
            <div className="user-session" title={`${authUser.email} · ${authUser.role}`}>
              <div className="avatar"><User size={17} /></div>
              <div className="user-session-copy"><strong>{authUser.email}</strong><span>{authUser.role}</span></div>
              <button type="button" className="logout-button" onClick={handleLogout}>Log out</button>
            </div>
          </div>
        </header>

        {view === 'chat' ? (
          <section className="chat-layout">
            <div className="chat-content">
              {messages.length === 0 ? (
                <div className="welcome">
                  <div className="welcome-icon">
                    <Sparkles size={27} />
                  </div>

                  <div className="eyebrow">YOUR AI WORKSPACE</div>
                  <h1>
                    Meet your knowledge,
                    <br />
                    <span>amplified.</span>
                  </h1>
                  <p className="welcome-description">
                    Ask questions, explore your documents, and turn information
                    into useful answers with NOVA.
                  </p>

                  <div className="suggestion-grid">
                    <button
                      className="suggestion-card"
                      onClick={() =>
                        setPrompt(
                          'Summarize the key information in my uploaded documents.'
                        )
                      }
                    >
                      <FileText size={19} />
                      <strong>Summarize documents</strong>
                      <span>Find the main ideas and takeaways.</span>
                    </button>

                    <button
                      className="suggestion-card"
                      onClick={() =>
                        setPrompt(
                          'Explain the main concepts in my knowledge base.'
                        )
                      }
                    >
                      <Database size={19} />
                      <strong>Explore my knowledge</strong>
                      <span>Ask questions about indexed content.</span>
                    </button>

                    <button
                      className="suggestion-card"
                      onClick={() =>
                        setPrompt(
                          'Help me understand a technical concept step by step.'
                        )
                      }
                    >
                      <Bot size={19} />
                      <strong>Learn something new</strong>
                      <span>Get a clear, structured explanation.</span>
                    </button>
                  </div>
                </div>
              ) : (
                <div className="messages">
                  {messages.map((message) => (
                    <article
                      className={`message-row ${message.role}`}
                      key={message.id}
                    >
                      <div
                        className={`message-avatar ${message.role}`}
                        aria-hidden="true"
                      >
                        {message.role === 'assistant' ? (
                          <Sparkles size={17} />
                        ) : (
                          <User size={17} />
                        )}
                      </div>

                      <div className="message-body">
                        <div className="message-author">
                          {message.role === 'assistant' ? 'NOVA' : 'You'}
                        </div>
                        <div className="message-text">
                          {message.content ||
                            (isSending && message.role === 'assistant' ? (
                              <span className="typing-indicator">
                                <i />
                                <i />
                                <i />
                              </span>
                            ) : null)}
                        </div>
                      </div>
                    </article>
                  ))}
                  <div ref={bottomRef} />
                </div>
              )}

              {error && (
                <div className="error-banner" role="alert">
                  <span>{error}</span>
                  <button type="button" onClick={() => setError('')} aria-label="Dismiss error">
                    <X size={16} />
                  </button>
                </div>
              )}

              {uploadMessage && (
                <div className="success-banner">
                  <span>{uploadMessage}</span>
                  <button
                    type="button"
                    onClick={() => setUploadMessage('')}
                    aria-label="Dismiss upload message"
                  >
                    <X size={16} />
                  </button>
                </div>
              )}
            </div>

            <div className="composer-area">
              <form className="composer" onSubmit={handleSend}>
                <textarea
                  ref={promptRef}
                  value={prompt}
                  onChange={(event) => setPrompt(event.target.value)}
                  onKeyDown={(event) => {
                    if (
                      event.key === 'Enter' &&
                      !event.shiftKey &&
                      !event.nativeEvent.isComposing
                    ) {
                      event.preventDefault();
                      void handleSend();
                    }
                  }}
                  placeholder="Ask NOVA anything..."
                  rows={1}
                  disabled={isSending}
                  aria-label="Message NOVA"
                />

                <div className="composer-toolbar">
                  <div className="composer-hint">
                    <span>↵</span> to send · Shift + ↵ for a new line
                  </div>

                  <div className="composer-actions">
                    <button
                      type="button"
                      className="icon-button"
                      title="Upload documents"
                      onClick={() => fileInputRef.current?.click()}
                      disabled={isUploading}
                    >
                      <Paperclip size={18} />
                    </button>

                    <button
                      className="send-button"
                      type="submit"
                      disabled={!prompt.trim() || isSending}
                      aria-label="Send message"
                    >
                      {isSending ? (
                        <RefreshCw className="spin" size={17} />
                      ) : (
                        <Send size={17} />
                      )}
                    </button>
                  </div>
                </div>
              </form>

              <p className="disclaimer">
                NOVA can make mistakes. Verify important information.
              </p>
            </div>
          </section>
        ) : (
          <section className="knowledge-layout">
            <div className="knowledge-heading">
              <div>
                <div className="eyebrow">YOUR CONTENT</div>
                <h1>Knowledge Base</h1>
                <p>
                  Manage the documents NOVA can use to answer your questions.
                </p>
              </div>

              <button
                type="button"
                className="primary-button"
                onClick={() => fileInputRef.current?.click()}
                disabled={isUploading}
              >
                <Upload size={17} />
                {isUploading ? 'Uploading…' : 'Upload documents'}
              </button>
            </div>

            <div
              className={`upload-dropzone ${dragActive ? 'drag-active' : ''}`}
              onDragOver={(event) => {
                event.preventDefault();
                setDragActive(true);
              }}
              onDragLeave={() => setDragActive(false)}
              onDrop={(event) => {
                event.preventDefault();
                setDragActive(false);
                void handleFiles(event.dataTransfer.files);
              }}
            >
              <div className="dropzone-icon">
                <Upload size={22} />
              </div>
              <strong>Drop files here to upload</strong>
              <span>PDF, DOC, DOCX, and TXT files are supported.</span>
              <button
                type="button"
                className="text-button"
                onClick={() => fileInputRef.current?.click()}
                disabled={isUploading}
              >
                Browse files
              </button>
            </div>

            {error && (
              <div className="error-banner" role="alert">
                <span>{error}</span>
                <button onClick={() => setError('')} aria-label="Dismiss error">
                  <X size={16} />
                </button>
              </div>
            )}

            {uploadMessage && (
              <div className="success-banner">
                <span>{uploadMessage}</span>
                <button
                  onClick={() => setUploadMessage('')}
                  aria-label="Dismiss upload message"
                >
                  <X size={16} />
                </button>
              </div>
            )}

            <div className="documents-header">
              <div>
                <h2>Indexed documents</h2>
                <span>{documents.length} documents</span>
              </div>
              <button
                type="button"
                className="icon-button refresh-button"
                onClick={() => void loadDocuments()}
                disabled={isLoadingDocuments}
                title="Refresh documents"
              >
                <RefreshCw
                  size={17}
                  className={isLoadingDocuments ? 'spin' : ''}
                />
              </button>
            </div>

            <div className="documents-panel">
              {isLoadingDocuments ? (
                <div className="empty-state">
                  <RefreshCw className="spin" size={22} />
                  <p>Loading your documents…</p>
                </div>
              ) : documents.length === 0 ? (
                <div className="empty-state">
                  <div className="dropzone-icon">
                    <Database size={22} />
                  </div>
                  <strong>Your knowledge base is empty</strong>
                  <p>Upload a document to get started.</p>
                </div>
              ) : (
                documents.map((document) => (
                  <div className="document-row" key={document.documentId}>
                    <div className="document-icon">
                      <FileText size={20} />
                    </div>

                    <button
                      type="button"
                      className="document-info"
                      onClick={() => setSelectedDocument(document)}
                    >
                      <strong>{document.filename}</strong>
                      <span>
                        {document.chunkCount} chunks ·{' '}
                        {formatDate(document.uploadedAt)}
                      </span>
                    </button>

                    {authUser.role === 'ADMIN' && (
                      <button
                        type="button"
                        className="icon-button delete-button"
                        onClick={() => void handleDelete(document)}
                        title={`Delete ${document.filename}`}
                        aria-label={`Delete ${document.filename}`}
                      >
                        <Trash2 size={17} />
                      </button>
                    )}
                  </div>
                ))
              )}
            </div>
          </section>
        )}

        {/* Keep the file input mounted in both Chat and Knowledge Base views. */}
        <input
          ref={fileInputRef}
          className="hidden-input"
          type="file"
          accept=".pdf,.doc,.docx,.txt"
          multiple
          onChange={(event) => {
            const files = event.currentTarget.files;
            if (files && files.length > 0) {
              void handleFiles(files);
            }
          }}
        />
      </main>

      {selectedDocument && (
        <div
          className="modal-backdrop"
          onClick={() => setSelectedDocument(null)}
        >
          <section
            className="document-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="document-modal-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <div className="document-icon">
                <FileText size={21} />
              </div>
              <button
                type="button"
                className="icon-button"
                onClick={() => setSelectedDocument(null)}
                aria-label="Close document details"
              >
                <X size={18} />
              </button>
            </div>

            <div className="eyebrow">DOCUMENT DETAILS</div>
            <h2 id="document-modal-title">{selectedDocument.filename}</h2>

            <div className="detail-list">
              <div>
                <span>Document ID</span>
                <strong>{selectedDocument.documentId}</strong>
              </div>
              <div>
                <span>Indexed chunks</span>
                <strong>{selectedDocument.chunkCount}</strong>
              </div>
              <div>
                <span>Uploaded</span>
                <strong>{formatDate(selectedDocument.uploadedAt)}</strong>
              </div>
            </div>

            {authUser.role === 'ADMIN' && (
              <button
                type="button"
                className="primary-button modal-delete"
                onClick={() => void handleDelete(selectedDocument)}
              >
                <Trash2 size={16} />
                Delete document
              </button>
            )}
          </section>
        </div>
      )}
    </div>
  );
}

export default App;