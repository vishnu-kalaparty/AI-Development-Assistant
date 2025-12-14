import { useState, useEffect } from 'react'
import CodeEditor from '../components/CodeEditor'
import './CodeAssistant.css'
import axios from 'axios'
import { Save, FolderOpen, Trash2, RefreshCw } from 'lucide-react'

export default function CodeAssistant() {
  const [code, setCode] = useState('')
  const [language, setLanguage] = useState('python')
  const [action, setAction] = useState<'review' | 'debug' | 'modify'>('review')
  const [instruction, setInstruction] = useState('')
  const [response, setResponse] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [fileName, setFileName] = useState('')
  const [files, setFiles] = useState<string[]>([])
  const [isSaving, setIsSaving] = useState(false)

  useEffect(() => {
    loadFiles()
  }, [])

  const loadFiles = async () => {
    try {
      const result = await axios.get('/api/code/files')
      setFiles(result.data.files || [])
    } catch (error) {
      console.error('Error loading files:', error)
    }
  }

  const extractCodeFromResponse = (text: string) => {
    // Match code blocks with language specifier: ```language\ncode\n```
    // This regex handles optional language and captures code content
    const codeBlockRegex = /```(\w+)?\s*\n([\s\S]*?)```/g
    const matches = [...text.matchAll(codeBlockRegex)]
    
    if (matches.length > 0) {
      // For modify action, use the last code block (modified version)
      // For review/debug, also use the last one if multiple suggestions
      const lastMatch = matches[matches.length - 1]
      const detectedLang = lastMatch[1]?.toLowerCase()
      const extractedCode = lastMatch[2].trim()
      
      // Only extract if there's actual code content
      if (!extractedCode || extractedCode.length < 3) {
        return null
      }
      
      // Map common language names
      const langMap: Record<string, string> = {
        'py': 'python',
        'python': 'python',
        'js': 'javascript',
        'javascript': 'javascript',
        'ts': 'typescript',
        'typescript': 'typescript',
        'java': 'java',
        'cpp': 'cpp',
        'c++': 'cpp',
        'c': 'cpp',
        'go': 'go',
        'golang': 'go',
      }
      
      return {
        code: extractedCode,
        language: detectedLang && langMap[detectedLang] ? langMap[detectedLang] : language
      }
    }
    
    return null
  }

  const handleAnalyzeCode = async () => {
    if (!code.trim() || isLoading) return
    if (action === 'modify' && !instruction.trim()) return

    setIsLoading(true)
    setResponse('')

    try {
      const result = await axios.post('/api/code/assist', {
        code: code.trim(),
        language,
        action,
        instruction: instruction.trim(),
      })

      const responseText = result.data.response
      setResponse(responseText)
      
      // Extract and update code if found in response (especially for modify action)
      if (action === 'modify' || action === 'debug') {
        const extracted = extractCodeFromResponse(responseText)
        if (extracted) {
          console.log('Code extracted:', extracted.code.substring(0, 100) + '...')
          console.log('Language detected:', extracted.language)
          setCode(extracted.code)
          if (extracted.language !== language) {
            setLanguage(extracted.language)
          }
        } else {
          console.log('No code block found in response')
        }
      }
    } catch (error) {
      console.error('Error processing code:', error)
      setResponse('Sorry, there was an error processing your code.')
    } finally {
      setIsLoading(false)
    }
  }

  const handleSaveCode = async () => {
    if (!code.trim() || isSaving) return

    setIsSaving(true)
    try {
      const result = await axios.post('/api/code/save', {
        code: code.trim(),
        language,
        instruction: fileName.trim() || undefined,
      })

      alert(result.data.response)
      await loadFiles()
      setFileName('')
    } catch (error: any) {
      console.error('Error saving code:', error)
      alert(error.response?.data?.response || 'Error saving file')
    } finally {
      setIsSaving(false)
    }
  }

  const handleLoadFile = async (file: string) => {
    try {
      const result = await axios.post('/api/code/load', { fileName: file })
      setCode(result.data.content)
      setFileName(file)
      
      // Try to detect language from extension
      const ext = file.split('.').pop()?.toLowerCase()
      const langMap: Record<string, string> = {
        'py': 'python',
        'js': 'javascript',
        'ts': 'typescript',
        'java': 'java',
        'cpp': 'cpp',
        'go': 'go',
      }
      if (ext && langMap[ext]) {
        setLanguage(langMap[ext])
      }
    } catch (error) {
      console.error('Error loading file:', error)
      alert('Error loading file')
    }
  }

  const handleDeleteFile = async (file: string) => {
    if (!confirm(`Delete ${file}?`)) return

    try {
      await axios.delete('/api/code/delete', { data: { fileName: file } })
      await loadFiles()
      if (fileName === file) {
        setFileName('')
        setCode('')
      }
    } catch (error) {
      console.error('Error deleting file:', error)
      alert('Error deleting file')
    }
  }

  const getButtonText = () => {
    if (isLoading) return 'Processing...'
    switch (action) {
      case 'review':
        return 'Review Code'
      case 'debug':
        return 'Debug Code'
      case 'modify':
        return 'Modify Code'
      default:
        return 'Analyze Code'
    }
  }

  return (
    <div className="code-assistant-page">
      <div className="code-header">
        <h1>Code Assistant</h1>
        <p>Review, debug, and modify code with AI-powered suggestions.</p>
      </div>

      <div className="code-container">
        <div className="sidebar">
          <div className="sidebar-header">
            <h3>Sandbox Files</h3>
            <button onClick={loadFiles} className="icon-button" title="Refresh">
              <RefreshCw size={16} />
            </button>
          </div>
          
          <div className="file-list">
            {files.length === 0 ? (
              <p className="empty-state">No files yet</p>
            ) : (
              files.map((file) => (
                <div key={file} className="file-item">
                  <button
                    className="file-name"
                    onClick={() => handleLoadFile(file)}
                    title={file}
                  >
                    <FolderOpen size={14} />
                    <span>{file}</span>
                  </button>
                  <button
                    className="delete-btn"
                    onClick={() => handleDeleteFile(file)}
                    title="Delete"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="editor-section">
          <div className="editor-controls">
            <div className="control-group">
              <label htmlFor="language">Language</label>
              <select
                id="language"
                value={language}
                onChange={(e) => setLanguage(e.target.value)}
                disabled={isLoading}
              >
                <option value="python">Python</option>
                <option value="javascript">JavaScript</option>
                <option value="typescript">TypeScript</option>
                <option value="java">Java</option>
                <option value="cpp">C++</option>
                <option value="go">Go</option>
              </select>
            </div>

            <div className="control-group">
              <label htmlFor="action">Action</label>
              <select
                id="action"
                value={action}
                onChange={(e) => setAction(e.target.value as any)}
                disabled={isLoading}
              >
                <option value="review">Review Code</option>
                <option value="debug">Debug Issues</option>
                <option value="modify">Modify Code</option>
              </select>
            </div>
          </div>

          <div className="editor-toolbar">
            <input
              type="text"
              placeholder="Enter filename (e.g., script.py)"
              value={fileName}
              onChange={(e) => setFileName(e.target.value)}
              className="filename-input"
            />
            <button
              onClick={handleSaveCode}
              disabled={isSaving || !code.trim()}
              className="save-button"
              title="Save to Sandbox"
            >
              <Save size={16} />
              {isSaving ? 'Saving...' : 'Save'}
            </button>
          </div>

          <div className="editor-wrapper">
            <CodeEditor
              value={code}
              onChange={(value) => setCode(value || '')}
              language={language}
              height="400px"
            />
          </div>

          {action === 'modify' && (
            <div className="instruction-input">
              <label htmlFor="instruction">Modification Instructions</label>
              <textarea
                id="instruction"
                value={instruction}
                onChange={(e) => setInstruction(e.target.value)}
                placeholder="Describe what you want to change..."
                rows={2}
                disabled={isLoading}
              />
            </div>
          )}

          <button
            onClick={handleAnalyzeCode}
            disabled={isLoading || !code.trim() || (action === 'modify' && !instruction.trim())}
            className="analyze-button"
          >
            {getButtonText()}
          </button>

          {(response || isLoading) && (
            <div className="response-section">
              <h3>Analysis Result</h3>
              <div className="response-content">
                {isLoading ? (
                  <p className="loading-text">Analyzing your code...</p>
                ) : (
                  <pre>{response}</pre>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
