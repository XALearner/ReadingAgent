import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { ArrowRight, BookOpen, Bot, Highlighter, Library, Loader2, MessageCircle, PanelLeft, Trash2, Upload } from 'lucide-react';
import { api } from './api/client';
import './styles.css';

const USER_KEY = 'demo-user';

function App() {
  const [books, setBooks] = useState([]);
  const [chapters, setChapters] = useState([]);
  const [activeBook, setActiveBook] = useState(null);
  const [activeChapter, setActiveChapter] = useState(null);
  const [highlights, setHighlights] = useState([]);
  const [messages, setMessages] = useState([]);
  const [question, setQuestion] = useState('');
  const [note, setNote] = useState('');
  const [selectedText, setSelectedText] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    refreshBooks();
  }, []);

  async function refreshBooks() {
    try {
      const data = await api.listBooks();
      setBooks(data);
      if (!activeBook && data.length > 0) {
        await openBook(data[0]);
      }
    } catch (err) {
      setError(err.message);
    }
  }

  async function openBook(book) {
    setActiveBook(book);
    setError('');
    const chapterList = await api.listChapters(book.id);
    setChapters(chapterList);
    setHighlights(await api.listHighlights(book.id));
    if (chapterList.length > 0) {
      await openChapter(chapterList[0].id, book.id);
    }
  }

  async function deleteBook(event, book) {
    event.stopPropagation();
    if (!window.confirm(`确定删除《${book.title}》吗？`)) {
      return;
    }
    setBusy(true);
    setError('');
    try {
      await api.deleteBook(book.id);
      const remainingBooks = books.filter((item) => item.id !== book.id);
      setBooks(remainingBooks);
      if (activeBook?.id === book.id) {
        setActiveBook(null);
        setActiveChapter(null);
        setChapters([]);
        setHighlights([]);
        setMessages([]);
        if (remainingBooks.length > 0) {
          await openBook(remainingBooks[0]);
        }
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function openChapter(chapterId, bookId = activeBook?.id) {
    const chapter = await api.getChapter(chapterId);
    setActiveChapter(chapter);
    window.scrollTo({ top: 0, behavior: 'smooth' });
    if (bookId) {
      api.saveProgress(bookId, { userKey: USER_KEY, chapterId, scrollPercent: 0 }).catch(() => {});
    }
  }

  async function goNextChapter() {
    if (!nextChapter) {
      return;
    }
    setError('');
    await openChapter(nextChapter.id);
  }

  async function handleUpload(event) {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    setBusy(true);
    setError('');
    try {
      const formData = new FormData();
      formData.append('file', file);
      const uploaded = await api.uploadBook(formData);
      await refreshBooks();
      await openBook(uploaded.book);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
      event.target.value = '';
    }
  }

  function captureSelection() {
    const text = window.getSelection()?.toString().trim();
    if (text) {
      setSelectedText(text.slice(0, 2000));
    }
  }

  async function saveHighlight() {
    if (!activeBook || !activeChapter || !selectedText) {
      return;
    }
    setBusy(true);
    setError('');
    try {
      const item = await api.createHighlight(activeBook.id, {
        chapterId: activeChapter.id,
        selectedText,
        note,
      });
      setHighlights([item, ...highlights]);
      setSelectedText('');
      setNote('');
      window.getSelection()?.removeAllRanges();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function askAgent(event) {
    event.preventDefault();
    if (!activeBook || !question.trim()) {
      return;
    }
    const userQuestion = question.trim();
    setQuestion('');
    setMessages((items) => [...items, { role: 'user', content: userQuestion }]);
    setBusy(true);
    setError('');
    try {
      const response = await api.ask(activeBook.id, {
        question: userQuestion,
        chapterId: activeChapter?.id,
      });
      setMessages((items) => [
        ...items,
        { role: 'assistant', content: response.answer, sources: response.sources },
      ]);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  const readingPercent = useMemo(() => {
    if (!chapters.length || !activeChapter) {
      return 0;
    }
    const index = chapters.findIndex((chapter) => chapter.id === activeChapter.id);
    return Math.round(((index + 1) / chapters.length) * 100);
  }, [chapters, activeChapter]);

  const nextChapter = useMemo(() => {
    if (!chapters.length || !activeChapter) {
      return null;
    }
    const index = chapters.findIndex((chapter) => chapter.id === activeChapter.id);
    return index >= 0 && index + 1 < chapters.length ? chapters[index + 1] : null;
  }, [chapters, activeChapter]);

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <BookOpen size={26} />
          <div>
            <strong>Reading Agent</strong>
            <span>AI 读书助手</span>
          </div>
        </div>

        <label className="upload-button">
          {busy ? <Loader2 className="spin" size={18} /> : <Upload size={18} />}
          <span>导入书籍</span>
          <input type="file" accept=".txt,.md,.markdown,.epub,.pdf" onClick={() => setError('')} onChange={handleUpload} />
        </label>

        <section className="panel">
          <h2><Library size={17} />书架</h2>
          <div className="book-list">
            {books.map((book) => (
              <div
                key={book.id}
                className={`book-row ${activeBook?.id === book.id ? 'active' : ''}`}
              >
                <button className="book-open" onClick={() => openBook(book)}>
                  <strong>{book.title}</strong>
                  <span>{book.chapterCount} 章</span>
                </button>
                <button
                  className="delete-book"
                  title="删除书籍"
                  onClick={(event) => deleteBook(event, book)}
                  disabled={busy}
                >
                  <Trash2 size={16} />
                </button>
              </div>
            ))}
            {books.length === 0 && <p className="empty">上传一本 TXT、Markdown、EPUB 或 PDF 开始阅读。</p>}
          </div>
        </section>

        <section className="panel chapter-panel">
          <h2><PanelLeft size={17} />目录</h2>
          <div className="chapter-list">
            {chapters.map((chapter) => (
              <button
                key={chapter.id}
                className={activeChapter?.id === chapter.id ? 'active' : ''}
                onClick={() => openChapter(chapter.id)}
              >
                {chapter.title}
              </button>
            ))}
          </div>
        </section>
      </aside>

      <main className="reader">
        {error && <div className="error">{error}</div>}
        {activeChapter ? (
          <>
            <div className="reader-top">
              <div>
                <span>{activeBook?.title}</span>
                <h1>{activeChapter.title}</h1>
              </div>
              <div className="progress">{readingPercent}%</div>
            </div>
            <article
              className={activeChapter.contentHtml ? 'epub-content' : ''}
              onMouseUp={captureSelection}
            >
              {activeChapter.contentHtml ? (
                <div dangerouslySetInnerHTML={{ __html: activeChapter.contentHtml }} />
              ) : (
                activeChapter.content.split('\n').map((line, index) => (
                  <p key={index}>{line || '\u00A0'}</p>
                ))
              )}
            </article>
            <div className="chapter-nav">
              {nextChapter ? (
                <button className="next-chapter" onClick={goNextChapter}>
                  <span>
                    <strong>下一章</strong>
                    <small>{nextChapter.title}</small>
                  </span>
                  <ArrowRight size={20} />
                </button>
              ) : (
                <div className="book-finished">
                  <strong>已读到最后一章</strong>
                  <span>这本书已经到末尾了。</span>
                </div>
              )}
            </div>
          </>
        ) : (
          <div className="welcome">
            <BookOpen size={44} />
            <h1>导入一本书，开始阅读</h1>
            <p>当前版本支持 UTF-8 文本、Markdown、EPUB 和 PDF。</p>
          </div>
        )}
      </main>

      <aside className="assistant">
        <section className="tool-block">
          <h2><Highlighter size={17} />划线笔记</h2>
          <textarea
            value={selectedText}
            onChange={(event) => setSelectedText(event.target.value)}
            placeholder="选中正文后会出现在这里"
          />
          <input
            value={note}
            onChange={(event) => setNote(event.target.value)}
            placeholder="写一点想法"
          />
          <button className="primary" disabled={!selectedText || busy} onClick={saveHighlight}>保存</button>
          <div className="note-list">
            {highlights.slice(0, 6).map((item) => (
              <div className="note-item" key={item.id}>
                <p>{item.selectedText}</p>
                {item.note && <span>{item.note}</span>}
              </div>
            ))}
          </div>
        </section>

        <section className="tool-block ai-block">
          <h2><Bot size={17} />问问这本书</h2>
          <div className="chat-list">
            {messages.map((message, index) => (
              <div key={index} className={`chat ${message.role}`}>
                <p>{message.content}</p>
                {message.sources?.length > 0 && (
                  <div className="sources">
                    {message.sources.slice(0, 3).map((source, sourceIndex) => (
                      <span key={sourceIndex}>{source.chapterTitle}</span>
                    ))}
                  </div>
                )}
              </div>
            ))}
            {messages.length === 0 && <p className="empty">可以问人物关系、概念解释、章节总结。</p>}
          </div>
          <form onSubmit={askAgent} className="ask-form">
            <input
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="输入你的问题"
            />
            <button className="icon-button" disabled={busy || !question.trim()} title="发送">
              {busy ? <Loader2 className="spin" size={18} /> : <MessageCircle size={18} />}
            </button>
          </form>
        </section>
      </aside>
    </div>
  );
}

createRoot(document.getElementById('root')).render(<App />);
