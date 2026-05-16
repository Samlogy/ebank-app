import { MockLLM, ChatMessage } from './mock-llm';
import { executeTool } from './tools';

// WHY LangChain pattern: standardises LLM integration (tool calling, RAG, memory).
// Swapping MockLLM for ChatOpenAI or ChatAnthropic requires only one line change here.

const llm = new MockLLM();

export interface ChainInput {
  message: string;
  sessionId: string;
  conversationHistory: ChatMessage[];
}

export interface ChainOutput {
  response: string;
  toolsUsed: string[];
}

export async function runChain(input: ChainInput): Promise<ChainOutput> {
  const messages: ChatMessage[] = [
    {
      role: 'system',
      content:
        'You are a helpful eBank virtual assistant. You help customers with their banking needs: checking balances and viewing transactions.',
    },
    ...input.conversationHistory,
    { role: 'user', content: input.message },
  ];

  // Step 1: Ask LLM — response may include tool call instructions.
  const llmResponse = await llm.invoke(messages);
  const toolsUsed: string[] = [];
  let finalResponse = llmResponse.content;

  // Step 2: Execute each requested tool call.
  if (llmResponse.toolCalls && llmResponse.toolCalls.length > 0) {
    for (const toolCall of llmResponse.toolCalls) {
      toolsUsed.push(toolCall.name);
      const toolResult = await executeTool(toolCall.name, toolCall.arguments);

      // Step 3: Re-invoke LLM with the tool result so it can generate the final answer.
      const messagesWithResult: ChatMessage[] = [
        ...messages,
        { role: 'assistant', content: `[Tool: ${toolCall.name} called]` },
        {
          role: 'system',
          content: `Tool result: ${JSON.stringify(toolResult)}`,
        },
      ];
      const finalLlmResponse = await llm.invoke(messagesWithResult);
      finalResponse = finalLlmResponse.content;
    }
  }

  return { response: finalResponse, toolsUsed };
}

// Streaming version: yields string tokens suitable for SSE or WebSocket streaming.
export async function* runChainStream(
  input: ChainInput,
): AsyncGenerator<string> {
  const messages: ChatMessage[] = [
    {
      role: 'system',
      content: 'You are a helpful eBank virtual assistant.',
    },
    ...input.conversationHistory,
    { role: 'user', content: input.message },
  ];

  // First, check for tool calls (non-streaming pass).
  const toolCheck = await llm.invoke(messages);

  if (toolCheck.toolCalls && toolCheck.toolCalls.length > 0) {
    yield '[Thinking...] ';
    for (const toolCall of toolCheck.toolCalls) {
      yield `[Calling ${toolCall.name}...] `;
      const result = await executeTool(toolCall.name, toolCall.arguments);

      const messagesWithResult: ChatMessage[] = [
        ...messages,
        {
          role: 'system',
          content: `Tool result: ${JSON.stringify(result)}`,
        },
      ];
      // Stream the final LLM response token by token.
      yield* llm.stream(messagesWithResult);
    }
  } else {
    yield* llm.stream(messages);
  }
}
