import React, { useState, useRef, useEffect } from 'react';
import {
  Send,
  Sparkles,
  Loader2,
  AlertCircle,
  Bot,
  Plus,
  Trash2,
  MessageSquare,
  PanelLeftClose,
  PanelLeft,
  RotateCcw,
  Check,
  X,
  TrendingDown,
  PieChart,
  Repeat,
  PiggyBank,
} from 'lucide-react';
import AiMessage from './AiMessage';
import QuickQuestions from './QuickQuestions';
import {
  useConversations,
  useConversationMessages,
  useSendMessage,
  useDeleteConversation,
  useAiHealth,
} from '../hooks/useAi';
import { aiService } from '../api/aiService';
import { useQueryClient } from '@tanstack/react-query';
import { AI_QUERY_KEYS } from '../hooks/useAi';

export default function AiChat() {
  const queryClient = useQueryClient();
  const { data: conversations = [], isLoading: loadingConversations } = useConversations();
  const { data: healthData } = useAiHealth();

  const [selectedConversationId, setSelectedConversationId] = useState(null);
  const [inputText, setInputText] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [lastFailedText, setLastFailedText] = useState('');
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
  const [confirmDeleteId, setConfirmDeleteId] = useState(null);
  const [optimisticMessages, setOptimisticMessages] = useState([]);
  const [isGenerating, setIsGenerating] = useState(false);

  const sendMessageMutation = useSendMessage();
  const deleteConversationMutation = useDeleteConversation();

  const messagesEndRef = useRef(null);
  const inputRef = useRef(null);

  // Automatically select the first conversation when loaded if none is active
  useEffect(() => {
    if (selectedConversationId === null && conversations.length > 0) {
      setSelectedConversationId(conversations[0].id);
    }
  }, [conversations, selectedConversationId]);

  // Fetch messages for active conversation
  const {
    data: serverMessages = [],
    isLoading: loadingMessages,
  } = useConversationMessages(selectedConversationId);

  // Combine saved messages with any in-flight optimistic message
  const displayMessages = selectedConversationId
    ? [...serverMessages, ...optimisticMessages]
    : optimisticMessages;

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [displayMessages, isGenerating]);

  // Switch conversation
  const handleSelectConversation = (id) => {
    if (id === selectedConversationId) return;
    setSelectedConversationId(id);
    setOptimisticMessages([]);
    setErrorMessage('');
    setLastFailedText('');
    setConfirmDeleteId(null);
  };

  // Start a new chat
  const handleNewChat = () => {
    setSelectedConversationId(null);
    setOptimisticMessages([]);
    setErrorMessage('');
    setLastFailedText('');
    setInputText('');
    setConfirmDeleteId(null);
    if (inputRef.current) {
      inputRef.current.focus();
    }
  };

  // Delete conversation with confirmation
  const handleDeleteConversation = async (e, convId) => {
    e.stopPropagation();
    try {
      await deleteConversationMutation.mutateAsync(convId);
      setConfirmDeleteId(null);
      if (selectedConversationId === convId) {
        const remaining = conversations.filter((c) => c.id !== convId);
        setSelectedConversationId(remaining.length > 0 ? remaining[0].id : null);
        setOptimisticMessages([]);
      }
    } catch (err) {
      setErrorMessage('Failed to delete conversation. Please try again.');
    }
  };

  // Send message
  const handleSendMessage = async (textToSend) => {
    const text = (textToSend || inputText).trim();
    if (!text || isGenerating) return;

    setErrorMessage('');
    setLastFailedText('');
    if (!textToSend) {
      setInputText('');
    }

    // Optimistically show user's message right away
    const tempUserMsg = {
      id: 'temp-' + Date.now(),
      role: 'USER',
      content: text,
      createdAt: new Date().toISOString(),
    };
    setOptimisticMessages([tempUserMsg]);
    setIsGenerating(true);

    try {
      if (selectedConversationId) {
        // Send to existing conversation
        await sendMessageMutation.mutateAsync({
          conversationId: selectedConversationId,
          message: text,
        });
        setOptimisticMessages([]);
      } else {
        // Create new conversation via direct chat call
        const response = await aiService.chat(text);
        if (response?.conversationId) {
          await queryClient.invalidateQueries({ queryKey: AI_QUERY_KEYS.conversations });
          setSelectedConversationId(response.conversationId);
          await queryClient.invalidateQueries({
            queryKey: AI_QUERY_KEYS.conversationMessages(response.conversationId),
          });
        }
        setOptimisticMessages([]);
      }
    } catch (err) {
      const errorMsg =
        err.response?.data?.message ||
        err.userMessage ||
        'Unable to receive AI response. Please make sure Ollama is running.';
      setErrorMessage(errorMsg);
      setLastFailedText(text);
      // Keep optimistic user message visible so the user doesn't lose their prompt!
    } finally {
      setIsGenerating(false);
    }
  };

  const handleRetry = () => {
    if (lastFailedText) {
      handleSendMessage(lastFailedText);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  // Find active conversation object
  const currentConversation = conversations.find((c) => c.id === selectedConversationId);

  return (
    <div className="flex h-[720px] bg-white rounded-3xl border border-slate-200/90 shadow-sm overflow-hidden">
      {/* Sidebar: Conversation History */}
      <div
        className={`${isSidebarOpen ? 'w-64 sm:w-72' : 'w-0'
          } transition-all duration-200 border-r border-slate-100 bg-slate-50/70 flex flex-col overflow-hidden shrink-0`}
      >
        {/* Sidebar Header: New Chat Button */}
        <div className="p-3 border-b border-slate-100 flex items-center gap-2">
          <button
            type="button"
            onClick={handleNewChat}
            className="flex-1 flex items-center justify-center gap-2 py-2 px-3 rounded-xl bg-white hover:bg-slate-100 text-slate-800 text-xs font-semibold border border-slate-200 shadow-2xs transition-colors cursor-pointer"
          >
            <Plus className="w-3.5 h-3.5 text-emerald-600" />
            <span>New Chat</span>
          </button>
        </div>

        {/* Sidebar List */}
        <div className="flex-1 overflow-y-auto p-2 space-y-1">
          <div className="px-2 py-1 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
            Conversations
          </div>

          {loadingConversations && (
            <div className="p-4 text-center text-xs text-slate-400 space-y-2">
              <Loader2 className="w-4 h-4 animate-spin mx-auto text-emerald-500" />
              <span>Loading chats...</span>
            </div>
          )}

          {!loadingConversations && conversations.length === 0 && (
            <div className="p-4 text-center text-xs text-slate-400">
              No conversations yet. Start a new chat above!
            </div>
          )}

          {conversations.map((conv) => {
            const isSelected = conv.id === selectedConversationId;
            const isConfirming = confirmDeleteId === conv.id;

            return (
              <div
                key={conv.id}
                onClick={() => handleSelectConversation(conv.id)}
                className={`group relative flex items-center gap-2.5 px-3 py-2.5 rounded-xl text-xs transition-colors cursor-pointer ${isSelected
                  ? 'bg-white text-slate-900 font-medium shadow-2xs border border-slate-200/80'
                  : 'text-slate-600 hover:bg-slate-100/80 hover:text-slate-900'
                  }`}
              >
                <MessageSquare
                  className={`w-3.5 h-3.5 shrink-0 ${isSelected ? 'text-emerald-600' : 'text-slate-400'
                    }`}
                />
                <span className="flex-1 truncate">{conv.title || 'Untitled Chat'}</span>

                {/* Inline Confirmation or Delete Icon */}
                {isConfirming ? (
                  <div
                    className="flex items-center gap-1 bg-white border border-rose-200 p-0.5 rounded-md shadow-2xs"
                    onClick={(e) => e.stopPropagation()}
                  >
                    <button
                      type="button"
                      title="Confirm delete"
                      onClick={(e) => handleDeleteConversation(e, conv.id)}
                      className="p-1 hover:bg-rose-50 text-rose-600 rounded cursor-pointer"
                    >
                      <Check className="w-3 h-3" />
                    </button>
                    <button
                      type="button"
                      title="Cancel"
                      onClick={(e) => {
                        e.stopPropagation();
                        setConfirmDeleteId(null);
                      }}
                      className="p-1 hover:bg-slate-100 text-slate-400 rounded cursor-pointer"
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </div>
                ) : (
                  <button
                    type="button"
                    title="Delete chat"
                    onClick={(e) => {
                      e.stopPropagation();
                      setConfirmDeleteId(conv.id);
                    }}
                    className={`opacity-0 group-hover:opacity-100 p-1 rounded hover:bg-rose-50 text-slate-400 hover:text-rose-600 transition-opacity cursor-pointer ${isSelected ? 'opacity-70' : ''
                      }`}
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>
            );
          })}
        </div>

        {/* Sidebar Footer */}
        <div className="p-3 border-t border-slate-100 bg-white/50 text-[11px] text-slate-400 flex items-center justify-between">
          <span>{conversations.length} saved chats</span>
          <span className="font-mono text-[10px] text-emerald-600 font-medium">MySQL sync</span>
        </div>
      </div>

      {/* Main Chat Area */}
      <div className="flex-1 flex flex-col overflow-hidden bg-white">
        {/* Top Header Bar */}
        <div className="p-3.5 px-5 border-b border-slate-100 flex items-center justify-between bg-white shrink-0">
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => setIsSidebarOpen(!isSidebarOpen)}
              title={isSidebarOpen ? 'Hide sidebar' : 'Show sidebar'}
              className="p-1.5 rounded-lg text-slate-500 hover:text-slate-800 hover:bg-slate-100 transition-colors cursor-pointer"
            >
              {isSidebarOpen ? (
                <PanelLeftClose className="w-4 h-4" />
              ) : (
                <PanelLeft className="w-4 h-4" />
              )}
            </button>

            <div>
              <div className="font-semibold text-sm text-slate-800 flex items-center gap-2">
                <span>{currentConversation ? currentConversation.title : 'New Chat'}</span>
                {/* <span className="flex items-center gap-1 text-[11px] font-normal px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
                  Local {healthData?.model || 'qwen3:1.7b'}
                </span> */}
              </div>
              <p className="text-[11px] text-slate-400">Persistent chat history • 100% private</p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={handleNewChat}
              className="text-xs text-slate-600 hover:text-slate-900 font-medium px-2.5 py-1.5 rounded-lg border border-slate-200 hover:bg-slate-50 transition-colors flex items-center gap-1.5 cursor-pointer"
            >
              <Plus className="w-3.5 h-3.5 text-emerald-600" />
              <span className="hidden sm:inline">New Chat</span>
            </button>
          </div>
        </div>

        {/* Message Scroll Area */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-6 space-y-3">
          {loadingMessages && (
            <div className="h-full flex items-center justify-center">
              <div className="text-center space-y-2 text-xs text-slate-400">
                <Loader2 className="w-5 h-5 animate-spin mx-auto text-emerald-600" />
                <span>Loading conversation history...</span>
              </div>
            </div>
          )}

          {!loadingMessages && displayMessages.length === 0 && (
            <div className="h-full flex flex-col items-center justify-center text-center p-6 space-y-6 max-w-lg mx-auto">
              <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-[#0F172A] to-[#1E293B] text-emerald-400 flex items-center justify-center shadow-md">
                <Bot className="w-6 h-6" />
              </div>
              <div className="space-y-1.5">
                <h3 className="font-semibold text-slate-900 text-lg">
                  FinPilot Financial Assistant
                </h3>
                <p className="text-xs text-slate-500 leading-relaxed">
                  Ask questions about your accounts, monthly spending breakdown, budget allocations,
                  or recurring subscriptions. Your conversation history is saved securely in your
                  private database.
                </p>
              </div>

              {/* Starter Prompt Cards */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 w-full text-left pt-2">
                <button
                  type="button"
                  onClick={() => handleSendMessage('Where did I spend the most this month?')}
                  className="p-3 rounded-xl border border-slate-200/80 bg-slate-50/60 hover:bg-slate-100 hover:border-slate-300 transition-all text-xs text-slate-700 flex items-start gap-2.5 cursor-pointer group"
                >
                  <TrendingDown className="w-4 h-4 text-emerald-600 mt-0.5 shrink-0 group-hover:scale-110 transition-transform" />
                  <div>
                    <div className="font-medium text-slate-800">Top spending</div>
                    <div className="text-slate-400 text-[11px]">Where did I spend the most?</div>
                  </div>
                </button>

                <button
                  type="button"
                  onClick={() => handleSendMessage('How is my budget for this month?')}
                  className="p-3 rounded-xl border border-slate-200/80 bg-slate-50/60 hover:bg-slate-100 hover:border-slate-300 transition-all text-xs text-slate-700 flex items-start gap-2.5 cursor-pointer group"
                >
                  <PieChart className="w-4 h-4 text-emerald-600 mt-0.5 shrink-0 group-hover:scale-110 transition-transform" />
                  <div>
                    <div className="font-medium text-slate-800">Budget status</div>
                    <div className="text-slate-400 text-[11px]">Check assigned & activity</div>
                  </div>
                </button>

                <button
                  type="button"
                  onClick={() => handleSendMessage('What are my recurring payments?')}
                  className="p-3 rounded-xl border border-slate-200/80 bg-slate-50/60 hover:bg-slate-100 hover:border-slate-300 transition-all text-xs text-slate-700 flex items-start gap-2.5 cursor-pointer group"
                >
                  <Repeat className="w-4 h-4 text-emerald-600 mt-0.5 shrink-0 group-hover:scale-110 transition-transform" />
                  <div>
                    <div className="font-medium text-slate-800">Recurring payments</div>
                    <div className="text-slate-400 text-[11px]">List ongoing subscriptions</div>
                  </div>
                </button>

                <button
                  type="button"
                  onClick={() => handleSendMessage('Give me a summary of this month.')}
                  className="p-3 rounded-xl border border-slate-200/80 bg-slate-50/60 hover:bg-slate-100 hover:border-slate-300 transition-all text-xs text-slate-700 flex items-start gap-2.5 cursor-pointer group"
                >
                  <PiggyBank className="w-4 h-4 text-emerald-600 mt-0.5 shrink-0 group-hover:scale-110 transition-transform" />
                  <div>
                    <div className="font-medium text-slate-800">Monthly summary</div>
                    <div className="text-slate-400 text-[11px]">Cash flow & net savings</div>
                  </div>
                </button>
              </div>
            </div>
          )}

          {!loadingMessages &&
            displayMessages.map((m) => {
              const formattedTime = m.createdAt
                ? new Date(m.createdAt).toLocaleTimeString([], {
                  hour: '2-digit',
                  minute: '2-digit',
                })
                : 'Just now';

              return (
                <AiMessage
                  key={m.id}
                  role={m.role}
                  isUser={m.role === 'USER'}
                  message={m.content}
                  timestamp={formattedTime}
                  model={healthData?.model || 'qwen3:1.7b'}
                />
              );
            })}

          {isGenerating && (
            <div className="flex items-center gap-3 my-3">
              <div className="w-8 h-8 rounded-full bg-slate-800 text-emerald-400 flex items-center justify-center shrink-0">
                <Bot className="w-4 h-4 animate-spin" />
              </div>
              <div className="bg-slate-50 border border-slate-200 rounded-2xl rounded-tl-xs p-3.5 text-xs text-slate-500 flex items-center gap-2">
                <Loader2 className="w-3.5 h-3.5 animate-spin text-emerald-600" />
                <span>Analyzing financial records with Qwen...</span>
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Error alert with Retry button */}
        {errorMessage && (
          <div className="mx-4 mb-2 p-3 rounded-xl bg-rose-50 border border-rose-200 text-xs text-rose-700 flex items-center gap-2.5">
            <AlertCircle className="w-4 h-4 shrink-0 text-rose-600" />
            <span className="flex-1">{errorMessage}</span>
            {lastFailedText && (
              <button
                type="button"
                onClick={handleRetry}
                disabled={isGenerating}
                className="px-2.5 py-1 rounded-md bg-rose-600 hover:bg-rose-700 text-white font-medium flex items-center gap-1 cursor-pointer transition-colors text-[11px]"
              >
                <RotateCcw className="w-3 h-3" />
                <span>Retry</span>
              </button>
            )}
            <button
              type="button"
              onClick={() => setErrorMessage('')}
              className="text-rose-500 hover:text-rose-800 font-bold ml-1 cursor-pointer"
            >
              ✕
            </button>
          </div>
        )}

        {/* Footer Input Area */}
        <div className="p-4 border-t border-slate-100 bg-white space-y-3 shrink-0">
          <QuickQuestions
            onSelectQuestion={(q) => handleSendMessage(q)}
            disabled={isGenerating}
          />

          <div className="flex items-center gap-2 bg-slate-50 border border-slate-200 rounded-xl p-1.5 focus-within:border-slate-400 focus-within:ring-2 focus-within:ring-slate-100 transition-all">
            <input
              ref={inputRef}
              type="text"
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Ask about spending, budgets, savings, or subscriptions..."
              disabled={isGenerating}
              className="flex-1 bg-transparent px-3 py-2 text-sm text-slate-800 placeholder-slate-400 outline-none"
            />
            <button
              type="button"
              onClick={() => handleSendMessage()}
              disabled={!inputText.trim() || isGenerating}
              className="p-2.5 rounded-lg bg-[#0F172A] hover:bg-slate-800 text-white font-medium transition-colors disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer shrink-0"
            >
              {isGenerating ? (
                <Loader2 className="w-4 h-4 animate-spin text-emerald-400" />
              ) : (
                <Send className="w-4 h-4" />
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
