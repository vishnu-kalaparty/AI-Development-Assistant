import { useState } from 'react'
import axios from 'axios'
import { Send } from 'lucide-react'
import './LearningTopics.css'

export default function LearningTopics() {
  const [topic, setTopic] = useState('')
  const [level, setLevel] = useState<'beginner' | 'intermediate' | 'advanced'>('beginner')
  const [response, setResponse] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    
    if (!topic.trim() || isLoading) return

    setIsLoading(true)
    setResponse('')

    try {
      const result = await axios.post('/api/chat/learning', {
        topic: topic.trim(),
        level,
      })

      setResponse(result.data.response)
    } catch (error) {
      console.error('Error fetching learning content:', error)
      setResponse('Sorry, there was an error processing your request.')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="learning-page">
      <div className="learning-header">
        <h1>Learning Topics</h1>
        <p>Get explanations tailored to different skill levels.</p>
      </div>

      <div className="learning-container">
        <div className="input-section">
          <form onSubmit={handleSubmit} className="learning-form">
            <div className="form-group">
              <label htmlFor="topic">Topic</label>
              <input
                id="topic"
                type="text"
                value={topic}
                onChange={(e) => setTopic(e.target.value)}
                placeholder="e.g., async/await in JavaScript"
                disabled={isLoading}
              />
            </div>

            <div className="form-group">
              <label htmlFor="level">Skill Level</label>
              <select
                id="level"
                value={level}
                onChange={(e) => setLevel(e.target.value as any)}
                disabled={isLoading}
              >
                <option value="beginner">Beginner</option>
                <option value="intermediate">Intermediate</option>
                <option value="advanced">Advanced</option>
              </select>
            </div>

            <button type="submit" disabled={isLoading || !topic.trim()} className="submit-button">
              <Send size={20} />
              <span>{isLoading ? 'Learning...' : 'Explain'}</span>
            </button>
          </form>
        </div>

        {response && (
          <div className="response-section">
            <h3>Explanation</h3>
            <div className="response-content">
              {response}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
