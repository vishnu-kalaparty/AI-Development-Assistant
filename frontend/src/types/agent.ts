export interface AgentStep {
  stage: string
  step: number
  thought: string
  toolName: string
  toolArgs: string
  observation: string
}

export type DraftFileStatus = 'new' | 'modified' | 'unchanged' | 'disk'
