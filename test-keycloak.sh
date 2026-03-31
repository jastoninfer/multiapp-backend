KC=http://localhost:8080
REALM=multiapp
CLIENT_ID=multiapp-cli
USERNAME='astoninfer@gmail.com'
PASSWORD='1234'

#curl -sS -w '\nHTTP_STATUS=%{http_code}\n' \
#  -X POST "$KC/realms/$REALM/protocol/openid-connect/token" \
#  -H 'Content-Type: application/x-www-form-urlencoded' \
#  --data-urlencode "grant_type=password" \
#  --data-urlencode "client_id=$CLIENT_ID" \
#  --data-urlencode "username=$USERNAME" \
#  --data-urlencode "password=$PASSWORD"

# 请求token
TOKEN_JSON=$(curl -s -X POST "$KC/realms/$REALM/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d "grant_type=password" \
  -d "client_id=$CLIENT_ID" \
  -d "username=$USERNAME" \
  -d "password=$PASSWORD" \
  -d "scope=openid phone"
  )
# 取回access_token
ACCESS_TOKEN="$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("access_token",""))' <<< "$TOKEN_JSON")"

#echo $ACCESS_TOKEN
#test -n "$ACCESS_TOKEN" && echo "got access token" || echo "no access token"
API=http://localhost:8081
# GET /me
#curl -i "$API/me" -H "Authorization: Bearer $ACCESS_TOKEN"
# GET /tenants
curl -i "$API/tenants" -H "Authorization: Bearer $ACCESS_TOKEN" \
-H "X-Tenant-Id: 00000000-0000-0000-0000-000000000002"