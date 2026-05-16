import * as apiClient from '../services/api.client';

export interface Tool {
  name: string;
  description: string;
  parameters: Record<
    string,
    { type: string; description: string; required: boolean }
  >;
  execute: (args: Record<string, string>) => Promise<any>;
}

// Tool definitions — these would be serialised and passed to a real LLM for tool calling.
// The MockLLM detects tools via keyword matching; a real LLM would select them from this schema.
export const tools: Tool[] = [
  {
    name: 'getAccountBalance',
    description: 'Get the current balance of a bank account',
    parameters: {
      accountId: {
        type: 'string',
        description: 'The account ID',
        required: true,
      },
    },
    execute: async (args) => apiClient.getAccountBalance(args.accountId),
  },
  {
    name: 'getRecentTransactions',
    description: 'Get recent transactions for a bank account',
    parameters: {
      accountId: {
        type: 'string',
        description: 'The account ID',
        required: true,
      },
      limit: {
        type: 'string',
        description: 'Number of transactions to return (default: 5)',
        required: false,
      },
    },
    execute: async (args) =>
      apiClient.getRecentTransactions(
        args.accountId,
        parseInt(args.limit || '5', 10),
      ),
  },
];

export async function executeTool(
  name: string,
  args: Record<string, string>,
): Promise<any> {
  const tool = tools.find((t) => t.name === name);
  if (!tool) {
    throw new Error(`Tool '${name}' not found`);
  }
  return tool.execute(args);
}
