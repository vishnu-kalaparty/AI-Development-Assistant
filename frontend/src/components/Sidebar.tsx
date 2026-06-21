import { Link, useLocation } from 'react-router-dom'
import { BookOpen, Code, FileText, GitBranch, Home, MessageSquare, Workflow } from 'lucide-react'
import ThemeToggle from './ThemeToggle'
import './Sidebar.css'

export default function Sidebar() {
  const location = useLocation()

  const links = [
    { path: '/', label: 'Home', icon: Home },
    { path: '/quick-chat', label: 'Quick Chat', icon: MessageSquare },
    { path: '/learning-topics', label: 'Learning', icon: BookOpen },
    { path: '/requirements', label: 'Requirements', icon: FileText },
    { path: '/code-assistant', label: 'Code Assistant', icon: Code },
    { path: '/sdlc', label: 'SDLC Workflow', icon: Workflow },
    { path: '/ai-agents', label: 'Agents', icon: GitBranch },
  ]

  return (
    <aside className="app-sidebar">
      <div className="sidebar-header">
        <p className="sidebar-kicker">Studio</p>
        <h1 className="sidebar-title">Dev Assistant</h1>
      </div>

      <nav className="sidebar-nav" aria-label="Main">
        {links.map((link) => {
          const Icon = link.icon
          const isActive = location.pathname === link.path
          return (
            <Link
              key={link.path}
              to={link.path}
              className={`nav-link ${isActive ? 'active' : ''}`}
              aria-current={isActive ? 'page' : undefined}
            >
              <Icon size={16} aria-hidden="true" />
              <span>{link.label}</span>
            </Link>
          )
        })}
      </nav>

      <ThemeToggle variant="sidebar" />
    </aside>
  )
}
