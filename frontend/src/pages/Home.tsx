import { Link } from 'react-router-dom'
import './Home.css'

export default function Home() {
  return (
    <div className="home-page">
      <header className="page-header home-intro">
        <p className="eyebrow">Local development studio</p>
        <h1>Write, review, and run software tasks in one place.</h1>
        <p>
          Chat, generate requirements, edit sandbox files, and run a six-stage SDLC workflow.
          File tools stay inside <code>data/sandbox</code>. Code execution uses a local timeout.
        </p>
        <div className="home-actions">
          <Link to="/sdlc" className="btn-primary home-link">Open SDLC workflow</Link>
          <Link to="/quick-chat" className="home-link ghost">Quick chat</Link>
        </div>
      </header>

      <section className="home-index" aria-label="Features">
        <article>
          <h2>Quick Chat</h2>
          <p>Ask coding questions without leaving the app.</p>
        </article>
        <article>
          <h2>Learning</h2>
          <p>Explanations at beginner, intermediate, or advanced level.</p>
        </article>
        <article>
          <h2>Requirements</h2>
          <p>Draft business, vision, plan, or technical documents and save them.</p>
        </article>
        <article>
          <h2>Code Assistant</h2>
          <p>Review, debug, or modify a file in the sandbox editor.</p>
        </article>
        <article>
          <h2>SDLC Workflow</h2>
          <p>Requirements, architecture, implementation, tests, review, then human approval.</p>
        </article>
        <article>
          <h2>Agents</h2>
          <p>Shorter engineer and reviewer run against the same sandbox tools.</p>
        </article>
      </section>
    </div>
  )
}
