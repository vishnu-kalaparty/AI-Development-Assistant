import './Home.css'

export default function Home() {
  return (
    <div className="home-page">
      <div className="hero-section">
        <h1>AI Development Assistant</h1>
        <p className="hero-subtitle">
          Specialized AI agents help you design, code, and ship software faster. 
          Powered by Google Gemini with sandboxed file operations.
        </p>
        
        <div className="hero-actions">
          <a href="/quick-chat" className="primary-cta">Start Chatting</a>
          <a href="/code-assistant" className="secondary-cta">Code Assistant</a>
        </div>
      </div>

      <div className="features-section">
        <h2>Features</h2>
        <ul className="features-list">
          <li><strong>Quick Chat</strong> - Instant answers to software development questions</li>
          <li><strong>Learning Topics</strong> - Explanations tailored to different skill levels</li>
          <li><strong>Requirements Engineering</strong> - Generate business requirements, vision statements, and project plans</li>
          <li><strong>Code Assistant</strong> - Review, debug, and modify code with AI-powered suggestions</li>
          <li><strong>AI Agents</strong> - ReAct-style agents with specialized personas that can read, write, and search files in a sandboxed environment</li>
        </ul>
      </div>

      <div className="info-section">
        <h2>How It Works</h2>
        <p>
          The AI Agents page implements the ReAct pattern - agents can reason about tasks 
          and use tools to interact with files. All file operations are sandboxed to the 
          <code>data/sandbox/</code> directory for security.
        </p>
        <p>
          The code assistant uses Google AI's Gemini models to review and modify code. 
          Requirements engineering features help generate structured documents for software projects.
        </p>
      </div>
    </div>
  )
}
