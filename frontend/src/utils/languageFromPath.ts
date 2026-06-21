const LANG_MAP: Record<string, string> = {
  py: 'python',
  python: 'python',
  js: 'javascript',
  javascript: 'javascript',
  ts: 'typescript',
  typescript: 'typescript',
  java: 'java',
  cpp: 'cpp',
  'c++': 'cpp',
  c: 'cpp',
  go: 'go',
  golang: 'go',
}

export function languageFromPath(path: string, fallback = 'plaintext'): string {
  const ext = path.split('.').pop()?.toLowerCase()
  if (!ext) return fallback
  return LANG_MAP[ext] ?? fallback
}

export function languageFromHint(hint: string | undefined, fallback = 'plaintext'): string {
  if (!hint) return fallback
  return LANG_MAP[hint.toLowerCase()] ?? fallback
}
