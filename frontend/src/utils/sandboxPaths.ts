import { DraftFileStatus } from '../types/agent'
import { readWorkflowDraft } from '../api/workflowApi'
import { readSandboxFile } from '../api/sandboxApi'

export function normalizeSandboxPath(path: string): string {
  return path.replace(/\\/g, '/').replace(/^\/+|\/+$/g, '')
}

export function resolveSandboxPath(filePath: string, sandboxRoot?: string): string {
  const trimmed = filePath.trim()
  if (!trimmed || !sandboxRoot?.trim()) return trimmed
  const root = normalizeSandboxPath(sandboxRoot)
  const normalized = trimmed.replace(/\\/g, '/').replace(/^\/+/, '')
  if (normalized === root || normalized.startsWith(`${root}/`)) return normalized
  return `${root}/${normalized}`
}

/** Strip a sandbox root prefix when the API returns global paths (older backend). */
export function scopeFilesToRoot(files: string[], root: string): string[] {
  if (!root || files.length === 0) return files
  const normalizedRoot = normalizeSandboxPath(root)
  const hasRootPrefix = files.some((file) => {
    const path = normalizeSandboxPath(file)
    return path === normalizedRoot || path.startsWith(`${normalizedRoot}/`)
  })
  if (!hasRootPrefix) return files
  return files
    .filter((file) => {
      const path = normalizeSandboxPath(file)
      return path === normalizedRoot || path.startsWith(`${normalizedRoot}/`)
    })
    .map((file) => {
      const path = normalizeSandboxPath(file)
      if (path === normalizedRoot) return ''
      return path.slice(normalizedRoot.length + 1)
    })
    .filter(Boolean)
}

export async function fetchSandboxFileContent(
  displayPath: string,
  sandboxRoot: string,
  workflow: { workflowId?: string; status?: string; awaitingHuman?: boolean } | null
): Promise<{ content: string; baseline?: string | null; fileStatus?: DraftFileStatus } | null> {
  const pathToLoad = resolveSandboxPath(displayPath, sandboxRoot)
  const preferDraft =
    workflow?.workflowId &&
    (workflow.awaitingHuman || workflow.status === 'running' || workflow.status === 'awaiting_human')

  if (preferDraft && workflow?.workflowId) {
    try {
      const draft = await readWorkflowDraft(workflow.workflowId, displayPath)
      if (draft.content) {
        return {
          content: draft.content,
          baseline: draft.baseline ?? null,
          fileStatus: (draft.fileStatus as DraftFileStatus | undefined) ?? 'new',
        }
      }
    } catch {
      // fall through to disk read
    }
  }

  if (pathToLoad) {
    try {
      const response = await readSandboxFile(pathToLoad)
      const content = response.content
      if (content && !content.startsWith('Error:') && !content.startsWith('Security Error:')) {
        return { content, baseline: null, fileStatus: 'disk' }
      }
    } catch {
      // fall through
    }
  }

  return null
}
