import { FormEvent, useCallback, useEffect, useRef, useState } from 'react'
import {
  AlertCircle,
  CheckCircle2,
  CircleDot,
  Loader2,
  RefreshCw,
  Send,
  Square,
} from 'lucide-react'
import { type DraftFileStatus } from '../types/agent'
import { listSandboxFiles, writeSandboxFile, deleteSandboxFile } from '../api/sandboxApi'
import {
  cancelWorkflow,
  fetchDraftManifest,
  fetchSandboxFolders,
  fetchWorkflow,
  fetchWorkflowMode,
  startWorkflow,
  submitWorkflowDecision,
  type WorkflowRun,
} from '../api/workflowApi'
import {
  fetchSandboxFileContent,
  normalizeSandboxPath,
  resolveSandboxPath,
  scopeFilesToRoot,
} from '../utils/sandboxPaths'
import SandboxExplorer from '../components/SandboxExplorer'
import SandboxExplorerModal, { SandboxExplorerExpandButton } from '../components/SandboxExplorerModal'
import { buildTreeFromPaths, SandboxTreeNode } from '../components/SandboxFileTree'
import SdlcTimeline, { WorkflowPhase } from '../components/SdlcTimeline'
import {
  liveActivityLine,
  LiveProcessByPhase,
  PhaseOutput,
  StatusBadge,
  visibleSteps,
} from '../components/sdlc/SdlcLivePanels'
import './SdlcWorkflow.css'

const EXAMPLE_TASK =
  'Build a small Python CLI Notes app. Users can add, list, view, and delete notes.\n\nPersist notes in a JSON file — no database. Keep it simple and runnable from the command line.'

const DEFAULT_FOLDER_NAME = 'python-cli-notes-app'

const EMPTY_PHASES: WorkflowPhase[] = [
  { id: 'requirements', label: 'Requirements', status: 'pending' },
  { id: 'architecture', label: 'Architecture', status: 'pending' },
  { id: 'implementation', label: 'Implementation', status: 'pending' },
  { id: 'testing', label: 'Testing', status: 'pending' },
  { id: 'review', label: 'Review', status: 'pending' },
  { id: 'approval', label: 'Approval', status: 'pending' },
]

const POLL_MS = 150

const NEW_RUN_VALUE = '__new__'

function slugFromTask(text: string): string {
  const slug = text
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 48)
  return slug || 'run'
}

function defaultFolderForTask(text: string): string {
  if (text.trim() === EXAMPLE_TASK.trim()) {
    return DEFAULT_FOLDER_NAME
  }
  return slugFromTask(text)
}

/** Path to browse in the sandbox panel (dropdown or active workflow). */
function resolveBrowseSandboxPath(
  selectedFolder: string,
  workflowSandboxPath: string,
  isWorkflowLocked: boolean
): string {
  if (isWorkflowLocked && workflowSandboxPath) {
    return workflowSandboxPath
  }
  if (selectedFolder !== NEW_RUN_VALUE) {
    return normalizeSandboxPath(selectedFolder)
  }
  return ''
}

