package org.example.keibaapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// /status のテンプレートが、今日の前向き記録の状況を含めて描画できることの確認(外部通信・DBなし)
@WebMvcTest(StatusController.class)
class StatusPageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RaceResultCollectionService raceResultCollectionService;
    @MockBean
    private TrackedRaceUrlRepository trackedRaceUrlRepository;
    @MockBean
    private RaceResultRecordRepository raceResultRecordRepository;
    @MockBean
    private RacePayoutRepository racePayoutRepository;
    @MockBean
    private HorseEnrichmentService horseEnrichmentService;
    @MockBean
    private RecentLogBuffer recentLogBuffer;
    @MockBean
    private RecordingHealthService recordingHealthService;

    @Test
    void 状況ページが今日の前向き記録を含めて描画できる() throws Exception {
        when(horseEnrichmentService.getLastBoardIssue()).thenReturn("なし");
        when(recordingHealthService.summarize()).thenReturn(new RecordingHealthService.Summary(
                LocalDate.of(2026, 11, 1), 36, 36, 20, 18,
                LocalDateTime.of(2026, 11, 1, 15, 37), LocalDateTime.of(2026, 11, 1, 8, 15)));

        mockMvc.perform(get("/status"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("今日の前向き記録")))
                .andExpect(content().string(containsString("36 / 36")))
                .andExpect(content().string(containsString("11/1 15:37")));
    }

    @Test
    void 開催のレース数が分からない日は朝オッズを件数だけで表示する() throws Exception {
        when(horseEnrichmentService.getLastBoardIssue()).thenReturn("なし");
        when(recordingHealthService.summarize()).thenReturn(new RecordingHealthService.Summary(
                LocalDate.of(2026, 11, 2), 0, 0, 0, 0, null, null));

        mockMvc.perform(get("/status"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("今日の前向き記録")))
                .andExpect(content().string(containsString("—")));
    }
}
