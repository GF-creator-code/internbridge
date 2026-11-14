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
                        # These canvases appear only after the real stats response is rendered.
                        expect(page.locator("canvas").first).to_be_visible(timeout=8000)
                        expect(page.get_by_text("岗位列表", exact=True)).to_be_visible()
                        self.assertEqual(errors, [])
                    finally:
                        page.close()


if __name__ == "__main__":
    unittest.main()
