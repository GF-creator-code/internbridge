package com.getjobs.application;

import com.getjobs.GetJobsApplication;
import com.getjobs.application.service.AiService;
import com.getjobs.application.service.JobsdbService;
import com.getjobs.application.service.ShixisengService;
import com.getjobs.application.service.OfferTodayService;
import com.getjobs.application.service.ResumeSkillsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Exercises the same initialization path as bootRun, using a private copy of the upstream first-install template. */
class FreshInstallationTest {
    @TempDir Path directory;

    @Test void startsFromAnUpstreamTemplateAndPreservesAnEditedProfile() throws Exception {
        String source = System.getenv("GETJOBS_TEST_DB_TEMPLATE");
        assumeTrue(source != null && !source.isBlank(), "Set GETJOBS_TEST_DB_TEMPLATE to the upstream database template");
        Path database = directory.resolve("getjobs.db");
        Files.copy(Path.of(source), database);
        String[] args = {"--spring.datasource.url=jdbc:sqlite:" + database,
                "--internbridge.open-dashboard=false", "--internbridge.open-platforms=false",
                "--spring.main.banner-mode=off", "--logging.file.name="};
        try (var app = new SpringApplicationBuilder(GetJobsApplication.class).web(WebApplicationType.NONE).run(args)) {
            var jobsdb = app.getBean(JobsdbService.class);
            var shixiseng = app.getBean(ShixisengService.class);
            var offerToday = app.getBean(OfferTodayService.class);
            assertTrue(jobsdb.loadJobsdbConfig().isCollectOnly());
            assertEquals(List.of("internship"), jobsdb.loadJobsdbConfig().getKeywords());
            assertTrue(offerToday.loadOfferTodayConfig().getExcludeKeywords().isEmpty());
            assertTrue(shixiseng.loadShixisengConfig().getExcludeKeywords().isEmpty());
            assertEquals("", app.getBean(AiService.class).getAiConfig().getIntroduce());
            var file = new MockMultipartFile("file", "example.txt", "text/plain", "Built SQL reports.".getBytes());
            assertTrue(assertThrows(IllegalArgumentException.class, () -> app.getBean(ResumeSkillsService.class).extract(file))
                    .getMessage().contains("设置中心"));
            app.getBean(AiService.class).saveOrUpdateAiConfig("Synthetic SQL project", "Custom template");

            var jdbc = app.getBean(JdbcTemplate.class);
            jdbc.update("INSERT INTO jobsdb_data(job_id,job_title,company_name,location,salary,delivery_status) VALUES(?,?,?,?,?,?)",
                    "example-1", "SQL intern", "Example, Partners", "香港", "HK$12,000–16,000/月", "未投递");
            jdbc.update("INSERT INTO jobsdb_data(job_id,job_title,company_name,location,salary,delivery_status) VALUES(?,?,?,?,?,?)",
                    "example-2", "Design intern", "Example", "香港", "200元/天", "已投递");
            var stats = jobsdb.getJobsdbStats(List.of("未投递"), "香港", null, null, "SQL");
            assertEquals(1, stats.kpi.total);
            assertEquals(14.0, stats.kpi.avgMonthlyK);
            assertEquals(1, jobsdb.listJobsdbJobs(List.of("未投递"), "香港", "SQL", 1, 20).total);
            assertTrue(jobsdb.listJobsdbJobs(List.of(), null, null, Integer.MAX_VALUE, 50).items.isEmpty());
        }
        try (var app = new SpringApplicationBuilder(GetJobsApplication.class).web(WebApplicationType.NONE).run(args)) {
            var profile = app.getBean(AiService.class).getAiConfig();
            assertEquals("Synthetic SQL project", profile.getIntroduce());
            assertEquals("Custom template", profile.getPrompt());
            assertEquals(2, app.getBean(JobsdbService.class).getJobsdbStats(List.of(), null, null, null, null).kpi.total);
        }
    }
}
