package org.example.keibaapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ADMIN_TOKENが設定されているサーバーでは、一致するトークンのときだけ全削除が実行される
@WebMvcTest(value = RaceController.class, properties = "admin.token=secret-for-test")
class AdminTokenAcceptedTest extends RaceControllerWebTestBase {

    @Test
    void 一致するトークンなら実行される() throws Exception {
        when(raceResultRecordRepository.count()).thenReturn(3L);
        when(trackedRaceUrlRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(post("/results/reset").param("token", "secret-for-test"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("削除件数: 3件")));

        verify(raceResultRecordRepository).deleteAll();
    }

    @Test
    void 違うトークンや無いトークンは拒否される() throws Exception {
        mockMvc.perform(post("/results/reset").param("token", "wrong")).andExpect(status().isForbidden());
        mockMvc.perform(post("/results/reset")).andExpect(status().isForbidden());

        verify(raceResultRecordRepository, never()).deleteAll();
    }
}
