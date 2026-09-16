package org.example.keibaapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST API（/api/v1）の入出力仕様を検証するテスト。
 * 外部通信やDBには依存せず、サービス層をモックしてコントローラの責務だけを確認します。
 */
@WebMvcTest(ApiController.class)
class ApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RaceService raceService;

    @MockBean
    private RaceResultRecordRepository raceResultRecordRepository;

    @MockBean
    private RaceResultStatsService raceResultStatsService;

    @MockBean
    private FavoriteHorseService favoriteHorseService;

    @MockBean
    private FavoriteJockeyService favoriteJockeyService;

    private RaceInfo createRace() {
        Horse lowScore = new Horse("1", "1", "アルファ", "騎手A", "55.0", 2.5);
        lowScore.setPredictionScore(10.0);
        Horse highScore = new Horse("2", "2", "ブラボー", "騎手B", "56.0", 8.0);
        highScore.setPredictionScore(50.0);

        return new RaceInfo(11, "東京", "テスト記念", LocalTime.of(15, 40),
                "芝", "2400m", List.of(lowScore, highScore));
    }

    @Test
    void 出馬表APIがレースと馬をJSONで返す() throws Exception {
        when(raceService.getBasicRaces()).thenReturn(List.of(createRace()));

        mockMvc.perform(get("/api/v1/races"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].raceNum").value(11))
                .andExpect(jsonPath("$[0].venue").value("東京"))
                .andExpect(jsonPath("$[0].horses.length()").value(2));
    }

    @Test
    void 予想APIは馬をスコア降順で返す() throws Exception {
        when(raceService.getRaces()).thenReturn(List.of(createRace()));

        mockMvc.perform(get("/api/v1/predictions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].horses[0].name").value("ブラボー"))
                .andExpect(jsonPath("$[0].horses[0].predictionScore").value(50.0))
                .andExpect(jsonPath("$[0].horses[1].name").value("アルファ"));
    }

    @Test
    void バックテスト統計APIが集計値を返す() throws Exception {
        RaceResultRecord record = new RaceResultRecord();
        when(raceResultRecordRepository.findAll()).thenReturn(List.of(record));
        when(raceResultStatsService.rate(anyInt(), anyInt())).thenReturn(25.0);
        when(raceResultStatsService.calculateRoi(anyList())).thenReturn(85.5);

        mockMvc.perform(get("/api/v1/results/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.top1WinRate").value(25.0))
                .andExpect(jsonPath("$.roi").value(85.5));
    }

    @Test
    void お気に入り馬の登録は201と登録内容を返す() throws Exception {
        when(favoriteHorseService.save("ディープインパクト"))
                .thenReturn(new FavoriteHorse("ディープインパクト"));

        mockMvc.perform(post("/api/v1/favorites/horses")
                        .contentType("application/json")
                        .content("{\"name\": \"ディープインパクト\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("ディープインパクト"));
    }

    @Test
    void お気に入り馬の登録は名前が空なら400を返す() throws Exception {
        mockMvc.perform(post("/api/v1/favorites/horses")
                        .contentType("application/json")
                        .content("{\"name\": \"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void お気に入り馬の削除は204を返す() throws Exception {
        mockMvc.perform(delete("/api/v1/favorites/horses/1"))
                .andExpect(status().isNoContent());
    }
}
