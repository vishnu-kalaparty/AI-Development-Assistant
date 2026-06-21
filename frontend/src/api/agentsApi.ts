import axios from 'axios'
import { startWorkflowBlocking } from './workflowApi'
import { AgentStep } from '../types/agent'

export interface AgentRunPayload {
  task: string
  engineerPersonaPath: string
  reviewerPersonaPath: string
  includeReviewer: boolean
}

export type AgentRunResult = {
  status: string
  summary: string
  steps: AgentStep[]
  filesWritten: string[]
  sandboxFiles?: string[]
}

export async function runAgents(payload: AgentRunPayload, timeoutMs = 180000): Promise<AgentRunResult> {
  const data = await startWorkflowBlocking(
    {
      task: payload.task,
      engineerPersonaPath: payload.engineerPersonaPath,
      reviewerPersonaPath: payload.reviewerPersonaPath,
      includeReviewer: payload.includeReviewer,
    },
    timeoutMs
  )
  return {
    status: data.status || 'completed',
    summary: data.summary || '',
    steps: data.steps || [],
    filesWritten: data.filesWritten || [],
    sandboxFiles: data.sandboxFiles,
  }
}

export async function loadPersona(personaPath: string): Promise<void> {
  await axios.post('/api/agents/load-persona', { personaPath })
}
