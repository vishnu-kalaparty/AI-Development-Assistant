import axios from 'axios'
import { AgentStep } from '../types/agent'
import { WorkflowPhase } from '../components/SdlcTimeline'

export interface WorkflowRun {
  workflowId: string
  status: string
  currentPhase: string
  summary: string
  requirementsDoc: string
  architectureDoc: string
  testReport: string
  reviewVerdict: string
  reviewNotes: string
  testsPassed: boolean
  awaitingHuman: boolean
  phases: WorkflowPhase[]
  steps: AgentStep[]
  filesWritten: string[]
  sandboxFiles: string[]
  sandboxPath?: string
}

export interface WorkflowMode {
  llmProvider: string
  mock: boolean
  neo4jReady: boolean
}

export interface WorkflowStartPayload {
  task: string
  parentFolder?: string
  folderName?: string
  engineerPersonaPath?: string
  reviewerPersonaPath?: string
  includeReviewer?: boolean
  blocking?: boolean
}

export interface DraftManifestEntry {
  path: string
  status: string
}

export async function fetchWorkflowMode(): Promise<WorkflowMode> {
  const result = await axios.get('/api/workflow/mode')
  return {
    llmProvider: String(result.data.llmProvider ?? 'qwen'),
    mock: result.data.mock === true,
    neo4jReady: result.data.neo4jReady === true,
  }
}

export async function fetchSandboxFolders(): Promise<string[]> {
  const result = await axios.get('/api/workflow/sandbox-folders')
  return result.data.folders || []
}

export async function startWorkflow(
  payload: WorkflowStartPayload,
  timeoutMs = 30000
): Promise<WorkflowRun> {
  const response = await axios.post('/api/workflow/run', payload, { timeout: timeoutMs })
  return response.data
}

/** Blocking agent-style run (sync pipeline, no HITL). */
export async function startWorkflowBlocking(
  payload: WorkflowStartPayload,
  timeoutMs = 180000
): Promise<WorkflowRun> {
  const response = await axios.post(
    '/api/workflow/run',
    { ...payload, blocking: true },
    { timeout: timeoutMs }
  )
  return response.data
}

export async function fetchWorkflow(workflowId: string): Promise<WorkflowRun> {
  const response = await axios.get(`/api/workflow/${workflowId}`)
  return response.data
}

export async function cancelWorkflow(workflowId: string): Promise<WorkflowRun> {
  const response = await axios.post(`/api/workflow/${workflowId}/cancel`)
  return response.data
}

export async function submitWorkflowDecision(
  workflowId: string,
  approved: boolean,
  notes: string,
  timeoutMs = 30000
): Promise<WorkflowRun> {
  const response = await axios.post(
    `/api/workflow/${workflowId}/decision`,
    { approved, notes },
    { timeout: timeoutMs }
  )
  return response.data
}

export async function fetchDraftManifest(workflowId: string): Promise<DraftManifestEntry[]> {
  const response = await axios.get(`/api/workflow/${workflowId}/draft-manifest`)
  return (response.data.files || []) as DraftManifestEntry[]
}

export async function readWorkflowDraft(
  workflowId: string,
  filePath: string
): Promise<{ content?: string; baseline?: string | null; fileStatus?: string }> {
  const response = await axios.post(`/api/workflow/${workflowId}/read-draft`, { filePath })
  return response.data
}
