package org.example.keibaapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 検証結果の全削除(/results/reset)の保護。ADMIN_TOKENが未設定のサーバーでは、常に拒否される
@WebMvcTest(RaceController.class)
class AdminProtectionTest extends RaceControllerWebTestBase {

    @Test
    void 管理用トークンが未設定なら常に拒否される() throws Exception {
        mockMvc.perform(post("/results/reset").param("token", "anything")).andExpect(status().isForbidden());
        mockMvc.perform(post("/results/reset").param("token", "")).andExpect(status().isForbidden());
        mockMvc.perform(post("/results/reset")).andExpect(status().isForbidden());

        verify(raceResultRecordRepository, never()).deleteAll();
    }

    @Test
    void GETでは実行できない() throws Exception {
        mockMvc.perform(get("/results/reset")).andExpect(status().isMethodNotAllowed());

        verify(raceResultRecordRepository, never()).deleteAll();
    }

    @Test
    void トップは出馬表へ移る() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/races"));
    }
}
