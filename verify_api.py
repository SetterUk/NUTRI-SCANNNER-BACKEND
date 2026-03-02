import requests

def test_scan():
    url = "http://127.0.0.1:8000/api/scan/8906010500344"
    params = {"user_profile": "General Health"}
    try:
        response = requests.post(url, params=params)
        print(f"Status Code: {response.status_code}")
        if response.status_code == 200:
             print("Success! Scan result returned.")
             print(response.json())
        else:
             print("Failed.")
             print(response.text)
    except Exception as e:
        print(f"Error connecting: {e}")

if __name__ == "__main__":
    test_scan()
