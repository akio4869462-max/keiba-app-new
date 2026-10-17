package org.example.keibaapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// /results/forward のテンプレートが実際に描画できることの確認(外部通信・DBなし)
@WebMvcTest(RaceController.class)
class ResultsForwardPageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RaceService raceService;
    @MockBean
    private RaceNotificationService notificationService;
    @MockBean
    private DummyRaceFactory dummyRaceFactory;
    @MockBean
    private RaceResultRecordRepository raceResultRecordRepository;
    @MockBean
    private RacePayoutRepository racePayoutRepository;
    @MockBean
    private RaceResultStatsService raceResultStatsService;
    @MockBean
    private BettingSimulationService bettingSimulationService;
    @MockBean
    private OddsSnapshotRepository oddsSnapshotRepository;
    @MockBean
    private ForwardEvaluationService forwardEvaluationService;
    @MockBean
    private RaceResultCollectionService raceResultCollectionService;
    @MockBean
    private TrackedRaceUrlRepository trackedRaceUrlRepository;
    @MockBean
    private FavoriteHorseService favoriteHorseService;
    @MockBean
    private FavoriteJockeyService favoriteJockeyService;

    @Test
    void 前向き成績ページが描画できる() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 17);
        when(forwardEvaluationService.evaluate(any(), any())).thenReturn(
                new ForwardEvaluationService.Evaluation(3, 2, date, date, List.of(
                        new ForwardEvaluationService.GroupResult("買い候補(推奨馬)", 2, 1, 0.18, 200, 1200),
                        new ForwardEvaluationService.GroupResult("妙味 10%以上・2〜8番人気", 0, 0, 0, 0, 0),
                        new ForwardEvaluationService.GroupResult("2〜8番人気 全部(基準)", 5, 1, 0.5, 500, 640)),
                        true));

        mockMvc.perform(get("/results/forward"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("10分前の前向き成績")))
                .andExpect(content().string(containsString("買い候補(推奨馬)")))
                .andExpect(content().string(containsString("600.0%")))
                .andExpect(content().string(containsString("サンプル件数が少ない")));
    }
}
