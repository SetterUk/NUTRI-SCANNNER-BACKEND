import requests
import base64
import os

# A tiny 1x1 pixel base64 image for testing
b64_image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="

url = "http://127.0.0.1:8000/api/scan/9999999/contribute"
payload = {
    "name": "Test Base64 Image Product",
    "ingredients_text": "Water, Sugar, Magic",
    "image_base64": f"data:image/png;base64,{b64_image}"
}

print("Testing backend /contribute API...")
try:
    response = requests.post(url, json=payload, timeout=15)
    print(f"Status Code: {response.status_code}")
    print(f"Response: {response.text}")
    
    # Check if file was created in app/static/images
    # We don't know the exact timestamp, so let's list the directory
    if os.path.exists("app/static/images"):
        files = os.listdir("app/static/images")
        test_files = [f for f in files if f.startswith("9999999_")]
        if test_files:
            print(f"Success! Image was saved as: {test_files[0]}")
        else:
            print("Failed: Image file not found in directory.")
    else:
        print("Failed: app/static/images directory not created.")
except Exception as e:
    print(f"Error: {e}")
