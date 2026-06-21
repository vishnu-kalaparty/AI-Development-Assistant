import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar.tsx'
import { ThemeProvider } from '../theme'
import './Layout.css'

export default function Layout() {
  return (
    <ThemeProvider>
      <div className="layout">
        <Sidebar />
        <main className="main-content">
          <Outlet />
        </main>
      </div>
    </ThemeProvider>
  )
}
