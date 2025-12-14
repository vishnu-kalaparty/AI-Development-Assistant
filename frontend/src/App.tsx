import { Routes, Route } from 'react-router-dom'
import Layout from './components/Layout'
import Home from './pages/Home'
import QuickChat from './pages/QuickChat.tsx'
import LearningTopics from './pages/LearningTopics.tsx'
import Requirements from './pages/Requirements.tsx'
import CodeAssistant from './pages/CodeAssistant.tsx'
import AIAgents from './pages/AIAgents.tsx'

function App() {
  return (
    <Routes>
      <Route path="/" element={<Layout />}>
        <Route index element={<Home />} />
        <Route path="quick-chat" element={<QuickChat />} />
        <Route path="learning-topics" element={<LearningTopics />} />
        <Route path="requirements" element={<Requirements />} />
        <Route path="code-assistant" element={<CodeAssistant />} />
        <Route path="ai-agents" element={<AIAgents />} />
      </Route>
    </Routes>
  )
}

export default App
