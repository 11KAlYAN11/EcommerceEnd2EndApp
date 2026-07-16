"""Provision Grafana alerting: Telegram contact point + notification policy + alert rules.

Run on the EC2 host (needs Grafana on localhost:3000).
Idempotent-ish: uses fixed UIDs and overwrite semantics where the API allows.
"""
import json
import urllib.request
import urllib.error
import base64
import os

GRAFANA = 'http://localhost:3000'
AUTH = base64.b64encode(b'admin:ShopEase@Grafana2024').decode()
HEADERS = {'Authorization': 'Basic ' + AUTH, 'Content-Type': 'application/json'}
DS_UID = 'ffsaghhzn7vuoa'

BOT_TOKEN = os.environ['TELEGRAM_BOT_TOKEN']
CHAT_ID = os.environ['TELEGRAM_CHAT_ID']


def api(method, path, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(GRAFANA + path, data=data, headers=HEADERS, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            body = resp.read()
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        return {'error': e.code, 'body': e.read().decode()[:300]}


# 1. Telegram contact point
cp = api('PUT', '/api/v1/provisioning/contact-points/telegram-shopease', {
    'uid': 'telegram-shopease',
    'name': 'Telegram',
    'type': 'telegram',
    'settings': {'bottoken': BOT_TOKEN, 'chatid': CHAT_ID},
})
if cp.get('error') == 404:  # doesn't exist yet -> create
    cp = api('POST', '/api/v1/provisioning/contact-points', {
        'uid': 'telegram-shopease',
        'name': 'Telegram',
        'type': 'telegram',
        'settings': {'bottoken': BOT_TOKEN, 'chatid': CHAT_ID},
    })
print('contact point:', cp)

# 2. Route everything to Telegram
pol = api('PUT', '/api/v1/provisioning/policies', {
    'receiver': 'Telegram',
    'group_by': ['alertname'],
    'group_wait': '30s',
    'group_interval': '2m',
    'repeat_interval': '4h',
})
print('policy:', pol)

# 3. Folder for the rules
folder = api('POST', '/api/folders', {'uid': 'shopease-alerts', 'title': 'ShopEase Alerts'})
print('folder:', folder.get('uid', folder))


def rule(uid, title, expr, threshold, summary, for_='2m'):
    return {
        'uid': uid,
        'title': title,
        'ruleGroup': 'shopease',
        'folderUID': 'shopease-alerts',
        'condition': 'C',
        'noDataState': 'OK',
        'execErrState': 'OK',
        'for': for_,
        'annotations': {'summary': summary},
        'data': [
            {
                'refId': 'A',
                'relativeTimeRange': {'from': 300, 'to': 0},
                'datasourceUid': DS_UID,
                'model': {'expr': expr, 'refId': 'A', 'instant': True},
            },
            {
                'refId': 'C',
                'relativeTimeRange': {'from': 0, 'to': 0},
                'datasourceUid': '__expr__',
                'model': {
                    'refId': 'C',
                    'type': 'threshold',
                    'expression': 'A',
                    'conditions': [{
                        'evaluator': {'type': 'gt', 'params': [threshold]},
                    }],
                },
            },
        ],
    }


rules = [
    rule('shopease-5xx',
         'High 5xx error rate',
         'sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))'
         ' / sum(rate(http_server_requests_seconds_count[5m]))',
         0.01,
         'More than 1% of requests are failing with 5xx'),
    rule('shopease-latency',
         'High API latency',
         'max(http_server_requests_seconds_max{uri!~"/actuator.*|root|UNKNOWN"})',
         1.0,
         'Slowest endpoint response time exceeded 1 second'),
    rule('shopease-db-pool',
         'DB connection pool exhausted',
         'max(hikaricp_connections_pending)',
         0,
         'Requests are waiting for a database connection (HikariCP pending > 0)',
         for_='1m'),
    rule('shopease-app-down',
         'App is down',
         'up{job="shopease"}',
         -1,  # placeholder; replaced below with lt evaluator
         'Prometheus cannot scrape the ShopEase app',
         for_='1m'),
]
# app-down alert fires when up < 1
rules[3]['data'][1]['model']['conditions'][0]['evaluator'] = {'type': 'lt', 'params': [1]}

for r in rules:
    res = api('POST', '/api/v1/provisioning/alert-rules', r)
    if res.get('error'):
        # try update if it already exists
        res = api('PUT', f"/api/v1/provisioning/alert-rules/{r['uid']}", r)
    print(r['title'], '->', res.get('uid', res))
