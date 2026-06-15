import { useEffect, useRef, useState } from 'react'
import { AlertCircle, Brain, CheckCircle2, CircleDot, Loader2, MessageSquare } from 'lucide-react'
import StreamingText from '../StreamingText'
import { AgentStep } from '../../types/agent'

export const ACTIVITY_PHASE_ORDER = ['requirements', 'architecture', 'implementation', 'testing', 'review'] as const

export const PHASE_LABELS: Record<string, string> = {
  requirements: 'Requirements',
  architecture: 'Architecture',
  implementation: 'Implementation',
  testing: 'Testing',
  review: 'Review',
}

const TOOL_LABELS: Record<string, string> = {
  list_files: 'List files',
  read_file: 'Read file',
  write_file: 'Write file',
  mkdir: 'Create folder',
  search_files: 'Search files',
  run_code: 'Run code',
  semantic_search: 'Semantic search',
  model: 'Model',
}

export function liveActivityLine(summary: string | undefined, steps: AgentStep[]): string {
  const latest = steps[steps.length - 1]
  if (latest) {
    if (latest.toolName === 'model') {
      return latest.thought?.trim() || latest.observation?.trim() || 'Thinking…'
    }
    const label = latest.toolName || 'agent'
    const detail =
      latest.thought?.trim() ||
      latest.observation?.trim() ||
      latest.toolArgs?.trim() ||
      ''
    return detail ? `${label}: ${detail}` : label
  }
  return summary?.trim() || 'Starting…'
}

export function visibleSteps(steps: AgentStep[]): AgentStep[] {
  return steps.filter((step) => {
    if (step.toolName !== 'model') {
      return true
    }
    return Boolean(step.observation?.trim() || step.thought?.trim())
  })
}

export function PhaseOutput({
  title,
  body,
  steps,
  forceShow,
  liveLabel,
  llmProvider,
  embedded,
}: {
  title: string
  body?: string
  steps?: AgentStep[]
  forceShow?: boolean
  liveLabel?: string
  llmProvider?: string
  embedded?: boolean
}) {
  if (!body && !steps?.length && !forceShow) return null
  return (
    <div className={embedded ? 'artifact-panel artifact-panel-embedded' : 'panel artifact-panel'}>
      <div className="sdlc-section-head">
        <h2>{title}</h2>
        {steps?.length ? <span className="muted">{steps.length} live steps</span> : null}
        {forceShow && liveLabel ? <span className="live-pill">{liveLabel}</span> : null}
      </div>
      {body ? (
        <pre className="artifact-body">
          <StreamingText text={body} animate={Boolean(forceShow)} charDelayMs={4} />
        </pre>
      ) : forceShow ? (
        <pre className="artifact-body muted">
          <StreamingText text="Working…" animate charDelayMs={8} />
        </pre>
      ) : null}
      {steps?.length ? (
        <PhaseSteps steps={steps} live={Boolean(forceShow)} llmProvider={llmProvider} />
      ) : null}
    </div>
  )
}

function groupStepsByPhase(steps: AgentStep[]) {
  const buckets = new Map<string, AgentStep[]>()
  for (const phase of ACTIVITY_PHASE_ORDER) {
    buckets.set(phase, [])
  }
  for (const step of steps) {
    const key = step.stage.toLowerCase()
    if (!buckets.has(key)) {
      buckets.set(key, [])
    }
    buckets.get(key)!.push(step)
  }
  return ACTIVITY_PHASE_ORDER.filter((phase) => (buckets.get(phase)?.length ?? 0) > 0).map((phase) => ({
    phase,
    label: PHASE_LABELS[phase] ?? phase,
    steps: buckets.get(phase) ?? [],
  }))
}

export function LiveProcessByPhase({
  steps,
  live,
  activePhase,
  llmProvider,
}: {
  steps: AgentStep[]
  live?: boolean
  activePhase?: string
  llmProvider?: string
}) {
  const groups = groupStepsByPhase(steps)
  const containerRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (!live || !containerRef.current) return
    containerRef.current.scrollTop = containerRef.current.scrollHeight
  }, [live, steps.length, steps[steps.length - 1]?.observation, steps[steps.length - 1]?.thought])

  return (
    <div className={`live-process-phases${live ? ' live-process-phases-active' : ''}`} ref={containerRef}>
      {groups.map((group) => {
        const isActive = live && activePhase?.toLowerCase() === group.phase
        const globalOffset = steps.findIndex((s) => s === group.steps[0])
        return (
          <section
            key={group.phase}
            className={`live-phase-block${isActive ? ' live-phase-block-active' : ''}`}
            aria-label={`${group.label} activity`}
          >
            <header className="live-phase-head">
              <span className="live-phase-title">{group.label}</span>
              <span className="live-phase-count">{group.steps.length} step{group.steps.length === 1 ? '' : 's'}</span>
              {isActive ? <span className="live-pill live-phase-active-pill">Active</span> : null}
            </header>
            <PhaseSteps
              steps={group.steps}
              live={live}
              llmProvider={llmProvider}
              globalStartIndex={globalOffset >= 0 ? globalOffset : 0}
              totalSteps={steps.length}
            />
          </section>
        )
      })}
    </div>
  )
}

