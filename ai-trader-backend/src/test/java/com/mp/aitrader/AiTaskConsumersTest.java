package com.mp.aitrader;

import com.mp.aitrader.agent.client.LangGraphClient;
import com.mp.aitrader.conversation.service.AiSummaryService;
import com.mp.aitrader.memory.service.AiMemoryService;
import com.mp.aitrader.task.AiTaskConsumers;
import com.mp.aitrader.task.AiTaskPayloads;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T4 验证：消费者只在业务成功后 ack，失败则 nack 进死信队列。
 *
 * <p>核心改变：改造前向量同步失败只是一句 {@code log.warn} —— 消息永久丢失且无人知晓；
 * 改造后失败会进入 {@code *.dlq}，可观测、可重放。
 *
 * <p>纯单元测试（Mockito），不需要 RabbitMQ broker / MySQL / Redis。
 */
@ExtendWith(MockitoExtension.class)
class AiTaskConsumersTest {

    @Mock
    private AiSummaryService summaryService;

    @Mock
    private AiMemoryService memoryService;

    @Mock
    private LangGraphClient langGraphClient;

    @Mock
    private Channel channel;

    @InjectMocks
    private AiTaskConsumers consumers;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(consumers, "aiMetrics",
                new com.mp.aitrader.metric.AiMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
    }

    @Test
    void shouldAck_whenSummarySucceeds() throws Exception {
        consumers.onSummary(AiTaskPayloads.SummaryTask.builder().conversationId(9L).build(), channel, 7L);

        verify(summaryService).generateAndSaveSummary(9L);
        verify(channel).basicAck(7L, false);
    }

    @Test
    void shouldNackToDeadLetter_whenSummaryFails() throws Exception {
        doThrow(new IllegalStateException("LLM 超时")).when(summaryService).generateAndSaveSummary(anyLong());

        consumers.onSummary(AiTaskPayloads.SummaryTask.builder().conversationId(9L).build(), channel, 7L);

        // requeue=false：不无限重投毒消息，交给 x-dead-letter-exchange 转入 *.dlq
        verify(channel).basicNack(7L, false, false);
        verify(channel, never()).basicAck(anyLong(), eq(false));
    }

    /** 改造前这种失败只 log.warn 一声就丢了；现在必须进死信。 */
    @Test
    void shouldNackToDeadLetter_whenPythonReportsVectorSaveFailure() throws Exception {
        when(langGraphClient.syncMemorySave(any(), any())).thenReturn(false);

        consumers.onVectorSave(AiTaskPayloads.VectorMemorySaveTask.builder()
                .userId(1L).memoryId(2L).content("止损 5%").memoryType("constraint").build(), channel, 3L);

        verify(channel).basicNack(3L, false, false);
        verify(channel, never()).basicAck(anyLong(), eq(false));
    }

    @Test
    void shouldAck_whenVectorSaveSucceeds() throws Exception {
        when(langGraphClient.syncMemorySave(any(), any())).thenReturn(true);

        consumers.onVectorSave(AiTaskPayloads.VectorMemorySaveTask.builder()
                .userId(1L).memoryId(2L).content("止损 5%").memoryType("constraint").build(), channel, 3L);

        verify(channel).basicAck(3L, false);
    }

    @Test
    void shouldAck_whenMemoryPersistSucceeds() throws Exception {
        consumers.onMemoryPersist(AiTaskPayloads.MemoryPersistTask.builder()
                .userId(1L).content("偏好波段").memoryType("preference").build(), channel, 5L);

        verify(memoryService).saveChatMemory(eq(1L), eq("偏好波段"), eq("preference"));
        verify(channel).basicAck(5L, false);
    }

    @Test
    void shouldNackToDeadLetter_whenRagSyncFails() throws Exception {
        when(langGraphClient.syncChunksToVectorStore(any(), any())).thenReturn(false);

        consumers.onRagSync(AiTaskPayloads.RagSyncTask.builder()
                .userId(1L).chunks(Collections.emptyList()).build(), channel, 11L);

        verify(channel).basicNack(11L, false, false);
    }

    @Test
    void shouldAck_whenVectorDeleteSucceeds() throws Exception {
        when(langGraphClient.syncMemoryDelete(any(), any(), anyString())).thenReturn(true);

        consumers.onVectorDelete(AiTaskPayloads.VectorMemoryDeleteTask.builder()
                .userId(1L).memoryIds(Collections.singletonList(2L)).memoryType("constraint").build(), channel, 13L);

        verify(channel).basicAck(13L, false);
    }
}
