import { useState, useRef, useEffect } from 'react'
import { Send, Plus, X } from 'lucide-react'
import SandboxExplorer from '../components/SandboxExplorer'
import { runAgents, loadPersona } from '../api/agentsApi'
import { useSandboxEditor } from '../hooks/useSandboxEditor'
import { AgentStep } from '../types/agent'
import './AIAgents.css'

interface Persona {
  name: string
  type: string
  path: string
}

const PERSONA_OPTIONS: Record<string, Persona[]> = {
  'Software Engineer': [
    { name: 'Default', type: 'Software Engineer', path: 'development/software_engineers/Software-Engineer.md' },
    { name: 'Emily', type: 'Software Engineer', path: 'development/software_engineers/Emily.md' },
    { name: 'Ari', type: 'Software Engineer', path: 'development/software_engineers/Ari.md' },
  ],
  'System Architect': [
    { name: 'Default', type: 'System Architect', path: 'design_architecture/system_architects/System-Architect.md' },
    { name: 'Sarah', type: 'System Architect', path: 'design_architecture/system_architects/Sarah.md' },
    { name: 'Alex-Architect', type: 'System Architect', path: 'design_architecture/system_architects/Alex-Architect.md' },
  ],
  'QA Engineer': [
    { name: 'Default', type: 'QA Engineer', path: 'quality_testing/qa_engineers/Alex-QA.md' },
  ],
  'DevOps Engineer': [
    { name: 'Default', type: 'DevOps Engineer', path: 'operations_deployment/devops_engineers/Alex-DevOps.md' },
  ],
}

const EXAMPLE_TASK =
  'Write a Python program fizzbuzz.py that prints FizzBuzz from 1 to 20 (Fizz for multiples of 3, Buzz for 5, FizzBuzz for both). Then run it to verify the output.'

