package com.finpilot.ai.service;

import com.finpilot.ai.client.OllamaClient;
import com.finpilot.ai.dto.AiChatResponse;
import com.finpilot.ai.dto.ChatConversationDto;
import com.finpilot.ai.dto.ChatMessageDto;
import com.finpilot.ai.dto.ollama.OllamaChatMessage;
import com.finpilot.ai.entity.ChatConversation;
import com.finpilot.ai.entity.ChatMessage;
import com.finpilot.ai.entity.MessageRole;
import com.finpilot.ai.exception.AiException;
import com.finpilot.ai.repository.ChatConversationRepository;
import com.finpilot.ai.repository.ChatMessageRepository;
import com.finpilot.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatHistoryService {

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final AiService aiService;
    private final OllamaClient ollamaClient;

    @Transactional(readOnly = true)
    public List<ChatConversationDto> getUserConversations(User user) {
        log.info("Fetching chat conversations for user ID: {}", user.getId());
        List<ChatConversation> conversations = conversationRepository.findByUserIdOrderByUpdatedAtDesc(user.getId());
        return conversations.stream()
                .map(this::mapToConversationDto)
                .toList();
    }

    @Transactional
    public ChatConversationDto createConversation(User user, String title) {
        log.info("Creating new conversation for user ID: {}", user.getId());
        String effectiveTitle = (title != null && !title.isBlank()) ? title.trim() : "New Chat";
        ChatConversation conversation = ChatConversation.builder()
                .user(user)
                .title(effectiveTitle)
                .build();
        ChatConversation saved = conversationRepository.save(conversation);
        return mapToConversationDto(saved);
    }

    @Transactional(readOnly = true)
    public ChatConversationDto getConversation(User user, Long conversationId) {
        log.info("Fetching conversation ID: {} for user ID: {}", conversationId, user.getId());
        ChatConversation conversation = conversationRepository.findByIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> new AiException("CONVERSATION_NOT_FOUND", "Conversation not found", HttpStatus.NOT_FOUND));
        return mapToConversationDto(conversation);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getConversationMessages(User user, Long conversationId) {
        log.info("Fetching messages for conversation ID: {} for user ID: {}", conversationId, user.getId());
        // Verify conversation ownership
        if (!conversationRepository.existsByIdAndUserId(conversationId, user.getId())) {
            throw new AiException("CONVERSATION_NOT_FOUND", "Conversation not found", HttpStatus.NOT_FOUND);
        }

        List<ChatMessage> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        return messages.stream()
                .map(this::mapToMessageDto)
                .toList();
    }

    @Transactional
    public AiChatResponse sendMessage(User user, Long conversationId, String messageContent) {
        if (messageContent == null || messageContent.isBlank()) {
            throw new AiException("INVALID_MESSAGE", "Message cannot be blank", HttpStatus.BAD_REQUEST);
        }

        ChatConversation conversation;
        boolean isNewConversation = false;

        if (conversationId != null) {
            conversation = conversationRepository.findByIdAndUserId(conversationId, user.getId())
                    .orElseThrow(() -> new AiException("CONVERSATION_NOT_FOUND", "Conversation not found", HttpStatus.NOT_FOUND));
        } else {
            isNewConversation = true;
            conversation = ChatConversation.builder()
                    .user(user)
                    .title(generateTitle(messageContent))
                    .build();
            conversation = conversationRepository.save(conversation);
        }

        // If conversation is named "New Chat", update title dynamically based on first message
        if (!isNewConversation && "New Chat".equalsIgnoreCase(conversation.getTitle())) {
            conversation.setTitle(generateTitle(messageContent));
        }

        // 1. Save USER message first to ensure persistence even if AI fails
        ChatMessage userMessage = ChatMessage.builder()
                .conversation(conversation)
                .role(MessageRole.USER)
                .content(messageContent.trim())
                .build();
        messageRepository.save(userMessage);

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        log.info("Saved user message to conversation ID: {}", conversation.getId());

        // 2. Fetch conversation history for contextual AI conversation
        List<ChatMessage> previousMessages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        List<OllamaChatMessage> historyForAi = new ArrayList<>();
        // Include up to 10 most recent messages before the current one for context
        int startIdx = Math.max(0, previousMessages.size() - 11);
        for (int i = startIdx; i < previousMessages.size() - 1; i++) {
            ChatMessage m = previousMessages.get(i);
            historyForAi.add(OllamaChatMessage.builder()
                    .role(m.getRole() == MessageRole.USER ? "user" : "assistant")
                    .content(m.getContent())
                    .build());
        }

        // 3. Generate AI response through Ollama
        String reply;
        try {
            reply = aiService.generateChatReply(user, messageContent.trim(), historyForAi);
        } catch (Exception e) {
            log.error("AI model failed for conversation ID: {}: {}", conversation.getId(), e.getMessage());
            // User message is safely stored in DB
            throw e;
        }

        // 4. Save ASSISTANT message to database
        ChatMessage assistantMessage = ChatMessage.builder()
                .conversation(conversation)
                .role(MessageRole.ASSISTANT)
                .content(reply)
                .build();
        messageRepository.save(assistantMessage);

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        log.info("Saved AI assistant response to conversation ID: {}", conversation.getId());

        return AiChatResponse.builder()
                .conversationId(conversation.getId())
                .conversationTitle(conversation.getTitle())
                .message(reply)
                .insights(new ArrayList<>())
                .model(ollamaClient.getModelName())
                .build();
    }

    @Transactional
    public void deleteConversation(User user, Long conversationId) {
        log.info("Deleting conversation ID: {} for user ID: {}", conversationId, user.getId());
        ChatConversation conversation = conversationRepository.findByIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> new AiException("CONVERSATION_NOT_FOUND", "Conversation not found", HttpStatus.NOT_FOUND));

        conversationRepository.delete(conversation);
        log.info("Successfully deleted conversation ID: {}", conversationId);
    }

    public static String generateTitle(String message) {
        if (message == null || message.isBlank()) {
            return "New Chat";
        }
        String clean = message.trim().replaceAll("\\s+", " ");
        if (clean.length() <= 40) {
            return clean;
        }
        int lastSpace = clean.substring(0, 40).lastIndexOf(' ');
        if (lastSpace > 15) {
            return clean.substring(0, lastSpace) + "...";
        }
        return clean.substring(0, 40) + "...";
    }

    private ChatConversationDto mapToConversationDto(ChatConversation conv) {
        List<ChatMessage> msgs = conv.getMessages();
        String lastMessage = null;
        if (msgs != null && !msgs.isEmpty()) {
            lastMessage = msgs.get(msgs.size() - 1).getContent();
            if (lastMessage.length() > 60) {
                lastMessage = lastMessage.substring(0, 57) + "...";
            }
        }
        return ChatConversationDto.builder()
                .id(conv.getId())
                .title(conv.getTitle())
                .createdAt(conv.getCreatedAt())
                .updatedAt(conv.getUpdatedAt())
                .messageCount(msgs != null ? msgs.size() : 0)
                .lastMessage(lastMessage)
                .build();
    }

    private ChatMessageDto mapToMessageDto(ChatMessage msg) {
        return ChatMessageDto.builder()
                .id(msg.getId())
                .conversationId(msg.getConversation().getId())
                .role(msg.getRole())
                .content(msg.getContent())
                .createdAt(msg.getCreatedAt())
                .build();
    }
}
