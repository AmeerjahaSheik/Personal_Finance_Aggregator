import api from '../../../services/api';

export const aiService = {
  getHealth: async () => {
    const response = await api.get('/api/ai/health');
    return response.data;
  },

  // Conversation Management
  getConversations: async () => {
    const response = await api.get('/api/ai/conversations');
    return response.data;
  },

  createConversation: async (title = 'New Chat') => {
    const response = await api.post('/api/ai/conversations', { title });
    return response.data;
  },

  getConversation: async (id) => {
    const response = await api.get(`/api/ai/conversations/${id}`);
    return response.data;
  },

  getConversationMessages: async (id) => {
    const response = await api.get(`/api/ai/conversations/${id}/messages`);
    return response.data;
  },

  sendMessage: async (conversationId, message) => {
    const response = await api.post(`/api/ai/conversations/${conversationId}/messages`, { message });
    return response.data;
  },

  deleteConversation: async (id) => {
    const response = await api.delete(`/api/ai/conversations/${id}`);
    return response.data;
  },

  // Direct chat (with optional conversationId)
  chat: async (message, conversationId = null) => {
    const response = await api.post('/api/ai/chat', { message, conversationId });
    return response.data;
  },

  getSpendingInsights: async (params = {}) => {
    const response = await api.get('/api/ai/insights/spending', { params });
    return response.data;
  },

  getBudgetInsights: async (params = {}) => {
    const response = await api.get('/api/ai/insights/budget', { params });
    return response.data;
  },

  getRecurringInsights: async () => {
    const response = await api.get('/api/ai/insights/recurring');
    return response.data;
  },

  getMonthlySummary: async (params = {}) => {
    const response = await api.get('/api/ai/summary/monthly', { params });
    return response.data;
  },

  suggestCategory: async (data) => {
    const response = await api.post('/api/ai/suggest-category', data);
    return response.data;
  },
};
