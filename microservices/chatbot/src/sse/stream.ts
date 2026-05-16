import { Response } from 'express';

// Initialises the HTTP response as an SSE stream.
export function initSSE(res: Response): void {
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.flushHeaders();
}

// Sends a named SSE event with an arbitrary JSON payload.
export function sendSSEEvent(res: Response, data: object, event = 'message'): void {
  res.write(`event: ${event}\n`);
  res.write(`data: ${JSON.stringify(data)}\n\n`);
}

// Sends a single streaming token chunk.
export function sendSSEToken(res: Response, token: string): void {
  res.write(`data: ${JSON.stringify({ type: 'token', content: token })}\n\n`);
}

// Signals that the stream is complete and closes the response.
export function sendSSEDone(res: Response): void {
  res.write(`data: ${JSON.stringify({ type: 'done' })}\n\n`);
  res.end();
}
