import { CommonModule } from '@angular/common'
import { Component, ElementRef, OnDestroy, ViewChild, signal } from '@angular/core'
import { FormsModule } from '@angular/forms'

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
function nextId(): string {
  return String(++msgCounter)
}

@Component({
    selector: 'app-chat-widget',
    imports: [CommonModule, FormsModule],
    templateUrl: './chat-widget.component.html'
})
export class ChatWidgetComponent implements OnDestroy {
  @ViewChild('bottomAnchor') bottomAnchor?: ElementRef<HTMLDivElement>

  readonly open = signal(false)
  readonly messages = signal<ChatMessage[]>([])
  readonly connected = signal(false)
  readonly connecting = signal(false)
  input = ''

  private ws: WebSocket | null = null

  toggle(): void {
    this.open.set(!this.open())
    if (this.open() && !this.connected() && !this.connecting()) {
      this.connect()
    } else if (!this.open() && this.ws) {
      this.ws.close()
    }
  }

  private connect(): void {
    if (this.ws && this.ws.readyState < 2) return
    this.connecting.set(true)

    const ws = new WebSocket('ws://localhost:8080/ws/chat')
    this.ws = ws

    ws.onopen = () => {
      this.connected.set(true)
      this.connecting.set(false)
    }

    ws.onmessage = (event) => {
      try {
        const data: WsMessage = JSON.parse(event.data as string)
        if (data.type === 'connected' || data.type === 'response') {
          this.messages.update((prev) => [
            ...prev,
            { id: nextId(), role: 'bot', text: data.message, toolsUsed: data.toolsUsed },
          ])
        } else if (data.type === 'error') {
          this.messages.update((prev) => [
            ...prev,
            { id: nextId(), role: 'bot', text: `Error: ${data.message}` },
          ])
        }
        this.scrollToBottom()
      } catch {
        // ignore malformed messages
      }
    }

    ws.onerror = () => {
      this.connected.set(false)
      this.connecting.set(false)
    }

    ws.onclose = () => {
      this.connected.set(false)
      this.connecting.set(false)
    }
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      this.bottomAnchor?.nativeElement.scrollIntoView({ behavior: 'smooth' })
    })
  }

  sendMessage(): void {
    const text = this.input.trim()
    if (!text || !this.ws || this.ws.readyState !== WebSocket.OPEN) return

    this.messages.update((prev) => [...prev, { id: nextId(), role: 'user', text }])
    this.input = ''
    this.ws.send(JSON.stringify({ message: text }))
    this.scrollToBottom()
  }

  handleKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter') this.sendMessage()
  }

  ngOnDestroy(): void {
    this.ws?.close()
  }
}
