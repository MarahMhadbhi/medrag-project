import { useEffect, useRef, useState } from 'react'
import { ask } from './api'
import Message from './components/Message'
import Welcome from './components/Welcome'
import { Icon, Logo } from './components/Icons'

export default function App() {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const endRef = useRef(null)
  const inputRef = useRef(null)

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [messages, loading])

  useEffect(() => {
    if (!loading) inputRef.current?.focus()
  }, [loading])

  async function send(question) {
    const q = question.trim()
    if (!q || loading) return

    setMessages((m) => [...m, { role: 'user', text: q }])
    setLoading(true)

    try {
      const data = await ask(q)
      setMessages((m) => [...m, { role: 'bot', data }])
    } catch (err) {
      setMessages((m) => [
        ...m,
        { role: 'bot', error: `Une erreur est survenue (${err.message}).`, question: q },
      ])
    } finally {
      setLoading(false)
    }
  }

  function handleSubmit(e) {
    e.preventDefault()
    const q = input
    setInput('')
    send(q)
  }

  function reset() {
    if (loading) return
    setMessages([])
    setInput('')
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
            <p className="tagline">Information santé sourcée</p>
          </div>
        </div>

        {messages.length > 0 && (
          <button type="button" className="ghost" onClick={reset} disabled={loading}>
            <Icon name="plus" size={16} />
            <span className="label">Nouvelle conversation</span>
          </button>
        )}
      </header>

      <main className="log" role="log" aria-live="polite">
        {messages.length === 0 ? (
          <Welcome onPick={send} disabled={loading} />
        ) : (
          messages.map((message, i) => <Message key={i} message={message} onRetry={send} />)
        )}

        {loading && (
          <div className="row bot">
            <div className="avatar">
              <Logo size={20} crossColor="var(--primary-soft)" />
            </div>
            <div className="bubble typing" aria-label="Recherche en cours">
              <span />
              <span />
              <span />
            </div>
          </div>
        )}
        <div ref={endRef} />
      </main>

      <form className="composer" onSubmit={handleSubmit}>
        <div className="composer-box">
          <input
            ref={inputRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            maxLength={1000}
            placeholder="Posez votre question…"
            autoComplete="off"
            aria-label="Votre question"
          />
          <button
            type="submit"
            className="send"
            disabled={loading || !input.trim()}
            aria-label="Envoyer"
          >
            <Icon name="send" size={20} />
          </button>
        </div>
        <p className="foot">
          Information à caractère éducatif. Elle ne remplace pas l’avis d’un professionnel de santé. En
          cas d’urgence, contactez les secours.
        </p>
      </form>
    </div>
  )
}