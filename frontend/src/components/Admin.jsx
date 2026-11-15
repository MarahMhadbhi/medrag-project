import { useCallback, useEffect, useState } from 'react'
import { deleteDocument, listDocuments, reindexDocument, uploadDocument } from '../api'
import { Icon, Logo } from './Icons'

const STATUS = {
  PENDING: { label: 'En attente', cls: 'pending' },
  INDEXING: { label: 'Indexation en cours', cls: 'pending' },
  READY: { label: 'Prêt', cls: 'ready' },
  FAILED: { label: 'Échec', cls: 'failed' },
}

const EMPTY_FORM = { title: '', organisme: 'OMS', sourceUrl: '', language: 'fr' }

function isHttpUrl(url) {
  return typeof url === 'string' && /^https?:\/\//i.test(url)
}

function Feedback({ message }) {
  if (!message) return null
  return (
    <p className={`feedback ${message.type}`} role={message.type === 'error' ? 'alert' : 'status'}>
      {message.text}
    </p>
  )
}

function DocumentItem({ doc, busy, onReindex, onDelete }) {
  const status = STATUS[doc.status] ?? { label: doc.status, cls: 'pending' }
  const percent =
    doc.chunkCount > 0 ? Math.round((doc.indexedChunkCount / doc.chunkCount) * 100) : 0
  const failed = doc.status === 'FAILED'

  return (
    <li className="doc">
      <div className="doc-head">
        <div className="doc-info">
          <span className="doc-title">{doc.title}</span>
          <span className="doc-meta">
            {doc.organisme} · {(doc.language || '').toUpperCase()} · {doc.pageCount} pages
            {isHttpUrl(doc.sourceUrl) && (
              <>
                {' · '}
                <a href={doc.sourceUrl} target="_blank" rel="noopener noreferrer">
                  Source
                </a>
              </>
            )}
          </span>
        </div>
        <span className={`status ${status.cls}`}>{status.label}</span>
      </div>

      <div
        className={`progress ${failed ? 'failed' : ''}`}
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        aria-label={`Progression de l'indexation de ${doc.title}`}
      >
        <span style={{ width: `${percent}%` }} />
      </div>

      <div className="doc-foot">
        <span className="doc-count">
          {doc.indexedChunkCount}/{doc.chunkCount} segments indexés ({percent} %)
        </span>
        <div className="doc-actions">
          {failed && (
            <button
              type="button"
              className="ghost small"
              disabled={busy}
              onClick={() => onReindex(doc)}
            >
              <Icon name="refresh" size={15} />
              Relancer
            </button>
          )}
          <button
            type="button"
            className="ghost small danger"
            disabled={busy}
            onClick={() => onDelete(doc)}
          >
            <Icon name="trash" size={15} />
            Supprimer
          </button>
        </div>
      </div>

      {failed && doc.errorMessage && <p className="doc-error">{doc.errorMessage}</p>}
    </li>
  )
}

