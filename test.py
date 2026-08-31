import urllib.request, json, urllib.error
req = urllib.request.Request(
    'https://nutri-scanner-api.onrender.com/api/chat', 
    data=json.dumps({'messages':[{'role':'user','content':'Is a banana healthy?'}]}).encode('utf-8'), 
    headers={'Content-Type':'application/json'}, 
    method='POST'
)
try:
    resp = urllib.request.urlopen(req)
    print(resp.read().decode('utf-8'))
except urllib.error.HTTPError as e:
    print(e.read().decode('utf-8'))
