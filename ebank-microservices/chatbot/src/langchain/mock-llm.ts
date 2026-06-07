// WHY mock LLM: allows full development and testing without requiring OpenAI/Anthropic API keys.
// PROs: free, fast, predictable, no network dependency.
// CONs: not intelligent, only handles predefined patterns.
// In production: replace MockLLM with ChatOpenAI or ChatAnthropic — one line change.

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
}

export interface LLMResponse {
  content: string;
  toolCalls?: ToolCall[];
}

export interface ToolCall {
  name: string;
  arguments: Record<string, string>;
}

// Determines if the user message requires a tool call based on keyword matching.
function detectToolCall(message: string): ToolCall | null {
  const lower = message.toLowerCase();

  if (lower.includes('balance') || lower.includes('solde')) {
    const match = message.match(/\b(ACC\w+|\d{5,})\b/);
    return {
      name: 'getAccountBalance',
      arguments: { accountId: match?.[1] ?? '1' },
    };
  }

  if (
    lower.includes('transaction') ||
    lower.includes('historique') ||
    lower.includes('history')
  ) {
    const match = message.match(/\b(ACC\w+|\d{5,})\b/);
    return {
      name: 'getRecentTransactions',
      arguments: { accountId: match?.[1] ?? '1', limit: '5' },
    };
  }

  return null;
}

// Generates a contextual text response, optionally incorporating a tool result.
function generateResponse(message: string, toolResult?: any): string {
  const lower = message.toLowerCase();

  if (toolResult?.balance !== undefined) {
    return `Your account balance is **${toolResult.balance} ${toolResult.currency}**. Is there anything else I can help you with?`;
  }

  if (Array.isArray(toolResult)) {
    if (toolResult.length === 0) {
      return 'No recent transactions found for this account.';
    }
    const lines = toolResult
      .map(
        (t: any, i: number) =>
          `${i + 1}. ${t.type || 'Transaction'}: ${t.amount || '0'} EUR - ${t.status || 'COMPLETED'}`,
      )
      .join('\n');
    return `Here are your recent transactions:\n\n${lines}`;
  }

  // Generic keyword-based fallback responses
  if (
    lower.includes('hello') ||
    lower.includes('bonjour') ||
    lower.includes('hi')
  ) {
    return "Hello! I'm your eBank virtual assistant. I can help you check your account balance or view your transactions. How can I assist you today?";
  }

  if (lower.includes('help') || lower.includes('aide')) {
    return (
      "I can help you with:\n" +
      "- **Check balance**: \"What's my account balance?\"\n" +
      '- **Transactions**: "Show my recent transactions"\n\n' +
      'What would you like to do?'
    );
  }

  return 'I understand you have a question about your banking. Could you be more specific? You can ask about your balance or recent transactions.';
}

export class MockLLM {
  async invoke(messages: ChatMessage[], _tools?: any[]): Promise<LLMResponse> {
    const lastUserMessage = messages.filter((m) => m.role === 'user').pop();
    if (!lastUserMessage) {
      return { content: 'Hello! How can I help you?' };
    }

    // Check for a tool result in the last system message (re-invoke path)
    const lastSystemMessage = [...messages]
      .reverse()
      .find((m) => m.role === 'system' && m.content.startsWith('Tool result:'));

    if (lastSystemMessage) {
      try {
        const jsonStr = lastSystemMessage.content.replace('Tool result: ', '');
        const toolResult = JSON.parse(jsonStr);
        return {
          content: generateResponse(lastUserMessage.content, toolResult),
        };
      } catch {
        // fall through to default
      }
    }

    const toolCall = detectToolCall(lastUserMessage.content);
    return {
      content: generateResponse(lastUserMessage.content),
      toolCalls: toolCall ? [toolCall] : undefined,
    };
  }

  // Simulate streaming by yielding individual words with a small delay.
  async *stream(messages: ChatMessage[]): AsyncGenerator<string> {
    const response = await this.invoke(messages);
    const words = response.content.split(' ');
    for (const word of words) {
      yield word + ' ';
      await new Promise((resolve) => setTimeout(resolve, 50));
    }
  }
}
