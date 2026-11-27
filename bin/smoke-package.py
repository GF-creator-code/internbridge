"""Start a release JAR in an empty directory and check its first-run pages."""

import argparse
import json
import os
import socket
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from urllib.error import URLError
from urllib.request import urlopen


def request(origin, route):
    with urlopen(origin + route, timeout=3) as response:
        return response.read()


def wait_for_health(process, origin):
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"Java exited before startup ({process.returncode})")
        try:
            if json.loads(request(origin, "/api/health")).get("status") == "UP":
                return
        except (URLError, TimeoutError):
            pass
        time.sleep(0.5)
    raise TimeoutError("The application did not become ready within 90 seconds")


def check_jar(jar, browser_tests):
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        port = listener.getsockname()[1]
    origin = f"http://127.0.0.1:{port}"

    with tempfile.TemporaryDirectory(prefix="internbridge smoke ") as folder:
        directory = Path(folder)
        log = directory / "startup.log"
        with log.open("wb") as output:
            process = subprocess.Popen(
                ["java", "-Dfile.encoding=UTF-8", "-jar", str(jar),
                 f"--server.port={port}", "--spring.output.ansi.enabled=never",
                 "--internbridge.open-dashboard=false", "--internbridge.open-platforms=false"],
                cwd=directory, stdout=output, stderr=subprocess.STDOUT,
            )
            try:
                wait_for_health(process, origin)
                for route in ("/", "/settings", "/dashboard", "/jobsdb"):
                    if b"/_next/static/" not in request(origin, route):
                        raise RuntimeError(f"Missing packaged frontend at {route}")
                if not (directory / "db" / "getjobs.db").is_file():
                    raise RuntimeError("The local database was not created")
                jobsdb = json.loads(request(origin, "/api/jobsdb/config"))["config"]
                if jobsdb.get("collectOnly") is not True:
                    raise RuntimeError("JobsDB must default to collection only")
                if browser_tests:
                    tests = Path(__file__).resolve().parents[1] / "front" / "tests"
                    subprocess.run(
                        [sys.executable, "-m", "unittest", "discover", "-s", str(tests), "-v"],
                        env={**os.environ, "GETJOBS_TEST_URL": origin}, check=True,
                    )
            except Exception:
                print(log.read_text(encoding="utf-8", errors="replace"), file=sys.stderr)
                raise
            finally:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path)
    parser.add_argument("--browser-tests", action="store_true", help="run the Playwright UI suite against the JAR")
    arguments = parser.parse_args()
    check_jar(arguments.jar.resolve(strict=True), arguments.browser_tests)
    print("Packaged app passed first-run checks")
