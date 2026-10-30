import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { aiService } from '../api/aiService';

export const AI_QUERY_KEYS = {
  health: ['ai', 'health'],
  conversations: ['ai', 'conversations'],
  conversationMessages: (id) => ['ai', 'conversations', id, 'messages'],
  spendingInsights: (params) => ['ai', 'insights', 'spending', params],
  budgetInsights: (params) => ['ai', 'insights', 'budget', params],
  recurringInsights: ['ai', 'insights', 'recurring'],
  monthlySummary: (params) => ['ai', 'summary', 'monthly', params],
};

export function useAiHealth() {
  return useQuery({
    queryKey: AI_QUERY_KEYS.health,
    queryFn: aiService.getHealth,
    staleTime: 60 * 1000,
    retry: 1,
  });
}

export function useConversations() {
  return useQuery({
    queryKey: AI_QUERY_KEYS.conversations,
    queryFn: aiService.getConversations,
    staleTime: 30 * 1000,
  });
}

export function useConversationMessages(conversationId) {
  return useQuery({
    queryKey: AI_QUERY_KEYS.conversationMessages(conversationId),
    queryFn: () => aiService.getConversationMessages(conversationId),
    enabled: Boolean(conversationId),
    staleTime: 10 * 1000,
  });
}

export function useCreateConversation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (title) => aiService.createConversation(title),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: AI_QUERY_KEYS.conversations });
    },
  });
}

export function useSendMessage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ conversationId, message }) => aiService.sendMessage(conversationId, message),
    onSuccess: (data, variables) => {
      queryClient.invalidateQueries({
        queryKey: AI_QUERY_KEYS.conversationMessages(variables.conversationId),
      });
      queryClient.invalidateQueries({
        queryKey: AI_QUERY_KEYS.conversations,
      });
    },
  });
}

export function useDeleteConversation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => aiService.deleteConversation(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: AI_QUERY_KEYS.conversations });
    },
  });
}

export function useMonthlySummary(params = {}) {
  return useQuery({
    queryKey: AI_QUERY_KEYS.monthlySummary(params),
    queryFn: () => aiService.getMonthlySummary(params),
    staleTime: 5 * 60 * 1000, // 5 min cache
    retry: 1,
  });
}

export function useSpendingInsights(params = {}) {
  return useQuery({
    queryKey: AI_QUERY_KEYS.spendingInsights(params),
    queryFn: () => aiService.getSpendingInsights(params),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });
}

export function useBudgetInsights(params = {}) {
  return useQuery({
    queryKey: AI_QUERY_KEYS.budgetInsights(params),
    queryFn: () => aiService.getBudgetInsights(params),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });
}

export function useRecurringInsights() {
  return useQuery({
    queryKey: AI_QUERY_KEYS.recurringInsights,
    queryFn: aiService.getRecurringInsights,
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });
}

export function useAiChat() {
  return useMutation({
    mutationFn: (message) => aiService.chat(message),
  });
}

export function useSuggestCategory() {
  return useMutation({
    mutationFn: (data) => aiService.suggestCategory(data),
  });
}