export default function Admin({ onBack }) {
  const [docs, setDocs] = useState(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [file, setFile] = useState(null)
  const [fileKey, setFileKey] = useState(0)
  const [submitting, setSubmitting] = useState(false)
  const [busyId, setBusyId] = useState(null)
  const [formMsg, setFormMsg] = useState(null)
  const [listMsg, setListMsg] = useState(null)

  const load = useCallback(async () => {
    try {
      setDocs(await listDocuments())
    } catch (err) {
      setListMsg({ type: 'error', text: err.message })
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  // Rafraîchissement automatique tant qu'un document est en cours d'indexation.
  const hasActive = docs?.some((d) => d.status === 'PENDING' || d.status === 'INDEXING') ?? false
  useEffect(() => {
    if (!hasActive) return undefined
    const timer = setInterval(load, 4000)
    return () => clearInterval(timer)
  }, [hasActive, load])

  function setField(name, value) {
    setForm((f) => ({ ...f, [name]: value }))
  }

  function handleFile(e) {
    const chosen = e.target.files?.[0] ?? null
    setFile(chosen)
    if (chosen && !form.title) {
      setField('title', chosen.name.replace(/\.pdf$/i, '').replace(/[-_]+/g, ' '))
    }
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setFormMsg(null)
    if (!file) {
      setFormMsg({ type: 'error', text: 'Choisissez un fichier PDF.' })
      return
    }
    if (!form.title.trim() || !form.organisme.trim()) {
      setFormMsg({ type: 'error', text: "Le titre et l'organisme sont obligatoires." })
      return
    }

    setSubmitting(true)
    try {
      await uploadDocument({ file, ...form })
      setFormMsg({ type: 'ok', text: `« ${form.title.trim()} » envoyé : l'indexation commence.` })
      setForm(EMPTY_FORM)
      setFile(null)
      setFileKey((k) => k + 1)
      await load()
    } catch (err) {
      setFormMsg({ type: 'error', text: err.message })
    } finally {
      setSubmitting(false)
    }
  }

  async function runAction(doc, action, okText) {
    setBusyId(doc.id)
    setListMsg(null)
    try {
      await action(doc.id)
      setListMsg({ type: 'ok', text: okText })
    } catch (err) {
      setListMsg({ type: 'error', text: err.message })
    } finally {
      setBusyId(null)
      await load()
    }
  }

  function handleReindex(doc) {
    runAction(doc, reindexDocument, `Indexation relancée pour « ${doc.title} ».`)
  }

  function handleDelete(doc) {
    if (!window.confirm(`Supprimer « ${doc.title} » ? Cette action est définitive.`)) return
    runAction(doc, deleteDocument, `« ${doc.title} » supprimé.`)
  }

  return (
    <div className="app">
      <header className="header">
        <div className="brand">
          <div className="brand-logo">
            <Logo size={26} />
          </div>
          <div>
            <h1>MedRAG</h1>
            <p className="tagline">Administration des documents</p>
          </div>
        </div>
        <button type="button" className="ghost" onClick={onBack} aria-label="Retour au chat">
          <Icon name="back" size={16} />
          <span className="label">Retour au chat</span>
        </button>
      </header>

      <main className="admin">
        <section className="panel">
          <h2>Ajouter un document</h2>
          <form className="form-grid" onSubmit={handleSubmit}>
            <div className="field wide">
              <label htmlFor="doc-file">Fichier PDF</label>
              <input
                key={fileKey}
                id="doc-file"
                type="file"
                accept=".pdf,application/pdf"
                onChange={handleFile}
                disabled={submitting}
              />
            </div>
            <div className="field wide">
              <label htmlFor="doc-title">Titre</label>
              <input
                id="doc-title"
                value={form.title}
                onChange={(e) => setField('title', e.target.value)}
                maxLength={200}
                placeholder="Ex. OMS - Anémie"
                disabled={submitting}
              />
            </div>
            <div className="field">
              <label htmlFor="doc-org">Organisme (source)</label>
              <input
                id="doc-org"
                value={form.organisme}
                onChange={(e) => setField('organisme', e.target.value)}
                maxLength={120}
                disabled={submitting}
              />
            </div>
            <div className="field">
              <label htmlFor="doc-lang">Langue</label>
              <select
                id="doc-lang"
                value={form.language}
                onChange={(e) => setField('language', e.target.value)}
                disabled={submitting}
              >
                <option value="fr">Français</option>
                <option value="en">Anglais</option>
                <option value="ar">Arabe</option>
              </select>
            </div>
            <div className="field wide">
              <label htmlFor="doc-url">Lien de la source (facultatif)</label>
              <input
                id="doc-url"
                type="url"
                value={form.sourceUrl}
                onChange={(e) => setField('sourceUrl', e.target.value)}
                placeholder="https://..."
                disabled={submitting}
              />
            </div>
            <div className="form-actions">
              <button type="submit" className="primary" disabled={submitting}>
                <Icon name="upload" size={18} />
                {submitting ? 'Envoi en cours…' : 'Envoyer'}
              </button>
            </div>
          </form>
          <Feedback message={formMsg} />
        </section>

        <section className="panel">
          <h2>Documents</h2>
          <Feedback message={listMsg} />
          {docs === null ? (
            <p className="empty">Chargement…</p>
          ) : docs.length === 0 ? (
            <p className="empty">Aucun document pour l'instant.</p>
          ) : (
            <ul className="doc-list">
              {docs.map((doc) => (
                <DocumentItem
                  key={doc.id}
                  doc={doc}
                  busy={busyId === doc.id}
                  onReindex={handleReindex}
                  onDelete={handleDelete}
                />
              ))}
            </ul>
          )}
        </section>
      </main>
    </div>
  )
}