import { Moon, Sun } from 'lucide-react'
import { useTheme } from '../theme'
import './ThemeToggle.css'

interface ThemeToggleProps {
  variant?: 'sidebar' | 'header'
  showLabel?: boolean
}

export default function ThemeToggle({ variant = 'header', showLabel = true }: ThemeToggleProps) {
  const { theme, toggleTheme } = useTheme()

  return (
    <button
      type="button"
      className={`theme-toggle-btn theme-toggle-btn--${variant}`}
      onClick={toggleTheme}
      aria-label={theme === 'light' ? 'Switch to dark theme' : 'Switch to light theme'}
    >
      {theme === 'light' ? <Moon size={16} aria-hidden="true" /> : <Sun size={16} aria-hidden="true" />}
      {showLabel ? <span>{theme === 'light' ? 'Dark' : 'Light'}</span> : null}
    </button>
  )
}
