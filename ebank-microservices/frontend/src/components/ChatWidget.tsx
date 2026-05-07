import { useState, useEffect, useRef, useCallback } from 'react'

interface ChatMessage {
  id: string
  role: 'user' | 'bot'
  text: string
  toolsUsed?: string[]
}

interface WsMessage {
  type: 'connected' | 'response' | 'error'
  sessionId?: string
  message: string
  toolsUsed?: string[]
}

let msgCounter = 0
function nextId() {
  return String(++msgCounter)
}

export default function ChatWidget() {
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [connected, setConnected] = useState(false)
  const [connecting, setConnecting] = useState(false)
  const wsRef = useRef<WebSocket | null>(null)
  const bottomRef = useRef<HTMLDivElement | null>(null)

  const connect = useCallback(() => {
    if (wsRef.current && wsRef.current.readyState < 2) return
    setConnecting(true)

    const ws = new WebSocket('ws://localhost:8080/ws/chat')
    wsRef.current = ws

    ws.onopen = () => {
      setConnected(true)
      setConnecting(false)
    }

    ws.onmessage = (event) => {
      try {
        const data: WsMessage = JSON.parse(event.data as string)
        if (data.type === 'connected' || data.type === 'response') {
          setMessages((prev) => [
            ...prev,
            {
              id: nextId(),
              role: 'bot',
              text: data.message,
              toolsUsed: data.toolsUsed,
            },
          ])
        } else if (data.type === 'error') {
          setMessages((prev) => [
            ...prev,
            { id: nextId(), role: 'bot', text: `Error: ${data.message}` },
          ])
        }
      } catch {
        // ignore malformed messages
      }
    }

    ws.onerror = () => {
      setConnected(false)
      setConnecting(false)
    }

    ws.onclose = () => {
      setConnected(false)
      setConnecting(false)
    }
  }, [])

  useEffect(() => {
    if (open && !connected && !connecting) {
      connect()
    }
    return () => {
      if (!open && wsRef.current) {
        wsRef.current.close()
      }
    }
  }, [open, connected, connecting, connect])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const sendMessage = () => {
    if (!input.trim() || !wsRef.current || wsRef.current.readyState !== WebSocket.OPEN)
      return

    const text = input.trim()
    setMessages((prev) => [...prev, { id: nextId(), role: 'user', text }])
    setInput('')
    wsRef.current.send(JSON.stringify({ message: text }))
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') sendMessage()
  }

  return (
    <>
      {/* Floating toggle button */}
      <button
        onClick={() => setOpen((o) => !o)}
        className="fixed bottom-6 right-6 w-14 h-14 bg-blue-600 hover:bg-blue-700 text-white rounded-full shadow-lg flex items-center justify-center text-2xl z-50 transition-colors"
        aria-label="Toggle chat"
      >
        {open ? (
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        ) : (
          <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24">
            <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 12H6v-2h12v2zm0-3H6V9h12v2zm0-3H6V6h12v2z" />
          </svg>
        )}
      </button>

      {/* Chat panel */}
      {open && (
        <div className="fixed bottom-24 right-6 w-96 h-[520px] bg-white rounded-2xl shadow-2xl flex flex-col z-50 border border-slate-200 overflow-hidden">
          {/* Header */}
          <div className="bg-[#0f172a] px-4 py-3 flex items-center gap-3">
            <div className="w-8 h-8 bg-blue-500 rounded-full flex items-center justify-center">
              <svg className="w-4 h-4 text-white" fill="currentColor" viewBox="0 0 24 24">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
              </svg>
            </div>
            <div>
              <p className="text-white text-sm font-semibold">eBank Assistant</p>
              <p className="text-slate-400 text-xs">
                {connecting ? 'Connecting...' : connected ? 'Online' : 'Offline'}
              </p>
            </div>
            <div className={`ml-auto w-2 h-2 rounded-full ${connected ? 'bg-green-400' : 'bg-slate-500'}`} />
          </div>

          {/* Messages */}
          <div className="flex-1 overflow-y-auto p-4 space-y-3 bg-slate-50">
            {messages.length === 0 && !connecting && (
              <p className="text-slate-400 text-sm text-center mt-8">
                {connected
                  ? 'Say hello to your banking assistant!'
                  : 'Could not connect to assistant.'}
              </p>
            )}
            {connecting && (
              <div className="flex justify-center mt-8">
                <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-500" />
              </div>
            )}
            {messages.map((msg) => (
              <div
                key={msg.id}
                className={`flex flex-col ${msg.role === 'user' ? 'items-end' : 'items-start'}`}
              >
                <div
                  className={`max-w-[80%] px-4 py-2.5 rounded-2xl text-sm leading-relaxed ${
                    msg.role === 'user'
                      ? 'bg-blue-600 text-white rounded-br-sm'
                      : 'bg-white text-slate-800 shadow-sm border border-slate-200 rounded-bl-sm'
                  }`}
                >
                  {msg.text}
                </div>
                {msg.toolsUsed && msg.toolsUsed.length > 0 && (
                  <div className="flex flex-wrap gap-1 mt-1 max-w-[80%]">
                    {msg.toolsUsed.map((tool) => (
                      <span
                        key={tool}
                        className="text-xs bg-slate-200 text-slate-600 px-2 py-0.5 rounded-full"
                      >
                        {tool}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            ))}
            <div ref={bottomRef} />
          </div>

          {/* Input */}
          <div className="p-3 border-t border-slate-200 bg-white flex gap-2">
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={connected ? 'Type a message…' : 'Not connected'}
              disabled={!connected}
              className="flex-1 px-3 py-2 text-sm border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-slate-100 disabled:cursor-not-allowed"
            />
            <button
              onClick={sendMessage}
              disabled={!connected || !input.trim()}
              className="px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-slate-300 text-white text-sm font-medium rounded-lg transition-colors"
            >
              Send
            </button>
          </div>
        </div>
      )}
    </>
  )
}
