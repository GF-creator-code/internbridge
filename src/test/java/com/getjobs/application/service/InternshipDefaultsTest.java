package com.getjobs.application.service;

import com.getjobs.worker.jobsdb.JobsdbConfig;
import com.getjobs.worker.utils.KeywordFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InternshipDefaultsTest {
    @Test void freshJobsdbUsesCollectionAndLegacyEmptyFilterAllowsAnyTitle() {
        assertTrue(new JobsdbConfig().isCollectOnly());
        assertTrue(KeywordFilter.matchesInclude("Software Engineering Intern", KeywordFilter.parse("\"[]\"")));
        assertTrue(KeywordFilter.matchesExclude("Marketing Intern", "Example", KeywordFilter.parse("[\"Marketing\"]")));
    }

    @Test void monthlySummaryDistinguishesPayPeriodsAndUsesAllEligibleAmounts() {
        var summary = new MonthlySalarySummary();
        for (String raw : new String[]{"100-150元/天", "HK$60/小時", "薪資面議", "8000"}) summary.add(raw);
        assertNull(summary.average());
        summary.add("HK$12,000 - HK$16,000/月");
        summary.add("12–18K");
        summary.add("HK$14k-18k/月");
        summary.add("7,000 per month");
        assertEquals(13.0, summary.average());
        assertEquals(1, summary.bucket(0));
        assertEquals(1, summary.bucket(1));
        assertEquals(2, summary.bucket(2));
    }
}
