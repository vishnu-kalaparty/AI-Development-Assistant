import { Link, useLocation } from 'react-router-dom'
import { Home, MessageSquare, BookOpen, FileText, Code, Bot } from 'lucide-react'
import './Sidebar.css'

export default function Sidebar() {
  const location = useLocation()
  
  const links = [
    { path: '/', label: 'Home', icon: Home },
    { path: '/quick-chat', label: 'Quick Chat', icon: MessageSquare },
    { path: '/learning-topics', label: 'Learning Topics', icon: BookOpen },
    { path: '/requirements', label: 'Requirements', icon: FileText },
    { path: '/code-assistant', label: 'Code Assistant', icon: Code },
    { path: '/ai-agents', label: 'AI Agents', icon: Bot },
  ]
  
  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <Bot size={32} />
        <h1 className="sidebar-title">AI Dev Assistant</h1>
      </div>
      
      <nav className="sidebar-nav">
        {links.map((link) => {
          const Icon = link.icon
          const isActive = location.pathname === link.path
          
          return (
            <Link
              key={link.path}
              to={link.path}
              className={`nav-link ${isActive ? 'active' : ''}`}
            >
              <Icon size={20} />
              <span>{link.label}</span>
            </Link>
          )
        })}
      </nav>
    </aside>
  )
}
