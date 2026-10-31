package org.example.keibaapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

// RaceControllerのWebテストで共通の、サービス・リポジトリのモック(外部通信・DBなし)
abstract class RaceControllerWebTestBase {

    @Autowired
    MockMvc mockMvc;
    @MockBean
    RaceService raceService;
    @MockBean
    RaceNotificationService notificationService;
    @MockBean
    DummyRaceFactory dummyRaceFactory;
    @MockBean
    RaceResultRecordRepository raceResultRecordRepository;
    @MockBean
    RacePayoutRepository racePayoutRepository;
    @MockBean
    RaceResultStatsService raceResultStatsService;
    @MockBean
    BettingSimulationService bettingSimulationService;
    @MockBean
    OddsSnapshotRepository oddsSnapshotRepository;
    @MockBean
    ForwardEvaluationService forwardEvaluationService;
    @MockBean
    RaceResultCollectionService raceResultCollectionService;
    @MockBean
    TrackedRaceUrlRepository trackedRaceUrlRepository;
    @MockBean
    FavoriteHorseService favoriteHorseService;
    @MockBean
    FavoriteJockeyService favoriteJockeyService;
}
