import { useState, useRef, useEffect } from 'react'
import { Send } from 'lucide-react'
import axios from 'axios'
import './QuickChat.css'

interface Message {
  role: 'user' | 'assistant'
  content: string
}

export default function QuickChat() {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    
    if (!input.trim() || isLoading) return

    const userMessage: Message = { role: 'user', content: input }
    setMessages((prev) => [...prev, userMessage])
    setInput('')
    setIsLoading(true)

    try {
      const response = await axios.post('/api/chat/quick', {
        messages: [...messages, userMessage],
      })

      const assistantMessage: Message = {
        role: 'assistant',
        content: response.data.response,
      }
      setMessages((prev) => [...prev, assistantMessage])
    } catch (error) {
      console.error('Error sending message:', error)
      const errorMessage: Message = {
        role: 'assistant',
        content: 'Sorry, there was an error processing your request.',
      }
      setMessages((prev) => [...prev, errorMessage])
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="chat-page">
      <div className="chat-header">
        <h1>Quick Chat</h1>
        <p>Get instant answers to your software development and coding questions.</p>
      </div>

      <div className="chat-container">
        <div className="messages">
          {messages.length === 0 ? (
            <div className="empty-state">
              <p>Ask a software development or coding question to get started!</p>
            </div>
          ) : (
            messages.map((message, index) => (
              <div key={index} className={`message ${message.role}`}>
                <div className="message-label">
                  {message.role === 'user' ? 'You' : 'Assistant'}
                </div>
                <div className="message-content">{message.content}</div>
              </div>
            ))
          )}
          {isLoading && (
            <div className="message assistant">
              <div className="message-label">Assistant</div>
              <div className="message-content loading">Thinking...</div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        <form onSubmit={handleSubmit} className="chat-input-form">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Ask a software development or coding question..."
            disabled={isLoading}
            className="chat-input"
          />
          <button type="submit" disabled={isLoading || !input.trim()} className="send-button">
            <Send size={20} />
          </button>
        </form>
      </div>
    </div>
  )
}
