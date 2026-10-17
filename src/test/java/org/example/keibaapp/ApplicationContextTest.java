package org.example.keibaapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

// アプリ全体のSpringコンテキストが起動できることの確認(H2を使い、DB・外部通信なし)。
// @WebMvcTestやMockitoの単体テストはサービス層をモックするため、コンストラクタが複数ある
// Beanに@Autowiredを付け忘れる等の配線ミスを検出できず、本番の起動失敗として初めて表に出た
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ctx;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
class ApplicationContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoadsAndKeyServicesAreWired() {
        assertNotNull(context.getBean(RaceService.class));
        assertNotNull(context.getBean(OddsSnapshotService.class));
        assertNotNull(context.getBean(TrackBiasService.class));
        assertNotNull(context.getBean(RaceNotificationService.class));
        assertNotNull(context.getBean(ForwardEvaluationService.class));
    }
}
