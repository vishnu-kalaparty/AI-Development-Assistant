import { useState, useRef, useEffect } from 'react'
import { Send, Plus, X, FolderOpen, Save, Trash2, RefreshCw } from 'lucide-react'
import CodeEditor from '../components/CodeEditor'
import axios from 'axios'
import './AIAgents.css'

interface Message {
  role: 'user' | 'assistant' | 'tool'
  content: string
}

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

export default function AIAgents() {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [activePersona, setActivePersona] = useState<Persona>(PERSONA_OPTIONS['Software Engineer'][1]) // Changed to index 1 (Emily)
  const [selectedType, setSelectedType] = useState('Software Engineer')
  const [selectedPersona, setSelectedPersona] = useState<Persona>(PERSONA_OPTIONS['Software Engineer'][1]) // Changed to index 1 (Emily)
  
  const [editorContent, setEditorContent] = useState('')
  const [editorPath, setEditorPath] = useState('')
  const [editorLanguage] = useState('python')
  const [sandboxFiles, setSandboxFiles] = useState<string[]>([])
  
  const messagesEndRef = useRef<HTMLDivElement>(null)

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages])

  useEffect(() => {
    setSelectedPersona(PERSONA_OPTIONS[selectedType][1])
  }, [selectedType])

  useEffect(() => {
    loadSandboxFiles()
  }, [])

  const loadSandboxFiles = async () => {
    try {
      const result = await axios.get('/api/agents/files')
      setSandboxFiles(result.data.files || [])
    } catch (error) {
      console.error('Error loading sandbox files:', error)
    }
  }

  const handleLoadPersona = async () => {
    try {
      const response = await axios.post('/api/agents/load-persona', {
        personaPath: selectedPersona.path,
      })

      setActivePersona(selectedPersona)
      setMessages([])
      console.log('Persona loaded:', response.data)
      alert(`Loaded persona: ${selectedPersona.name}`)
    } catch (error: any) {
      console.error('Error loading persona:', error)
      const errorMsg = error.response?.data?.error || error.message || 'Failed to load persona'
      alert(`Failed to load persona: ${errorMsg}`)
    }
  }

  const handleNewTask = () => {
    setMessages([])
    // Reset to default persona (Emily - index 1)
    setActivePersona(PERSONA_OPTIONS['Software Engineer'][1])
    setSelectedType('Software Engineer')
    setSelectedPersona(PERSONA_OPTIONS['Software Engineer'][1])
    setEditorContent('')
    setEditorPath('')
  }

  const handleRemovePersona = () => {
    // Reset to default persona (Emily - index 1)
    const defaultPersona = PERSONA_OPTIONS['Software Engineer'][1]
    setActivePersona(defaultPersona)
    setSelectedType('Software Engineer')
    setSelectedPersona(defaultPersona)
    alert(`Reset to default persona: ${defaultPersona.name}`)
  }

  const handleLoadFile = async (filePath?: string) => {
    const pathToLoad = filePath || editorPath.trim()
    if (!pathToLoad) {
      alert('Enter a file path to load')
      return
    }

    try {
      const response = await axios.post('/api/agents/read-file', {
        filePath: pathToLoad,
      })

      setEditorContent(response.data.content)
      setEditorPath(pathToLoad)
      alert(`Loaded file: ${pathToLoad}`)
    } catch (error) {
      console.error('Error loading file:', error)
      alert('Failed to load file')
    }
  }

  const handleSaveFile = async () => {
    if (!editorPath.trim()) {
      alert('Enter a file path before saving')
      return
    }

    try {
      await axios.post('/api/agents/write-file', {
        filePath: editorPath.trim(),
        content: editorContent,
      })

      alert(`Saved file: ${editorPath}`)
      await loadSandboxFiles()
    } catch (error) {
      console.error('Error saving file:', error)
      alert('Failed to save file')
    }
  }

  const handleDeleteFile = async (fileName: string) => {
    if (!confirm(`Delete ${fileName}?`)) return

    try {
      await axios.delete('/api/agents/delete-file', { data: { fileName } })
      await loadSandboxFiles()
      if (editorPath === fileName) {
        setEditorPath('')
        setEditorContent('')
      }
      alert(`Deleted ${fileName}`)
    } catch (error) {
      console.error('Error deleting file:', error)
      alert('Error deleting file')
    }
  }

  const extractCodeFromResponse = (text: string) => {
    // Match code blocks with language specifier: ```language\ncode\n```
    const codeBlockRegex = /```(\w+)?\n([\s\S]*?)```/g
    const matches = [...text.matchAll(codeBlockRegex)]
    
    if (matches.length > 0) {
      const lastMatch = matches[matches.length - 1] // Get the last code block
      const extractedCode = lastMatch[2].trim()
      return extractedCode
    }
    
    return null
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    
    if (!input.trim() || isLoading) return

    const userMessage: Message = { role: 'user', content: input }
    setMessages((prev) => [...prev, userMessage])
    setInput('')
    setIsLoading(true)

    try {
      const response = await axios.post('/api/agents/chat', {
        messages: [...messages, userMessage],
        personaPath: activePersona?.path || '',
        editorContent: editorContent || '',
        editorPath: editorPath || '',
      })

      const newMessages = response.data.messages || []
      setMessages(newMessages)

      // Extract code from the last assistant message
      const lastAssistantMsg = newMessages
        .filter((m: Message) => m.role === 'assistant')
        .pop()
      
      if (lastAssistantMsg) {
        const extractedCode = extractCodeFromResponse(lastAssistantMsg.content)
        if (extractedCode) {
          setEditorContent(extractedCode)
        }
      }

      // Update editor if file was loaded
      if (response.data.editorContent) {
        setEditorContent(response.data.editorContent)
      }
      if (response.data.editorPath) {
        setEditorPath(response.data.editorPath)
      }
    } catch (error: any) {
      console.error('Error sending message:', error)
      const errorMsg = error.response?.data?.error || error.message || 'Unknown error'
      const errorMessage: Message = {
        role: 'assistant',
        content: `Sorry, there was an error: ${errorMsg}`,
      }
      setMessages((prev) => [...prev, errorMessage])
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="agents-page">
      <div className="agents-header">
        <h1>AI Agents</h1>
        <p>ReAct-style coding agents with personas and sandbox tools</p>
      </div>

      <div className="agents-container">
        <div className="left-panel">
          <div className="chat-section">
            <h3>Chat & Personas</h3>
            
            <div className="persona-selector">
              <div className="persona-info-header">
                <h4>Active Persona</h4>
                <div className="persona-details">
                  <p><strong>Type:</strong> {activePersona.type}</p>
                  <p><strong>Persona:</strong> {activePersona.name}</p>
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
                  <Plus size={16} />
                  Load Persona
                </button>
                <button onClick={handleNewTask} disabled={isLoading}>
                  <Plus size={16} />
                  New Task
                </button>
                <button onClick={handleRemovePersona} className="remove-btn" disabled={isLoading} title="Reset to default">
                  <X size={16} />
                  Reset
                </button>
              </div>
            </div>

            <div className="messages-container">
              {messages.length === 0 ? (
                <div className="empty-messages">
                  <p>Describe your coding task, bug, or request...</p>
                </div>
              ) : (
                messages.map((message, index) => (
                  <div key={index} className={`agent-message ${message.role}`}>
                    <div className="message-role">
                      {message.role === 'user' ? 'User' : message.role === 'tool' ? 'Tool' : 'Assistant'}
                    </div>
                    <pre className="message-text">{message.content}</pre>
                  </div>
                ))
              )}
              {isLoading && (
                <div className="agent-message assistant">
                  <div className="message-role">Assistant</div>
                  <pre className="message-text">Thinking...</pre>
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>

            <form onSubmit={handleSubmit} className="agent-input-form">
              <div className="input-wrapper">
                {editorContent && (
                  <div className="context-indicator">
                    <span>📎 Active file in context</span>
                    {editorPath && <span className="file-path">{editorPath}</span>}
                  </div>
                )}
                <input
                  type="text"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  placeholder="Describe your coding task..."
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
          <div className="editor-section">
            <div className="editor-header">
              <div>
                <h3>Sandbox Code Editor</h3>
                <p className="editor-subtitle">Files in data/sandbox</p>
              </div>
              <button onClick={loadSandboxFiles} className="icon-button-small" title="Refresh files">
                <RefreshCw size={16} />
              </button>
            </div>

            <div className="sandbox-file-list">
              {sandboxFiles.length === 0 ? (
                <p className="empty-files">No files in sandbox</p>
              ) : (
                <div className="file-grid">
                  {sandboxFiles.map((file) => (
                    <div key={file} className="sandbox-file-item">
                      <button
                        className="file-load-btn"
                        onClick={() => handleLoadFile(file)}
                        title={`Load ${file}`}
                      >
                        <FolderOpen size={14} />
                        <span>{file}</span>
                      </button>
                      <button
                        className="file-delete-btn"
                        onClick={() => handleDeleteFile(file)}
                        title="Delete"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="editor-controls">
              <input
                type="text"
                value={editorPath}
                onChange={(e) => setEditorPath(e.target.value)}
                placeholder="e.g., main.py or src/app.py"
              />
              <button onClick={() => handleLoadFile()} className="icon-button">
                <FolderOpen size={18} />
                Load
              </button>
            </div>

            <div className="editor-wrapper">
              <CodeEditor
                value={editorContent}
                onChange={(value) => setEditorContent(value || '')}
                language={editorLanguage}
                height="420px"
              />
            </div>

            <button onClick={handleSaveFile} className="save-file-button">
              <Save size={18} />
              Save to Sandbox
            </button>

            <p className="editor-info">
              The chat agent can list/search/read/write files in <code>data/sandbox</code> using tools.
              You can load/save specific files into this editor for manual review and editing.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