export default function AIAgents() {
  const [input, setInput] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [activePersona, setActivePersona] = useState<Persona>(PERSONA_OPTIONS['Software Engineer'][0])
  const [selectedType, setSelectedType] = useState('Software Engineer')
  const [selectedPersona, setSelectedPersona] = useState<Persona>(PERSONA_OPTIONS['Software Engineer'][0])
  const [includeReviewer, setIncludeReviewer] = useState(true)

  const sandbox = useSandboxEditor()

  const [steps, setSteps] = useState<AgentStep[]>([])
  const [status, setStatus] = useState('')
  const [summary, setSummary] = useState('')

  const messagesEndRef = useRef<HTMLDivElement>(null)

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    scrollToBottom()
  }, [steps, summary, isLoading])

  useEffect(() => {
    const options = PERSONA_OPTIONS[selectedType]
    setSelectedPersona(options[0])
  }, [selectedType])

  const handleLoadPersona = async () => {
    try {
      await loadPersona(selectedPersona.path)
      setActivePersona(selectedPersona)
      alert(`Loaded persona: ${selectedPersona.name}`)
    } catch (error: any) {
      const errorMsg = error.response?.data?.error || error.message || 'Failed to load persona'
      alert(`Failed to load persona: ${errorMsg}`)
    }
  }

  const handleNewTask = () => {
    setSteps([])
    setStatus('')
    setSummary('')
    setActivePersona(PERSONA_OPTIONS['Software Engineer'][0])
    setSelectedType('Software Engineer')
    setSelectedPersona(PERSONA_OPTIONS['Software Engineer'][0])
    sandbox.clearEditor()
  }

  const handleRemovePersona = () => {
    const defaultPersona = PERSONA_OPTIONS['Software Engineer'][0]
    setActivePersona(defaultPersona)
    setSelectedType('Software Engineer')
    setSelectedPersona(defaultPersona)
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()

    if (!input.trim() || isLoading) return

    const task = input.trim()
    setInput('')
    setIsLoading(true)
    setSteps([])
    setStatus('running')
    setSummary('')

    try {
      const data = await runAgents({
        task,
        engineerPersonaPath: activePersona?.path || '',
        reviewerPersonaPath: 'quality_testing/qa_engineers/Alex-QA.md',
        includeReviewer,
      })

      setStatus(data.status || 'completed')
      setSummary(data.summary || '')
      setSteps(data.steps || [])
      await sandbox.refresh()

      const written: string[] = data.filesWritten || []
      const fileToLoad = written[written.length - 1]
      if (fileToLoad) {
        await sandbox.loadEditorFile(fileToLoad)
      }
    } catch (error: any) {
      console.error('Error running agents:', error)
      const errorMsg = error.response?.data?.summary || error.message || 'Unknown error'
      setStatus('error')
      setSummary(errorMsg)
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="agents-page">
      <div className="agents-header page-header">
        <div>
          <h1>AI Agents</h1>
          <p>Engineer and reviewer run against sandbox tools. For the full six-stage flow, use SDLC Workflow.</p>
        </div>
      </div>

      <div className="agents-container">
        <div className="left-panel">
          <div className="chat-section">
            <h3>Run timeline</h3>

            <div className="persona-selector">
              <div className="persona-info-header">
                <h4>Engineer persona</h4>
                <div className="persona-details">
                  <p><strong>Type:</strong> {activePersona.type}</p>
                  <p><strong>Persona:</strong> {activePersona.name}</p>
                  <p><strong>Reviewer:</strong> {includeReviewer ? 'Alex-QA' : 'Off'}</p>
                </div>
              </div>

              <div className="selector-row">
                <div className="selector-group">
                  <label htmlFor="persona-type">Persona Type:</label>
                  <select
                    id="persona-type"
                    value={selectedType}
                    onChange={(e) => setSelectedType(e.target.value)}
                    disabled={isLoading}
                  >
                    {Object.keys(PERSONA_OPTIONS).map((type) => (
                      <option key={type} value={type}>
                        {type}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="selector-group">
                  <label htmlFor="persona-name">Persona:</label>
                  <select
                    id="persona-name"
                    value={selectedPersona.name}
                    onChange={(e) => {
                      const persona = PERSONA_OPTIONS[selectedType].find(
                        (p) => p.name === e.target.value
                      )
                      if (persona) setSelectedPersona(persona)
                    }}
                    disabled={isLoading}
                  >
                    {PERSONA_OPTIONS[selectedType].map((persona) => (
                      <option key={persona.name} value={persona.name}>
                        {persona.name}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="persona-actions">
                <button onClick={handleLoadPersona} disabled={isLoading}>
                  Load Persona
                </button>
                <button onClick={handleRemovePersona} disabled={isLoading}>
                  <X size={16} />
                  Reset
                </button>
                <button onClick={handleNewTask} disabled={isLoading} title="New task">
                  <Plus size={16} />
                </button>
              </div>

              <label className="reviewer-toggle">
                <input
                  type="checkbox"
                  checked={includeReviewer}
                  onChange={(e) => setIncludeReviewer(e.target.checked)}
                  disabled={isLoading}
                />
                Include QA reviewer
              </label>
            </div>

            <div className="messages-container">
              {steps.length === 0 && !summary && !isLoading ? (
                <div className="empty-messages">
                  <p>Give the agents a coding task. They will write files, run them, and review the result.</p>
                  <button
                    className="example-task-btn"
                    onClick={() => setInput(EXAMPLE_TASK)}
                    type="button"
                  >
                    Use FizzBuzz example
                  </button>
                </div>
              ) : (
                <>
                  {status && (
                    <div className={`run-status status-${status}`}>
                      Status: {status}
                    </div>
                  )}
                  {steps.map((step, index) => (
                    <div key={index} className={`agent-message stage-${step.stage.toLowerCase()}`}>
                      <div className="message-role">
                        {step.stage} · step {step.step} · {step.toolName}
                      </div>
                      {step.thought && <pre className="message-text">{step.thought}</pre>}
                      <pre className="tool-args">{step.toolArgs}</pre>
                      <pre className="tool-observation">{step.observation}</pre>
                    </div>
                  ))}
                  {summary && (
                    <div className="agent-message assistant">
                      <div className="message-role">Summary</div>
                      <pre className="message-text">{summary}</pre>
                    </div>
                  )}
                </>
              )}
              {isLoading && (
                <div className="agent-message assistant">
                  <div className="message-role">Pipeline</div>
                  <pre className="message-text">
                    {includeReviewer
                      ? 'Engineer is using tools, then QA will review and run the code...'
                      : 'Engineer is using tools in the sandbox...'}
                  </pre>
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>

            <form onSubmit={handleSubmit} className="agent-input-form">
              <div className="input-wrapper">
                <input
                  type="text"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  placeholder="Describe a coding task to generate, run, and review..."
                  disabled={isLoading}
                />
              </div>
              <button type="submit" disabled={isLoading || !input.trim()}>
                <Send size={20} />
              </button>
            </form>
          </div>
        </div>

        <div className="right-panel">
          <div className="editor-section agents-sandbox-panel">
            <SandboxExplorer
              sandboxFiles={sandbox.files}
              sandboxTree={sandbox.tree}
              draftFileStatuses={{}}
              activeSandboxPath=""
              editorPath={sandbox.editorPath}
              editorContent={sandbox.editorContent}
              editorBaseline={null}
              editorFileStatus={null}
              showDiff={false}
              readOnly={false}
              awaitingHuman={false}
              onEditorPathChange={sandbox.setEditorPath}
              onEditorContentChange={sandbox.setEditorContent}
              onLoadFile={(path) => void sandbox.loadEditorFile(path)}
              onDeleteFile={async (path) => {
                if (!confirm(`Delete ${path}?`)) return
                await sandbox.deleteFile(path)
                if (sandbox.editorPath === path) sandbox.clearEditor()
              }}
              onRefresh={() => void sandbox.refresh()}
              onSave={() => void sandbox.saveEditorFile().catch(() => alert('Failed to save file'))}
            />
            <p className="editor-info">
              The Engineer writes files with <code>write_file</code> and verifies them with <code>run_code</code>.
              Paths are validated so agents cannot leave <code>data/sandbox</code>.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
