import { AlertCircle, CheckCircle2, Circle, CircleDot, Clock } from 'lucide-react'
import StreamingText from './StreamingText'
import './SdlcTimeline.css'

export interface WorkflowPhase {
  id: string
  label: string
  status: string
  summary?: string
}

interface SdlcTimelineProps {
  phases: WorkflowPhase[]
  activePhaseId?: string
  liveLine?: string
  animateLive?: boolean
}

function StatusIcon({ status }: { status: string }) {
  if (status === 'done') return <CheckCircle2 size={16} aria-hidden="true" />
  if (status === 'active') return <CircleDot size={16} aria-hidden="true" />
  if (status === 'waiting') return <Clock size={16} aria-hidden="true" />
  if (status === 'error') return <AlertCircle size={16} aria-hidden="true" />
  return <Circle size={16} aria-hidden="true" />
}

function statusLabel(status: string) {
  switch (status) {
    case 'done':
      return 'Done'
    case 'active':
      return 'In progress'
    case 'waiting':
      return 'Waiting'
    case 'error':
      return 'Error'
    default:
      return 'Pending'
  }
}

export default function SdlcTimeline({
  phases,
  activePhaseId,
  liveLine,
  animateLive,
}: SdlcTimelineProps) {
  return (
    <ol className="sdlc-timeline" aria-label="SDLC phases">
      {phases.map((phase) => {
        const isActive = phase.status === 'active' && phase.id === activePhaseId
        const showLiveLine = isActive && Boolean(liveLine?.trim())
        const summary = phase.summary?.trim()
        const animateSummary = Boolean(animateLive && isActive && summary)

        return (
          <li key={phase.id} className={`timeline-item status-${phase.status}`}>
            <div className="timeline-heading">
              <StatusIcon status={phase.status} />
              <span className="timeline-label">{phase.label}</span>
              <span className="status-pill">{statusLabel(phase.status)}</span>
            </div>
            {showLiveLine ? (
              <p className="timeline-live-line">
                <StreamingText text={liveLine!} animate={animateLive} charDelayMs={6} />
              </p>
            ) : null}
            {summary ? (
              <pre className="timeline-summary">
                <StreamingText
                  text={summary}
                  animate={animateSummary}
                  charDelayMs={4}
                />
              </pre>
            ) : isActive && animateLive && !showLiveLine ? (
              <p className="timeline-live-line muted">
                <StreamingText text="Starting phase…" animate charDelayMs={8} />
              </p>
            ) : null}
          </li>
        )
      })}
    </ol>
  )
}
