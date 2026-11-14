import { Icon, Logo } from './Icons'

// Questions basées sur la fiche « anémie » indexée. À adapter quand tu ajouteras d'autres documents.
const SUGGESTIONS = [
  { icon: 'activity', text: "Quels sont les symptômes de l'anémie ?" },
  { icon: 'droplet', text: "Quelles sont les causes de l'anémie ?" },
  { icon: 'shield', text: "Comment prévenir l'anémie ?" },
  { icon: 'users', text: "Qui est le plus touché par l'anémie ?" },
]

export default function Welcome({ onPick, disabled }) {
  return (
    <section className="welcome">
      <div className="hero-logo">
        <Logo size={44} />
      </div>
      <h2>Bonjour, comment puis-je vous aider ?</h2>
      <p>
        Posez une question de santé publique. Les réponses s’appuient uniquement sur des documents
        d’organismes de santé, avec leurs sources.
      </p>

      <div className="chips">
        {SUGGESTIONS.map((s) => (
          <button
            key={s.text}
            type="button"
            className="chip"
            disabled={disabled}
            onClick={() => onPick(s.text)}
          >
            <span className="chip-icon">
              <Icon name={s.icon} size={18} />
            </span>
            <span>{s.text}</span>
          </button>
        ))}
      </div>
    </section>
  )
}