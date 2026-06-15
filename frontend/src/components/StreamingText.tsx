import { useEffect, useRef, useState } from 'react'

const DEFAULT_CHAR_MS = 6

export default function StreamingText({
  text,
  animate = false,
  charDelayMs = DEFAULT_CHAR_MS,
  className = '',
}: {
  text: string
  animate?: boolean
  charDelayMs?: number
  className?: string
}) {
  const [visibleLen, setVisibleLen] = useState(animate ? 0 : text.length)
  const prevTextRef = useRef(text)

  useEffect(() => {
    if (!animate) {
      setVisibleLen(text.length)
      prevTextRef.current = text
      return
    }

    const prev = prevTextRef.current
    if (text !== prev) {
      if (text.startsWith(prev) && text.length > prev.length) {
        prevTextRef.current = text
      } else {
        prevTextRef.current = text
        setVisibleLen(0)
      }
    }
  }, [animate, text])

  useEffect(() => {
    if (!animate || visibleLen >= text.length) return
    const lag = text.length - visibleLen
    const burst = lag > 60 ? 4 : lag > 25 ? 3 : lag > 10 ? 2 : 1
    const timer = window.setTimeout(() => {
      setVisibleLen((count) => Math.min(count + burst, text.length))
    }, charDelayMs)
    return () => window.clearTimeout(timer)
  }, [animate, visibleLen, text.length, charDelayMs])

  const streaming = animate && visibleLen < text.length

  return (
    <span
      className={`streaming-text${streaming ? ' streaming-text-active' : ''}${className ? ` ${className}` : ''}`}
      aria-busy={streaming}
    >
      {text.slice(0, visibleLen)}
      {streaming ? <span className="step-cursor" aria-hidden="true" /> : null}
    </span>
  )
}