export default function SdlcWorkflow() {
  const [task, setTask] = useState(EXAMPLE_TASK)
  const [selectedFolder, setSelectedFolder] = useState(NEW_RUN_VALUE)
  const [folderName, setFolderName] = useState(DEFAULT_FOLDER_NAME)
  const [sandboxFolders, setSandboxFolders] = useState<string[]>([])
  const [notes, setNotes] = useState('')
  const [isStarting, setIsStarting] = useState(false)
  const [isDeciding, setIsDeciding] = useState(false)
  const [isStopping, setIsStopping] = useState(false)
  const [workflow, setWorkflow] = useState<WorkflowRun | null>(null)
  const [error, setError] = useState('')
  const [editorContent, setEditorContent] = useState('')
  const [editorPath, setEditorPath] = useState('')
  const [editorBaseline, setEditorBaseline] = useState<string | null>(null)
  const [editorFileStatus, setEditorFileStatus] = useState<DraftFileStatus | null>(null)
  const [draftFileStatuses, setDraftFileStatuses] = useState<Record<string, 'new' | 'modified'>>({})
  const [explorerOpen, setExplorerOpen] = useState(false)
  const [sandboxFiles, setSandboxFiles] = useState<string[]>([])
  const [sandboxTree, setSandboxTree] = useState<SandboxTreeNode[]>([])
  const [llmProvider, setLlmProvider] = useState('qwen')
  const [mockMode, setMockMode] = useState(true)
  const [neo4jReady, setNeo4jReady] = useState(false)
  const pollRef = useRef<number | null>(null)
  const lastLoadedWriteKey = useRef('')
  const browseRequestId = useRef(0)

  const isRunning = workflow?.status === 'running'
  const busy = isStarting || isDeciding || isRunning || isStopping

  const workflowSandboxPath = workflow?.sandboxPath?.trim() || ''
  const isWorkflowLocked =
    workflow?.status === 'running' ||
    workflow?.status === 'awaiting_human' ||
    workflow?.status === 'cancelled' ||
    Boolean(workflow?.awaitingHuman)

  const effectiveSandboxPath = resolveBrowseSandboxPath(
    selectedFolder,
    workflowSandboxPath,
    isWorkflowLocked
  )

  const loadSandboxFiles = useCallback(async (sandboxRoot?: string) => {
    const root = sandboxRoot?.trim() ? normalizeSandboxPath(sandboxRoot) : ''
    const requestId = ++browseRequestId.current
    try {
      const result = await listSandboxFiles(root || undefined)
      if (requestId !== browseRequestId.current) return
      const files = scopeFilesToRoot(result.files || [], root)
      const tree: SandboxTreeNode[] =
        result.tree?.length ? result.tree : buildTreeFromPaths(files)
      setSandboxFiles(files)
      setSandboxTree(tree)
    } catch {
      if (requestId !== browseRequestId.current) return
      setSandboxFiles([])
      setSandboxTree([])
    }
  }, [])

  const browseSandbox = useCallback(
    (sandboxRoot?: string, clearEditor = true) => {
      const root = sandboxRoot?.trim() || ''
      if (clearEditor) {
        setEditorPath('')
        setEditorContent('')
        setEditorBaseline(null)
        setEditorFileStatus(null)
        setDraftFileStatuses({})
      }
      if (!root) {
        setSandboxFiles([])
        setSandboxTree([])
        return
      }
      void loadSandboxFiles(root)
    },
    [loadSandboxFiles]
  )

  const handleSandboxFolderSelect = useCallback(
    (value: string) => {
      setSelectedFolder(value)
      if (busy || isWorkflowLocked) return
      if (value !== NEW_RUN_VALUE) {
        browseSandbox(value)
        return
      }
      browseSandbox('')
    },
    [busy, isWorkflowLocked, browseSandbox]
  )

  useEffect(() => {
    void loadMode()
    void loadSandboxFolders()
    return () => stopPolling()
  }, [])

  useEffect(() => {
    if (selectedFolder === NEW_RUN_VALUE) {
      setFolderName(defaultFolderForTask(task))
    } else {
      setFolderName(selectedFolder)
    }
  }, [selectedFolder, task])

  useEffect(() => {
    if (workflow?.status === 'running' && workflow.workflowId) {
      startPolling(workflow.workflowId)
    } else {
      stopPolling()
    }
    return () => stopPolling()
  }, [workflow?.workflowId, workflow?.status])

  useEffect(() => {
    if (isWorkflowLocked) return
    browseSandbox(effectiveSandboxPath)
  }, [effectiveSandboxPath, isWorkflowLocked, browseSandbox])

  const loadSandboxFolders = async () => {
    try {
      setSandboxFolders(await fetchSandboxFolders())
    } catch {
      setSandboxFolders([])
    }
  }

  const loadMode = async () => {
    try {
      const mode = await fetchWorkflowMode()
      setLlmProvider(mode.llmProvider)
      setMockMode(mode.mock)
      setNeo4jReady(mode.neo4jReady)
    } catch {
      setLlmProvider('qwen')
      setMockMode(true)
      setNeo4jReady(false)
    }
  }

  const parentFolder =
    selectedFolder === NEW_RUN_VALUE ? '' : selectedFolder.trim()

  const loadDraftManifest = async (workflowId: string) => {
    try {
      const files = await fetchDraftManifest(workflowId)
      const map: Record<string, 'new' | 'modified'> = {}
      for (const entry of files) {
        if (entry.status === 'new' || entry.status === 'modified') {
          map[entry.path] = entry.status
        }
      }
      setDraftFileStatuses(map)
    } catch {
      setDraftFileStatuses({})
    }
  }

  const refreshSandboxFiles = () => {
    if (effectiveSandboxPath) {
      void loadSandboxFiles(effectiveSandboxPath)
    } else if (workflow?.sandboxFiles?.length) {
      setSandboxFiles(workflow.sandboxFiles)
      setSandboxTree(buildTreeFromPaths(workflow.sandboxFiles))
    }
    if (workflow?.workflowId) {
      void loadDraftManifest(workflow.workflowId)
    }
  }

  const awaitingApproval = Boolean(workflow?.awaitingHuman || workflow?.status === 'awaiting_human')
  const showEditorDiff =
    awaitingApproval && (editorFileStatus === 'new' || editorFileStatus === 'modified')

  const explorerProps = {
    sandboxFiles,
    sandboxTree,
    draftFileStatuses,
    activeSandboxPath: effectiveSandboxPath,
    editorPath,
    editorContent,
    editorBaseline,
    editorFileStatus,
    showDiff: showEditorDiff,
    readOnly: awaitingApproval,
    awaitingHuman: awaitingApproval,
    onEditorPathChange: setEditorPath,
    onEditorContentChange: setEditorContent,
    onLoadFile: (path?: string) => void handleLoadFile(path),
    onDeleteFile: (path: string) => void handleDeleteFile(path),
    onRefresh: refreshSandboxFiles,
    onSave: () => void handleSaveFile(),
  }

  const stopPolling = () => {
    if (pollRef.current != null) {
      window.clearInterval(pollRef.current)
      pollRef.current = null
    }
  }

  const startPolling = (workflowId: string) => {
    stopPolling()
    const poll = async () => {
      try {
        await applyWorkflow(await fetchWorkflow(workflowId), false)
      } catch {
        // keep polling; transient errors during long runs are fine
      }
    }
    void poll()
    pollRef.current = window.setInterval(() => void poll(), POLL_MS)
  }

  const applyWorkflow = async (data: WorkflowRun, loadLatestFile: boolean) => {
    setWorkflow(data)
    if (data.status === 'error') {
      setError(data.summary || 'Workflow failed')
    }
    if (data.status === 'cancelled') {
      stopPolling()
      if (data.sandboxFiles?.length) {
        setSandboxFiles(data.sandboxFiles)
        setSandboxTree(buildTreeFromPaths(data.sandboxFiles))
      }
      if (data.workflowId) {
        void loadDraftManifest(data.workflowId)
      }
    }
    const diskApproved = data.status === 'completed'
    const workflowOwnsSandbox =
      data.status === 'running' ||
      data.status === 'awaiting_human' ||
      data.awaitingHuman ||
      diskApproved
    if (diskApproved && data.sandboxPath?.trim() && selectedFolder !== NEW_RUN_VALUE) {
      await loadSandboxFiles(data.sandboxPath)
      setDraftFileStatuses({})
      setEditorBaseline(null)
      setEditorFileStatus(null)
    } else if (workflowOwnsSandbox && data.sandboxFiles?.length) {
      setSandboxFiles(data.sandboxFiles)
      setSandboxTree(buildTreeFromPaths(data.sandboxFiles))
    } else if (workflowOwnsSandbox) {
      setSandboxFiles([])
      setSandboxTree([])
    }
    if (data.workflowId && (data.awaitingHuman || data.status === 'awaiting_human' || data.status === 'running')) {
      await loadDraftManifest(data.workflowId)
    } else if (!data.awaitingHuman && data.status !== 'running') {
      setDraftFileStatuses({})
    }
    const writeSteps = (data.steps || []).filter((s) => s.toolName === 'write_file' && s.toolArgs)
    const latestWrite = writeSteps[writeSteps.length - 1]
    const fileToLoad =
      latestWrite?.toolArgs ||
      (loadLatestFile ? (data.filesWritten || [])[(data.filesWritten || []).length - 1] : undefined)
    const writeKey = fileToLoad ? `${fileToLoad}:${writeSteps.length}:${latestWrite?.observation ?? ''}` : ''
    if (
      fileToLoad &&
      writeKey !== lastLoadedWriteKey.current &&
      (loadLatestFile || data.status === 'running' || data.awaitingHuman)
    ) {
      lastLoadedWriteKey.current = writeKey
      await loadWorkflowFile(fileToLoad, data)
    }
  }

  const loadWorkflowFile = async (displayPath: string, data: WorkflowRun) => {
    const sandboxRoot = data.sandboxPath?.trim() || ''
    const loaded = await fetchSandboxFileContent(displayPath, sandboxRoot, data)
    if (loaded != null) {
      setEditorContent(loaded.content)
      setEditorPath(displayPath)
      setEditorBaseline(loaded.baseline ?? null)
      setEditorFileStatus(loaded.fileStatus ?? null)
    }
  }

  const handleRun = async (e?: FormEvent, overrideTask?: string) => {
    e?.preventDefault()
    const nextTask = (overrideTask ?? task).trim()
    if (!nextTask || busy) return
    if (overrideTask) setTask(nextTask)
    setIsStarting(true)
    setError('')
    setWorkflow(null)
    setSandboxFiles([])
    setSandboxTree([])
    setDraftFileStatuses({})
    setEditorBaseline(null)
    setEditorFileStatus(null)
    lastLoadedWriteKey.current = ''
    try {
      await applyWorkflow(
        await startWorkflow({
          task: nextTask,
          parentFolder: parentFolder.trim() || undefined,
          folderName: folderName.trim() || undefined,
        }),
        true
      )
    } catch (err: any) {
      const raw = err.response?.data?.summary || err.message || 'Workflow failed'
      setError(raw)
    } finally {
      setIsStarting(false)
    }
  }

  const handleStop = async () => {
    if (!workflow?.workflowId || !isRunning || isStopping) return
    setIsStopping(true)
    setError('')
    try {
      await applyWorkflow(await cancelWorkflow(workflow.workflowId), false)
    } catch (err: any) {
      const raw = err.response?.data?.summary || err.message || 'Failed to stop workflow'
      setError(raw)
    } finally {
      setIsStopping(false)
      stopPolling()
    }
  }

  const handleDecision = async (approved: boolean) => {
    if (!workflow?.workflowId || busy) return
    setIsDeciding(true)
    setError('')
    try {
      await applyWorkflow(await submitWorkflowDecision(workflow.workflowId, approved, notes), true)
      setNotes('')
      if (approved) {
        void loadSandboxFolders()
      } else {
        setEditorPath('')
        setEditorContent('')
        setEditorBaseline(null)
        setEditorFileStatus(null)
        setDraftFileStatuses({})
        lastLoadedWriteKey.current = ''
        if (effectiveSandboxPath) {
          await loadSandboxFiles(effectiveSandboxPath)
        }
      }
    } catch (err: any) {
      const raw = err.response?.data?.summary || err.message || 'Decision failed'
      setError(raw)
    } finally {
      setIsDeciding(false)
    }
  }

  const handleLoadFile = async (filePath?: string) => {
    const displayPath = filePath || editorPath.trim()
    if (!displayPath) return
    setError('')
    const loaded = await fetchSandboxFileContent(displayPath, effectiveSandboxPath, workflow)
    if (loaded != null) {
      setEditorContent(loaded.content)
      setEditorPath(displayPath)
      setEditorBaseline(loaded.baseline ?? null)
      setEditorFileStatus(loaded.fileStatus ?? null)
      return
    }
    setError('Failed to load file')
  }

  const handleSaveFile = async () => {
    if (!editorPath.trim()) return
    if (workflow?.awaitingHuman || workflow?.status === 'awaiting_human') {
      setError('Draft files cannot be saved to disk until you approve the workflow.')
      return
    }
    const pathToSave = resolveSandboxPath(editorPath.trim(), effectiveSandboxPath)
    try {
      await writeSandboxFile(pathToSave, editorContent)
      await loadSandboxFiles(effectiveSandboxPath || undefined)
    } catch {
      setError('Failed to save file')
    }
  }

  const handleDeleteFile = async (fileName: string) => {
    if (!confirm(`Delete ${fileName}?`)) return
    const pathToDelete = resolveSandboxPath(fileName, effectiveSandboxPath)
    try {
      await deleteSandboxFile(pathToDelete)
      await loadSandboxFiles(effectiveSandboxPath || undefined)
      if (editorPath === fileName) {
        setEditorPath('')
        setEditorContent('')
      }
    } catch {
      setError('Failed to delete file')
    }
  }

  const statusText = workflow?.status || (isStarting ? 'running' : 'idle')
  const phases = workflow?.phases?.length ? workflow.phases : EMPTY_PHASES
  const doneCount = phases.filter((p) => p.status === 'done' || p.status === 'waiting').length
  const currentPhase = workflow?.currentPhase ?? ''
  const isLiveRun = workflow?.status === 'running' || isRunning
  const activitySteps = (workflow?.steps ?? []).filter((s) =>
    ['requirements', 'architecture', 'implementation', 'testing', 'review'].includes(
      s.stage.toLowerCase()
    )
  )
  const visibleActivity = visibleSteps(activitySteps)
  const showLivePanel =
    Boolean(workflow) &&
    (isLiveRun || workflow?.awaitingHuman || visibleActivity.length > 0)

  return (
    <div className="sdlc-page">
      <header className="page-header sdlc-header">
        <div>
          <h1>SDLC Workflow</h1>
          <p>
            Six stages from requirements through human approval. Live status updates while a run is in
            progress.
          </p>
        </div>
        <div className="sdlc-mode" role="status" aria-live="polite">
          <CircleDot size={16} aria-hidden="true" />
          <span>
            {mockMode ? 'Mock' : `Live · ${llmProvider}`}
            {!mockMode && !neo4jReady ? ' · Neo4j off' : !mockMode && neo4jReady ? ' · Neo4j' : ''}
          </span>
        </div>
      </header>

      <form onSubmit={(e) => void handleRun(e)} className="sdlc-task panel">
        <label htmlFor="sdlc-task">Task</label>
        <textarea
          id="sdlc-task"
          value={task}
          onChange={(e) => setTask(e.target.value)}
          placeholder="Describe the program to design, implement, test, and review."
          rows={3}
          disabled={busy}
        />
        <div className="sdlc-run-options sdlc-run-options-compact">
          <div className="sdlc-run-option">
            <label htmlFor="sdlc-sandbox-folder">Sandbox folder</label>
            <select
              id="sdlc-sandbox-folder"
              value={selectedFolder}
              onChange={(e) => handleSandboxFolderSelect(e.target.value)}
              disabled={busy || isWorkflowLocked}
            >
              <option value={NEW_RUN_VALUE}>New run (auto)</option>
              {sandboxFolders.map((folder) => (
                <option key={folder} value={folder}>
                  {folder}
                </option>
              ))}
            </select>
          </div>
          <div className="sdlc-run-option">
            <label htmlFor="sdlc-folder-name">Folder name</label>
            <input
              id="sdlc-folder-name"
              type="text"
              value={folderName}
              onChange={(e) => setFolderName(e.target.value)}
              placeholder="e.g. python-cli-notes-app or runs/my-run"
              disabled={busy}
              spellCheck={false}
            />
          </div>
        </div>
        <p className="muted sdlc-parent-hint">
          {selectedFolder === NEW_RUN_VALUE ? (
            <>
              Target folder defaults from your task slug (saved under <code>runs/</code> unless you
              include a path). Draft files stay in memory until approval.
            </>
          ) : (
            <>
              Continue from <code>{selectedFolder}</code>. Edit folder name to run into a renamed
              copy; drafts replace files only after you approve.
            </>
          )}
        </p>
        <div className="sdlc-task-actions">
          <button type="submit" className="btn-primary" disabled={busy || !task.trim() || !folderName.trim()}>
            {isStarting || isRunning ? <Loader2 size={16} className="spin" /> : <Send size={16} />}
            {isStarting || isRunning ? 'Running…' : 'Start workflow'}
          </button>
          {isRunning && (
            <button type="button" className="btn-ghost sdlc-stop-btn" onClick={() => void handleStop()} disabled={isStopping}>
              {isStopping ? <Loader2 size={16} className="spin" /> : <Square size={16} aria-hidden="true" />}
              Stop
            </button>
          )}
          <button type="button" className="btn-ghost" onClick={() => setTask(EXAMPLE_TASK)} disabled={busy}>
            Use example
          </button>
          <StatusBadge status={statusText} />
        </div>
        {(isRunning || isStarting) && (
          <p className="sdlc-live-hint" aria-live="polite">
            <Loader2 size={14} className="spin" aria-hidden="true" />
            Live updates · phase {workflow?.currentPhase || 'requirements'} · {doneCount}/{phases.length}{' '}
            stages finished
          </p>
        )}
      </form>

      {error && (
        <p className="sdlc-error" role="alert">
          <AlertCircle size={16} aria-hidden="true" />
          Error: {error}
        </p>
      )}

      <div className="sdlc-layout">
        <section className="sdlc-artifacts">
          <div className="panel sdlc-timeline-panel">
            <div className="sdlc-section-head">
              <h2>Phase progress</h2>
              <span className="muted">
                {doneCount} of {phases.length} complete
              </span>
            </div>
            <SdlcTimeline
              phases={phases}
              activePhaseId={currentPhase}
              liveLine={isLiveRun ? liveActivityLine(workflow?.summary, visibleActivity) : undefined}
              animateLive={isLiveRun}
            />
          </div>

          <PhaseOutput
            title="Requirements"
            body={workflow?.requirementsDoc}
            steps={
              isLiveRun && currentPhase === 'requirements'
                ? visibleActivity.filter((s) => s.stage.toLowerCase() === 'requirements')
                : undefined
            }
            forceShow={isLiveRun && currentPhase === 'requirements'}
            liveLabel={currentPhase}
            llmProvider={llmProvider}
            embedded
          />
          <PhaseOutput
            title="Architecture"
            body={workflow?.architectureDoc}
            steps={
              isLiveRun && currentPhase === 'architecture'
                ? visibleActivity.filter((s) => s.stage.toLowerCase() === 'architecture')
                : undefined
            }
            forceShow={isLiveRun && currentPhase === 'architecture'}
            liveLabel={currentPhase}
            llmProvider={llmProvider}
            embedded
          />

          {workflow?.summary && workflow.status !== 'running' && (
            <div className="panel">
              <h2>Summary</h2>
              <pre className="artifact-body">{workflow.summary}</pre>
            </div>
          )}
        </section>

        {showLivePanel && (
          <section className="panel sdlc-live-panel" aria-label="Live process">
            <div className="sdlc-section-head">
              <h2>Live process</h2>
              {isLiveRun ? <span className="live-pill">Updating</span> : null}
            </div>

            {workflow?.awaitingHuman && (
              <div className="approval-box approval-box-inline">
                <div className="sdlc-section-head">
                  <h3>Human approval</h3>
                  <StatusBadge status="awaiting_human" />
                </div>
                <p className="muted">
                  Review finished. Approve the result or send revision notes back to implementation.
                </p>
                <label htmlFor="approval-notes">Revision notes</label>
                <textarea
                  id="approval-notes"
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  rows={3}
                  disabled={isDeciding}
                  placeholder="Optional notes if you request changes"
                />
                <div className="approval-actions">
                  <button
                    type="button"
                    className="btn-primary"
                    onClick={() => void handleDecision(true)}
                    disabled={isDeciding}
                  >
                    {isDeciding ? <Loader2 size={16} className="spin" /> : <CheckCircle2 size={16} />}
                    Approve
                  </button>
                  <button type="button" onClick={() => void handleDecision(false)} disabled={isDeciding}>
                    Revert & request changes
                  </button>
                </div>
              </div>
            )}

            <div className="artifact-panel artifact-panel-embedded">
              <p className="muted live-process-hint">
                Each SDLC phase shows its model turns and sandbox tool calls in order. The active phase is highlighted.
              </p>
              {visibleActivity.length > 0 ? (
                <LiveProcessByPhase
                  steps={visibleActivity}
                  live={isLiveRun}
                  activePhase={currentPhase}
                  llmProvider={llmProvider}
                />
              ) : isLiveRun ? (
                <p className="muted sdlc-waiting">Waiting for agent activity…</p>
              ) : (
                <p className="muted">No agent steps recorded.</p>
              )}
            </div>

            {workflow?.testReport && workflow.status !== 'running' ? (
              <PhaseOutput title="Test report" body={workflow.testReport} embedded />
            ) : null}
            {workflow?.reviewNotes && workflow.status !== 'running' ? (
              <PhaseOutput
                title={`Review${workflow?.reviewVerdict ? ` (${workflow.reviewVerdict})` : ''}`}
                body={workflow.reviewNotes}
                embedded
              />
            ) : null}
          </section>
        )}

        <aside className="sdlc-files panel">
          <div className="files-header">
            <h2>Sandbox</h2>
            <div className="files-header-actions">
              <SandboxExplorerExpandButton onClick={() => setExplorerOpen(true)} />
              <button
                type="button"
                className="btn-ghost"
                onClick={refreshSandboxFiles}
                aria-label="Refresh files"
              >
                <RefreshCw size={16} />
              </button>
            </div>
          </div>
          <SandboxExplorer key={effectiveSandboxPath || 'sandbox-empty'} {...explorerProps} layout="compact" />
        </aside>

        <SandboxExplorerModal
          key={effectiveSandboxPath || 'sandbox-empty-modal'}
          {...explorerProps}
          open={explorerOpen}
          onClose={() => setExplorerOpen(false)}
        />
      </div>
    </div>
  )
}
