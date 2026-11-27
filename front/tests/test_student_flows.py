"""Exercise student workflows with an in-memory profile and synthetic postings only."""
import csv
import io
import os
from pathlib import Path
import unittest

from playwright.sync_api import expect, sync_playwright


class StudentFlowsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.base = os.getenv("GETJOBS_TEST_URL", "http://127.0.0.1:9527").rstrip("/")
        cls.driver = sync_playwright().start()
        cls.browser = cls.driver.chromium.launch(channel=os.getenv("GETJOBS_TEST_BROWSER", "chrome"))

    @classmethod
    def tearDownClass(cls):
        cls.browser.close()
        cls.driver.stop()

    def setUp(self):
        self.page = self.browser.new_page(viewport={"width": 390, "height": 844}, reduced_motion="reduce")

    def tearDown(self):
        self.page.close()

    def test_upload_save_retry_compare_and_reload(self):
        profile = {"introduce": "Python project", "prompt": "Custom template"}
        skill = {"name": "SQL", "evidence": "Built SQL reports"}
        ignored = {"name": "Excel", "evidence": "Prepared Excel charts"}
        attempts = []
        comparisons = []

        def config(route):
            if route.request.method == "POST":
                attempts.append(route.request.post_data_json)
                if len(attempts) == 1:
                    route.fulfill(status=503, json={"success": False})
                    return
                profile.update(attempts[-1])
            route.fulfill(json={"success": True, "data": profile})

        def match(route):
            comparisons.append(route.request.post_data_json)
            route.fulfill(json={"matches": [{"requirement": "SQL", "evidence": skill["evidence"]}],
                                "gaps": ["粵語"]})

        p = self.page
        p.route("**/api/ai/config", config)
        p.route("**/api/boss/config", lambda r: r.fulfill(json={"config": {"enableAi": False}}))
        p.route("**/api/ai/resume/skills", lambda r: r.fulfill(json=[skill, ignored]))
        p.route("**/api/ai/resume/match", match)
        p.goto(self.base + "/settings?tab=match")
        expect(p.get_by_label("技能介绍", exact=True)).to_have_value("Python project")
        p.locator("#resume-file").set_input_files({"name": "synthetic.txt", "mimeType": "text/plain",
                                                    "buffer": b"Built SQL reports. Prepared Excel charts."})
        p.get_by_role("checkbox", name="Excel", exact=False).uncheck()
        p.get_by_role("button", name="加入并保存所选技能").click()
        expect(p.get_by_text("保存失败，修改仍在本页，可再次保存。")).to_be_visible()
        expect(p.locator("#introduce")).to_have_value("Python project\nSQL：Built SQL reports")
        p.get_by_role("button", name="保存修改").click()
        expect(p.get_by_text("已保存，下次投递任务将使用这份介绍。")).to_be_visible()
        self.assertEqual(len(attempts), 2)
        self.assertEqual(profile["prompt"], "Custom template")
        self.assertNotIn("Excel", profile["introduce"])
        p.get_by_label("职位描述", exact=True).fill("SQL 和粵語")
        p.get_by_role("button", name="分析岗位匹配").click()
        expect(p.get_by_role("heading", name="暂缺依据的要求（1）")).to_be_visible()
        expect(p.get_by_text("粵語", exact=True)).to_be_visible()
        self.assertEqual(comparisons, [{"profile": profile["introduce"], "job": "SQL 和粵語"}])
        p.get_by_label("职位描述", exact=True).fill("新要求")
        expect(p.get_by_role("heading", name="暂缺依据的要求（1）")).to_have_count(0)
        p.reload()
        expect(p.locator("#introduce")).to_have_value(profile["introduce"])

    def test_config_load_error_can_retry_without_saving_a_blank_profile(self):
        calls = []
        def request(route):
            calls.append(route.request.method)
            if len(calls) == 1:
                route.fulfill(status=503, json={"success": False})
            else:
                route.fulfill(json={"success": True, "data": {"introduce": "Recovered profile", "prompt": "template"}})
        self.page.route("**/api/ai/config", request)
        self.page.goto(self.base + "/settings?tab=match")
        expect(self.page.get_by_text("无法读取已保存的技能介绍。", exact=False)).to_be_visible()
        expect(self.page.locator("#introduce")).to_have_count(0)
        self.page.get_by_role("button", name="重新读取").click()
        expect(self.page.locator("#introduce")).to_have_value("Recovered profile")
        self.assertEqual(calls, ["GET", "GET"])

    def test_keyboard_navigation_and_mobile_width(self):
        p = self.page
        p.goto(self.base + "/")
        expect(p.get_by_role("heading", name="欢迎使用 InternBridge")).to_be_visible()
        menu = p.get_by_role("button", name="导航", exact=True)
        menu.click()
        expect(p.get_by_role("dialog", name="导航菜单")).to_be_visible()
        p.keyboard.press("Escape")
        expect(menu).to_be_focused()
        menu.click()
        p.get_by_role("dialog").get_by_role("link", name="JobsDB").click()
        expect(p).to_have_url(self.base + "/jobsdb")
        expect(p.get_by_role("dialog", name="导航菜单")).to_have_count(0)
        for width in (320, 390, 1440):
            p.set_viewport_size({"width": width, "height": 844})
            self.assertLessEqual(p.evaluate("document.documentElement.scrollWidth"), width)

    def test_filters_table_charts_and_export_use_the_same_records(self):
        p = self.page
        samples = [
            {"id": 1, "jobId": "sample-1", "companyName": 'Example, "Partners"', "jobTitle": "SQL Intern",
             "salary": "HK$15,000/月", "location": "香港", "deliveryStatus": "未投递",
             "jobLink": "https://example.com/opening", "createTime": "2026-01-01"},
            {"id": 2, "jobId": "sample-2", "companyName": "Another", "jobTitle": "Design Intern",
             "location": "香港", "deliveryStatus": "已投递", "jobLink": "https://example.com/design"},
        ]
        from urllib.parse import parse_qs, urlparse
        def data(route):
            params = parse_qs(urlparse(route.request.url).query)
            records = [row for row in samples if params.get("keyword", [""])[0].lower() in row["jobTitle"].lower()
                       and row["deliveryStatus"] in params.get("statuses", [row["deliveryStatus"]])[0].split(",")]
            if "/stats" in route.request.url:
                route.fulfill(json={"kpi": {"total": len(records), "delivered": 0, "pending": len(records), "failed": 0},
                                    "charts": {"byStatus": [{"name": "未投递", "value": len(records)}] if records else [],
                                               "byCity": [], "byCompany": [], "salaryBuckets": []}})
            else:
                route.fulfill(json={"items": records, "total": len(records), "page": 1, "size": 20})
        p.route("**/api/jobsdb/stats?*", data)
        p.route("**/api/jobsdb/list?*", data)
        p.goto(self.base + "/jobsdb")
        p.get_by_role("tab", name="投递分析").click()
        expect(p.get_by_role("link", name="Design Intern")).to_be_visible()
        p.get_by_label("公司或岗位关键词").fill("SQL")
        p.get_by_role("button", name="未投递", exact=True).click()
        expect(p.get_by_role("button", name="导出筛选结果 CSV")).to_be_disabled()
        p.get_by_role("button", name="应用筛选").click()
        expect(p.get_by_role("link", name="SQL Intern")).to_be_visible()
        expect(p.get_by_role("link", name="Design Intern")).to_have_count(0)
        expect(p.locator("canvas").first).to_be_visible()
        with p.expect_download() as download:
            p.get_by_role("button", name="导出筛选结果 CSV").click()
        self.assertTrue(download.value.suggested_filename.startswith("jobsdb_jobs_"))
        rows = list(csv.reader(io.StringIO(Path(download.value.path()).read_text(encoding="utf-8-sig"))))
        self.assertEqual(len(rows), 2)
        self.assertEqual(rows[1][:2], ['Example, "Partners"', "SQL Intern"])
        p.get_by_role("button", name="重置筛选").click()
        expect(p.get_by_role("link", name="Design Intern")).to_be_visible()


if __name__ == "__main__":
    unittest.main()
