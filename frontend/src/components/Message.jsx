import { useState } from 'react'
import ReactMarkdown from 'react-markdown'
import { Icon, Logo } from './Icons'

function isHttpUrl(url) {
  return typeof url === 'string' && /^https?:\/\//i.test(url)
}

function Avatar() {
  return (
    <div className="avatar">
      <Logo size={20} crossColor="var(--primary-soft)" />
    </div>
  )
}

function SourceItem({ source }) {
  const content = (
    <>
      <span className="badge">{source.ref}</span>
      <span className="s-text">
        <span className="s-title">{source.title}</span>
        <span className="s-meta">
          {source.organisme} · page {source.page}
        </span>
      </span>
    </>
  )

  return isHttpUrl(source.sourceUrl) ? (
    <a className="source" href={source.sourceUrl} target="_blank" rel="noopener noreferrer">
      {content}
    </a>
  ) : (
    <div className="source">{content}</div>
  )
}

function Disclaimer({ text }) {
  return (
    <div className="disclaimer">
      <Icon name="info" size={16} />
      <span>{text}</span>
    </div>
  )
}

function CopyButton({ text }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      setTimeout(() => setCopied(false), 1600)
    } catch {
      // Presse-papiers indisponible : on ignore.
    }
  }

  return (
    <button type="button" className="ghost small" onClick={copy}>
      <Icon name={copied ? 'check' : 'copy'} size={15} />
      {copied ? 'Copié' : 'Copier'}
    </button>
  )
}

export default function Message({ message, onRetry }) {
  if (message.role === 'user') {
    return (
      <div className="row user">
        <div className="bubble user">{message.text}</div>
      </div>
    )
  }

  if (message.error) {
    return (
      <div className="row bot">
        <Avatar />
        <div className="bubble error">
          <p>{message.error}</p>
          {message.question && (
            <button type="button" className="ghost small" onClick={() => onRetry(message.question)}>
              Réessayer
            </button>
          )}
        </div>
      </div>
    )
  }

  const { answer, answered, sources, disclaimer } = message.data

  if (answered === false) {
    return (
      <div className="row bot">
        <Avatar />
        <div className="bubble notfound">
          <div className="nf-head">
            <Icon name="search" size={18} />
            <strong>Information introuvable</strong>
          </div>
          <p>{answer}</p>
          <p className="hint">
            Essayez de reformuler votre question, ou posez-la sur un sujet couvert par les documents
            indexés.
          </p>
          <Disclaimer text={disclaimer} />
        </div>
      </div>
    )
  }

  return (
    <div className="row bot">
      <Avatar />
      <div className="bubble">
        <div className="md">
          <ReactMarkdown>{answer}</ReactMarkdown>
        </div>

        {sources && sources.length > 0 && (
          <details className="sources" open>
            <summary>
              {sources.length} source{sources.length > 1 ? 's' : ''}
            </summary>
            <ul className="source-list">
              {sources.map((s) => (
                <li key={s.ref}>
                  <SourceItem source={s} />
                </li>
              ))}
            </ul>
          </details>
        )}

        <Disclaimer text={disclaimer} />

        <div className="actions">
          <CopyButton text={answer} />
        </div>
      </div>
    </div>
  )
}