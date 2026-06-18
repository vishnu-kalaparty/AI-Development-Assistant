import { RefreshCw, Save } from 'lucide-react'
import CodeEditor from './CodeEditor'
import SandboxFileTree, { SandboxTreeNode } from './SandboxFileTree'
import { DraftFileStatus } from '../types/agent'
import './SandboxExplorer.css'

function editorLanguage(path: string): string {
  if (path.endsWith('.java')) return 'java'
  if (path.endsWith('.md')) return 'markdown'
  if (path.endsWith('.ts') || path.endsWith('.tsx')) return 'typescript'
  if (path.endsWith('.js') || path.endsWith('.jsx')) return 'javascript'
  if (path.endsWith('.json')) return 'json'
  if (path.endsWith('.yml') || path.endsWith('.yaml')) return 'yaml'
  return 'python'
}

export interface SandboxExplorerProps {
  sandboxFiles: string[]
  sandboxTree: SandboxTreeNode[]
  draftFileStatuses: Record<string, DraftFileStatus>
  activeSandboxPath: string
  editorPath: string
  editorContent: string
  editorBaseline: string | null
  editorFileStatus: DraftFileStatus | null
  showDiff: boolean
  readOnly: boolean
  awaitingHuman: boolean
  layout?: 'compact' | 'expanded'
  onEditorPathChange: (path: string) => void
  onEditorContentChange: (content: string) => void
  onLoadFile: (path?: string) => void
  onDeleteFile: (path: string) => void
  onRefresh: () => void
  onSave: () => void
}

export default function SandboxExplorer({
  sandboxFiles,
  sandboxTree,
  draftFileStatuses,
  activeSandboxPath,
  editorPath,
  editorContent,
  editorBaseline,
  editorFileStatus,
  showDiff,
  readOnly,
  awaitingHuman,
  layout = 'compact',
  onEditorPathChange,
  onEditorContentChange,
  onLoadFile,
  onDeleteFile,
  onRefresh,
  onSave,
}: SandboxExplorerProps) {
  const expanded = layout === 'expanded'
  const draftCount = Object.keys(draftFileStatuses).length

  return (
    <div className={`sandbox-explorer sandbox-explorer-${layout}`}>
      <div className="sandbox-explorer-body">
        <aside className="sandbox-explorer-sidebar" aria-label="Sandbox files">
          <div className="sandbox-explorer-sidebar-head">
            <span className="sandbox-explorer-sidebar-title">Files</span>
            <button type="button" className="btn-ghost sandbox-explorer-icon-btn" onClick={onRefresh} aria-label="Refresh files">
              <RefreshCw size={15} />
            </button>
          </div>
          {activeSandboxPath ? (
            <p className="muted sandbox-explorer-path">
              <code>{activeSandboxPath}</code>
            </p>
          ) : null}
          {draftCount > 0 ? (
            <ul className="sandbox-explorer-legend" aria-label="File status legend">
              <li>
                <span className="sandbox-tree-badge sandbox-tree-badge-new">new</span>
                <span>Draft only</span>
              </li>
              <li>
                <span className="sandbox-tree-badge sandbox-tree-badge-modified">modified</span>
                <span>Changed vs disk</span>
              </li>
            </ul>
          ) : null}
          <div className="sandbox-explorer-tree">
            <SandboxFileTree
              files={sandboxFiles}
              tree={sandboxTree}
              onLoad={(file) => onLoadFile(file)}
              onDelete={(file) => onDeleteFile(file)}
              emptyLabel="No files yet"
              fileStatuses={draftFileStatuses}
            />
          </div>
        </aside>

        <section className="sandbox-explorer-editor-pane" aria-label="File editor">
          <div className="editor-controls">
            <input
              value={editorPath}
              onChange={(e) => onEditorPathChange(e.target.value)}
              placeholder="e.g. src/app.py"
              aria-label="Sandbox file path"
            />
            <button type="button" onClick={() => onLoadFile()}>
              Load
            </button>
          </div>
          <CodeEditor
            value={editorContent}
            onChange={(value) => onEditorContentChange(value || '')}
            language={editorLanguage(editorPath)}
            height={expanded ? 'min(68vh, 720px)' : '320px'}
            original={editorBaseline}
            showDiff={showDiff}
            fileStatus={editorFileStatus}
            readOnly={readOnly}
          />
          {!readOnly ? (
            <button type="button" className="btn-primary save-btn" onClick={onSave}>
              <Save size={16} />
              Save to sandbox
            </button>
          ) : null}
          {awaitingHuman ? (
            <p className="muted sdlc-mock-note">
              Draft review — disk and index update only after Approve.
            </p>
          ) : null}
        </section>
      </div>
    </div>
  )
}
