import { useEffect } from 'react'
import { Maximize2, X } from 'lucide-react'
import SandboxExplorer, { SandboxExplorerProps } from './SandboxExplorer'
import './SandboxExplorer.css'

interface SandboxExplorerModalProps extends SandboxExplorerProps {
  open: boolean
  onClose: () => void
}

export function SandboxExplorerExpandButton({ onClick }: { onClick: () => void }) {
  return (
    <button
      type="button"
      className="btn-ghost sandbox-explorer-expand-btn"
      onClick={onClick}
      aria-label="Expand sandbox explorer"
      title="Expand file explorer"
    >
      <Maximize2 size={16} />
    </button>
  )
}

export default function SandboxExplorerModal({ open, onClose, ...explorerProps }: SandboxExplorerModalProps) {
  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null

  return (
    <div
      className="sandbox-explorer-modal-backdrop"
      role="presentation"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div
        className="sandbox-explorer-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="sandbox-explorer-modal-title"
      >
        <header className="sandbox-explorer-modal-head">
          <h2 id="sandbox-explorer-modal-title">Sandbox explorer</h2>
          <div className="sandbox-explorer-modal-actions">
            <button type="button" className="btn-ghost" onClick={onClose} aria-label="Close explorer">
              <X size={18} />
            </button>
          </div>
        </header>
        <div className="sandbox-explorer-modal-body">
          <SandboxExplorer {...explorerProps} layout="expanded" />
        </div>
      </div>
    </div>
  )
}
