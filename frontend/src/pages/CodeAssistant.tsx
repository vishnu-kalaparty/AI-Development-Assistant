import { useState } from 'react'
import CodeEditor from '../components/CodeEditor'
import SandboxFileTree from '../components/SandboxFileTree'
import './CodeAssistant.css'
import axios from 'axios'
import { Save, RefreshCw } from 'lucide-react'
import { useSandboxFiles } from '../hooks/useSandboxFiles'
import { languageFromHint, languageFromPath } from '../utils/languageFromPath'

export default function CodeAssistant() {
  const [code, setCode] = useState('')
  const [language, setLanguage] = useState('python')
  const [action, setAction] = useState<'review' | 'debug' | 'modify'>('review')
  const [instruction, setInstruction] = useState('')
  const [response, setResponse] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [fileName, setFileName] = useState('')
  const [isSaving, setIsSaving] = useState(false)
  const { files, tree, refresh: loadFiles, loadFile, deleteFile } = useSandboxFiles()

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
      
      if (!extractedCode || extractedCode.length < 3) {
        return null
      }
      
      return {
        code: extractedCode,
        language: languageFromHint(detectedLang, language)
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
      const content = await loadFile(file)
      setCode(content)
      setFileName(file)
      setLanguage(languageFromPath(file, language))
    } catch (error) {
      console.error('Error loading file:', error)
      alert('Error loading file')
    }
  }

  const handleDeleteFile = async (file: string) => {
    if (!confirm(`Delete ${file}?`)) return

    try {
      await deleteFile(file)
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
            <SandboxFileTree
              files={files}
              tree={tree}
              onLoad={handleLoadFile}
              onDelete={handleDeleteFile}
              emptyLabel="No files yet"
            />
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
              placeholder="Enter path (e.g., src/script.py)"
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
