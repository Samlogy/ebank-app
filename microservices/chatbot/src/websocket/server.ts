import { WebSocketServer, WebSocket } from 'ws';
import { Server } from 'http';
import { v4 as uuidv4 } from 'uuid';
import { runChain } from '../langchain/chain';
import { ChatMessage } from '../langchain/mock-llm';

interface Session {
  id: string;
  history: ChatMessage[];
}

// In-memory session store: maps sessionId -> conversation history.
// In production this should be backed by Redis for multi-instance deployments.
const sessions = new Map<string, Session>();

export function initWebSocketServer(server: Server): void {
  const wss = new WebSocketServer({ server, path: '/ws/chat' });

  wss.on('connection', (ws: WebSocket) => {
    const sessionId = uuidv4();
    sessions.set(sessionId, { id: sessionId, history: [] });
    console.log(`[WS] New connection: ${sessionId}`);

    // Greet the client with their session ID so they can reference it later.
    ws.send(
      JSON.stringify({
        type: 'connected',
        sessionId,
        message: 'Connected to eBank Chatbot. How can I help you?',
      }),
    );

    ws.on('message', async (data: Buffer) => {
      try {
        const payload = JSON.parse(data.toString()) as { message: string };
        const userMessage = payload.message;
        const session = sessions.get(sessionId);

        if (!session) {
          ws.send(
            JSON.stringify({ type: 'error', message: 'Session not found.' }),
          );
          return;
        }

        console.log(`[WS] Message from ${sessionId}: ${userMessage}`);

        // Append user turn to history before processing.
        session.history.push({ role: 'user', content: userMessage });

        const result = await runChain({
          message: userMessage,
          sessionId,
          // Keep only the last 10 messages to avoid unbounded context growth.
          conversationHistory: session.history.slice(-10),
        });

        // Append assistant turn to history.
        session.history.push({ role: 'assistant', content: result.response });

        ws.send(
          JSON.stringify({
            type: 'response',
            sessionId,
            message: result.response,
            toolsUsed: result.toolsUsed,
          }),
        );
      } catch (err) {
        console.error('[WS] Error:', err);
        ws.send(
          JSON.stringify({
            type: 'error',
            message: 'An error occurred processing your request.',
          }),
        );
      }
    });

    ws.on('close', () => {
      sessions.delete(sessionId);
      console.log(`[WS] Session closed: ${sessionId}`);
    });

    ws.on('error', (err) => {
      console.error(`[WS] Socket error for session ${sessionId}:`, err);
    });
  });

  console.log('[WS] WebSocket server initialised on /ws/chat');
}