function PhaseSteps({
  steps,
  live,
  llmProvider,
  globalStartIndex = 0,
  totalSteps,
}: {
  steps: AgentStep[]
  live?: boolean
  llmProvider?: string
  globalStartIndex?: number
  totalSteps?: number
}) {
  const count = totalSteps ?? steps.length

  return (
    <div className={`phase-steps${live ? ' phase-steps-live' : ''}${totalSteps != null ? ' phase-steps-nested' : ''}`}>
      {steps.map((step, index) => {
        const globalIndex = globalStartIndex + index
        const isLatest = live && globalIndex === count - 1
        const providerLabel = llmProvider?.trim() || 'model'
        const kindLabel = TOOL_LABELS[step.toolName] ?? (step.toolName || 'Message')
        return (
          <article
            key={`${step.stage}-${step.step}-${step.toolName ?? 'msg'}-${globalIndex}`}
            className={`step-card${step.toolName === 'model' ? ' step-card-model' : ''}${step.toolName && step.toolName !== 'model' ? ' step-card-tool' : ''}${isLatest ? ' step-card-latest' : ''}`}
          >
            <p className="step-meta">
              <span className="step-kind">{kindLabel}</span>
              <span className="step-num">#{step.step}</span>
              {step.toolName === 'model' ? (
                <span className="step-model-badge"> · {providerLabel}</span>
              ) : null}
              {step.toolName === 'write_file' && step.toolArgs ? (
                <span className="step-path"> · {step.toolArgs}</span>
              ) : null}
              {step.toolName === 'write_file' ? (
                <WriteLineBadge observation={step.observation} />
              ) : null}
            </p>
            {step.thought && step.toolName !== 'model' ? (
              <pre>
                <StreamingText text={step.thought} animate={isLatest} charDelayMs={6} />
              </pre>
            ) : null}
            {step.toolName === 'model' ? (
              <ModelStepContent step={step} live={isLatest} />
            ) : null}
            {step.toolArgs && step.toolName !== 'write_file' && step.toolName !== 'model' ? (
              <pre className="step-args">
                <span className="step-field-label">Input</span>
                <StreamingText text={step.toolArgs} animate={isLatest} charDelayMs={5} />
              </pre>
            ) : null}
            {step.observation && step.toolName !== 'model' ? (
              <pre className="step-obs">
                <span className="step-field-label">Result</span>
                <StreamingText text={step.observation} animate={isLatest} charDelayMs={5} />
              </pre>
            ) : null}
          </article>
        )
      })}
    </div>
  )
}

function ModelStepContent({ step, live }: { step: AgentStep; live?: boolean }) {
  const thought = step.thought?.trim()
  const response = step.observation?.trim()
  const showThought = Boolean(thought && thought !== response)
  const hasOutput = Boolean(response)
  const [reasoningOpen, setReasoningOpen] = useState(!hasOutput)

  useEffect(() => {
    if (hasOutput && live) {
      setReasoningOpen(false)
    }
  }, [hasOutput, live, response?.length])

  return (
    <div className="model-step-panels">
      {showThought ? (
        <details
          className="step-thinking-collapsed model-reasoning-collapse"
          open={reasoningOpen}
          onToggle={(e) => setReasoningOpen((e.target as HTMLDetailsElement).open)}
        >
          <summary>
            <Brain size={14} aria-hidden="true" />
            <span>Reasoning{hasOutput ? ' (click to expand)' : ''}</span>
          </summary>
          <div className="typewriter-block typewriter-thought">
            <StreamingText text={thought!} animate={Boolean(live && !hasOutput)} charDelayMs={4} />
          </div>
        </details>
      ) : null}
      {response ? (
        <section className="model-panel model-panel-response" aria-label="Model output">
          <header className="model-panel-head">
            <MessageSquare size={14} aria-hidden="true" />
            <span>{showThought ? 'Output' : 'Response'}</span>
          </header>
          <div className="typewriter-block typewriter-response">
            <StreamingText text={response} animate={Boolean(live)} charDelayMs={4} />
          </div>
        </section>
      ) : showThought ? (
        <p className="muted model-awaiting-output">
          <Loader2 size={12} className="spin" aria-hidden="true" /> Generating output…
        </p>
      ) : null}
    </div>
  )
}

function WriteLineBadge({ observation }: { observation?: string }) {
  if (!observation) return null
  const match = observation.match(/:\s*(\d+)\s*lines\s*\(([^)]+)\)/)
  if (!match) return null
  return (
    <span className="step-line-badge" title={observation}>
      {' '}
      · {match[1]} lines ({match[2]})
    </span>
  )
}

export function StatusBadge({ status }: { status: string }) {
  const label = statusLabel(status)
  return (
    <span className={`status-pill status-${status}`} aria-live="polite">
      <StatusIcon status={status} />
      {label}
    </span>
  )
}

function StatusIcon({ status }: { status: string }) {
  if (status === 'completed') return <CheckCircle2 size={14} aria-hidden="true" />
  if (status === 'awaiting_human') return <CircleDot size={14} aria-hidden="true" />
  if (status === 'error') return <AlertCircle size={14} aria-hidden="true" />
  if (status === 'running') return <Loader2 size={14} className="spin" aria-hidden="true" />
  return <CircleDot size={14} aria-hidden="true" />
}

function statusLabel(status: string) {
  switch (status) {
    case 'awaiting_human':
      return 'Waiting for approval'
    case 'completed':
      return 'Completed'
    case 'needs_changes':
      return 'Changes requested'
    case 'exhausted':
      return 'Round limit reached'
    case 'error':
      return 'Error'
    case 'cancelled':
      return 'Stopped'
    case 'running':
      return 'Running'
    default:
      return 'Idle'
  }
}
