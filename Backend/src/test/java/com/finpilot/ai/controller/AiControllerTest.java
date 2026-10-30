package com.finpilot.ai.controller;

import com.finpilot.ai.dto.*;
import com.finpilot.ai.entity.MessageRole;
import com.finpilot.ai.exception.AiErrorResponse;
import com.finpilot.ai.exception.AiException;
import com.finpilot.ai.service.AiService;
import com.finpilot.ai.service.ChatHistoryService;
import com.finpilot.security.UserPrincipal;
import com.finpilot.user.entity.AuthProvider;
import com.finpilot.user.entity.Role;
import com.finpilot.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    @Mock
    private AiService aiService;

    @Mock
    private ChatHistoryService chatHistoryService;

    @InjectMocks
    private AiController aiController;

    private User testUser;
    private UserPrincipal testUserPrincipal;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("user@finpilot.com")
                .name("FinPilot User")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build();

        testUserPrincipal = UserPrincipal.create(testUser);
    }

    @Test
    @DisplayName("GET /api/ai/health returns 200 OK")
    void testGetHealth() {
        when(aiService.checkHealth()).thenReturn(
                AiHealthResponse.builder().available(true).model("qwen3:1.7b").build()
        );

        ResponseEntity<AiHealthResponse> response = aiController.getHealth();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("qwen3:1.7b", response.getBody().getModel());
    }

    @Test
    @DisplayName("POST /api/ai/chat returns 200 OK when authenticated")
    void testChat_Authenticated() {
        AiChatRequest request = AiChatRequest.builder().message("Where did I spend?").build();
        when(chatHistoryService.sendMessage(eq(testUser), any(), eq("Where did I spend?")))
                .thenReturn(AiChatResponse.builder().message("Your highest category was Bills.").model("qwen3:1.7b").build());

        ResponseEntity<AiChatResponse> response = aiController.chat(testUserPrincipal, request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Your highest category was Bills.", response.getBody().getMessage());
    }

    @Test
    @DisplayName("POST /api/ai/chat returns 401 UNAUTHORIZED when unauthenticated")
    void testChat_Unauthenticated() {
        AiChatRequest request = AiChatRequest.builder().message("Where did I spend?").build();
        ResponseEntity<AiChatResponse> response = aiController.chat(null, request);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("GET /api/ai/conversations returns list of conversations")
    void testGetConversations() {
        ChatConversationDto conv = ChatConversationDto.builder()
                .id(10L)
                .title("Spending Advice")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .messageCount(2)
                .build();
        when(chatHistoryService.getUserConversations(eq(testUser))).thenReturn(List.of(conv));

        ResponseEntity<List<ChatConversationDto>> response = aiController.getConversations(testUserPrincipal);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("Spending Advice", response.getBody().get(0).getTitle());
    }

    @Test
    @DisplayName("POST /api/ai/conversations creates a new conversation")
    void testCreateConversation() {
        CreateConversationRequest req = CreateConversationRequest.builder().title("Budget Q&A").build();
        ChatConversationDto created = ChatConversationDto.builder()
                .id(11L)
                .title("Budget Q&A")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(chatHistoryService.createConversation(eq(testUser), eq("Budget Q&A"))).thenReturn(created);

        ResponseEntity<ChatConversationDto> response = aiController.createConversation(testUserPrincipal, req);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(11L, response.getBody().getId());
    }

    @Test
    @DisplayName("GET /api/ai/conversations/{id}/messages returns messages for conversation")
    void testGetConversationMessages() {
        ChatMessageDto msg1 = ChatMessageDto.builder()
                .id(1L)
                .conversationId(10L)
                .role(MessageRole.USER)
                .content("Hello")
                .createdAt(LocalDateTime.now())
                .build();
        ChatMessageDto msg2 = ChatMessageDto.builder()
                .id(2L)
                .conversationId(10L)
                .role(MessageRole.ASSISTANT)
                .content("Hi there!")
                .createdAt(LocalDateTime.now())
                .build();
        when(chatHistoryService.getConversationMessages(eq(testUser), eq(10L))).thenReturn(List.of(msg1, msg2));

        ResponseEntity<List<ChatMessageDto>> response = aiController.getConversationMessages(testUserPrincipal, 10L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    @DisplayName("POST /api/ai/conversations/{id}/messages sends message and returns AI response")
    void testSendMessageToConversation() {
        SendMessageRequest req = SendMessageRequest.builder().message("What is my balance?").build();
        AiChatResponse aiResp = AiChatResponse.builder()
                .conversationId(10L)
                .conversationTitle("What is my balance?")
                .message("Your balance is ₹50,000.")
                .model("qwen3:1.7b")
                .build();
        when(chatHistoryService.sendMessage(eq(testUser), eq(10L), eq("What is my balance?"))).thenReturn(aiResp);

        ResponseEntity<AiChatResponse> response = aiController.sendMessage(testUserPrincipal, 10L, req);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Your balance is ₹50,000.", response.getBody().getMessage());
    }

    @Test
    @DisplayName("DELETE /api/ai/conversations/{id} deletes conversation")
    void testDeleteConversation() {
        doNothing().when(chatHistoryService).deleteConversation(eq(testUser), eq(10L));

        ResponseEntity<Void> response = aiController.deleteConversation(testUserPrincipal, 10L);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(chatHistoryService, times(1)).deleteConversation(eq(testUser), eq(10L));
    }

    @Test
    @DisplayName("GET /api/ai/insights/spending returns 200 OK")
    void testGetSpendingInsights() {
        when(aiService.generateSpendingInsights(eq(testUser), any(), any(), eq(9), eq(2026)))
                .thenReturn(AiInsightsResponse.builder().summary("Spending summary").insights(List.of()).build());

        ResponseEntity<AiInsightsResponse> response = aiController.getSpendingInsights(testUserPrincipal, null, null, 9, 2026);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Spending summary", response.getBody().getSummary());
    }

    @Test
    @DisplayName("GET /api/ai/insights/budget returns 200 OK")
    void testGetBudgetInsights() {
        when(aiService.generateBudgetInsights(eq(testUser), eq(2026), eq(9)))
                .thenReturn(AiInsightsResponse.builder().summary("Budget summary").insights(List.of()).build());

        ResponseEntity<AiInsightsResponse> response = aiController.getBudgetInsights(testUserPrincipal, 2026, 9);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Budget summary", response.getBody().getSummary());
    }

    @Test
    @DisplayName("GET /api/ai/insights/recurring returns 200 OK")
    void testGetRecurringInsights() {
        when(aiService.generateRecurringInsights(eq(testUser)))
                .thenReturn(AiInsightsResponse.builder().summary("Recurring summary").insights(List.of()).build());

        ResponseEntity<AiInsightsResponse> response = aiController.getRecurringInsights(testUserPrincipal);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Recurring summary", response.getBody().getSummary());
    }

    @Test
    @DisplayName("GET /api/ai/summary/monthly returns 200 OK")
    void testGetMonthlySummary() {
        when(aiService.generateMonthlySummary(eq(testUser), eq(2026), eq(9)))
                .thenReturn(AiInsightsResponse.builder().summary("Monthly summary").insights(List.of()).build());

        ResponseEntity<AiInsightsResponse> response = aiController.getMonthlySummary(testUserPrincipal, 2026, 9);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Monthly summary", response.getBody().getSummary());
    }

    @Test
    @DisplayName("POST /api/ai/suggest-category returns 200 OK")
    void testSuggestCategory() {
        SuggestCategoryRequest req = SuggestCategoryRequest.builder()
                .merchantName("Swiggy")
                .description("Food delivery")
                .amount(new BigDecimal("450.00"))
                .build();

        when(aiService.suggestCategory(eq(testUser), eq(req)))
                .thenReturn(SuggestCategoryResponse.builder().suggestedCategory("Food").reason("Food delivery").build());

        ResponseEntity<SuggestCategoryResponse> response = aiController.suggestCategory(testUserPrincipal, req);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Food", response.getBody().getSuggestedCategory());
    }

    @Test
    @DisplayName("AiException handler produces structured JSON without 500 stack trace")
    void testHandleAiException() {
        AiException ex = AiException.aiUnavailable();
        ResponseEntity<AiErrorResponse> response = aiController.handleAiException(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("AI_UNAVAILABLE", response.getBody().getCode());
        assertEquals("Local AI is currently unavailable. Please make sure Ollama is running.", response.getBody().getMessage());
    }
}
