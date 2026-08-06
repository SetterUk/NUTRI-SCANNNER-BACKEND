import time
import requests
from datetime import datetime

# Render spins down free tier instances after 15 minutes of inactivity.
# We will ping the health endpoint every 10 minutes (600 seconds) to prevent this.
PING_INTERVAL_SECONDS = 600
URL = "https://nutri-scanner-api.onrender.com/health"

print(f"Starting Keep-Alive script for {URL}")
print(f"Pinging every {PING_INTERVAL_SECONDS // 60} minutes...\n")

while True:
    try:
        current_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        response = requests.get(URL)
        
        if response.status_code == 200:
            print(f"[{current_time}] Ping successful! Server is awake.")
        else:
            print(f"[{current_time}] Ping failed with status code: {response.status_code}")
            
    except requests.exceptions.RequestException as e:
        print(f"[{current_time}] Error connecting to server: {e}")
        
    # Wait for the specified interval before pinging again
    time.sleep(PING_INTERVAL_SECONDS)
