import { useCallback } from 'react'
import Editor, { DiffEditor, OnMount } from '@monaco-editor/react'
import { useTheme } from '../theme'
import { DraftFileStatus } from '../types/agent'
import './CodeEditor.css'

interface CodeEditorProps {
  value: string
  onChange: (value: string | undefined) => void
  language?: string
  height?: string | number
  readOnly?: boolean
  /** Disk baseline for inline diff (omit for normal editor). */
  original?: string | null
  showDiff?: boolean
  fileStatus?: DraftFileStatus | null
}

const LANGUAGE_LABELS: Record<string, string> = {
  python: 'Python',
  javascript: 'JavaScript',
  typescript: 'TypeScript',
  java: 'Java',
  cpp: 'C++',
  go: 'Go',
}

const STATUS_LABELS: Record<DraftFileStatus, string> = {
  new: 'New draft',
  modified: 'Modified',
  unchanged: 'Unchanged',
  disk: 'On disk',
}

export default function CodeEditor({
  value,
  onChange,
  language = 'python',
  height = '400px',
  readOnly = false,
  original = null,
  showDiff = false,
  fileStatus = null,
}: CodeEditorProps) {
  const { theme } = useTheme()
  const editorTheme = theme === 'dark' ? 'vs-dark' : 'vs-light'

  const handleMount: OnMount = useCallback((editor) => {
    editor.updateOptions({
      fontFamily: 'IBM Plex Mono, SFMono-Regular, Menlo, Consolas, monospace',
      fontSize: 14,
      lineHeight: 22,
      padding: { top: 12, bottom: 12 },
      minimap: { enabled: false },
      scrollBeyondLastLine: false,
      automaticLayout: true,
      renderLineHighlight: 'line',
      cursorBlinking: 'smooth',
      smoothScrolling: true,
    })
  }, [])

  const modeLabel = showDiff
    ? fileStatus && STATUS_LABELS[fileStatus]
      ? `Diff · ${STATUS_LABELS[fileStatus]}`
      : 'Diff view'
    : 'Editor'

  const diffOriginal = original ?? ''

  return (
    <div className={`code-editor${showDiff ? ' code-editor-diff' : ''}`}>
      <div className="code-editor-label">
        <span>{LANGUAGE_LABELS[language] || language}</span>
        <span className="code-editor-mode">{modeLabel}</span>
      </div>
      {showDiff ? (
        <div className="code-editor-frame code-editor-diff-frame" style={{ height }}>
          <DiffEditor
            height="100%"
            language={language}
            original={diffOriginal}
            modified={value}
            theme={editorTheme}
            options={{
              readOnly,
              renderSideBySide: false,
              minimap: { enabled: false },
              fontSize: 14,
              lineNumbers: 'on',
              scrollBeyondLastLine: false,
              automaticLayout: true,
              padding: { top: 12, bottom: 12 },
              lineHeight: 22,
              ignoreTrimWhitespace: false,
            }}
          />
        </div>
      ) : (
        <div className="code-editor-frame" style={{ height }}>
          <Editor
            height="100%"
            language={language}
            value={value}
            onChange={onChange}
            onMount={handleMount}
            theme={editorTheme}
            options={{
              minimap: { enabled: false },
              fontSize: 14,
              lineNumbers: 'on',
              readOnly,
              scrollBeyondLastLine: false,
              automaticLayout: true,
              padding: { top: 12, bottom: 12 },
              lineHeight: 22,
            }}
          />
        </div>
      )}
      {showDiff ? (
        <p className="code-editor-diff-legend" aria-hidden="true">
          <span className="diff-legend-added">Added / changed lines</span>
          <span className="diff-legend-removed">Removed lines</span>
        </p>
      ) : null}
    </div>
  )
}
