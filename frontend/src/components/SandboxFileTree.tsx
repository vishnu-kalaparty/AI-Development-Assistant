import { useMemo, useState } from 'react'
import { ChevronDown, ChevronRight, FileText, Folder, FolderOpen, Trash2 } from 'lucide-react'
import { DraftFileStatus } from '../types/agent'
import './SandboxFileTree.css'

export interface SandboxTreeNode {
  name: string
  path: string
  type: 'file' | 'directory'
  children?: SandboxTreeNode[]
}

interface SandboxFileTreeProps {
  files?: string[]
  tree?: SandboxTreeNode[] | null
  onLoad: (path: string) => void
  onDelete?: (path: string) => void
  emptyLabel?: string
  /** Draft path → status for review highlighting */
  fileStatuses?: Record<string, DraftFileStatus>
}

export function buildTreeFromPaths(paths: string[]): SandboxTreeNode[] {
  const root: SandboxTreeNode[] = []

  for (const raw of paths) {
    const normalized = raw.replace(/\\/g, '/').replace(/^\/+|\/+$/g, '')
    if (!normalized) continue

    const parts = normalized.split('/')
    let level = root

    parts.forEach((part, index) => {
      const isFile = index === parts.length - 1
      const path = parts.slice(0, index + 1).join('/')
      let node = level.find((entry) => entry.name === part && entry.type === (isFile ? 'file' : 'directory'))

      if (!node) {
        node = {
          name: part,
          path,
          type: isFile ? 'file' : 'directory',
          children: isFile ? undefined : [],
        }
        level.push(node)
      }

      if (!isFile) {
        if (!node.children) node.children = []
        level = node.children
      }
    })
  }

  const sortNodes = (nodes: SandboxTreeNode[]) => {
    nodes.sort((a, b) => {
      if (a.type !== b.type) return a.type === 'directory' ? -1 : 1
      return a.name.localeCompare(b.name)
    })
    nodes.forEach((node) => {
      if (node.children) sortNodes(node.children)
    })
  }

  sortNodes(root)
  return root
}

function TreeNodeRow({
  node,
  depth,
  onLoad,
  onDelete,
  fileStatuses,
}: {
  node: SandboxTreeNode
  depth: number
  onLoad: (path: string) => void
  onDelete?: (path: string) => void
  fileStatuses?: Record<string, DraftFileStatus>
}) {
  const [open, setOpen] = useState(depth < 2)
  const isDir = node.type === 'directory'

  if (isDir) {
    return (
      <div className="sandbox-tree-branch">
        <button
          type="button"
          className="sandbox-tree-row sandbox-tree-folder"
          style={{ paddingLeft: `${0.35 + depth * 0.85}rem` }}
          onClick={() => setOpen((value) => !value)}
          aria-expanded={open}
        >
          {open ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
          {open ? <FolderOpen size={14} /> : <Folder size={14} />}
          <span>{node.name}</span>
        </button>
        {open &&
          (node.children || []).map((child) => (
            <TreeNodeRow
              key={`${child.type}:${child.path}`}
              node={child}
              depth={depth + 1}
              onLoad={onLoad}
              onDelete={onDelete}
              fileStatuses={fileStatuses}
            />
          ))}
      </div>
    )
  }

  const status = fileStatuses?.[node.path]

  return (
    <div
      className={`sandbox-tree-row sandbox-tree-file${status ? ` sandbox-tree-file-${status}` : ''}`}
      style={{ paddingLeft: `${0.35 + depth * 0.85}rem` }}
    >
      <button type="button" className="sandbox-tree-file-btn" onClick={() => onLoad(node.path)} title={node.path}>
        <FileText size={14} />
        <span>{node.name}</span>
        {status === 'new' ? <span className="sandbox-tree-badge sandbox-tree-badge-new">new</span> : null}
        {status === 'modified' ? (
          <span className="sandbox-tree-badge sandbox-tree-badge-modified">modified</span>
        ) : null}
      </button>
      {onDelete ? (
        <button
          type="button"
          className="sandbox-tree-delete"
          onClick={() => onDelete(node.path)}
          aria-label={`Delete ${node.path}`}
          title="Delete"
        >
          <Trash2 size={14} />
        </button>
      ) : null}
    </div>
  )
}

export default function SandboxFileTree({
  files = [],
  tree,
  onLoad,
  onDelete,
  emptyLabel = 'No files in sandbox',
  fileStatuses,
}: SandboxFileTreeProps) {
  const nodes = useMemo(() => {
    if (files.length > 0) return buildTreeFromPaths(files)
    if (tree && tree.length > 0) return tree
    return []
  }, [files, tree])

  if (nodes.length === 0) {
    return <p className="sandbox-tree-empty">{emptyLabel}</p>
  }

  return (
    <div className="sandbox-tree" role="tree">
      {nodes.map((node) => (
        <TreeNodeRow
          key={`${node.type}:${node.path}`}
          node={node}
          depth={0}
          onLoad={onLoad}
          onDelete={onDelete}
          fileStatuses={fileStatuses}
        />
      ))}
    </div>
  )
}
