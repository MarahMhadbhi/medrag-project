const FALLBACK_MESSAGES = {
  400: 'Fichier ou champs invalides (PDF obligatoire, titre et organisme requis).',
  404: 'Document introuvable.',
  409: 'Action impossible pour le moment.',
  413: 'Fichier trop volumineux.',
  422: 'PDF illisible, corrompu ou sans texte (document scanné ?).',
}

// Message affiché : celui du serveur s'il existe, sinon un message de secours.
async function fail(res, overrides = {}) {
  let serverMessage = ''
  try {
    const body = await res.json()
    serverMessage = typeof body.message === 'string' ? body.message : ''
  } catch {
    // corps absent ou non JSON
  }
  const message =
    serverMessage || overrides[res.status] || FALLBACK_MESSAGES[res.status] || `Erreur ${res.status}`
  throw new Error(message)
}

async function send(url, options, overrides) {
  let res
  try {
    res = await fetch(url, options)
  } catch {
    throw new Error('Serveur injoignable. Le backend est-il démarré ?')
  }
  if (!res.ok) await fail(res, overrides)
  return res
}

export async function ask(question) {
  const res = await fetch('/api/ask', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question }),
  })
  if (!res.ok) {
    throw new Error(`erreur ${res.status}`)
  }
  return res.json()
}

export async function listDocuments() {
  const res = await send('/api/documents')
  return res.json()
}

export async function uploadDocument({ file, title, organisme, sourceUrl, language }) {
  const data = new FormData()
  data.append('file', file)
  data.append('title', title.trim())
  data.append('organisme', organisme.trim())
  data.append('language', language)
  if (sourceUrl.trim()) data.append('sourceUrl', sourceUrl.trim())

  // Pas d'en-tête Content-Type : le navigateur ajoute lui-même la bonne valeur.
  const res = await send(
    '/api/documents',
    { method: 'POST', body: data },
    { 409: 'Ce fichier a déjà été envoyé.' },
  )
  return res.json()
}

export async function reindexDocument(id) {
  await send(
    `/api/documents/${id}/reindex`,
    { method: 'POST' },
    { 409: 'Une indexation est déjà en cours pour ce document.' },
  )
}

export async function deleteDocument(id) {
  await send(`/api/documents/${id}`, { method: 'DELETE' })
}