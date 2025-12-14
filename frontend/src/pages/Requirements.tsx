import { useState, useEffect } from 'react'
import axios from 'axios'
import './Requirements.css'
import { FileText, Trash2, RefreshCw } from 'lucide-react'

export default function Requirements() {
  const [productName, setProductName] = useState('')
  const [requirementType, setRequirementType] = useState('Business Requirements')
  const [response, setResponse] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [files, setFiles] = useState<string[]>([])

  useEffect(() => {
    loadFiles()
  }, [])

  const loadFiles = async () => {
    try {
      const result = await axios.get('/api/requirements/files')
      setFiles(result.data.files || [])
    } catch (error) {
      console.error('Error loading requirements files:', error)
    }
  }

  const handleGenerate = async () => {
    if (!productName.trim() || isLoading) return

    setIsLoading(true)
    setResponse('')

    try {
      const result = await axios.post('/api/requirements/generate', {
        productName: productName.trim(),
        requirementType,
      })

      setResponse(result.data.content)
    } catch (error) {
      console.error('Error generating requirements:', error)
      setResponse('Sorry, there was an error generating requirements.')
    } finally {
      setIsLoading(false)
    }
  }

  const handleSave = async () => {
    if (!response) return

    try {
      await axios.post('/api/requirements/save', {
        productName: productName.trim(),
        requirementType,
        content: response,
      })
      alert('Requirements saved successfully!')
      await loadFiles()
    } catch (error) {
      console.error('Error saving requirements:', error)
      alert('Failed to save requirements.')
    }
  }

  const handleLoadFile = async (fileName: string) => {
    try {
      const result = await axios.post('/api/requirements/load', { fileName })
      setResponse(result.data.content)
      
      // Try to extract product name and type from filename
      const parts = fileName.replace('.txt', '').split('_')
      if (parts.length >= 2) {
        const type = parts[0].replace(/_/g, ' ')
        const productParts = parts.slice(1, -1).join(' ')
        
        // Map filename prefixes to dropdown values
        const typeMap: Record<string, string> = {
          'Vision Statement': 'Vision Statement',
          'Business Requirements': 'Business Requirements',
          'Project Plan': 'Project Plan',
          'Technical Specifications': 'Technical Specifications',
        }
        
        if (typeMap[type]) {
          setRequirementType(typeMap[type])
        }
        if (productParts) {
          setProductName(productParts)
        }
      }
    } catch (error) {
      console.error('Error loading file:', error)
      alert('Error loading file')
    }
  }

  const handleDeleteFile = async (fileName: string) => {
    if (!confirm(`Delete ${fileName}?`)) return

    try {
      await axios.delete('/api/requirements/delete', { data: { fileName } })
      await loadFiles()
      alert(`Deleted ${fileName}`)
    } catch (error) {
      console.error('Error deleting file:', error)
      alert('Error deleting file')
    }
  }

  return (
    <div className="requirements-page">
      <div className="requirements-header">
        <h1>Requirements Engineering</h1>
        <p>Generate business requirements, vision statements, and project plans.</p>
      </div>

      <div className="requirements-layout">
        <div className="sidebar">
          <div className="sidebar-header">
            <h3>Saved Documents</h3>
            <button onClick={loadFiles} className="icon-button" title="Refresh">
              <RefreshCw size={16} />
            </button>
          </div>
          
          <div className="file-list">
            {files.length === 0 ? (
              <p className="empty-state">No documents yet</p>
            ) : (
              files.map((file) => (
                <div key={file} className="file-item">
                  <button
                    className="file-name"
                    onClick={() => handleLoadFile(file)}
                    title={file}
                  >
                    <FileText size={14} />
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

        <div className="requirements-container">
          <div className="input-section">
            <div className="form-group">
              <label htmlFor="productName">Product Name</label>
              <input
                id="productName"
                type="text"
                value={productName}
                onChange={(e) => setProductName(e.target.value)}
                placeholder="e.g., Task Management App"
                disabled={isLoading}
              />
            </div>

            <div className="form-group">
              <label htmlFor="requirementType">Document Type</label>
              <select
                id="requirementType"
                value={requirementType}
                onChange={(e) => setRequirementType(e.target.value)}
                disabled={isLoading}
              >
                <option value="Business Requirements">Business Requirements</option>
                <option value="Vision Statement">Vision Statement</option>
                <option value="Project Plan">Project Plan</option>
                <option value="Technical Specifications">Technical Specifications</option>
              </select>
            </div>

            <div className="button-group">
              <button
                onClick={handleGenerate}
                disabled={isLoading || !productName.trim()}
                className="generate-button"
              >
                {isLoading ? 'Generating...' : 'Generate'}
              </button>

              {response && (
                <button onClick={handleSave} className="save-button">
                  Save Document
                </button>
              )}
            </div>
          </div>

          {response && (
            <div className="output-section">
              <h3>Generated Document</h3>
              <div className="document-content">
                {response}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
