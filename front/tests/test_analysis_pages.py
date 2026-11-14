"""Read-only browser checks. Run against the local service with Python Playwright."""
import os
import unittest
from playwright.sync_api import expect, sync_playwright


class AnalysisPagesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.base = os.getenv("GETJOBS_TEST_URL", "http://127.0.0.1:9527").rstrip("/")
        cls.playwright = sync_playwright().start()
        cls.browser = cls.playwright.chromium.launch(
            channel=os.getenv("GETJOBS_TEST_BROWSER", "chrome"), headless=True
        )

    @classmethod
    def tearDownClass(cls):
        cls.browser.close()
        cls.playwright.stop()

    def test_analysis_opens_from_tab_and_direct_link(self):
        for platform in ("shixiseng", "jobsdb", "offertoday"):
            for direct in (False, True):
                with self.subTest(platform=platform, direct=direct):
                    page = self.browser.new_page()
                    errors = []
                    page.on("pageerror", lambda error: errors.append(str(error)))
                    try:
                        path = f"/{platform}" + ("/analysis" if direct else "")
                        page.goto(self.base + path, wait_until="domcontentloaded")
                        if not direct:
                            page.get_by_role("tab", name="投递分析").click()
                        # A fresh database also has a valid, useful analysis page.
                        title = page.get_by_role("heading", name="岗位列表")
                        expect(title).to_be_visible(timeout=8000)
                        expect(page.get_by_text("总岗位数", exact=True)).to_be_visible(timeout=8000)
                        expect(page.locator("main").get_by_role("alert")).to_have_count(0)
                        expect(page.get_by_role("region", name="岗位列表，可横向滚动").or_(
                            page.get_by_text("尚无岗位记录。", exact=False))).to_be_visible(timeout=8000)
                        self.assertEqual(errors, [])
                    finally:
                        page.close()


if __name__ == "__main__":
    unittest.main()
