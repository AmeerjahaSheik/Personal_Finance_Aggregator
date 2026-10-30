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
import com.finpilot.user.entity.AuthProvider;
import com.finpilot.user.entity.Role;
import com.finpilot.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceTest {

    @Mock
    private ChatConversationRepository conversationRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private AiService aiService;

    @Mock
    private OllamaClient ollamaClient;

    @InjectMocks
    private ChatHistoryService chatHistoryService;

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        user1 = User.builder()
                .id(1L)
                .email("user1@example.com")
                .name("User One")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build();

        user2 = User.builder()
                .id(2L)
                .email("user2@example.com")
                .name("User Two")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build();
    }

    @Test
    @DisplayName("getUserConversations returns list sorted by updated_at")
    void testGetUserConversations() {
        ChatConversation c1 = ChatConversation.builder()
                .id(1L)
                .user(user1)
                .title("Conversation 1")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .messages(new ArrayList<>())
                .build();

        when(conversationRepository.findByUserIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(c1));

        List<ChatConversationDto> result = chatHistoryService.getUserConversations(user1);
        assertEquals(1, result.size());
        assertEquals("Conversation 1", result.get(0).getTitle());
    }

    @Test
    @DisplayName("createConversation creates conversation with default title when title is blank")
    void testCreateConversationDefaultTitle() {
        when(conversationRepository.save(any(ChatConversation.class))).thenAnswer(inv -> {
            ChatConversation c = inv.getArgument(0);
            c.setId(10L);
            c.setCreatedAt(LocalDateTime.now());
            c.setUpdatedAt(LocalDateTime.now());
            return c;
        });

        ChatConversationDto result = chatHistoryService.createConversation(user1, "");
        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals("New Chat", result.getTitle());
    }

    @Test
    @DisplayName("getConversationMessages throws 404 when user is not owner")
    void testGetConversationMessages_Unauthorized() {
        when(conversationRepository.existsByIdAndUserId(99L, 1L)).thenReturn(false);

        AiException ex = assertThrows(AiException.class, () -> chatHistoryService.getConversationMessages(user1, 99L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    @DisplayName("sendMessage persists user message before calling AI and saves assistant response")
    void testSendMessage_Success() {
        ChatConversation conv = ChatConversation.builder()
                .id(5L)
                .user(user1)
                .title("New Chat")
                .messages(new ArrayList<>())
                .build();

        when(conversationRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(conv));
        when(conversationRepository.save(any(ChatConversation.class))).thenReturn(conv);
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(5L)).thenReturn(new ArrayList<>());
        when(aiService.generateChatReply(eq(user1), eq("Where did I spend the most?"), any()))
                .thenReturn("Your largest spending was Food & Dining.");
        when(ollamaClient.getModelName()).thenReturn("qwen3:1.7b");

        AiChatResponse response = chatHistoryService.sendMessage(user1, 5L, "Where did I spend the most?");

        assertNotNull(response);
        assertEquals("Your largest spending was Food & Dining.", response.getMessage());
        assertEquals(5L, response.getConversationId());
        assertEquals("qwen3:1.7b", response.getModel());

        // Verify two messages were saved: USER and ASSISTANT
        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, times(2)).save(msgCaptor.capture());

        List<ChatMessage> savedMessages = msgCaptor.getAllValues();
        assertEquals(MessageRole.USER, savedMessages.get(0).getRole());
        assertEquals("Where did I spend the most?", savedMessages.get(0).getContent());
        assertEquals(MessageRole.ASSISTANT, savedMessages.get(1).getRole());
        assertEquals("Your largest spending was Food & Dining.", savedMessages.get(1).getContent());
    }

    @Test
    @DisplayName("sendMessage preserves user message even when AI fails")
    void testSendMessage_AiFails_PreservesUserMessage() {
        ChatConversation conv = ChatConversation.builder()
                .id(5L)
                .user(user1)
                .title("Financial Overview")
                .messages(new ArrayList<>())
                .build();

        when(conversationRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(conv));
        when(conversationRepository.save(any(ChatConversation.class))).thenReturn(conv);
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(5L)).thenReturn(new ArrayList<>());
        when(aiService.generateChatReply(eq(user1), any(), any()))
                .thenThrow(AiException.timeout());

        assertThrows(AiException.class, () -> chatHistoryService.sendMessage(user1, 5L, "Analyze my budget"));

        // Verify USER message was saved BEFORE the AI call failed
        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, times(1)).save(msgCaptor.capture());
        assertEquals(MessageRole.USER, msgCaptor.getValue().getRole());
        assertEquals("Analyze my budget", msgCaptor.getValue().getContent());
    }

    @Test
    @DisplayName("deleteConversation deletes only when user is conversation owner")
    void testDeleteConversation_Success() {
        ChatConversation conv = ChatConversation.builder()
                .id(8L)
                .user(user1)
                .title("Old Conversation")
                .build();

        when(conversationRepository.findByIdAndUserId(8L, 1L)).thenReturn(Optional.of(conv));

        chatHistoryService.deleteConversation(user1, 8L);
        verify(conversationRepository, times(1)).delete(conv);
    }

    @Test
    @DisplayName("deleteConversation throws 404 when user2 tries to delete user1's conversation")
    void testDeleteConversation_UserIsolation() {
        when(conversationRepository.findByIdAndUserId(8L, 2L)).thenReturn(Optional.empty());

        AiException ex = assertThrows(AiException.class, () -> chatHistoryService.deleteConversation(user2, 8L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
        verify(conversationRepository, never()).delete(any());
    }

    @Test
    @DisplayName("generateTitle creates concise titles correctly")
    void testGenerateTitle() {
        assertEquals("New Chat", ChatHistoryService.generateTitle(""));
        assertEquals("What is my balance?", ChatHistoryService.generateTitle("What is my balance?"));
        assertTrue(ChatHistoryService.generateTitle("Can you provide me a very detailed breakdown of all my expenditures for October 2026?").endsWith("..."));
    }
}
