import os
import sys
import time
import logging
from datetime import datetime
import requests

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
# Render spins down free-tier instances after 15 minutes of inactivity.
# Pinging every 8 minutes (480s) provides a safety margin in case of network drops.
DEFAULT_URL = os.getenv("KEEP_ALIVE_URL", "https://nutri-scanner-api.onrender.com/health")
DEFAULT_INTERVAL_SECONDS = int(os.getenv("KEEP_ALIVE_INTERVAL", "480"))  # 8 minutes
RETRY_DELAY_SECONDS = 15  # Retry quickly if a ping fails
REQUEST_TIMEOUT_SECONDS = 45  # Cold starts on Render take up to 30-40 seconds

# Setup logging (writes to console and keep_alive.log)
log_file = os.path.join(os.path.dirname(os.path.abspath(__file__)), "keep_alive.log")
logging.basicConfig(
    level=logging.INFO,
    format="[%(asctime)s] %(levelname)s: %(message)s",
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler(log_file, encoding="utf-8")
    ]
)
logger = logging.getLogger("KeepAlive")


def ping_server(url: str) -> bool:
    """Send a GET request to wake/keep awake the server."""
    start_time = time.time()
    try:
        response = requests.get(url, timeout=REQUEST_TIMEOUT_SECONDS)
        elapsed = time.time() - start_time
        if response.status_code == 200:
            logger.info("Server is ALIVE (HTTP 200) - Response time: %.2fs", elapsed)
            return True
        else:
            logger.warning("Server returned HTTP %d (%.2fs)", response.status_code, elapsed)
            return False
    except requests.exceptions.Timeout:
        logger.warning("Ping timed out after %ds (Server might be waking up...)", REQUEST_TIMEOUT_SECONDS)
        return False
    except requests.exceptions.RequestException as e:
        logger.error("Connection error: %s", e)
        return False


def main():
    url = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_URL
    interval = int(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_INTERVAL_SECONDS

    logger.info("==================================================")
    logger.info(" Render Keep-Alive Service Started")
    logger.info(" Target URL : %s", url)
    logger.info(" Interval   : %d seconds (%d mins)", interval, interval // 60)
    logger.info(" Log File   : %s", log_file)
    logger.info("==================================================")

    consecutive_failures = 0

    while True:
        success = ping_server(url)

        if success:
            consecutive_failures = 0
            time.sleep(interval)
        else:
            consecutive_failures += 1
            if consecutive_failures < 3:
                logger.info("Retrying in %ds (Attempt %d/3)...", RETRY_DELAY_SECONDS, consecutive_failures)
                time.sleep(RETRY_DELAY_SECONDS)
            else:
                logger.error("3 consecutive pings failed. Waiting normal interval (%ds) before next attempt.", interval)
                time.sleep(interval)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        logger.info("Keep-Alive stopped by user.")

